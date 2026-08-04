# Unified Evidence Model

Tuần 3. Thiết kế dựa trên artifact **thật** trong [`docs/samples/vulnshop/`](samples/vulnshop/) —
không hình dung trước rồi sửa sau. Mỗi trường trong bảng provenance dưới đây trỏ được về đúng
chỗ trong artifact thật, và `docs/check-evidence-model.py` kiểm chứng tự động điều đó (xem cuối
tài liệu).

Phạm vi tuần 3 (V2, đã hiện thực): `analysis_run`, `evidence_record`, `warning`, `evidence_link`,
`claim`, `claim_citation`. Bảng "thô" mirror trực tiếp từng công cụ (`test_case`, `stack_frame`,
`coverage_line`, `coverage_method`, `warning_location`) **chưa tạo** — xem mục "Thiết kế cho V5"
ở cuối, kèm 4 phát hiện từ artifact thật làm căn cứ.

> **Về số hiệu migration**: tài liệu này và kế hoạch 20 tuần ban đầu đều gọi đợt bảng thô là "V3".
> Tuần 5-7 đã dùng mất `V3__auth_and_snapshot_files.sql` và `V4__project_owner_not_null.sql`, nên
> số hiệu thật của đợt bảng thô là **`V5`**. Mọi chỗ ghi "V3" trong ngữ cảnh bảng thô đã được sửa
> thành V5; giữ lại ghi chú này để đối chiếu được với các commit và tài liệu cũ.

## 1. 6 loại evidence

| Loại | Sinh từ | V2 lưu ở |
|---|---|---|
| `STATIC_WARNING` | SpotBugs, Semgrep | `evidence_record` + `warning` |
| `CODE_CONTEXT` | JavaParser (tuần 10-11) | `evidence_record` (nội dung rỗng ở V2) |
| `TEST_RESULT` | Surefire | `evidence_record` (V5 mới có `test_case`) |
| `FAILED_TEST` | Surefire | `evidence_record` (V5 mới có `test_case`) |
| `STACK_TRACE` | Surefire | `evidence_record` (V5 mới có `stack_frame`) |
| `COVERAGE_INFORMATION` | JaCoCo | `evidence_record` (V5 mới có `coverage_line`/`coverage_method`) |

V2 cho phép mọi loại evidence tồn tại như một `evidence_record` (đơn vị citable), nhưng chỉ
`STATIC_WARNING` có bảng mở rộng 1:1 (`warning`) lưu metadata riêng ở dạng có ràng buộc SQL;
4 loại còn lại tạm lưu toàn bộ chi tiết trong `content` JSONB cho tới khi bảng thô V5 ra đời.

## 2. Bảng provenance — `evidence_record` (chung cho mọi loại)

Locator là biểu thức mà [`check-evidence-model.py`](check-evidence-model.py) chạy thật trên
artifact để xác nhận giá trị ví dụ khớp. `DERIVED` nghĩa là cột không đến từ một artifact cụ thể
— luôn kèm lý do.

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| id | DERIVED | sinh UUID lúc materialize | — |
| seq_in_run | DERIVED | đếm tăng dần trong phạm vi run | — |
| short_id | DERIVED | generated column Postgres (`EV-XXXX`) | `EV-0001A` |
| analysis_run_id | DERIVED | FK tới analysis_run của lần chạy | — |
| type | DERIVED | parser chọn theo loại tool (spotbugs/semgrep→STATIC_WARNING) | `STATIC_WARNING` |
| source | DERIVED | tên tool literal | `spotbugs` |
| file_path | spotbugs / semgrep / jacoco | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@sourcepath` | `vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java` |
| start_line | spotbugs / semgrep | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@start` | `40` |
| end_line | spotbugs / semgrep | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@end` | `40` |
| symbol | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/Method/@name` | `checksumForDeduplication` |
| summary | DERIVED | ghép từ message của tool, luôn dựng được kể cả khi vị trí NULL | — |
| content | DERIVED | payload JSONB đặc thù theo loại + metadata riêng tool | — |
| status | DERIVED | mặc định `ACTIVE` | `ACTIVE` |
| created_at | DERIVED | thời điểm materialize | — |

