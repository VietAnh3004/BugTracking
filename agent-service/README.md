# agent-service

Evidence-Grounded AI Agent: truy xuất bằng chứng qua tool-call, suy luận, và sinh phản hồi có
cấu trúc trong đó **mỗi nhận định phải kèm Evidence ID**.

## Vai trò trong hệ thống

Service này **không truy cập cơ sở dữ liệu**. Mọi bằng chứng lấy qua Evidence Tool API của
core-backend. Nhờ đó năm tool trong thiết kế là ranh giới kiến trúc thật — mỗi lượt truy xuất
đều quan sát được, đo được và ghi log được cho thực nghiệm.

Việc hậu kiểm trích dẫn (đối chiếu từng Evidence ID với Evidence Store) **cũng không nằm ở đây**
mà ở core-backend, để bước kiểm tra thực sự độc lập với LLM. Đó không chỉ là quy ước: service
này không có kết nối DB nên nó **không thể** tự tuyên bố một trích dẫn là hợp lệ.

**Frontend không gọi thẳng vào đây.** Luồng là `frontend → core-backend → agent-service`; câu
trả lời quay ngược đúng đường đó và chỉ ra khỏi core-backend sau khi qua CitationValidator.
Xem [ADR 0010](../docs/adr/0010-vong-lap-agent-va-hau-kiem-trich-dan.md).

## Cấu trúc

| Thư mục | Vai trò |
|---|---|
| `config.py` | Cấu hình từ biến môi trường, fail-fast khi thiếu |
| `main.py` | FastAPI app: `GET /health`, `POST /analyze` (SSE) |
| `schemas/` | `Claim`, `Verdict`, `AnalysisResponse`, và hợp đồng HTTP |
| `providers/` | `LlmProvider` + `AnthropicProvider` + `ScriptedProvider` |
| `tools/` | Khai báo 5 evidence tool và cổng ra core-backend |
| `agent/` | Vòng lặp retrieval → reasoning → structured output |
| `prompts/` | Prompt Jinja2, tách khỏi code và version hoá |

## Vòng lặp agent

```
system + câu hỏi
   → [vòng 1..N]  model gọi tool  →  POST /api/v1/tools/<tên>  →  kết quả vào ngữ cảnh
   → [lượt cuối]  ép nộp AnalysisResponse qua tool submit_analysis
```

Bốn điều dễ làm sai:

- **Ép định dạng và cho phép gọi tool loại trừ nhau.** Ép nộp nghĩa là buộc model gọi đúng một
  tool, nên bật từ vòng đầu thì nó không bao giờ truy xuất được bằng chứng nào. Vì vậy bước nộp
  là **một lượt gọi riêng** sau khi vòng lặp kết thúc.
- **`analysis_run_id` do agent loop điền, không nằm trong schema tool.** Phạm vi tra cứu bằng
  chứng không phải thứ LLM được chọn — `short_id` chỉ duy nhất trong một run.
- **Tool hỏng không giết cả lượt.** Lỗi HTTP thành một `tool_result` đọc được để model đổi cách
  hỏi. Ngược lại, tên tool sai là bug của chính ta và phải nổ.
- **Hết ngân sách vòng lặp thì nói thẳng với model**, không im lặng cắt ngang — nó cần biết để
  nộp thứ đang có kèm phần còn thiếu.

`ScriptedProvider` trả kịch bản định sẵn. Mọi test chạy trên nó (quy ước repo: **không gọi LLM
thật trong test**), và đặt `LLM_PROVIDER=scripted` thì nghiệm thu được toàn bộ đường đi
end-to-end mà không cần khoá API.

## SSE stream cái gì

**Tiến trình truy xuất bằng chứng**, không phải từng token của câu trả lời. Đầu ra bị ép theo
cấu trúc nên một JSON dở dang chẳng đọc được — mà tệ hơn, nó sẽ hiện ra những Evidence ID chưa
qua hậu kiểm, đúng thứ mà cả cơ chế trích dẫn sinh ra để chặn.

| Sự kiện | Nội dung |
|---|---|
| `status` | Vòng thứ mấy, đang làm gì |
| `tool_call` | Tên tool và tham số model vừa sinh |
| `tool_result` | Số đo: thời lượng, số mục, lỗi nếu có. KHÔNG kèm payload |
| `answer` | Câu trả lời NGUYÊN BẢN + toàn bộ `llm_trace` của lượt |
| `error` | Lỗi kèm trace đã có tới thời điểm hỏng |

## Cạm bẫy cấu hình

**Đừng đặt `LLM_TEMPERATURE` với provider anthropic.** Các model từ Claude Opus 4.7 trở đi (gồm
`claude-sonnet-5`) trả lời **400** cho tham số này, nên đặt vào là mọi request đều hỏng.
`AnthropicProvider` nổ ngay lúc khởi động nếu thấy biến này thay vì để hỏng giữa hội thoại. Độ
ổn định giữa các lần chạy được báo cáo bằng cách chạy lặp rồi đo phương sai (kế hoạch §6).

`PROMPT_VERSION` đi thẳng vào `llm_trace.prompt_version`. Đổi prompt mà quên tăng số này thì kết
quả của hai phiên bản prompt nằm lẫn vào nhau và không tách lại được.

## Chạy local

```bash
python -m venv .venv && .venv/Scripts/activate     # Windows
pip install -e ".[dev,anthropic]"
cp .env.example .env                                # điền LLM_API_KEY và SERVICE_TOKEN
uvicorn agent_service.main:app --reload --port 8000
```

`SERVICE_TOKEN` phải **trùng** với biến cùng tên của core-backend.

## Kiểm tra chất lượng

```bash
ruff check src tests && ruff format --check src tests
mypy src
pytest
```

Test không gọi API thật: LLM luôn được mock. Gọi thật trong CI vừa tốn tiền vừa khiến test
không lặp lại được kết quả.
