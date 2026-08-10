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
| [docs/adr/](docs/adr/) | Mười quyết định kiến trúc lớn và lý do |
| [docs/evidence-model.md](docs/evidence-model.md) | Schema Unified Evidence Model — bảng provenance cho **cả 7 bảng bằng chứng** (V2 + 5 bảng thô của V5), mỗi cột trỏ về đúng chỗ trong artifact thật. Sửa schema thì phải sửa cả tài liệu: `docs/check-evidence-model.py` đối chiếu HAI CHIỀU và CI đỏ nếu lệch. Đọc trước khi đụng vào parser trong `service/analysis/` |
| [docs/spike-tuan-1.md](docs/spike-tuan-1.md) | Xác thực rủi ro: 3 nguồn bằng chứng khớp `file:line` |
| [docs/huong-dan-test-frontend.md](docs/huong-dan-test-frontend.md) | Giải thích snapshot/cảnh báo/severity/sandbox + các bước nghiệm thu bằng trình duyệt thật |
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

**Máy này mặc định mở PowerShell 5.1, nhưng gần như mọi lệnh trong repo viết cho Git Bash.** Khác
biệt gây vấp nhiều nhất, theo thứ tự đã gặp: PowerShell 5.1 **không hiểu `&&`** (báo
*"The token '&&' is not a valid statement separator"*, dùng `;` hoặc `if ($?) { }`) · **không có
`zip`** (dùng `Compress-Archive`, vốn **không có tham số loại trừ** nên phải qua thư mục trung
gian) · **`curl` là alias của `Invoke-WebRequest`**, phải gõ **`curl.exe`** đầy đủ mới ra curl thật
· `Invoke-RestMethod` **không gửi được multipart** (tham số `-Form` chỉ có từ PowerShell 6.1).
Bản PowerShell của các lệnh nghiệm thu nằm ở `docs/huong-dan-test-frontend.md`.

