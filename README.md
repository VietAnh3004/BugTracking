# Evidence-Based Program Analysis with AI Agents

Nền tảng web hỗ trợ phân tích chương trình dựa trên bằng chứng và AI Agent.

Đồ án tốt nghiệp — Nguyễn Việt Anh (20224923), Khoa học máy tính IT1
GVHD: PGS.TS. Huỳnh Quyết Thắng — Đại học Bách khoa Hà Nội, 2026.

---

## Bài toán

Công cụ phân tích tĩnh sinh ra rất nhiều cảnh báo, trong đó có false positive và cảnh báo khó
diễn giải. LLM có thể giải thích chúng bằng ngôn ngữ tự nhiên nhưng dễ đưa ra nhận định thiếu
căn cứ khi không có đủ bằng chứng.

Hệ thống này tổ chức một **chuỗi bằng chứng** — cảnh báo tĩnh, ngữ cảnh mã nguồn, bằng chứng
kiểm thử — vào một **Unified Evidence Model**, rồi để AI Agent truy xuất bằng chứng qua tool-call
và trả lời người dùng qua giao diện hội thoại. **Mỗi nhận định của Agent bắt buộc gắn Evidence ID
kiểm chứng được**; nhận định không gắn được bằng chứng hợp lệ bị hạ xuống trạng thái
`INSUFFICIENT_EVIDENCE` thay vì được trình bày như kết luận chắc chắn.

```
Static Warning → Code/Test Evidence → Evidence Retrieval → AI Reasoning
              → Conversational Answer → Evidence Traceability → Human Decision
```

---

## Kiến trúc

Ba service triển khai **độc lập**, mỗi service một image và một bộ biến môi trường riêng:

| Service | Công nghệ | Vai trò |
|---|---|---|
| `frontend` | React + TypeScript + Vite, phục vụ bởi Nginx | SPA tĩnh: warning list, evidence explorer, chat, human review |
| `core-backend` | Spring Boot 3, Java 21 | Repository manager, static analysis adapter, test evidence collector, evidence store, citation validator, human review |
| `agent-service` | FastAPI, Python 3.12+ | Agent loop, 5 evidence tool, retrieval, `LlmProvider` (Anthropic / OpenAI / Ollama) |
| `postgres` | PostgreSQL 16 + pgvector | Evidence store + embedding |

Hai quyết định kiến trúc đáng chú ý:

- **Agent Service không truy cập DB trực tiếp.** Nó gọi Evidence Tool API do core-backend expose,
  nên 5 tool trong thiết kế là ranh giới kiến trúc thật — dễ log, dễ đo, dễ thay thế.
- **CitationValidator chạy ở core-backend**, ngoài process của agent — để việc hậu kiểm trích dẫn
  thực sự độc lập với LLM chứ không chỉ là mô tả.

---

## Hiện chạy được tới đâu

**Trên giao diện web**: đăng nhập → tạo dự án → tải mã nguồn (`.zip`) lên → duyệt cây file và xem
mã theo dòng → xem danh sách cảnh báo đã chuẩn hoá của một lần phân tích, bấm để nhảy tới đúng
dòng nguồn.

**Chỉ gọi được bằng API, chưa có nút bấm**: nạp artifact analyzer
(`POST /api/v1/snapshots/{id}/analysis-runs`) và chạy analyzer trong Docker sandbox
(`.../analysis-runs/sandbox`). Cả hai đã chạy thật; phần còn thiếu là nút trên UI. Các bước nghiệm
thu bằng `curl`: [docs/huong-dan-test-frontend.md](docs/huong-dan-test-frontend.md).

**Chưa có**: liên kết bằng chứng ba tầng, `CODE_CONTEXT` qua JavaParser, Evidence Explorer, agent
loop và giao diện hội thoại — `agent-service` mới có khung cấu hình, `LlmProvider` trừu tượng và
lược đồ phản hồi.

Trạng thái chi tiết theo từng tuần và từng hạng mục: [docs/ke-hoach-20-tuan.md](docs/ke-hoach-20-tuan.md).
Đó là **nguồn sự thật duy nhất về tiến độ**; mục này chỉ mô tả định tính để khỏi lệch số.

---

## Cấu trúc thư mục

