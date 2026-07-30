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

**Comment trong XML không được chứa `--`** (đã làm hỏng pom.xml một lần).

**`mvn verify` báo lỗi giả sau nhiều lần `mvn test`/`spotless:apply` liên tiếp không `clean`.**
Gặp thật: `ProjectController` báo "No qualifying bean of type ProjectMapper" dù `@Mapper` hoàn
toàn đúng — nguyên nhân là compile tăng tiến (incremental) của Maven không sinh lại
`ProjectMapperImpl` (annotation processor MapStruct) sau nhiều lần build liên tiếp trong cùng
phiên. Trước khi coi một lỗi biên dịch/bean-không-tìm-thấy là lỗi thật, chạy lại với
`mvn clean verify` — nếu xanh thì lỗi trước đó là giả, do stale `target/`.

**`TestFixtures` (test helper dùng `saveAndFlush` nhiều bước liên tiếp trong cùng method):
method nào cần entity vừa lưu vẫn "managed" ở bước sau (vd gán vào quan hệ `@MapsId`) phải đánh
dấu `@Transactional`.** Thiếu annotation này, mỗi `saveAndFlush` tự mở/đóng transaction riêng,
entity trả về bị detach, và Hibernate từ chối gán entity detached vào một quan hệ với lỗi
"detached entity passed to persist". Xem
`core-backend/src/test/java/vn/hust/datn/evidence/testsupport/TestFixtures.java` (method
`warning()`).

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
