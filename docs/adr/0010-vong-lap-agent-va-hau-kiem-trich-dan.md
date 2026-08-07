# ADR 0010 — Vòng lặp agent, hậu kiểm trích dẫn, và đường đi của một câu trả lời

- **Trạng thái**: Chấp nhận
- **Ngày**: 07/08/2026
- **Liên quan**: [ADR 0001](0001-tach-agent-service-khoi-core-backend.md) (tách agent-service),
  [ADR 0005](0005-evidence-tool-api-la-rpc.md) (Evidence Tool API là RPC),
  [ADR 0009](0009-lien-ket-bang-chung-ba-tang.md) (liên kết bằng chứng — phát hiện 3 là đầu vào
  trực tiếp của quyết định 5 dưới đây)

## Bối cảnh

Hết tuần 10-11, Evidence Store đã đầy đủ: 124 bằng chứng và 123 liên kết trên vulnshop, năm
evidence tool trả dữ liệu thật. Nhưng chưa có gì *dùng* chúng — `agent-service` mới có phần khung,
và bảng `claim` / `claim_citation` mà V2 dựng sẵn chưa có writer nào.

Tuần 12-13 nối phần còn thiếu. Deliverable của lộ trình: *"Hỏi 'test nào liên quan?' → trả lời có
citation click được"*. Tài liệu này ghi lại những gì phải quyết khi hiện thực nó.

## Quyết định

### 1. Frontend gọi core-backend, core-backend gọi agent-service — không có đường tắt

```
trình duyệt ──REST/SSE──> core-backend ──HTTP/SSE──> agent-service
                               │                          │
                               │ JDBC                     │ X-Service-Token
                               ▼                          ▼
                          Evidence Store <──── /api/v1/tools/**
```

Kiến trúc mục tiêu ở §1 của kế hoạch đã vẽ đúng hình này và `application.yml` đã có sẵn
`app.agent.base-url` từ tuần 4 — nhưng chưa ai dùng, nên nó cần được chốt lại thành quyết định
tường minh trước khi có người nối tắt.

Ba lý do, xếp theo mức nghiêm trọng:

1. **Câu trả lời phải qua hậu kiểm trước khi tới mắt người dùng.** Nếu trình duyệt đọc thẳng
   stream của agent-service, người dùng đã đọc xong kết luận *trước khi* CitationValidator kịp hạ
   cấp nó. Bước hậu kiểm khi đó chỉ còn là một dòng trong báo cáo.
2. **agent-service không có xác thực người dùng.** Nó không biết ai sở hữu run nào; thêm JWT vào
   đó là nhân đôi toàn bộ tầng auth (và nhân đôi cả chỗ để sai).
3. **`toolChain` tắt hẳn CORS** (ADR 0005). Trình duyệt gọi thẳng sẽ bị chặn ở preflight với
   thông điệp *"blocked by CORS policy"* — người debug đi sửa CORS trong khi vấn đề là chọn nhầm
   endpoint.

### 2. SSE stream TIẾN TRÌNH TRUY XUẤT, không stream token của câu trả lời

Phản xạ đầu tiên khi nghe "chat + streaming" là stream từng token. Ở đây điều đó vừa vô dụng vừa
có hại: đầu ra bị **ép theo cấu trúc**, nên thứ chảy ra là JSON dở dang — không đọc được, và tệ
hơn là nó phơi ra những Evidence ID **chưa qua hậu kiểm**, đúng thứ mà cả cơ chế trích dẫn sinh ra
để chặn.

Cái được stream là: agent đang gọi tool nào, mất bao lâu, lấy về bao nhiêu mục.

| Sự kiện | Ai sinh | Ghi chú |
|---|---|---|
| `status` | agent-service | Vòng thứ mấy |
| `tool_call` | agent-service | Tên tool + tham số model vừa sinh |
| `tool_result` | agent-service | Số đo, **không kèm payload** |
| `answer` | **core-backend** | Đã hậu kiểm, đã ghi DB. Sự kiện `answer` của agent-service **không bao giờ** đi tiếp |
| `error` | cả hai | |

Phần thưởng ngoài dự tính: nó làm luận điểm *"5 tool là ranh giới kiến trúc thật, đo được"*
**nhìn thấy được bằng mắt** trong lúc demo, thay vì phải tin vào một bảng số.

