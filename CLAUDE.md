# CLAUDE.md

Hướng dẫn cho Claude Code khi làm việc trong repository này.

## Dự án là gì

Đồ án tốt nghiệp — nền tảng web phân tích cảnh báo phân tích tĩnh **dựa trên bằng chứng**, dùng
AI Agent truy xuất bằng chứng qua tool-call và trả lời qua giao diện hội thoại. Mỗi nhận định của
Agent bắt buộc gắn Evidence ID kiểm chứng được.

Sinh viên: Nguyễn Việt Anh (20224923), ĐHBK Hà Nội. GVHD: PGS.TS. Huỳnh Quyết Thắng.
Bảo vệ dự kiến giữa tháng 12/2026.

**Giao tiếp bằng tiếng Việt.**

## Tài liệu phải đọc trước khi làm việc

| File | Nội dung |
|---|---|
| [docs/ke-hoach-20-tuan.md](docs/ke-hoach-20-tuan.md) | **Lộ trình đầy đủ 20 tuần** + chuẩn code + thiết kế. Đọc trước tiên |
| [docs/adr/](docs/adr/) | Bốn quyết định kiến trúc lớn và lý do |
| [docs/evidence-model.md](docs/evidence-model.md) | Schema Unified Evidence Model. **Mục 7 ghi 5 bảng còn nợ** (`test_case`, `stack_frame`, `coverage_line`, `coverage_method`, `warning_location`) — hoãn có chủ đích sang tuần 8-9, không phải quên. Đọc trước khi đụng vào StaticAnalysisAdapter/TestEvidenceCollector |
| [docs/spike-tuan-1.md](docs/spike-tuan-1.md) | Xác thực rủi ro: 3 nguồn bằng chứng khớp `file:line` |
| [case-study/vulnshop/SEEDED.md](case-study/vulnshop/SEEDED.md) | Danh mục lỗi gieo có chủ đích + phát hiện ảnh hưởng thiết kế |

Xem `git log` để biết đang dở ở đâu — commit message ghi khá chi tiết lý do.

## Quyết định đã chốt (không tự ý đổi)

| Hạng mục | Lựa chọn |
|---|---|
| Case study | Java — SpotBugs + find-sec-bugs, Semgrep, JUnit, JaCoCo |
| Backend | Spring Boot (core, sở hữu dữ liệu) + FastAPI (agent, **không** truy cập DB) |
| LLM | Provider-agnostic qua interface `LlmProvider` |
| Thời gian | 20 tuần, bắt đầu 27/07/2026 |

Hai ràng buộc người dùng nêu rõ, áp dụng cho mọi thay đổi:

1. **FE và BE phải deploy độc lập** lên hai server khác nhau. Kéo theo: cấu hình runtime qua
   `window.__APP_CONFIG__` (không dùng `VITE_*`), CORS thật, JWT stateless, không chia sẻ
   filesystem. Xem [ADR 0002](docs/adr/0002-frontend-cau-hinh-luc-chay.md).
2. **Clean code**: phân tầng rõ, chia component theo feature, cấu hình qua `.env` với fail-fast,
   không hardcode. Chi tiết ở mục 1bis của kế hoạch.

## Cấu trúc

```
core-backend/    Spring Boot 3, Java 21 — evidence store, adapter, citation validator
agent-service/   FastAPI — agent loop, 5 evidence tool, LlmProvider
frontend/        React + Vite + TS strict
case-study/      vulnshop (ứng dụng gieo lỗi) + semgrep-rules đã vendor
docs/samples/    Artifact THẬT của analyzer — căn cứ thiết kế + fixture cho test parser
infra/           docker-compose (Postgres+pgvector, MinIO)
```

## Cạm bẫy môi trường trên máy này

Đã mất thời gian gỡ, đừng vấp lại:

**JDK 21 bắt buộc.** JDK mới hơn làm SpotBugs trả về **0 cảnh báo mà vẫn báo thành công** — kiểu
hỏng im lặng nguy hiểm nhất.

`JAVA_HOME` đã được đặt vĩnh viễn ở mức hệ thống (`C:\Program Files\Java\jdk-21.0.12`) và Git Bash
thừa kế đúng — `java -version` và `mvn -v` chạy được ngay, **không cần export gì**.