**Giá trị lưu xuống `file_path` là bản ĐÃ CHUẨN HOÁ về `snapshot_file.path`** (đường dẫn tương đối
gốc repo đã upload), còn cột "Ví dụ" ở trên là giá trị **nguyên văn trong artifact** mà locator đọc
được. Hai thứ khác nhau là bình thường: `sourcepath` của SpotBugs là package-relative
(`vn/hust/.../PasswordHasher.java`), trong khi đường dẫn tra cứu được của snapshot là
`src/main/java/vn/hust/.../PasswordHasher.java`. Giá trị nguyên văn vẫn được giữ lại trong
`content.path_raw` để reparse được khi heuristic chuẩn hoá sai.

Ví dụ đối chứng thêm cho `file_path` từ hai nguồn còn lại (không map trực tiếp vào một cột riêng,
nhưng xác nhận công thức chuẩn hoá path đúng — xem mục 5):

- **Semgrep** (`unvalidated-redirect`, `AuthController.java:49-52`): SARIF ghi đường dẫn tuyệt đối
  trong container `results[0].locations[0].physicalLocation.artifactLocation.uri` =
  `/work/vulnshop/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java`.
  **Không được chuẩn hoá bằng cách strip tiền tố `/work/vulnshop/src/main/java/`** — tiền tố này
  chỉ là hệ quả của cách `case-study/run-analysis.sh` mount thư mục vào container Docker
  (`/work` = thư mục `case-study/`); đổi cách mount, đổi máy, hoặc chạy qua sandbox của
  core-backend là tiền tố khác ngay, và mọi vị trí âm thầm không khớp được với snapshot.
  Cách đúng: **khớp hậu tố dài nhất, duy nhất** với danh sách `snapshot_file.path` — rút dần
  segment đầu (`/work/...` → `work/...` → `vulnshop/...` → `src/main/java/...`) tới khi còn đúng
  một ứng viên. Không tìm được thì để `file_path` NULL và **đếm vào `analysis_run.unresolved_location_count`**,
  không bao giờ đoán bừa.
- **JaCoCo** (`OrderStatusChecker.java`): không có cột path — phải ghép
  `package/@name` (`vn/hust/datn/casestudy/vulnshop/order`) + `'/'` + `sourcefile/@name`
  (`OrderStatusChecker.java`), rồi mới đưa qua cùng bước khớp hậu tố như trên.