### 3. Ép định dạng đầu ra là MỘT LƯỢT GỌI RIÊNG, sau vòng lặp tool

Ép model nộp đúng lược đồ nghĩa là buộc nó gọi đúng một tool (`submit_analysis` với
`tool_choice`). Bật từ vòng đầu thì nó **không bao giờ truy xuất được bằng chứng nào** — và kết
luận lúc đó là suy đoán thuần tuý dù đúng khuôn dạng.

Nên vòng lặp chạy với `tool_choice` tự do cho tới khi model thôi gọi tool, rồi một lượt cuối mới
ép nộp. Lượt cuối nhìn thấy toàn bộ lịch sử nên nó chỉ định dạng lại kết luận đã có, không suy
luận lại.

Ép bằng **tool-call** thay vì bằng cơ chế structured-output riêng của từng hãng cũng là chủ ý: nó
chạy như nhau trên cả ba provider, nên thực nghiệm tuần 15 so được kết quả giữa chúng mà không
đổi mã.

### 4. `analysis_run_id` không nằm trong schema tool

Bốn trong năm tool nhận `evidence_id`, nhưng **không tool nào** khai `analysis_run_id` trong
JSON Schema — agent loop điền nó từ phạm vi hội thoại, và ghi đè nếu model cố tự điền.

Phạm vi tra cứu là thứ quyết định một Evidence ID có nghĩa hay không (`short_id` chỉ duy nhất
trong một run). Để model chọn phạm vi nghĩa là để nó tự chọn nơi trích dẫn của mình được coi là
hợp lệ.

### 5. Prompt phải nói thẳng về cái bẫy `CONTRADICTS`

ADR 0009 phát hiện 3 đo được trên vulnshop: **`CONTRADICTS` nhiều gấp hơn 5 lần `SUPPORTS`**
(43 so với 8), vì mỗi test xanh trong lớp test khớp đều sinh một liên kết. Đó là dữ liệu đúng,
nhưng một LLM đọc 43 dòng "test xanh phủ dòng này" rất dễ nghiêng về `LIKELY_FALSE_POSITIVE` —
tức là rơi đúng cái bẫy trung tâm mà case study dựng lên.

Ba biện pháp, cả ba đều cần:

- **Mô tả tool** của `get_related_tests` viết thẳng: `CONTRADICTS` không chứng minh cảnh báo sai.
- **System prompt** dành hẳn một mục: số lượng liên kết không có ý nghĩa (mười test xanh cùng đi
  qua một dòng vẫn là *một* loại bằng chứng yếu); muốn kết luận `LIKELY_FALSE_POSITIVE` thì cần
  một lý do **trong mã**, và phải đọc mã bằng `get_code_context` rồi nêu ra.
- **`rationale` trong chính dữ liệu** đã mang sẵn câu cảnh báo từ tuần 10-11.

### 6. Hậu kiểm hạ cấp, không xoá — và có bốn kết cục, không phải hai

`CitationValidator` ở core-backend, ngoài process của agent. Đó không chỉ là quy ước: agent-service
không có kết nối DB nên nó **không thể** tự tuyên bố một trích dẫn là hợp lệ.

| Kết cục | Nghĩa là gì về mô hình |
|---|---|
| `RESOLVED` | Trích dẫn đúng |
| `WRONG_RUN` | Lỗi **ngữ cảnh** — nhớ ID từ lượt trước |
| `UNKNOWN_ID` | **Bịa** — đúng khuôn dạng nhưng không tồn tại ở đâu |
| `MALFORMED` | Viết mô tả thay vì ID |

Gộp `WRONG_RUN` vào `UNKNOWN_ID` sẽ giấu mất một loại lỗi hoàn toàn khác, và báo cáo tuần 15 sẽ
gọi tất cả là "hallucination".

Nhận định không có trích dẫn `RESOLVED` nào bị **hạ verdict**, giữ nguyên `downgraded_from_verdict`.
**Không xoá nhận định nào** — chúng là mẫu số của Unsupported Claim Rate; xoá đi thì chỉ số này
luôn đẹp và luôn vô nghĩa.

