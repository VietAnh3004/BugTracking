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

## Cấu trúc thư mục

```
├── core-backend/     Spring Boot (Maven)
├── agent-service/    FastAPI
├── frontend/         React + Vite
├── case-study/
│   ├── vulnshop/     Ứng dụng Java làm case study (có lỗi thật + false positive chủ đích)
│   └── ground-truth/ Nhãn TP/FP do người gán — KHÔNG tái tạo được, luôn commit
├── experiments/      Script chạy 3 cấu hình LLM-Code / LLM-Static / Evidence-Agent
├── infra/            docker-compose, Dockerfile, init.sql
├── docs/             Đề cương, kiến trúc, ADR
└── report/           Báo cáo + slide
```

---

## Chạy local

Yêu cầu: Docker, JDK 21+, Node 20+, Python 3.12+.

```bash
cp infra/.env.example infra/.env      # điền giá trị thật
docker compose -f infra/docker-compose.yml up -d
```

`docker-compose.yml` chỉ để tiện chạy local — **không** phải điều kiện để hệ thống hoạt động.
Mỗi service khởi động được đơn lẻ chỉ với biến môi trường của nó.

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

- [docs/architecture.md](docs/architecture.md) — kiến trúc chi tiết
- [docs/evidence-model.md](docs/evidence-model.md) — Unified Evidence Model
- [docs/adr/](docs/adr/) — các quyết định kiến trúc và lý do