**Đừng export lại `JAVA_HOME` trong Git Bash.** Giá trị dạng Windows chứa dấu `:` mà Git Bash lại
dùng `:` làm dấu phân tách PATH, nên `export PATH="$JAVA_HOME/bin:$PATH"` sẽ làm hỏng PATH và
Maven chết với lỗi `ClassNotFoundException: ...plexus.classworlds.launcher.Launcher`.

**Python trong Git Bash**: dùng `python` (Python 3.14.4). **`python3` thì hỏng** — nó trúng App
Execution Alias của Windows và báo "Python was not found". Script nào viết `python3` sẽ chết trên
máy này.

**Kéo Docker image**: CDN gốc của Docker Hub hay đứt giữa chừng với image lớn (đã thử 40 lần
đều hỏng). Kéo qua mirror rồi gắn lại tên:
```bash
docker pull mirror.gcr.io/pgvector/pgvector:pg16
docker tag  mirror.gcr.io/pgvector/pgvector:pg16 pgvector/pgvector:pg16
```

**Mount Docker từ Git Bash**: cần `MSYS_NO_PATHCONV=1` và đường dẫn dạng `//d/study/...`.

**Testcontainers (core-backend) không tự kết nối được Docker Desktop trên máy này.** Cả named
pipe mặc định lẫn `docker info` qua CLI đều OK, nhưng `docker-java` (thư viện Testcontainers dùng)
nhận về `BadRequestException 400` với body rỗng — lỗi tương thích giữa Testcontainers 1.21.0 và
Docker Desktop 29.0.1, không phải cấu hình sai. Đã sửa 2 việc, cả hai đều cần:

1. Nâng `testcontainers.version` lên `1.21.4` trong `core-backend/pom.xml` (đã làm, không cần lặp lại).
2. Bật **Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"** trong Docker
   Desktop (làm một lần, việc của người dùng, còn nguyên sau khi bật), rồi mỗi phiên Git Bash mới
   chạy test cần export biến này trước (chưa tìm cách persist được qua PowerShell/registry):
   ```bash
   export DOCKER_HOST="tcp://127.0.0.1:2375"
   ```
   Dùng `127.0.0.1`, **không phải `localhost`** — Cloudflare WARP trên máy này chặn DNS cho
   `localhost`, khiến `docker -H tcp://localhost:2375` báo "lookup localhost ... no such host".

**Testcontainers dùng chung 1 container qua nhiều class test**: KHÔNG dùng annotation
`@Testcontainers`/`@Container` trên field static — nó khiến Testcontainers tự `stop()` container
trong `afterAll` của class test *đầu tiên* chạy xong, các class sau dùng chung field đó connect
vào container đã chết (`CannotGetJdbcConnection`, timeout đúng 30s). Tự `start()` thủ công trong
static initializer, không bao giờ gọi `stop()` — xem `core-backend/src/test/java/vn/hust/datn/evidence/AbstractIntegrationTest.java`.

**Test frontend phải dùng `happy-dom`, KHÔNG dùng `jsdom`** — trên máy này `require('jsdom')` mất
**~11 giây mỗi lần** kể cả khi cache đã ấm (đo lặp nhiều lần; lần đầu sau khi cài là 39s), còn
`require('happy-dom')` chỉ ~1-3 giây. Nguyên nhân: jsdom gồm hàng nghìn file nhỏ, bị Windows
Defender quét lại mỗi lần nạp. Hệ quả nếu dùng jsdom: mọi worker của Vitest vượt ngưỡng khởi động
60 giây và **toàn bộ** test đỏ với `[vitest-pool]: Failed to start forks worker ... Timeout waiting
for worker to respond` — thông điệp **không hề nhắc tới jsdom**, và đổi `--pool=threads` cũng hỏng
y hệt (đúng 60.0s), rất dễ đi sửa nhầm chỗ. Cách khoanh vùng nhanh: chạy
`npx vitest run --config <config tối giản>` với `environment: 'node'` (xanh trong 3s) rồi đổi sang
`'jsdom'` (đỏ sau 60s) — biến số duy nhất khác nhau chính là thủ phạm.

