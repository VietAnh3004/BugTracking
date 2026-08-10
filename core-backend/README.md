# core-backend

Quản lý dự án và mã nguồn, chạy phân tích tĩnh, thu thập bằng chứng kiểm thử, lưu Evidence Store,
hậu kiểm trích dẫn và ghi nhận human review.

## Vai trò trong hệ thống

Service này sở hữu dữ liệu. `agent-service` không có kết nối cơ sở dữ liệu — nó lấy bằng chứng
qua Evidence Tool API do đây expose, nên mỗi lượt truy xuất của agent đều log và đo được.

**Hậu kiểm trích dẫn nằm ở đây**, ngoài process của agent, để bước kiểm tra thực sự độc lập với
LLM. Xem [ADR 0001](../docs/adr/0001-tach-agent-service-khoi-core-backend.md).

## Cấu trúc

```
controller/            Nhận request, validate DTO, gọi service. Không logic nghiệp vụ.
service/               Toàn bộ logic nghiệp vụ. Không biết gì về HTTP.
  analysis/            Parser artifact analyzer -> bằng chứng, và điều phối một lần phân tích.
  analysis/sandbox/    Chạy analyzer trong Docker trên mã nguồn người dùng.
repository/            Truy cập dữ liệu (Spring Data JPA).
domain/                Entity, value object, enum.
dto/                   Record request/response, tách biệt entity.
mapper/                MapStruct, entity <-> DTO.
config/                Bean, security, CORS, OpenAPI. Nhóm cấu hình nằm trong config/properties.
exception/             Exception nghiệp vụ + @RestControllerAdvice xử lý tập trung.
storage/               Cổng ra object storage. Là HẠ TẦNG, không phải nghiệp vụ — xem package-info.
```

Quy ước:

- **Không trả entity JPA trực tiếp ra API** — luôn qua DTO, tránh lộ trường nội bộ và lỗi
  lazy-loading khi serialize.
- **Constructor injection**, không `@Autowired` trên field.
- **Không `try/catch` trong controller** — mọi lỗi nổi lên `GlobalExceptionHandler` và trả về
  cùng một định dạng RFC 7807.
- **Schema chỉ thay đổi qua Flyway**, `ddl-auto` luôn là `validate`.

## Yêu cầu môi trường

**JDK 21.** Không dùng JDK mới hơn: SpotBugs và các formatter dựa trên javac không chạy được
trên JDK 25 — xem [ADR 0003](../docs/adr/0003-chuan-hoa-jdk-21.md).

## Xác thực

Hai `SecurityFilterChain` tách biệt theo đường dẫn:

| Nhóm endpoint | Cách xác thực |
|---|---|
| `/api/v1/tools/**` | Header `X-Service-Token` (bí mật chia sẻ với agent-service) |
| Còn lại | JWT Bearer, access token 15 phút + refresh token xoay vòng |

Refresh token **xoay vòng và phát hiện tái sử dụng**: dùng lại một token đã xoay sẽ thu hồi cả họ.
Đánh đổi đầy đủ ở [ADR 0006](../docs/adr/0006-xac-thuc-jwt-va-refresh-token-xoay-vong.md).

Quy tắc phân quyền duy nhất: **tài nguyên không thuộc về bạn ⇒ 404, không phải 403** — lọc theo
`ownerId` ngay trong câu truy vấn, không "tìm rồi so sánh trong Java".

## Lưu mã nguồn

Upload zip → giải nén an toàn (chống zip-slip ba tầng, chống zip-bomb) → mỗi file một object trên
MinIO (`snapshots/{id}/{path}`) + chỉ mục phẳng trong bảng `snapshot_file`. Postgres là nguồn sự
thật; object storage được phép có rác. Xem
[ADR 0007](../docs/adr/0007-luu-snapshot-theo-tung-file-tren-object-storage.md).

## Từ artifact analyzer tới bằng chứng

Bốn parser trong `service/analysis/` biến output thô của công cụ thành `evidence_record` cùng các
bảng thô đi kèm (`warning`, `warning_location`, `test_case`, `stack_frame`, `coverage_line`,
`coverage_method`). Từng cột trỏ về đâu trong artifact thật: [docs/evidence-model.md](../docs/evidence-model.md).

| Parser | Đọc | Sinh ra |
|---|---|---|
| `SpotbugsXmlAdapter` | `spotbugs.xml` | `STATIC_WARNING` |
| `SemgrepSarifAdapter` | `semgrep.sarif` | `STATIC_WARNING` |
| `SurefireResultCollector` (+ `StackTraceParser`) | `TEST-*.xml` | `TEST_RESULT`, `FAILED_TEST`, `STACK_TRACE` |
| `JacocoCoverageCollector` | `jacoco.xml` | `COVERAGE_INFORMATION` |

