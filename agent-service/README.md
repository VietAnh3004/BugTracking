# agent-service

Evidence-Grounded AI Agent: truy xuất bằng chứng qua tool-call, suy luận, và sinh phản hồi có
cấu trúc trong đó **mỗi nhận định phải kèm Evidence ID**.

## Vai trò trong hệ thống

Service này **không truy cập cơ sở dữ liệu**. Mọi bằng chứng lấy qua Evidence Tool API của
core-backend. Nhờ đó năm tool trong thiết kế là ranh giới kiến trúc thật — mỗi lượt truy xuất
đều quan sát được, đo được và ghi log được cho thực nghiệm.

Việc hậu kiểm trích dẫn (đối chiếu từng Evidence ID với Evidence Store) **cũng không nằm ở đây**
mà ở core-backend, để bước kiểm tra thực sự độc lập với LLM.

## Cấu trúc

Service này **mới có phần khung** — agent loop và năm tool là việc của tuần 12-13
([kế hoạch](../docs/ke-hoach-20-tuan.md)). Cột "Trạng thái" nói rõ cái gì đã chạy được, để không
ai đọc sơ đồ này rồi tưởng đã có sẵn thứ chưa viết.

| Thư mục | Vai trò | Trạng thái |
|---|---|---|
| `config.py` | Cấu hình từ biến môi trường, fail-fast khi thiếu | ✅ |
| `main.py` | FastAPI app | ✅ |
| `schemas/` | `Claim`, `Verdict`, `AnalysisResponse` | ✅ |
| `providers/` | `LlmProvider` + `Message` / `ToolCall` / `LlmResponse` | Mới có lớp trừu tượng, chưa có cài đặt nào |
| `tools/` | 5 evidence tool, gọi ngược về core-backend | Còn rỗng |
| `agent/` | Vòng lặp retrieval → reasoning → structured output | Còn rỗng |
| `prompts/` | Prompt Jinja2, tách khỏi code và version hoá | Còn rỗng |

`LlmProvider` là abstract base class với đúng một method `chat(messages, tools, response_schema)`
— thêm provider mới không phải sửa agent loop. Cài đặt cụ thể cho Anthropic / OpenAI / Ollama
chọn theo biến `LLM_PROVIDER`; `pyproject.toml` để SDK của từng nhà cung cấp trong extras riêng
(`.[anthropic]`, `.[openai]`) nên không cần cài cả ba mới chạy được service.

Prompt để trong file riêng chứ không nhúng giữa code: báo cáo cần trích dẫn prompt chính xác,
và thực nghiệm cần biết phiên bản prompt nào sinh ra kết quả nào.

Trước khi viết mã gọi LLM thật, **nạp skill `claude-api`** để lấy model ID, giá và API tool-use
đúng thay vì viết theo trí nhớ.

## Chạy local

```bash
python -m venv .venv && .venv/Scripts/activate     # Windows
pip install -e ".[dev,anthropic]"
cp .env.example .env                                # điền LLM_API_KEY
uvicorn agent_service.main:app --reload --port 8000
```

## Kiểm tra chất lượng

```bash
ruff check src tests && ruff format --check src tests
mypy src
pytest
```

Test không gọi API thật: LLM luôn được mock. Gọi thật trong CI vừa tốn tiền vừa khiến test
không lặp lại được kết quả.