**Sửa file bằng Python một dòng: LUÔN mở dạng nhị phân khi nội dung có dấu `\`.** Đã hỏng thật hai
lần trong một phiên: `'D:\study\...\vulnshop'` viết trong chuỗi Python bị nuốt `\v` thành ký tự
điều khiển U+000B, cho ra `case-study<VT>ulnshop` — và trên terminal nó **hiển thị y như đúng** vì
VT vô hình. Lần thứ hai còn tệ hơn: chuỗi *tìm kiếm* và chuỗi *thay thế* cùng bị nuốt như nhau nên
`replace` báo thành công mà không đổi gì. Cách chắc chắn: `open(p,'rb')` + so khớp `bytes`, dựng ký
tự đặc biệt bằng `bytes([92])` thay vì viết `\\`. (Cạm bẫy CRLF của `io.open(p,'w')` ghi riêng ở
mục script `.sh` phía trên.)

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

**Git Bash + Docker có HAI cái bẫy đường dẫn NGƯỢC CHIỀU nhau — chỉ tắt path conversion là hỏng
cái còn lại.** Đường dẫn **trong container** (`/work`, `name:/work/src`) bị MSYS biến thành
`C:/Program Files/Git/work` nên cần `MSYS_NO_PATHCONV=1`; nhưng đường dẫn **trên host** (build
context, nguồn của `docker cp`) ở dạng `/d/study/...` thì daemon Windows không hiểu và báo
`unable to prepare context: path "/d/study/..." not found`. Cách dùng được: bật `MSYS_NO_PATHCONV`
cho mọi lệnh docker **và** chuyển tay mọi đường dẫn host bằng `cygpath -m`. Xem
`infra/analyzer/build.sh`.

**Script `.sh` chạy trong container Linux phải giữ LF — CRLF làm shebang thành `bash\r`.** Thông
điệp lỗi là `/usr/bin/env: 'bash\r': No such file or directory`, **không hề nhắc tới xuống dòng**.
Gặp thật khi một script Python sửa hộ file: `io.open(path, 'w')` trên Windows mặc định đổi `\n`
thành `\r\n`, nên một lần chỉnh sửa tự động là đủ làm hỏng. Đã chặn tận gốc bằng
`*.sh text eol=lf` trong `.gitattributes`; khi sửa file bằng Python luôn mở dạng nhị phân
(`open(p,'rb')` / `open(p,'wb')`) hoặc truyền `newline='\n'`.

**`docker cp <thư mục>` gán quyền ROOT cho file chép vào, kể cả khi container chạy non-root.**
Triệu chứng gặp thật: Maven trong container chết với
`Cannot create resource output directory: /work/src/target/classes` — một lỗi *về trình biên dịch*,
không hề nhắc tới quyền, nên rất dễ đi sửa nhầm chỗ. Cách đúng: tự dựng tar mang uid/gid của user
trong image rồi đưa qua stdin (`docker cp -` giữ nguyên uid/gid trong tar):
`tar --owner=10001 --group=10001 --numeric-owner -cf - -C "$W" src | docker cp - "$c:/work"`.
Đích phải là thư mục **đã tồn tại**, nên tar mang sẵn tiền tố `src/`.

**`mvn dependency:go-offline` KHÔNG kéo provider của Surefire.** Surefire phân giải
`surefire-junit-platform` *lúc chạy*, sau khi dò được khung test đang dùng, nên nó không nằm trong
đồ thị phụ thuộc mà `go-offline` biết — và nó còn kéo tiếp `junit-platform-launcher`. Hậu quả khi
chạy offline: mất **toàn bộ** test + độ phủ, trong khi SpotBugs vẫn chạy xong và script vẫn exit 0.
`mvn test -Dtest=<lớp không tồn tại>` **không** ép được nó phân giải (thoát trước bước đó). Cách
dùng được: đọc phiên bản từ `help:effective-pom` rồi `dependency:get` thẳng. Xem
`infra/analyzer/java/analyze.sh`.

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

**Cùng thông điệp lỗi đó còn có nguyên nhân THỨ HAI: quá nhiều worker chạy song song.** Gặp ở
tuần 8-9, khi bộ test frontend lên 13 file: `npm test` đỏ toàn bộ với đúng chuỗi
`Failed to start forks worker ... Timeout waiting for worker to respond` **dù đang dùng
happy-dom** — rất dễ tưởng đã quay lại lỗi jsdom cũ và đi sửa nhầm chỗ lần nữa. Nguyên nhân khác
hẳn: mặc định Vitest mở ~(số core − 1) tiến trình cùng lúc, máy này 16 core nên 15 tiến trình
đồng thời nạp module graph và cùng bị Windows Defender quét, tất cả vượt ngưỡng 60 giây.
Đo được: **15 worker ĐỎ · 8 worker xanh trong 26s · 4 worker xanh trong 49s · 2 worker xanh trong
118s**. Đã chặn tận gốc bằng `maxWorkers: '50%'` trong `frontend/vite.config.ts` (dùng phần trăm
để co giãn theo máy chạy CI, không hardcode số). Cách phân biệt nhanh hai nguyên nhân: chạy lại
với `npx vitest run --maxWorkers=2` — xanh nghĩa là lỗi tải, vẫn đỏ nghĩa là lỗi environment.

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

**Flyway thay placeholder CẢ TRONG COMMENT SQL.** Gặp thật ở tuần 10-11: comment trong
`V7__evidence_embedding_and_linker.sql` viết ra tên một placeholder (dạng đô-la kèm ngoặc nhọn)
để **giải thích vì sao KHÔNG dùng nó** — Flyway vẫn quét trúng và ứng dụng chết lúc khởi động với
`No value provided for placeholder`. Triệu chứng ở test là `ApplicationContext failure threshold
(1) exceeded` trên **mọi** test tích hợp, hoàn toàn không gợi ý gì về SQL; phải lần tới `Caused by`
cuối cùng mới thấy. Muốn nhắc tên một placeholder trong comment thì gọi nó bằng lời, đừng viết
đúng cú pháp.

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

**Dạng thứ BA của tranh chấp `target/`: `.class` hỏng, báo bằng `ClassFormatError: Extra bytes at
the end of class file`.** Gặp thật ở tuần 8-9 ngay sau một lần `mvn clean verify`: Spring không nạp
được `GlobalExceptionHandler` với *"problem with class file or dependent class"*, và **mọi** test
tích hợp đỏ hàng loạt với `ApplicationContext failure threshold (1) exceeded` — một thông điệp
hoàn toàn không gợi ý gì về nguyên nhân. Phải lần tới `Caused by` thứ hai mới thấy `ClassFormatError`.
Nguyên nhân giống hai dạng kia (IDE background compiler ghi đè file trong `target/` giữa lúc `mvn`
đang đọc), cách xử lý cũng giống: chạy lại y nguyên lệnh đó. Mẹo khoanh vùng: `grep -n "Caused by"`
trên log và đọc cái CUỐI CÙNG, đừng dừng ở cái đầu tiên.

**Một lỗi biên dịch bất kỳ làm Lombok DỪNG sinh code, và javac báo tiếp một loạt
`cannot find symbol: method getId()` trên các entity — trông y hệt entity hỏng.** Gặp thật ở tuần
8-9: lỗi thật chỉ là `@Scheduled(fixedDelay = INTERVAL_MS)` với `INTERVAL_MS` tính bằng
`Duration.ofMinutes(30).toMillis()` (annotation đòi **constant expression**, biểu thức gọi method
không phải), nhưng log đầy `cannot find symbol` ở `AnalysisRun.getId()` — rất dễ đi sửa nhầm entity
hoặc tưởng Lombok hỏng. **Luôn sửa lỗi ĐẦU TIÊN trong danh sách rồi biên dịch lại trước khi tin
những lỗi phía sau.** Cùng họ với bẫy "đọc `Caused by` cuối cùng" ở dưới.

**`InspectVolumeResponse` của docker-java nằm ở `com.github.dockerjava.api.command`, KHÔNG phải
`api.model`** — khác với `Container`, `HostConfig`, `Frame`, `Volume` vốn đều ở `api.model`, nên
IDE tự import rất dễ chọn nhầm chỗ rồi báo "cannot find symbol" một cách khó hiểu.

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

**`@Transactional(REQUIRES_NEW)` NUỐT MỌI UPDATE chưa commit của transaction bên ngoài.** Gặp thật
ở tuần 10-11 và suýt vô hiệu hoá cả một cơ chế chống hỏng-im-lặng: `AnalysisRunPersistenceService
.finalizeRun` là `REQUIRES_NEW`, nên nó **treo** transaction hiện tại rồi **đọc lại** hàng
`analysis_run` trong một transaction mới. Một `UPDATE analysis_run SET link_count = ...` chưa commit
từ bên ngoài là **vô hình** với nó, và dirty-check của Hibernate ghi đè cả hàng bằng giá trị cũ —
bộ đếm về 0 trong im lặng, mọi guard "0 liên kết = lỗi" bị vô hiệu trong khi nhìn vẫn như đang chạy.
Cách đúng: số đo đi **ngược lên** qua một record kết cục (`RunOutcome.withLinks`) để chính
`finalizeRun` ghi. Cùng họ bài học với `@Transactional(noRollbackFor=...)` ở trên: **ranh giới
transaction là chỗ dữ liệu biến mất mà không ai báo.**

**JavaParser: PHẢI khai `ParserConfiguration.LanguageLevel` tường minh.** Mặc định thấp hơn Java 21
sẽ parse hỏng `record` / `sealed` / pattern matching trong `switch`, và hỏng theo kiểu trả
`ParseResult` không thành công cho **CẢ FILE** — mất sạch ngữ cảnh mã của file đó trong khi pipeline
vẫn báo thành công. Dùng `new JavaParser(config)` chứ đừng dùng `StaticJavaParser`: nó là singleton
toàn cục, cấu hình bị lớp khác đổi là hỏng ngầm.

**`OffsetDateTime.now()` ghi xuống Postgres rồi đọc lại KHÔNG cho lại chuỗi cũ — lệch vì HAI lý
do độc lập.** Gặp thật ở tuần 14 khi chạy stack thật: `PUT .../review` lần đầu (INSERT, giá trị
lấy từ object trong bộ nhớ) trả `2026-08-10T16:39:40.3879908+07:00`, lần sau (đã đọc lại từ DB)
trả `2026-08-10T09:39:40.387991Z`. Cùng một thời điểm, hai biểu diễn: (1) `timestamptz` chỉ giữ
tới **microsecond** còn `OffsetDateTime.now()` cho tới **nanosecond**; (2) JDBC đọc `timestamptz`
về luôn ở **UTC** còn `now()` mang offset của máy chủ. Không có lỗi nào xảy ra — chỉ có một trường
đổi giá trị giữa hai lần gọi, nên mọi phép so khớp theo chuỗi (snapshot test, diff JSON, cache
phía client) đều sai. Cách chữa: đóng dấu bằng
`OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)`. **Test bằng `assertThat(...)
.isEqualTo(...)` KHÔNG bắt được**: assertion của AssertJ trên `OffsetDateTime` so theo *thời điểm*,
nên `+07:00` và `Z` của cùng một mốc vẫn xanh — phải so `toString()`.

**Cột `vector` của pgvector cần ép kiểu `?::vector` trong JdbcTemplate**, y hệt `?::jsonb`. Thiếu ép
kiểu là lỗi **runtime**, không phải lỗi biên dịch. Ngoài ra `vector` **không khai số chiều** thì
KHÔNG tạo được index HNSW/IVFFlat (`ERROR: column does not have dimensions`) — đã đo trên chính
Postgres của dự án, xem ADR 0009.

**`temperature` LÀM HỎNG MỌI REQUEST tới Claude từ Opus 4.7 trở đi — kể cả `temperature=0`.** Các
model đời mới (gồm `claude-sonnet-5` mà dự án đang khai) **từ chối** `temperature`/`top_p`/`top_k`
bằng lỗi **400**, không phải bỏ qua. Gặp thật: `.env.example` của agent-service đặt
`LLM_TEMPERATURE=0.0` từ tuần 1 với lý do rất hợp lý ("để thực nghiệm bớt dao động"), và nó sẽ làm
**không request nào chạy** ngay khi bật provider thật — một cấu hình sai nằm im 12 tuần vì chưa ai
gọi LLM. Nay mặc định là "không gửi", và `AnthropicProvider` **nổ lúc khởi động** nếu thấy biến
này. Muốn kết quả ổn định giữa các lần chạy thì chạy lặp rồi đo phương sai, đừng tìm một tham số
để ép — API không còn nhận nữa. Trước khi viết mã gọi LLM, **nạp skill `claude-api`**.

**`RestClient` của Spring dùng JDK HttpClient, và nó THỬ NÂNG CẤP h2c — LÀM MẤT THÂN REQUEST.**
Không có Apache/Jetty/Netty trên classpath thì Spring chọn `HttpClient` của JDK, vốn mặc định
`Version.HTTP_2`. Với một server HTTP/1.1 (uvicorn), nó gửi `Upgrade: h2c` +
`Transfer-Encoding: chunked` **nhưng không gửi thân request**. Triệu chứng gặp thật ở tuần 12-13:
FastAPI trả `422 {"loc":["body"],"msg":"Field required"}` cho một `POST` mà phía Java tin là đã
gửi đủ — không có lỗi, không có cảnh báo, chỉ là một request rỗng. Chỉ tìm ra bằng cách dựng một
server nghe thử rồi in nguyên văn thứ đi qua dây. Cách chữa: ghim
`HttpClient.newBuilder().version(HTTP_1_1)` rồi truyền vào `JdkClientHttpRequestFactory`. Xem
`AgentClient`.

**Kèm THÂN response vào thông báo lỗi khi gọi service khác, đừng chỉ kèm mã trạng thái.** Cùng ca
trên: `"agent-service tra ve 422"` không nói được gì và biến một lỗi hợp đồng sửa trong một phút
thành một cuộc điều tra; `"...: {'loc':['body'],'msg':'Field required'}"` chỉ thẳng nguyên nhân.

**Lọc SAU khi đã `LIMIT` là lọc sai — và với LLM thì sai âm thầm.** `EvidenceQueryService
.searchEvidence` từng lấy `limit` bản ghi rồi mới lọc theo `types` trong Java: hỏi 3 cảnh báo tĩnh
có thể trả **rỗng** dù run có hàng chục. Tệ hơn, truy vấn không có `ORDER BY` nên **cùng một lời
gọi cho kết quả khác nhau giữa các lần chạy** — đúng lúc tôi tưởng mình đã kiểm chứng bản vá thì
thật ra chỉ gặp may. LLM nhận "không tìm thấy" là tin ngay và kết luận `INSUFFICIENT_EVIDENCE`
nhầm. Bộ lọc phải xuống truy vấn, và mọi truy vấn có `LIMIT` phải có `ORDER BY` ổn định.

**`mvn spring-boot:run` chạy nền: TaskStop giết wrapper nhưng KHÔNG giết JVM đã fork.** Lần khởi
động sau thất bại vì cổng 8080 còn bị giữ, `/actuator/health` vẫn trả `UP` (của tiến trình CŨ),
nên nhìn như đã restart. Hậu quả: nghiệm thu một bản vá trên mã chưa hề được nạp. Kiểm bằng
`Get-NetTCPConnection -LocalPort 8080` rồi `Stop-Process` đúng PID, và luôn xác nhận
`grep -c "Started CoreBackendApplication" run.log` bằng 1.

**Ép structured output và cho phép gọi tool LOẠI TRỪ NHAU.** Ép model nộp đúng lược đồ nghĩa là
buộc nó gọi đúng một tool (`tool_choice: {type: "tool", name: ...}`). Bật từ vòng đầu của agent
loop thì nó **không bao giờ truy xuất được bằng chứng nào** — mà vẫn trả về một câu trả lời đúng
khuôn dạng, nên nhìn không ra. Bước nộp phải là một lượt gọi RIÊNG sau khi vòng lặp tool kết thúc.

**Mọi `tool_result` của cùng một lượt phải nằm trong MỘT message người dùng.** Tách ra nhiều
message khiến model dần thôi gọi tool song song — hỏng âm thầm, chỉ lộ ra ở số vòng lặp tăng dần
và chi phí tăng theo, không có thông báo lỗi nào. Xem `_split_system` trong
`agent-service/src/agent_service/providers/anthropic_provider.py` và test canh nó.

**Khung SSE CUỐI thường không có dòng trống theo sau — và khung cuối chính là câu trả lời.** Cả ba
tầng trong repo này (agent-service phát, `AgentClient` đọc, `sseClient.ts` đọc) đều phải xử lý ca
đó. Đánh rơi nó không có lỗi nào: người dùng thấy tiến trình chạy xong rồi im lặng. Mỗi tầng có
một test riêng cho đúng ca này.

**Trình duyệt KHÔNG dùng được `EventSource` cho SSE có xác thực.** Nó không gửi được header tuỳ ý
(không gắn được `Authorization: Bearer`) và chỉ làm được `GET`. Nhét token vào query string thì
token nằm trong log của mọi reverse proxy trên đường đi. Cách dùng được: `fetch` + tự đọc
`ReadableStream`, và **bắt buộc** `TextDecoder.decode(value, { stream: true })` — thiếu cờ đó thì
một ký tự tiếng Việt bị cắt giữa hai chunk sẽ thành U+FFFD ngay giữa câu trả lời.

**Lombok `@RequiredArgsConstructor` KHÔNG chép `@Qualifier` sang tham số constructor.** Có hai bean
cùng kiểu (`agentExecutor` và `sandboxExecutor`) thì việc tiêm rơi vào trùng-tên-trường — một ràng
buộc ngầm mà đổi tên biến là hỏng, và hỏng lúc khởi động chứ không lúc biên dịch. Viết constructor
tay khi cần `@Qualifier`; xem `AnalysisRunService` và `ConversationService`.

**Suy tên lớp production từ tên lớp test: trả DANH SÁCH ứng viên, đừng đoán một giá trị.** Gặp thật:
`UserRepositoryIntegrationTest` bỏ hậu tố `Test` ra `UserRepositoryIntegration` — một lớp không tồn
tại — nên **toàn bộ bằng chứng kiểm thử của cạm bẫy trung tâm (`findByUsername`) biến mất trong im
lặng**. Tên lớp test không đủ để biết ranh giới giữa tên lớp production và mô tả phạm vi
(`Integration`/`Unit`/`E2E`/`Slice`); chỉ dữ liệu mới trả lời được lớp nào thật sự tồn tại. Xem
`TestClassNameConvention.productionClassCandidates`.

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

**Liên kết bằng chứng không thể chỉ dựa vào stack trace.** Thực tế chỉ 3/6 test hỏng cho frame
trỏ về mã production; test hỏng kiểu assertion ném lỗi từ chính lớp test. Cần kết hợp liên kết
theo độ phủ hoặc theo quy ước tên, nếu không mất một nửa bằng chứng test hỏng.

Tệ hơn thế: có frame production mới là điều kiện **CẦN**, chưa đủ — frame còn phải rơi vào một
dòng **đã có cảnh báo**. Tới hết tuần 8-9, cả hai frame production của vulnshop đều thuộc nhóm lỗi
"không công cụ nào bắt", nên EvidenceLinker tầng 3 chạy đúng mà sinh **0 liên kết**. Phải gieo
thêm TP-16 (`UserService.displayNameOf`) mới có một ca `SUPPORTS` thật. Xem
`case-study/vulnshop/SEEDED.md`.
