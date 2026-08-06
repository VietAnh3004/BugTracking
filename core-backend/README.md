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

`POST /api/v1/analysis-runs/{id}/links/rebuild` tính lại liên kết mà không cần chạy lại analyzer.
Ranh giới: nó tính lại **cách khớp**, không tính lại **kết quả parse** — sửa `CodeContextExtractor`
hay chuẩn hoá đường dẫn thì phải phân tích lại từ đầu, vì chúng sinh ra `evidence_record` mà
`short_id` là thứ LLM đã trích dẫn.

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