Cả câu trả lời chỉ trụ được khi có **ít nhất một** nhận định đứng vững. Không có điều kiện này thì
một câu trả lời gồm toàn nhận định vô căn cứ vẫn ra `LIKELY_FALSE_POSITIVE`.

### 7. Chuẩn hoá đúng hai thứ: khoảng trắng và chữ hoa

`ShortIdFormat.normalize` chỉ làm hai việc đó. Gỡ thêm ngoặc vuông, backtick hay dấu chấm câu
quanh ID nghe rất tử tế — và sẽ biến Evidence Citation Accuracy thành phép đo **lòng khoan dung
của chính hàm đó** thay vì phép đo mức bám bằng chứng của mô hình. Ngược lại, hạ cấp một trích
dẫn chỉ vì mô hình không bấm phím Shift cũng là đo nhầm thứ.

`claim_citation.cited_short_id_raw` giữ **nguyên văn** chuỗi LLM viết, kể cả khi vô nghĩa.

### 8. `llm_trace` khoá theo (hội thoại, message, vòng lặp) và ghi cả lượt hỏng

Một lượt hỏi đáp gọi model nhiều lần, nên khoá không thể là message. `message_id` bằng `NULL`
nghĩa là agent chết trước khi kịp sinh câu trả lời — chính những dòng đó cho biết nó chết ở vòng
thứ mấy, thứ không tái tạo được sau.

agent-service không ghi bảng này (không có DB). Nó trả số đo trong payload, core-backend ghi
**cùng transaction** với message — nên không tồn tại trạng thái "đã trả lời nhưng chưa có trace".

## Ba chỗ dữ liệu và API thật buộc đi khác dự tính

1. **`temperature` không dùng được nữa.** `.env.example` đặt `LLM_TEMPERATURE=0.0` từ tuần 1 với
   lý do "để kết quả thực nghiệm bớt dao động". Các model từ Claude Opus 4.7 trở đi — gồm cả
   `claude-sonnet-5` đang khai — **trả lời 400** cho tham số này. Tức là ngay khi bật provider
   thật, không request nào chạy. Mặc định đổi thành "không gửi", và `AnthropicProvider` nổ ngay
   lúc khởi động nếu thấy biến này. Độ ổn định vẫn báo cáo được: kế hoạch §6 vốn đã chạy 3
   lần/warning và đo phương sai.

2. **Khung SSE cuối thường không có dòng trống theo sau — mà nó chính là `answer`.** Cả ba tầng
   (agent-service phát, core-backend đọc, trình duyệt đọc) đều phải xử lý ca này. Đánh rơi nó
   không có thông báo lỗi nào: người dùng thấy tiến trình chạy xong rồi im lặng.

3. **Trình duyệt không dùng được `EventSource`.** API sẵn có của trình duyệt cho SSE **không gửi
   được header tuỳ ý**, nên không gắn được `Authorization: Bearer`; cách duy nhất là nhét token
   vào query string, và query string nằm trong log của mọi reverse proxy trên đường đi. Nó cũng
   chỉ làm được `GET` trong khi hỏi một câu là `POST`. Phải tự đọc `ReadableStream` bằng `fetch`.

## Hệ quả

- Bốn bất biến của một lượt trả lời được ép ở **tầng DB** chứ không chỉ trong Java (V8):
  lượt của agent luôn có verdict, hạ cấp chỉ đi một chiều, claim không mồ côi, ordinal không trùng.
- `contradicting_short_ids` nằm trên `message` dưới dạng JSONB và **không** đi qua
  `claim_citation`: nó không phải nhận định, đưa vào sẽ làm mẫu số của Unsupported Claim Rate lẫn
  lộn hai thứ khác nhau. Đánh đổi: những ID này không có kết quả hậu kiểm, giao diện hiện chúng
  thành liên kết thẳng và để Evidence Explorer báo 404 nếu sai.
- `LLM_PROVIDER=scripted` chạy được toàn bộ đường đi end-to-end mà không cần khoá API — dùng cho
  nghiệm thu và cho CI.
- Tuần 14 (Human Review) đã có sẵn chỗ neo: `message.verdict` là thứ người dùng chấp nhận hay bác
  bỏ, và `claim.downgraded_from_verdict` cho biết hệ thống đã tự sửa mô hình bao nhiêu lần trước
  khi tới tay người.
