# Unified Evidence Model

Tuần 3. Thiết kế dựa trên artifact **thật** trong [`docs/samples/vulnshop/`](samples/vulnshop/) —
không hình dung trước rồi sửa sau. Mỗi trường trong bảng provenance dưới đây trỏ được về đúng
chỗ trong artifact thật, và `docs/check-evidence-model.py` kiểm chứng tự động điều đó (xem cuối
tài liệu).

Phạm vi tuần 3 (V2, đã hiện thực): `analysis_run`, `evidence_record`, `warning`, `evidence_link`,
`claim`, `claim_citation`. Bảng "thô" mirror trực tiếp từng công cụ (`test_case`, `stack_frame`,
`coverage_line`, `coverage_method`, `warning_location`) **chưa tạo** — xem mục "Thiết kế cho V3"
ở cuối, kèm 4 phát hiện từ artifact thật làm căn cứ.

## 1. 6 loại evidence

| Loại | Sinh từ | V2 lưu ở |
|---|---|---|
| `STATIC_WARNING` | SpotBugs, Semgrep | `evidence_record` + `warning` |
| `CODE_CONTEXT` | JavaParser (tuần 10-11) | `evidence_record` (nội dung rỗng ở V2) |
| `TEST_RESULT` | Surefire | `evidence_record` (V3 mới có `test_case`) |
| `FAILED_TEST` | Surefire | `evidence_record` (V3 mới có `test_case`) |
| `STACK_TRACE` | Surefire | `evidence_record` (V3 mới có `stack_frame`) |
| `COVERAGE_INFORMATION` | JaCoCo | `evidence_record` (V3 mới có `coverage_line`/`coverage_method`) |

V2 cho phép mọi loại evidence tồn tại như một `evidence_record` (đơn vị citable), nhưng chỉ
`STATIC_WARNING` có bảng mở rộng 1:1 (`warning`) lưu metadata riêng ở dạng có ràng buộc SQL;
4 loại còn lại tạm lưu toàn bộ chi tiết trong `content` JSONB cho tới khi bảng thô V3 ra đời.

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

Ví dụ đối chứng thêm cho `file_path` từ hai nguồn còn lại (không map trực tiếp vào một cột riêng,
nhưng xác nhận công thức chuẩn hoá path đúng — xem mục 5):

- **Semgrep** (`unvalidated-redirect`, `AuthController.java:49-52`): SARIF ghi đường dẫn tuyệt đối
  trong container `results[0].locations[0].physicalLocation.artifactLocation.uri` =
  `/work/vulnshop/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java`. Sau khi
  strip tiền tố `/work/vulnshop/src/main/java/`, khớp đúng định dạng canonical của SpotBugs.
- **JaCoCo** (`OrderStatusChecker.java`): không có cột path — phải ghép
  `package/@name` (`vn/hust/datn/casestudy/vulnshop/order`) + `'/'` + `sourcefile/@name`
  (`OrderStatusChecker.java`).

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

## 7. Thiết kế cho V3 (tuần 8-9) — chưa hiện thực

Không tạo ở V2 vì không có consumer nào cho tới EvidenceLinker (tuần 10-11) và parser thật
(tuần 8-9); tạo sớm sẽ là bảng rỗng không parser, không test, không ai biết sai cho tới lúc đó.

| Bảng | Vì sao cần | Cột đáng chú ý |
|---|---|---|
| `warning_location` | Phát hiện 1: 3/28 warning có nhiều vị trí | `ordinal`, `role_raw` (không enum hoá — detector mới sinh role mới), `is_primary`, `start_line` nullable |
| `test_case` | Nguồn TEST_RESULT/FAILED_TEST | `status`, `failure_kind` (FAILURE/ERROR — khác `<error>` XML nghĩa), `stack_trace_raw` |
| `stack_frame` | Phát hiện 2: parse riêng để reparse được nếu heuristic sai | `frame_kind` (PRODUCTION/TEST/TEST_FRAMEWORK/JDK/LIBRARY), `line_number` nullable (`Native Method`) |
| `coverage_line` | Giữ `mi/ci/mb/cb` để phân biệt PARTIAL, không thu về boolean | `status` generated (COVERED/MISSED/PARTIAL) |
| `coverage_method` | Phát hiện 3: KHÔNG có FK sang coverage_line (quan hệ không tồn tại trong dữ liệu thật) | `first_line` (không có `end_line` — JaCoCo không cung cấp) |

`evidence_link.method` đã có sẵn giá trị `STACK_FRAME`/`COVERAGE`/`NAMING_CONVENTION` chờ V3 vì
phát hiện 2 chứng minh không thể chỉ dựa vào stack trace.

## 8. Kiểm chứng tự động

```bash
python docs/check-evidence-model.py
```

Kiểm 3 việc: (a) mọi locator ở mục 2-3 resolve được trên artifact thật và giá trị khớp cột "Ví
dụ"; (b) mọi cột trong `V2__evidence_model.sql` có đúng một dòng provenance (DERIVED hoặc có
locator) và ngược lại; (c) `exit 1` nếu lệch, dùng trong CI — đúng khuôn
`case-study/run-analysis.sh --check`.