**`UrlBasedCorsConfigurationSource` chỉ áp cho ĐÚNG các mẫu đường dẫn đã đăng ký — endpoint ngoài
mẫu không có header CORS nào.** Gặp thật: `CorsConfig` đăng ký `/api/**`, nhưng frontend gọi
`/actuator/health` ngay ở màn hình đầu tiên (`useBackendHealth`) nên bị trình duyệt chặn với
*"No 'Access-Control-Allow-Origin' header is present"* ngay khi mở app — trong khi **mọi endpoint
`/api/**` vẫn chạy tốt**, rất dễ đổ lỗi nhầm cho frontend. **`curl` KHÔNG phát hiện được** vì curl
không ép chính sách same-origin: nó trả 200 bình thường dù không có header CORS. Bài học chung:
kiểm CORS bằng curl chỉ chứng minh header *có tồn tại* trên endpoint bạn nghĩ tới — muốn biết
frontend gọi những đường dẫn nào thì phải mở trình duyệt thật. Test canh:
`CorsPreflightTest.actuatorHealthCoHeaderCorsVaFrontendGoiDuoc`.

**`@ConfigurationProperties` KHÔNG fail-fast khi thiếu biến môi trường — khác hẳn `@Value`.**
`@Value` dùng placeholder resolver nghiêm ngặt và ném lỗi khi không giải được `${VAR}`, nhưng
`@ConfigurationProperties` dùng `PropertySourcesPlaceholdersResolver` với
`ignoreUnresolvablePlaceholders = true`: biến thiếu được bind **nguyên văn** thành chuỗi
`"${VAR}"`. Gặp thật khi kiểm chứng end-to-end tuần 5-7: bỏ `CORS_ORIGINS` thì ứng dụng **khởi
động bình thường**, `/actuator/health` trả `UP`, nhưng origin được phép là chuỗi `"${CORS_ORIGINS}"`
— mọi request từ trình duyệt bị chặn CORS trong khi người vận hành thấy một ứng dụng "khoẻ mạnh".
`@NotBlank` không cứu được vì `"${CORS_ORIGINS}"` là chuỗi không rỗng. Các biến khác chỉ tình cờ
fail-fast nhờ validation ở tầng dưới (`JwtProperties` kiểm độ dài byte, MinIO từ chối hostname lạ)
— tức là bảo vệ ngẫu nhiên, không phải cơ chế. Đã sửa bằng
`core-backend/src/main/java/vn/hust/datn/evidence/config/UnresolvedPlaceholderValidator.java`
(nghe `ApplicationEnvironmentPreparedEvent`, quét mọi property `app.*`, đăng ký qua
`META-INF/spring.factories` vì lúc đó chưa có ApplicationContext để `@Component` hoạt động).

**MinIO từ chối khởi động nếu `MINIO_ROOT_PASSWORD` < 8 ký tự** (user < 3 ký tự cũng vậy) và thoát
với exit code 1 — nhưng `docker compose up -d` vẫn báo "Started", nên rất dễ tưởng nó đang chạy.
Triệu chứng thật: `curl :9000/minio/health/live` trả về rỗng/connection refused, còn
`docker compose ps` không liệt kê service đó. Kiểm bằng `docker logs evidence-agent-minio-1`.

**Comment trong XML không được chứa `--`** (đã làm hỏng pom.xml một lần).

**`mvn verify` báo lỗi giả sau nhiều lần `mvn test`/`spotless:apply` liên tiếp không `clean`.**
Gặp thật: `ProjectController` báo "No qualifying bean of type ProjectMapper" dù `@Mapper` hoàn
toàn đúng — nguyên nhân là compile tăng tiến (incremental) của Maven không sinh lại
`ProjectMapperImpl` (annotation processor MapStruct) sau nhiều lần build liên tiếp trong cùng
phiên. Trước khi coi một lỗi biên dịch/bean-không-tìm-thấy là lỗi thật, chạy lại với
`mvn clean verify` — nếu xanh thì lỗi trước đó là giả, do stale `target/`.