Bốn điều dễ làm sai, đều thuộc loại hỏng-im-lặng:

- **Chọn parser theo NỘI DUNG, không theo tên file** (`AnalyzerRegistry.detect`). Vì vậy endpoint
  nạp artifact dùng một trường lặp `artifacts`, không phải bốn trường có tên sẵn — đặt tên trường
  là hứa một điều mà hệ thống không kiểm.
- **Artifact là dữ liệu KHÔNG TIN CẬY**: nó do analyzer chạy trên repo người lạ sinh ra. Thuộc
  tính thiếu phải thành một run `FAILED` kèm lý do đọc được, không phải `NullPointerException`.
- **Đường dẫn trong artifact có bốn định dạng, không join thẳng được.** `SnapshotPathResolver`
  khớp **hậu tố dài nhất và duy nhất** với `snapshot_file.path`; không khớp thì để NULL và cộng
  vào `analysis_run.unresolved_location_count` — không bao giờ đoán bừa.
- **Analyzer trả 0 cảnh báo bị coi là lỗi**, ép bằng CHECK constraint trên `analysis_run.status`
  chứ không chỉ bằng code. Một pipeline "thành công" với Evidence Store rỗng nguy hiểm hơn nhiều
  so với báo lỗi thẳng.

## Nối bằng chứng rời rạc thành chuỗi bằng chứng

`service/linker/EvidenceLinker` sinh `evidence_link` theo ba tầng, mỗi tầng một `method` riêng để
phân biệt được bằng chứng mạnh với bằng chứng suy ra. Quyết định và bốn chỗ dữ liệu thật buộc đi
khác kế hoạch: [ADR 0009](../docs/adr/0009-lien-ket-bang-chung-ba-tang.md).

| Tầng | Quan hệ | `method` mạnh nhất → yếu nhất |
|---|---|---|
| 1 — vị trí | `LOCATED_IN` | `LINE_EXACT` · `RANGE_OVERLAP` |
| 2 — độ phủ + quy ước tên | `COVERS` · `SUPPORTS` · `CONTRADICTS` | `LINE_EXACT` · `METHOD_AGGREGATE` · `LINE_NEAREST_EXECUTABLE` · `NAMING_CONVENTION` |
| 3 — stack trace | `SUPPORTS` | `STACK_FRAME` |

Bốn điều dễ làm sai:

- **`weight` là độ tin cậy của LIÊN KẾT**, không phải mức độ phủ và không phải mức nghiêm trọng của
  cảnh báo. Trạng thái phủ nằm ở `rationale`.
- **Không dùng `ON CONFLICT DO NOTHING`.** `UNIQUE (from, to, relation)` vỡ khi hai tầng cùng đề
  xuất một cặp; `ON CONFLICT` để thứ tự chèn quyết định độ tin cậy được ghi lại, và một liên kết
  `STACK_FRAME` có thể bị `NAMING_CONVENTION` chiếm chỗ. Dedup ở `LinkAccumulator`; trùng sau dedup
  nghĩa là bug và phải nổ.
- **`CONTRADICTS` không có nghĩa là false positive.** vulnshop cố tình đặt bẫy đúng chỗ này, nên
  `rationale` mang sẵn câu cảnh báo — đặt vào chính dữ liệu để nó đi theo bằng chứng tới mọi nơi.
- **Bộ đếm liên kết đi qua `RunOutcome`** ở đường ingest, KHÔNG do linker tự UPDATE `analysis_run`:
  `finalizeRun` là `REQUIRES_NEW` nên nó treo transaction ngoài và sẽ ghi đè bằng giá trị cũ.
- **Suy tên lớp production trả DANH SÁCH ứng viên**, không đoán một giá trị. `FooIntegrationTest`
  có thể là test của `FooIntegration` hoặc test tích hợp của `Foo`, và chỉ dữ liệu mới trả lời
  được. Đoán sai là mất bằng chứng mà không có gì báo.

Ba bộ đếm `link_count` · `code_context_count` · `unlinked_warning_count` ra tới `AnalysisRunResponse`
và hiện trên màn hình cảnh báo. Đó là phần thứ ba của cơ chế chống hỏng-im-lặng — hai phần kia là
ràng buộc `ck_run_links_not_silently_empty` ở tầng DB và log WARN. Thiếu phần hiển thị thì muốn biết
một run có 30/34 cảnh báo trơ trọi phải mở DB.

`POST /api/v1/analysis-runs/{id}/links/rebuild` tính lại liên kết mà không cần chạy lại analyzer.
Ranh giới: nó tính lại **cách khớp**, không tính lại **kết quả parse** — sửa `CodeContextExtractor`
hay chuẩn hoá đường dẫn thì phải phân tích lại từ đầu, vì chúng sinh ra `evidence_record` mà
`short_id` là thứ LLM đã trích dẫn.