## 3. Bảng provenance — `warning` (mở rộng 1:1 cho STATIC_WARNING)

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| evidence_record_id | DERIVED | FK = PK, `@OneToOne @MapsId` | — |
| tool | DERIVED | literal theo nguồn parse | `spotbugs` |
| rule_id | spotbugs / semgrep | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/@type` | `WEAK_MESSAGE_DIGEST_MD5` |
| rule_id_normalized | DERIVED | bỏ tiền tố rác `work.semgrep-rules.` của Semgrep | `use-of-md5` |
| severity_raw | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/@priority` | `1` |
| severity_normalized | DERIVED | mapping tường minh (mục 4) | `HIGH` |
| cwe_ids | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/@cweid` | `328` |
| has_primary_location | DERIVED | true nếu tồn tại `SourceLine[@primary='true']` con trực tiếp | `true` |
| source_index | DERIVED | thứ tự phần tử trong file gốc | `0` |
| severity_rank | DERIVED | generated column, sinh từ severity_normalized (HIGH=0…INFO=3) | `0` |

`severity_rank` tồn tại vì `ORDER BY severity_normalized` là thứ tự **chữ cái**: HIGH, INFO, LOW,
MEDIUM. Sai mà rất khó thấy vì HIGH vẫn đứng đầu. Mọi truy vấn sắp xếp theo mức nghiêm trọng phải
dùng cột này.

Ví dụ thứ hai cho `rule_id`/`severity`, minh hoạ Semgrep KHÔNG có `severity_raw` (đã xác nhận
`result.level` không tồn tại trên SARIF result, chỉ có ở `tool.driver.rules[].defaultConfiguration.level`):

```json
{
  "ruleId": "work.semgrep-rules.java.spring.security.audit.spring-sqli.spring-sqli",
  "message": {"text": "Detected a string argument from a public method contract in a raw SQL statement..."}
}
```

## 4. Chuẩn hoá severity

SpotBugs (`priority` 1-3, **1 là cao nhất** — trái trực giác, ghi rõ trong code) và Semgrep
(`level` + `precision`) dùng hai thang rời rạc không giao nhau. Mapping tường minh:

| Nguồn | Giá trị gốc | `severity_normalized` |
|---|---|---|
| SpotBugs | priority = 1 | HIGH |
| SpotBugs | priority = 2 | MEDIUM |
| SpotBugs | priority = 3 | LOW |
| Semgrep | level = error | HIGH |
| Semgrep | level = warning, precision = very-high | MEDIUM |
| Semgrep | level = warning (precision khác) | LOW |
| Semgrep | level = note | INFO |

Ghi chú cho người viết test ở tuần 8-9: trong `semgrep.sarif` thật, **cả 60/60 rule đều có
`precision: very-high`**, nên nhánh *"level = warning, precision khác"* → LOW **không có mẫu thật
nào**; chỉ kiểm được bằng fixture tự chế. Hai nhánh `level = error` (20 rule) và `level = note`
(1 rule) thì có rule thật trong `tool.driver.rules[]` — ghép một `result` giả trỏ tới rule có
thật là test được, không cần bịa cả rule.

## 5. ERD

```mermaid
erDiagram
    PROJECT ||--o{ REPOSITORY_SNAPSHOT : "co nhieu"
    REPOSITORY_SNAPSHOT ||--o{ ANALYSIS_RUN : "duoc phan tich trong"
    ANALYSIS_RUN ||--o{ EVIDENCE_RECORD : "sinh ra"
    EVIDENCE_RECORD ||--o| WARNING : "mo rong 1:1 (STATIC_WARNING)"
    EVIDENCE_RECORD ||--o{ EVIDENCE_LINK : "from"
    EVIDENCE_RECORD ||--o{ EVIDENCE_LINK : "to"
    CLAIM ||--o{ CLAIM_CITATION : "trich dan"
    CLAIM_CITATION }o--o| EVIDENCE_RECORD : "resolve toi (co the NULL)"

    PROJECT {
        uuid id PK
        text name
    }
    REPOSITORY_SNAPSHOT {
        uuid id PK
        uuid project_id FK
        text commit_sha "nullable - khong artifact nao co"
    }
    ANALYSIS_RUN {
        uuid id PK
        uuid snapshot_id FK
        text status "RUNNING/SUCCEEDED/SUCCEEDED_CLEAN/FAILED"
        int finding_count
    }
    EVIDENCE_RECORD {
        uuid id PK
        text short_id "EV-XXXX, generated"
        text type "6 loai"
        text source "spotbugs/semgrep/surefire/jacoco/parser"
        text file_path "nullable"
        text symbol "nullable - Semgrep khong co"
        jsonb content
    }
    WARNING {
        uuid evidence_record_id PK_FK
        text tool
        text rule_id
        text severity_normalized
        boolean has_primary_location
    }
    EVIDENCE_LINK {
        uuid id PK
        uuid from_evidence_id FK
        uuid to_evidence_id FK
        text relation "SUPPORTS/CONTRADICTS/COVERS/LOCATED_IN"
        text method "do tin cay cua lien ket"
    }
    CLAIM {
        uuid id PK
        text verdict "3 loai"
        text downgraded_from_verdict "nullable"
    }
    CLAIM_CITATION {
        uuid id PK
        uuid claim_id FK
        text cited_short_id_raw "nguyen van LLM viet ra"
        text resolution "RESOLVED/UNKNOWN_ID/WRONG_RUN/MALFORMED"
    }
```

## 6. Bốn phát hiện từ artifact thật quyết định thiết kế

Đã tự parse toàn bộ `docs/samples/vulnshop/`, không suy đoán. Xem thêm
[ADR 0004](adr/0004-unified-evidence-model-hai-tang.md).

1. **Không phải warning nào cũng chỉ có 1 vị trí, nhưng đa số có** — 25/28 BugInstance chỉ có
   đúng 1 `SourceLine` con trực tiếp; 3/28 có 2-3 vị trí (một BugInstance `UNSAFE_HASH_EQUALS`
   trỏ tới 3 file khác nhau: `PasswordHasher.java:48`, `AuthService.java:32`, `User.java:4`).
   5/28 có đúng 1 vị trí nhưng **không** đánh dấu `primary='true'` (chỉ `synthetic='true'`) —
   parser phải fallback, không phải lỗi thiết kế cần bảng riêng.
2. **3/5 test hỏng không có stack frame nào trỏ về mã production.** Toàn bộ test hỏng kiểu
   `AssertionFailedError` (khác `ArithmeticException`/`NullPointerException`) có stack trace chỉ
   chứa frame `org.junit.jupiter.api.*` rồi tới lớp test — không hề đi qua mã đang kiểm thử. Ca
   đau nhất: `OrderStatusCheckerTest` fail vì lỗi ở đúng dòng SpotBugs báo
   `ES_COMPARING_PARAMETER_STRING_WITH_EQ` (`OrderStatusChecker.java:18`), nhưng stack trace
   không hề nhắc tới file đó.
3. **JaCoCo có cấu trúc kép, không lồng nhau.** `package > class > method > counter` (aggregate,
   không có số dòng) tách biệt hoàn toàn với `package > sourcefile > line` (số dòng, không biết
   method). `method/@line` chỉ là dòng đầu, không có dòng cuối, và method **không** theo thứ tự
   dòng (`SortField.<clinit> line=9` nằm sau `column line=22`) — không có cách tin cậy để biết
   "dòng 42 thuộc method nào" chỉ từ `jacoco.xml`.
4. **4 định dạng đường dẫn khác nhau, không join trực tiếp được**: SpotBugs
   `vn/hust/.../Foo.java` (package-relative) · Semgrep `/work/vulnshop/src/main/java/vn/...`
   (tuyệt đối trong container Docker, và `run.originalUriBaseIds` — nơi đáng lẽ khai báo cách
   giải `%SRCROOT%` — **không tồn tại** trong SARIF thật) · JaCoCo phải ghép
   `package/@name + '/' + sourcefile/@name` · Surefire không có path nào, chỉ tên file trần
   trong text thô của stack trace.

## 7. Năm bảng thô (V5, tuần 8-9)

Không tạo ở V2 vì không có consumer nào cho tới EvidenceLinker (tuần 10-11) và parser thật
(tuần 8-9); tạo sớm sẽ là bảng rỗng không parser, không test, không ai biết sai cho tới lúc đó.

| Bảng | Vì sao cần | Khuôn quan hệ |
|---|---|---|
| `warning_location` | Phát hiện 1: 3/28 warning có nhiều vị trí | 1:N `(evidence_record_id, ordinal)`, FK trỏ `warning` |
| `test_case` | Nguồn TEST_RESULT/FAILED_TEST | **1:1 PK=FK** như `warning` |
| `stack_frame` | Phát hiện 2: parse riêng để reparse được nếu heuristic sai | 1:N `(evidence_record_id, ordinal)` |
| `coverage_line` | Giữ `mi/ci/mb/cb` để phân biệt PARTIAL, không thu về boolean | 1:N `(evidence_record_id, line_number)` |
| `coverage_method` | Phát hiện 3: KHÔNG có FK sang `coverage_line` (quan hệ không tồn tại trong dữ liệu thật) | 1:N + UUID PK |

Ba sai lệch có chủ đích so với bản thiết kế ban đầu ở mục này, đều phát sinh khi viết DDL thật:

- `stack_frame.frame_kind` có thêm giá trị **`UNKNOWN`**: một dòng `at ...` không parse được vẫn
  phải sinh ra hàng. Bỏ qua im lặng là đúng thứ dự án này chống.
- `test_case` có thêm `stack_trace_evidence_id`, `display_name`, `report_file`. Cột đầu là
  provenance **cấu trúc** (trace này thuộc test nào), không phải `evidence_link` ngữ nghĩa — nhờ nó
  tuần 8-9 tra được trace của một test hỏng mà chưa cần EvidenceLinker.
- `warning_location` có thêm `path_raw` (đường dẫn nguyên văn) bên cạnh `file_path` (đã chuẩn hoá),
  để reparse được khi heuristic chuẩn hoá sai.

`evidence_link.method` đã có sẵn giá trị `STACK_FRAME`/`METHOD_AGGREGATE`/`NAMING_CONVENTION` chờ
EvidenceLinker vì phát hiện 2 chứng minh không thể chỉ dựa vào stack trace. (Bản trước của tài liệu
này ghi `COVERAGE` — **giá trị đó chưa bao giờ tồn tại** trong CHECK của V2 lẫn enum
`EvidenceLinkMethod`; giá trị đúng cho liên kết theo độ phủ là `METHOD_AGGREGATE`.)

## 8. Bảng provenance — `warning_location`

Ví dụ dùng chung: `WEAK_MESSAGE_DIGEST_MD5` tại `PasswordHasher.java:40` (vị trí `ordinal=0`).

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| evidence_record_id | DERIVED | FK tới `warning.evidence_record_id` (= `evidence_record.id`) | — |
| ordinal | DERIVED | thứ tự `SourceLine` con trực tiếp trong BugInstance | `0` |
| role_raw | spotbugs | `spotbugs:BugInstance[@type='OBL_UNSATISFIED_OBLIGATION']/SourceLine[2]/@role` | `SOURCE_LINE_PATH_CONTINUES` |
| is_primary | DERIVED | có `@primary='true'` hay không; false cho 5/28 bug chỉ có `synthetic='true'` | `true` |
| path_raw | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@sourcepath` | `vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java` |
| file_path | DERIVED | chuẩn hoá `path_raw` về `snapshot_file.path`; NULL nếu không khớp | — |
| start_line | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@start` | `40` |
| end_line | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@end` | `40` |
| class_name | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/SourceLine[@primary='true']/@classname` | `vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher` |
| method_name | spotbugs | `spotbugs:BugInstance[@type='WEAK_MESSAGE_DIGEST_MD5']/Method[@primary='true']/@name` | `checksumForDeduplication` |

Locator của `role_raw` cố tình lấy từ một BugInstance **khác** (`OBL_UNSATISFIED_OBLIGATION`,
vị trí thứ 2): 27/33 vị trí thật không có `@role`, nên ví dụ MD5 sẽ không chứng minh được gì.

## 9. Bảng provenance — `test_case`

Ví dụ dùng chung: `OrderStatusCheckerTest.isShipped_chuoiTaoLucChay_vanPhaiTraVeDung` — test hỏng
kiểu assertion, tức là ca **không có** frame nào trỏ về mã production (3/5 test hỏng thuộc loại
này, phát hiện 2).

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| evidence_record_id | DERIVED | PK = FK tới evidence_record, khuôn 1:1 như warning | — |
| class_name | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/@classname` | `vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest` |
| method_name | DERIVED | tách từ display_name, bỏ hậu tố `[n]` của test tham số hoá | `isShipped_chuoiTaoLucChay_vanPhaiTraVeDung` |
| display_name | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/@name` | `isShipped_chuoiTaoLucChay_vanPhaiTraVeDung` |
| status | DERIVED | có thẻ `failure`/`error`/`skipped` con hay không | `FAILED` |
| failure_kind | DERIVED | tên thẻ: `<failure>`→FAILURE, `<error>`→ERROR | `FAILURE` |
| failure_type | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/failure/@type` | `org.opentest4j.AssertionFailedError` |
| failure_message | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/failure/@message` | `expected: <true> but was: <false>` |
| stack_trace_raw | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/failure` | |
| duration_ms | surefire | `surefire:TEST-vn.hust.datn.casestudy.vulnshop.order.OrderStatusCheckerTest.xml#./testcase[@name='isShipped_chuoiTaoLucChay_vanPhaiTraVeDung']/@time` | `0.002` |
| stack_trace_evidence_id | DERIVED | FK tới evidence_record loại STACK_TRACE của chính test này | — |
| report_file | DERIVED | tên file `TEST-*.xml` đã sinh ra hàng này | — |

`duration_ms` là chỗ duy nhất có **đổi đơn vị**: Surefire ghi `@time` bằng **giây** dạng thập phân,
cột lưu **mili giây** dạng INT. Cột "Ví dụ" ở trên là giá trị nguyên văn trong artifact (`0.002`),
giá trị lưu xuống là `2`.

## 10. Bảng provenance — `stack_frame`

Bảng này **hoàn toàn DERIVED**: mọi cột đến từ việc parse `test_case.stack_trace_raw`, không có
locator nào trỏ thẳng vào artifact được vì stack trace trong Surefire XML là **text thô**, không
có cấu trúc XML.

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| evidence_record_id | DERIVED | FK tới evidence_record loại STACK_TRACE | — |
| ordinal | DERIVED | thứ tự dòng `at ...` trong trace | `0` |
| raw_line | DERIVED | nguyên văn một dòng `at ...` — nguồn để reparse | — |
| module_prefix | DERIVED | phần trước `/` cuối cùng của FQCN | `java.base` |
| declaring_class | DERIVED | FQCN sau khi bỏ module_prefix | — |
| method_name | DERIVED | tên method trong dòng `at ...` | — |
| source_file_name | DERIVED | tên file trần trong ngoặc; NULL cho `(Native Method)` | — |
| line_number | DERIVED | số sau dấu `:` trong ngoặc; NULL khi không có | — |
| file_path | DERIVED | chuẩn hoá từ package + source_file_name về snapshot_file.path | — |
| frame_kind | DERIVED | heuristic theo package; `UNKNOWN` khi dòng không parse được | `PRODUCTION` |

Vì sao vẫn tách thành bảng thay vì chỉ giữ text: heuristic `frame_kind` **sẽ** phải sửa (chỉ 2/5
test hỏng thật có frame production), và khi sửa phải reparse được từ `raw_line` mà không cần quay
lại artifact gốc.

## 11. Bảng provenance — `coverage_line`

Ví dụ dùng chung: `OrderStatusChecker.java` dòng 27 — một dòng `MISSED` có nhánh chưa chạy.

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| evidence_record_id | DERIVED | FK tới evidence_record loại COVERAGE_INFORMATION cấp sourcefile | — |
| line_number | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/sourcefile[@name='OrderStatusChecker.java']/line[@nr='27']/@nr` | `27` |
| mi | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/sourcefile[@name='OrderStatusChecker.java']/line[@nr='27']/@mi` | `10` |
| ci | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/sourcefile[@name='OrderStatusChecker.java']/line[@nr='27']/@ci` | `0` |
| mb | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/sourcefile[@name='OrderStatusChecker.java']/line[@nr='27']/@mb` | `4` |
| cb | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/sourcefile[@name='OrderStatusChecker.java']/line[@nr='27']/@cb` | `0` |
| status | DERIVED | generated column từ mi/ci/mb/cb | `MISSED` |

**Đừng thu bốn bộ đếm về một boolean.** "Dòng được phủ" theo JaCoCo là `ci > 0`, tức COVERED
**cộng** PARTIAL = 77 + 5 = **82** trên vulnshop — khớp counter `LINE covered='82'` của chính
JaCoCo và `expected-analysis.json`. Đếm mỗi COVERED ra 77 là sai.

## 12. Bảng provenance — `coverage_method`

Ví dụ dùng chung: method đầu tiên của `OrderStatusChecker` (`isShipped`).

| Cột | Nguồn | Locator | Ví dụ |
|---|---|---|---|
| id | DERIVED | sinh UUID lúc materialize | — |
| evidence_record_id | DERIVED | FK tới cùng evidence_record cấp sourcefile | — |
| class_name | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/@name` | `vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker` |
| method_name | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/@name` | `isShipped` |
| method_desc | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/@desc` | `(Ljava/lang/String;)Z` |
| first_line | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/@line` | `18` |
| missed_instructions | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='INSTRUCTION']/@missed` | `0` |
| covered_instructions | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='INSTRUCTION']/@covered` | `7` |
| missed_branches | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='BRANCH']/@missed` | `0` |
| covered_branches | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='BRANCH']/@covered` | `2` |
| missed_lines | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='LINE']/@missed` | `0` |
| covered_lines | jacoco | `jacoco:package[@name='vn/hust/datn/casestudy/vulnshop/order']/class[@name='vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker']/method[1]/counter[@type='LINE']/@covered` | `1` |

`class_name` dùng dấu `/` (dạng JaCoCo) chứ không phải `.`: giữ nguyên văn để đối chiếu ngược lại
artifact được. `first_line` **không có** `end_line` đi kèm vì JaCoCo không cung cấp — đó cũng là lý
do bảng này không có FK nào sang `coverage_line`.

## 13. Kiểm chứng tự động

```bash
python docs/check-evidence-model.py
```

Kiểm 3 việc: (a) mọi locator ở các mục "Bảng provenance" resolve được trên artifact thật và giá
trị khớp cột "Ví dụ"; (b) đối chiếu **hai chiều, theo từng bảng** giữa cột SQL trong **mọi** file
migration (`V*__*.sql`) và dòng provenance trong tài liệu này — thừa hay thiếu ở bất kỳ chiều nào
đều là lỗi; (c) `exit 1` nếu lệch, dùng trong CI — đúng khuôn `case-study/run-analysis.sh --check`.

Hai điều cần biết khi thêm bảng mới vào tài liệu:

- Tiêu đề mục phải đúng dạng ``## N. Bảng provenance — `<tên bảng>` ...`` thì script mới gom được
  dòng theo bảng; và tên bảng phải được thêm vào `DOCUMENTED_TABLES` trong script, nếu không
  script báo lỗi thay vì bỏ qua im lặng.
- Script đọc cột SQL bằng regex `^ {4}(\w+) +`, tức **đúng 4 dấu cách thụt đầu**. Căn lề lệch làm
  nó đọc được ít cột hơn thực tế; có guard "mỗi bảng ≥3 cột" chặn trường hợp đọc được 0 cột rồi
  in "OK".