**Chạy `mvn` ở chế độ nền (background): thông báo "completed (exit code 0)" của harness KHÔNG
đáng tin — phải tự đọc log.** Gặp thật 3 lần liên tiếp trong một phiên: harness báo exit code 0
trong khi log ghi rõ `BUILD FAILURE` và `EXIT=1`. Cách làm đúng đã dùng xuyên suốt: luôn
`mvn ... > verify.log 2>&1; echo "EXIT=$?" >> verify.log`, rồi kiểm bằng
`grep -E "BUILD|^EXIT|Tests run:.*Failures" verify.log`. Đừng bao giờ kết luận "build xanh" từ
thông báo của harness. (Ghi chú liên quan: `mvn ... | tail -N; echo $?` cũng sai — đó là exit code
của `tail`, không phải của `mvn`.)

**Lỗi khoá file trên Windows khi `mvn clean` tái diễn thường xuyên, và có HAI dạng khác nhau:**
`Failed to delete ...\target\test-classes` (hỏng ở goal `clean`) và
`error while writing ...: ...\target\classes\...\X.class` (hỏng ở goal `compile`). Cả hai đều do
tranh chấp với IDE background compiler / antivirus đang giữ file trong `target/`, KHÔNG phải lỗi
code — chạy lại y nguyên lệnh đó là xanh. Gặp thật 2 lần liên tiếp trong một phiên (phải chạy tới
lần thứ ba mới qua). Cùng họ với cạm bẫy "IDE ghi đè `*MapperImpl.java`" ghi ở dưới: `target/` là
tài nguyên tranh chấp giữa IDE và `mvn`. Trước khi tốn thời gian debug, kiểm xem thông điệp lỗi có
nhắc tới đường dẫn trong `target/` hay không.

**`TestFixtures` (test helper dùng `saveAndFlush` nhiều bước liên tiếp trong cùng method):
method nào cần entity vừa lưu vẫn "managed" ở bước sau (vd gán vào quan hệ `@MapsId`) phải đánh
dấu `@Transactional`.** Thiếu annotation này, mỗi `saveAndFlush` tự mở/đóng transaction riêng,
entity trả về bị detach, và Hibernate từ chối gán entity detached vào một quan hệ với lỗi
"detached entity passed to persist". Xem
`core-backend/src/test/java/vn/hust/datn/evidence/testsupport/TestFixtures.java` (method
`warning()`).

**Spring Security + `oauth2-resource-server` (từ tuần 5-7, xem `SecurityConfig.java`): CORS phải
gắn vào Spring Security, không phải `WebMvcConfigurer.addCorsMappings`.** `addCorsMappings` chạy
ở tầng `HandlerMapping` bên trong `DispatcherServlet` — Spring Security là servlet filter chạy
**trước** đó. Preflight `OPTIONS` không mang header `Authorization` (đặc tả CORS cấm), nên với
`anyRequest().authenticated()` nó bị `AuthorizationFilter` trả 401 trước khi bao giờ tới
`HandlerMapping`. Trình duyệt thấy preflight không phải 2xx và báo **"blocked by CORS policy"**
— không phải 401 — khiến người debug đi sửa nhầm chỗ (CORS thay vì auth). Cách đúng: bean
`CorsConfigurationSource` + `http.cors(cors -> cors.configurationSource(...))` trong
`SecurityFilterChain`. Xem `core-backend/src/main/java/vn/hust/datn/evidence/config/CorsConfig.java`
và test canh lỗi này: `CorsPreflightTest`.

**`NimbusJwtEncoder.encode(JwtEncoderParameters.from(claims))` (một tham số, không kèm header) sẽ
ném `JwtEncodingException: Failed to select a JWK signing key` nếu `JWKSource` chỉ có khoá HMAC.**
Khi không truyền `JwsHeader` tường minh, mặc định thuật toán là **RS256**, không phải HS256 — JWK
đối xứng (`OctetSequenceKey`) không khớp. Luôn gọi
`JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)`. Ngoài ra
`OctetSequenceKey` phải khai tường minh `.keyUse(KeyUse.SIGNATURE)` và `.keyID(...)` — thiếu một
trong hai, `NimbusJwtEncoder` không chọn được JWK dù thuật toán đã đúng, và lỗi chỉ lộ ra lúc gọi
`encode()` (runtime), không lộ lúc khởi động ứng dụng hay lúc biên dịch. Xem
`core-backend/src/main/java/vn/hust/datn/evidence/config/JwtConfig.java`.

