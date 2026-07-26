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

```
src/agent_service/
├── config.py      Cấu hình từ biến môi trường, fail-fast khi thiếu
├── main.py        FastAPI app
├── schemas/       Lược đồ phản hồi có cấu trúc (Claim, Verdict, AnalysisResponse)
├── providers/     LlmProvider + cài đặt cho Anthropic / OpenAI / Ollama
├── tools/         5 evidence tool, gọi ngược về core-backend
├── agent/         Vòng lặp retrieval → reasoning → structured output
└── prompts/       Prompt dạng template Jinja2, tách khỏi code và version hoá
```

Prompt để trong file riêng chứ không nhúng giữa code: báo cáo cần trích dẫn prompt chính xác,
và thực nghiệm cần biết phiên bản prompt nào sinh ra kết quả nào.

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