## Hỏi đáp với Agent, và hậu kiểm trích dẫn

Service này là **người gọi duy nhất** của agent-service, và cũng là chỗ câu trả lời được kiểm
trước khi tới người dùng. Quyết định đầy đủ ở [ADR 0010](../docs/adr/0010-vong-lap-agent-va-hau-kiem-trich-dan.md).

```
trình duyệt ──SSE──> core-backend ──SSE──> agent-service ──5 tool──> Evidence Store
                          │                     │
                    CitationValidator      không có kết nối DB
```

Trình duyệt **không** gọi thẳng agent-service. Ba lý do, nặng nhất trước: câu trả lời phải qua hậu
kiểm trước khi người dùng đọc được (nếu không, bước hậu kiểm chỉ còn là một dòng trong báo cáo);
agent-service không có xác thực người dùng; và `toolChain` tắt hẳn CORS nên trình duyệt bị chặn ở
preflight với thông điệp đánh lạc hướng.

`service/citation/CitationValidator` đối chiếu từng Evidence ID với Evidence Store, cho ra **bốn**
kết cục — mỗi cái nói về một lỗi khác của mô hình, nên không được gộp:

| Kết cục | Nghĩa là gì |
|---|---|
| `RESOLVED` | Có thật, trong đúng run đang xét |
| `WRONG_RUN` | Có thật nhưng thuộc run khác — lỗi **ngữ cảnh**, không phải bịa |
| `UNKNOWN_ID` | Đúng khuôn dạng nhưng không tồn tại ở đâu — **bịa** |
| `MALFORMED` | Không phải Evidence ID |

Bốn điều dễ làm sai:

- **Hạ cấp, không xoá.** Nhận định vô căn cứ vẫn được ghi — chúng là mẫu số của Unsupported Claim
  Rate. Xoá đi thì chỉ số này luôn đẹp và luôn vô nghĩa.
- **Chuẩn hoá đúng hai thứ**: khoảng trắng và chữ hoa. Gỡ thêm ngoặc hay dấu chấm câu sẽ biến
  Evidence Citation Accuracy thành phép đo lòng khoan dung của `ShortIdFormat`.
- **Không ghi "đã hạ cấp" khi mô hình vốn đã kết luận `INSUFFICIENT_EVIDENCE`** — làm vậy sẽ thổi
  phồng số lần hạ cấp bằng những lần chẳng có gì xảy ra.
- **`ConversationService.ask` KHÔNG mang `@Transactional`**: nó chờ một lời gọi mạng hàng chục
  giây, giữ kết nối DB suốt thời gian đó sẽ vét cạn pool. Mọi thao tác ghi qua
  `ConversationPersistenceService` (cùng khuôn `AnalysisRunPersistenceService`).

SSE stream **tiến trình truy xuất bằng chứng**, không stream token của câu trả lời: đầu ra bị ép
theo cấu trúc nên JSON dở dang vừa không đọc được vừa phơi ra Evidence ID chưa qua hậu kiểm. Sự
kiện `answer` của agent-service **không bao giờ** đi tiếp — cái ra tới trình duyệt là bản đã kiểm
và đã ghi DB.

## Quyết định của người review, và chỉ số của một lần phân tích

`human_review` neo vào **cảnh báo** (`evidence_record`), không neo vào câu trả lời của Agent — lý
do đầy đủ ở [ADR 0011](../docs/adr/0011-human-review-gan-vao-canh-bao.md). `UNIQUE (evidence_record_id,
reviewer_id)` ép "một người, một cảnh báo, một quyết định"; đổi ý là UPDATE.

`message_id` (nullable) ghi câu trả lời người dùng đã đọc trước khi quyết. `NULL` = không dùng
Agent, tức **nhóm đối chứng của user study**. Nó là biến thực nghiệm chứ không phải metadata, nên
`HumanReviewService` kiểm **ba** điều và trả 400 nếu sai một, thay vì lặng lẽ ghi NULL:

1. message tồn tại;
2. thuộc một hội thoại của **chính người đang review**;
3. hội thoại đó thuộc **đúng run** đang xét.

Bốn lựa chọn: `ACCEPT` / `REJECT` / `UNCERTAIN` / `INSUFFICIENT_EVIDENCE`. Hai cái cuối cố ý
KHÔNG gộp — `UNCERTAIN` nói về độ khó của cảnh báo, `INSUFFICIENT_EVIDENCE` nói về thiếu sót của
pipeline bằng chứng.

`RunMetricsService` là **nguồn tính toán duy nhất**; `RunExportService.toJson` gọi lại nó chứ
không tự tính. Hai chỗ cùng tính một chỉ số sớm muộn cũng lệch, và lúc đó không ai biết con số nào
trong báo cáo là đúng.