**Bốn cạm bẫy khi parse artifact analyzer (tuần 8-9).** Tất cả đều thuộc loại hỏng-im-lặng: parser
vẫn chạy xong, vẫn trả về dữ liệu, chỉ là sai hoặc thiếu.

1. **`Element.getElementsByTagName` trong Java là ĐỆ QUY** — tương đương `.//` của XPath, KHÁC
   `findall` của Python (chỉ lấy con trực tiếp). Trong `spotbugs.xml`, `<SourceLine>` xuất hiện ở
   **ba tầng lồng nhau** (trong `<Class>`, trong `<Method>`, và con trực tiếp của `<BugInstance>`)
   mà chỉ con trực tiếp mới là vị trí cảnh báo. Dùng nhầm ra **90 vị trí thay vì 33** và không có
   gì báo lỗi. Dùng `XmlParsers.directChildren`.

2. **`jacoco.xml` KHÔNG dùng được `disallow-doctype-decl=true`** — cách chống XXE mặc định. File
   thật có `<!DOCTYPE report ... "report.dtd">` (và `report.dtd` không tồn tại cạnh nó), nên bật cờ
   đó sẽ **từ chối chính file cần đọc**. Phải có **hai** cấu hình parser: bộ nghiêm ngặt cho
   SpotBugs/Surefire, bộ nới hơn (`load-external-dtd=false` + tắt external entity, vẫn chấp nhận
   DOCTYPE) cho JaCoCo. Xem `XmlParsers`.

3. **Regex tách stack frame trong `case-study/summarize.py:25` SAI, đừng chép lại.**
   `at ([\w.$]+)\.(\w+)\(([\w.]+):(\d+)\)` bỏ sót hai loại frame: `[\w.$]+` không có `/` nên trượt
   frame mang tiền tố module JPMS (`at java.base/java.lang.reflect.Method.invoke(...)` — mọi trace
   JUnit đều có), và `:(\d+)` bắt buộc số dòng nên bỏ luôn `(Native Method)`/`(Unknown Source)`.
   Thêm một bẫy nữa gặp khi tự viết lại: tiền tố **module** dùng MỘT gạch (`java.base/`) còn tiền
   tố **classloader** dùng HAI (`app//`).

4. **SARIF của Semgrep không có khoá `$schema`** dù đặc tả SARIF khuyến dùng, và viết
   `"version":"2.1.0"` **không dấu cách**. Hàm nhận dạng định dạng dựa vào hai giả định đó sẽ trượt
   trên artifact thật. Nhận dạng theo cấu trúc bắt buộc thực sự có (`{` + `"runs":[`).
   Ngoài ra `result` **không có** `level` và `result.properties` là object **rỗng** — mức nghiêm
   trọng chỉ lấy được bằng join sang `tool.driver.rules[]` theo `ruleId`.

**Artifact analyzer là dữ liệu KHÔNG TIN CẬY — thiếu thuộc tính phải cho lỗi đọc được, không phải
NPE.** Từ giai đoạn Docker sandbox, artifact do analyzer chạy trên **repo người dùng upload** sinh
ra. Gặp thật ở tuần 8-9: `<testcase>` thiếu `@classname` và `<class>` thiếu `@name` đều cho
`NullPointerException` — tức HTTP 500 kèm một stack trace vô nghĩa, thay vì một `analysis_run`
FAILED kèm lý do người vận hành đọc được. Mọi `XmlParsers.attr(...)` trả về `null` được dùng tiếp
đều phải kiểm.

**`No qualifying bean of type 'XMapper'` có thể xảy ra NGAY CẢ SAU `mvn clean verify`** — không
chỉ do build tăng tiến như đã ghi ở trên. Gặp thật: `target/generated-sources/annotations/.../XMapperImpl.java`
bị **IDE background compiler (Eclipse JDT)** ghi đè — file đó có comment
`compiler: Eclipse JDT (IDE)` thay vì Maven's javac, và **thiếu `@Component`** vì IDE không biết
cờ `-Amapstruct.defaultComponentModel=spring` chỉ khai trong `pom.xml`. Đây là tranh chấp giữa
IDE (chạy nền, tự biên dịch để lấy diagnostics) và tiến trình `mvn` — cả hai cùng ghi vào
`target/`. Triệu chứng: `mvn clean verify` đỏ với lỗi thiếu bean dù `mvn clean verify` lần **trước
đó hoặc sau đó** xanh với cùng một bộ code không đổi.