```
├── core-backend/       Spring Boot (Maven)
├── agent-service/      FastAPI
├── frontend/           React + Vite
├── case-study/
│   ├── vulnshop/       Ứng dụng Java làm case study (có lỗi thật + false positive chủ đích)
│   ├── semgrep-rules/  Ruleset Semgrep đã vendor — điều kiện để thực nghiệm tái lập được
│   └── ground-truth/   Nhãn TP/FP do người gán — KHÔNG tái tạo được, luôn commit
├── experiments/        Script chạy 3 cấu hình LLM-Code / LLM-Static / Evidence-Agent
├── infra/
│   ├── analyzer/       Hai image chạy analyzer trong sandbox + script dựng-và-đối-chiếu
│   └── ...             docker-compose, Dockerfile, init.sql
├── docs/               Đề cương, kiến trúc, ADR, artifact mẫu
└── report/             Báo cáo + slide
```

---

## Chạy local

Yêu cầu: Docker, **JDK 21** (không phải bản mới hơn — SpotBugs và Spotless vỡ trên JDK 25, xem
[ADR 0003](docs/adr/0003-chuan-hoa-jdk-21.md)), Node 20+, Python 3.12+.

```bash
cp infra/.env.example infra/.env      # điền giá trị thật
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
```

`docker-compose.yml` chỉ để tiện chạy local — **không** phải điều kiện để hệ thống hoạt động.
Mỗi service khởi động được đơn lẻ chỉ với biến môi trường của nó.

**Docker sandbox cho analyzer mặc định TẮT** (`ANALYSIS_RUNNER=disabled`). Bật nó đòi mount
`/var/run/docker.sock`, tương đương cấp quyền root trên máy chủ — nên dòng mount để **comment**
trong compose chứ không đặt sau một biến môi trường. Cách bật và rủi ro đi kèm:
[infra/README.md](infra/README.md) và [ADR 0008](docs/adr/0008-chay-analyzer-trong-docker-sandbox.md).

Chi tiết từng service: xem README trong thư mục tương ứng.

---

## Cấu hình

Không có secret nào được commit. Mỗi service có `.env.example` liệt kê đủ biến với giá trị giả;
`.env` bị gitignore. Các service **fail ngay lúc khởi động** nếu thiếu biến bắt buộc, thay vì
lỗi mơ hồ lúc chạy.

Frontend đọc API base URL **lúc chạy** qua `window.__APP_CONFIG__` (sinh bởi entrypoint của
container), không phải lúc build — nên một image dùng được cho mọi môi trường.

---

## Tài liệu

- [docs/ke-hoach-20-tuan.md](docs/ke-hoach-20-tuan.md) — **kế hoạch đầy đủ + trạng thái tiến độ**
  (nguồn sự thật duy nhất về tiến độ; các file khác không chép lại con số)
- [docs/evidence-model.md](docs/evidence-model.md) — Unified Evidence Model: bảng provenance từng
  cột trỏ về đúng chỗ trong artifact thật, ERD, và cách kiểm chứng tự động
- [docs/adr/](docs/adr/) — các quyết định kiến trúc và lý do
- [docs/spike-tuan-1.md](docs/spike-tuan-1.md) — xác thực rủi ro: 3 nguồn bằng chứng khớp `file:line`
- [docs/spike-spotbugs-cli.md](docs/spike-spotbugs-cli.md) — SpotBugs CLI cho kết quả trùng khít
  Maven plugin, tiền đề của Docker sandbox
- [docs/huong-dan-test-frontend.md](docs/huong-dan-test-frontend.md) — nghiệm thu bằng trình duyệt
  thật (kèm bản lệnh cho cả Git Bash lẫn PowerShell)
- [docs/related-work.md](docs/related-work.md) — khung khảo sát tài liệu cho chương 2
- [case-study/vulnshop/SEEDED.md](case-study/vulnshop/SEEDED.md) — danh mục lỗi gieo có chủ đích
- [docs/samples/](docs/samples/) — artifact thật của analyzer
- [infra/analyzer/README.md](infra/analyzer/README.md) — hai image analyzer và năm cạm bẫy khi dựng
- [CLAUDE.md](CLAUDE.md) — quy ước, lệnh hay dùng, cạm bẫy môi trường