Ba điều dễ làm sai, đều ghi trong comment của `RunMetricsService` vì không suy ra được từ SQL:

- **Tỉ lệ có mẫu số 0 trả `null`, không trả `0`** — nên `RunMetricsResponse` dùng `Double` bọc và
  có đúng một hàm chia. "Chưa ai review" khác hẳn "review hết mà 0% khớp".
- **`UNCERTAIN` luôn tính là không khớp nhưng ở lại trong mẫu số.** Loại nó ra làm chỉ số đẹp lên
  đúng theo mức độ khó của bộ dữ liệu.
- **Nhãn `BLIND_SECOND_RATER` không tính vào độ chính xác** — nó để đo mức đồng thuận giữa hai
  người gán nhãn (Cohen's kappa), dùng làm chuẩn nữa là đếm hai lần cùng một ý kiến.

Export có hai định dạng không thay thế nhau: **CSV** một dòng một cảnh báo (nạp thẳng vào công cụ
thống kê) và **JSON** đủ claim/citation/trace để truy ngược một con số về bằng chứng sinh ra nó.
CSV **phải có BOM UTF-8** — `note` là tiếng Việt do người dùng gõ, thiếu BOM thì Excel trên Windows
mở ra là chữ rác. CSV cũng ghi qua `commons-csv` chứ không ghép chuỗi tay (`note` chứa được dấu
phẩy, nháy kép và xuống dòng).

`ground_truth_label` dựng sẵn ở V9 nhưng **trống tới tuần 15**. Khoá chính là
`(evidence_record_id, source)` vì cần hai người gán nhãn độc lập; `rationale NOT NULL` chặn bẫy
circularity mà kế hoạch §5 cảnh báo — bắt viết lý do khiến việc gán nhãn theo output của Agent lộ
ra ngay lúc gán.

## Chạy analyzer trong Docker sandbox

`POST /api/v1/snapshots/{id}/analysis-runs/sandbox` dựng cây mã nguồn thành tar, chạy hai image
analyzer rồi thu artifact về — người dùng không phải tự cài JDK 21 hay tự cấu hình find-sec-bugs.
Quyết định và rủi ro: [ADR 0008](../docs/adr/0008-chay-analyzer-trong-docker-sandbox.md).

**Mặc định TẮT** (`ANALYSIS_RUNNER=disabled`): ứng dụng khởi động bình thường trên máy chủ không có
Docker daemon, và gọi endpoint này khi đang tắt trả 503 kèm lý do đọc được chứ không phải 500.
Bật lên đòi mount `/var/run/docker.sock` — tương đương cấp quyền root trên host, nên đó là một
quyết định vận hành có ý thức. Cách bật: [infra/README.md](../infra/README.md).

`SandboxReaper` dọn container và volume mồ côi mang nhãn `evidence.run-id` (khối `finally` chỉ
chạy khi JVM còn sống; deploy lại giữa chừng thì rác nằm lại vĩnh viễn). Nó bỏ qua container
`running` và có thời gian ân hạn 1 giờ để không đụng vào run đang chạy.

## Chạy local

Cần Postgres + MinIO (`docker compose -f ../infra/docker-compose.yml --env-file ../infra/.env up -d postgres minio`).

```bash
cp .env.example .env      # điền giá trị thật
mvn spring-boot:run
```

Spring Boot **không tự đọc file `.env`** (dự án không dùng thư viện dotenv) — nó chỉ là tài liệu
cho biết cần những biến nào. Khi chạy bằng `java -jar`, phải export trước:

```bash
set -a && . ./.env && set +a && java -jar target/core-backend-0.1.0.jar
```

Thiếu biến bắt buộc thì ứng dụng **dừng ngay lúc khởi động** kèm tên biến còn thiếu. Điều này
KHÔNG tự nhiên có: `@ConfigurationProperties` bind `${VAR}` không giải được thành chuỗi nguyên văn
thay vì báo lỗi, nên cần `UnresolvedPlaceholderValidator` ép — xem Javadoc lớp đó.

- API: http://localhost:8080/api/v1
- OpenAPI: http://localhost:8080/api/v1/openapi
- Swagger UI: http://localhost:8080/swagger-ui.html

## Kiểm tra chất lượng

```bash
mvn spotless:apply    # sửa format
mvn verify            # test + kiểm tra format
```

Integration test dùng Testcontainers nên cần Docker. Trên Windows, mỗi phiên Git Bash phải export
`DOCKER_HOST="tcp://127.0.0.1:2375"` trước — lý do đầy đủ trong `CLAUDE.md` ở gốc repo.

Test class phải đặt tên `*Test.java`: `pom.xml` chỉ cấu hình Surefire, file `*IT.java` sẽ **không
bao giờ chạy** mà build vẫn báo thành công.