**Đã sửa tận gốc (tuần 8-9) — không cần chạy lại nữa.** Cách xử lý cũ ghi ở đây là "chạy lại một
lần nữa"; lần gặp ở tuần 8-9 chạy lại **hai** lần vẫn đỏ, vì vừa thêm 9 file Java nên IDE biên dịch
lại liên tục. Nguyên nhân gốc: 4 mapper khai `@Mapper` trần. Nay đã khai tường minh
`@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)` nên **cả Maven lẫn IDE đều sinh
`@Component`**. Cờ trong `pom.xml` vẫn giữ để bảo vệ mapper nào sau này quên khai.

Nếu vẫn gặp lại triệu chứng này với một mapper mới: kiểm ngay `grep -c "@Component"
target/generated-sources/annotations/**/XMapperImpl.java` — ra 0 nghĩa là mapper đó thiếu
`componentModel` trên annotation, không phải lỗi ngẫu nhiên theo thời điểm.

**`@Transactional` mặc định ROLLBACK khi có unchecked exception thoát ra khỏi method — kể cả khi
exception đó là thứ BẠN CHỦ Ý ném ra sau khi đã ghi dữ liệu quan trọng (vd thu hồi refresh token
trước khi báo lỗi).** Gặp thật: `RefreshTokenService.refresh()` gọi `revokeFamily(...)` (UPDATE
thật) rồi ném `RefreshTokenReuseException` để báo 401 — nhưng vì method có `@Transactional` không
khai `noRollbackFor`, Spring rollback TOÀN BỘ transaction khi exception thoát ra, xoá luôn thao
tác thu hồi vừa ghi. Biện pháp bảo mật quan trọng nhất của luồng refresh token **không bao giờ
thực sự lưu xuống DB** dù code "trông đúng" và log "trông đúng". Chỉ `RefreshTokenRotationTest`
(gọi lại lần thứ hai để xác nhận token mới nhất cũng bị thu hồi) mới bắt được lỗi này — test chỉ
assert "ném đúng exception" ở lần gọi đầu sẽ xanh giả. Sửa bằng
`@Transactional(noRollbackFor = {RefreshTokenReuseException.class, InvalidRefreshTokenException.class})`.
Bài học chung: **ghi-rồi-ném trong một method `@Transactional` luôn cần xét lại rollback rule**,
không chỉ riêng refresh token.

**Test class đặt tên `*IT.java` (hậu tố "Integration Test" kiểu Maven Failsafe) sẽ KHÔNG BAO GIỜ
CHẠY trong repo này.** `pom.xml` chỉ cấu hình `maven-surefire-plugin` (bind vào phase `test`, mặc
định chỉ include `**/Test*.java, **/*Test.java, **/*Tests.java, **/*TestCase.java`) — không có
`maven-failsafe-plugin` (bind vào phase `integration-test`/`verify`, include `**/*IT.java`).
Triệu chứng: `mvn clean verify` báo "BUILD SUCCESS" dù test đó có bug rành rành — vì nó chưa từng
được thực thi, không phải vì nó đã pass. Gặp thật với `MinioObjectStorageClientIT` (đã đổi tên
thành `MinioObjectStorageClientTest` để khớp quy ước `*Test.java` toàn repo). Trước khi tin một
test class "xanh", kiểm `target/surefire-reports/` có file `.txt` cho đúng tên class đó không.

**`http.exceptionHandling(ex -> ex.authenticationEntryPoint(...))` KHÔNG đủ để 401 trả về JSON khi
dùng `oauth2ResourceServer()`.** `OAuth2ResourceServerConfigurer` tự đăng ký
`BearerTokenAuthenticationEntryPoint` riêng qua
`ExceptionHandlingConfigurer.defaultAuthenticationEntryPointFor(...)` — cơ chế ưu tiên theo
request-matcher, đứng **trước** entry point "mặc định" khai ở `exceptionHandling()`. Không khai
entry point tường minh **bên trong** `oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(...).jwt(...))`
thì 401 rơi vào entry point gốc của Spring Security (không trả JSON, không theo format
ProblemDetail của dự án) — triệu chứng cụ thể từng gặp: test `MockMvc` báo
`No value at JSON path "$.type"` vì body response rỗng. Xem
`core-backend/src/main/java/vn/hust/datn/evidence/config/SecurityConfig.java`.

## Lệnh hay dùng

```bash
# Hạ tầng
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres minio
docker exec evidence-agent-postgres-1 psql -U evidence -d evidence_agent -c "\dx"

# core-backend  (cần JAVA_HOME=JDK 21)
cd core-backend && mvn spotless:apply && mvn verify

# agent-service
cd agent-service && ./.venv/Scripts/python.exe -m pytest -q
./.venv/Scripts/python.exe -m ruff check src tests && ./.venv/Scripts/python.exe -m mypy src

# frontend
cd frontend && npm run lint && npm run format:check && npm run build

# Case study: chạy cả 4 analyzer rồi thu artifact
./case-study/run-analysis.sh vulnshop
python case-study/summarize.py vulnshop            # xem thống kê
python case-study/summarize.py vulnshop --check    # đối chiếu expected-analysis.json
```

## Con số kỳ vọng của vulnshop

Nguồn sự thật duy nhất: [case-study/vulnshop/expected-analysis.json](case-study/vulnshop/expected-analysis.json).
CI đối chiếu tự động qua `run-analysis.sh vulnshop --check`.

**Năm test hỏng là dữ liệu đầu vào của đồ án** (nguồn `FAILED_TEST` + `STACK_TRACE`), không phải
lỗi cần sửa. Đừng "sửa" chúng.

Lệch số liệu thì `summarize.py` gợi ý nguyên nhân thường gặp. Sửa `expected-analysis.json` chỉ khi
đã hiểu **vì sao** số đổi, và ghi lý do vào commit message.

Chạy lại analyzer làm `docs/samples/` hiện ra "đã sửa" trong git kể cả khi kết quả không đổi
(timestamp). Nếu `summarize.py` khớp số thì `git checkout -- docs/samples/`.

## Quy ước

- **Conventional Commits**, commit nhỏ một mục đích. Nhánh `main` luôn chạy được.
- **Không trả entity JPA ra API** — luôn qua DTO.
- **Schema chỉ đổi qua Flyway**, `ddl-auto: validate`.
- **Không gọi `fetch` trực tiếp trong component React** — qua `apiClient`.
- **Không nhúng prompt giữa code Python** — để trong `prompts/*.jinja2`, version hoá.
- **Không gọi LLM thật trong test** — luôn mock.
- Ba loại verdict của agent: `LIKELY_TRUE_POSITIVE`, `LIKELY_FALSE_POSITIVE`,
  `INSUFFICIENT_EVIDENCE`. Claim không gắn được Evidence ID hợp lệ bị hạ xuống loại thứ ba,
  **không xoá** — cần giữ để tính Unsupported Claim Rate.

## Nguyên tắc dễ bị bỏ qua

**Analyzer trả về 0 cảnh báo phải bị coi là lỗi**, không được lặng lẽ ghi `analysis_run` rỗng.
Đã gặp hai lần trong thực tế (SpotBugs trên JDK 25; find-sec-bugs khai báo sai chỗ trong pom).
Pipeline "chạy thành công" nhưng Evidence Store rỗng nguy hiểm hơn nhiều so với báo lỗi thẳng.

**Test xanh không có nghĩa là cảnh báo sai.** vulnshop cố tình đặt bẫy: `findByUsername` có lỗ
hổng SQL injection nhưng test đường đi bình thường vẫn xanh, và có một test xanh khác *chứng minh*
lỗ hổng có thật. Agent nào kết luận "có test phủ + test xanh ⇒ false positive" là sai.

**Liên kết bằng chứng không thể chỉ dựa vào stack trace.** Thực tế chỉ 2/5 test hỏng cho frame
trỏ về mã production; test hỏng kiểu assertion ném lỗi từ chính lớp test. Cần kết hợp liên kết
theo độ phủ hoặc theo quy ước tên, nếu không mất 60% bằng chứng test hỏng.
