# Thực nghiệm 3 cấu hình: LLM-Code / LLM-Static / Evidence-Agent

Giao thức của phép so sánh trung tâm trong đồ án (kế hoạch §6): **chuỗi bằng chứng có giúp LLM đánh
giá cảnh báo phân tích tĩnh đúng hơn không?** Dựng ngày 27/11/2026. Chương 4 trích số liệu từ đây.

## 1. Ba cấu hình

Cùng model, cùng cảnh báo, cùng lược đồ trả lời (`AnalysisResponse`: verdict + nhận định + Evidence
ID); chỉ khác **lượng thông tin model được thấy**.

| Cấu hình | Model thấy gì | Tool | Lượt gọi model / ô |
|---|---|---|---|
| **LLM-Code** | Đoạn mã quanh dòng bị cảnh báo (bản ghi CODE_CONTEXT, đánh số dòng, dòng đó đánh dấu `>>`) và vị trí `file:dòng`. **Không** biết có cảnh báo nào | Không | 1 |
| **LLM-Static** | Như LLM-Code, cộng nội dung cảnh báo: công cụ, quy tắc, mức nghiêm trọng, thông điệp | Không | 1 |
| **Evidence-Agent** | Agent thật của hệ thống: cùng system prompt, cùng 5 tool, tự truy xuất test, độ phủ, stack trace, ngữ cảnh mã, tìm theo nghĩa | 5 tool | ~5–9 |

- Hai cấu hình đối chứng do agent-service tự lấy trước bằng chứng (`get_warning`, `get_code_context`)
  rồi nộp đúng **một** lượt gọi model. Lần lấy trước không tính là tool call của model.
- Câu hỏi: `experiment_question.jinja2` ("Cảnh báo này là lỗi thật hay báo nhầm?…") cho Evidence-Agent
  và LLM-Static. LLM-Code không biết có cảnh báo, nên câu hỏi là "Dòng N có lỗi thật không?".
- Prompt: [prompt-versions.md](prompt-versions.md), mục "Prompt của thực nghiệm 3 cấu hình".

## 2. Thiết lập

| Tham số | Giá trị | Ghi chú |
|---|---|---|
| Tập cảnh báo | 34 cảnh báo của run vulnshop `ab3e8b0b` | |
| Model | Gemini free (`LLM_MODEL` trong `infra/.env`, hiện `gemini-3.5-flash-lite`), tier `free` | Người dùng chốt 26/11/2026. Model ghi vào từng ô (cột `model`) |
| Số lần lặp | 3 (`app.experiment.repeats`) | Báo được độ dao động giữa các lần chạy |
| Prompt | `PROMPT_VERSION=v2` | Ghi vào từng ô (cột `prompt_version`) |
| Embedding | Bật (`EMBEDDING_PROVIDER=gemini`, 124/124 vector) | Evidence-Agent có tìm theo nghĩa |
| Tổng số ô | 34 × 3 × 3 = **306** | |

## 3. Chạy

Trang **Dashboard** của run → thẻ **Thực nghiệm 3 cấu hình** (chỉ chủ dự án):

- **Chạy / Chạy tiếp**: đi tuần tự theo thứ tự lần lặp → cảnh báo → cấu hình, nên dừng ở đâu thì số ô
  của ba cấu hình vẫn cân nhau. Ô đã thành công bị bỏ qua; ô hỏng được chạy lại.
- **Tự dừng** sau 3 ô hỏng liên tiếp (`app.experiment.max-consecutive-failures`). Trên Gemini free đó
  gần như luôn là hết hạn mức trong ngày: hôm sau bấm Chạy tiếp.
- **Dừng**: dừng sau ô đang chạy, ô đó không được ghi.
- **Tải CSV**: mỗi ô một dòng.
- Khởi động lại core-backend thì lượt chạy dừng (trạng thái chạy chỉ nằm trong bộ nhớ). Dữ liệu đã ghi
  còn nguyên.

Mỗi ô được ghi vào bảng `experiment_result` (V16) ngay khi xong, gồm câu trả lời đã hậu kiểm và nhật ký
từng vòng gọi model. Bảng này **tách khỏi** `conversation`/`message`: không trộn với câu trả lời Agent
của user study, nên không ảnh hưởng các chỉ số khác trên Dashboard.

## 4. Chấm và chỉ số

Mọi câu trả lời, của cả ba cấu hình, đi qua đúng `CitationValidator` như Agent thật: nhận định không có
Evidence ID tra được bị hạ verdict xuống `INSUFFICIENT_EVIDENCE` (không xoá).

Đáp án là nhãn ground truth **MANUAL**. Ô mà cảnh báo chưa có nhãn TP/FP (chưa gán, hoặc `UNCERTAIN`)
không vào mẫu số độ chính xác. Mọi tỉ lệ có mẫu số 0 hiện "—", không phải 0%.

| Chỉ số | Định nghĩa |
|---|---|
| Độ chính xác | Số ô verdict khớp nhãn / số ô có nhãn, gộp mọi lần lặp. `LIKELY_TRUE_POSITIVE`↔TP, `LIKELY_FALSE_POSITIVE`↔FP; `INSUFFICIENT_EVIDENCE` tính là **sai** (giống `agentAccuracy` của Dashboard) |
| Theo từng lần lặp | Độ chính xác của lần 1 · 2 · 3 |
| Độ lệch giữa các lần | Độ lệch chuẩn mẫu (n − 1) của độ chính xác qua các lần lặp có dữ liệu; cần ≥ 2 lần |
| Chính xác khi có kết luận | Như độ chính xác nhưng bỏ các ô `INSUFFICIENT_EVIDENCE` |
| Tỉ lệ "Thiếu bằng chứng" | Số ô `INSUFFICIENT_EVIDENCE` / số ô thành công |
| Unsupported Claim Rate | Số nhận định không có trích dẫn RESOLVED / tổng nhận định |
| Trích dẫn hợp lệ | Số trích dẫn RESOLVED / tổng trích dẫn |
| Độ ổn định | Tỉ lệ cảnh báo cho cùng verdict ở cả 3 lần lặp, chỉ tính cảnh báo đã đủ 3 ô thành công |
| Chi phí | Trung bình lượt gọi model, tool call, token vào/ra, độ trễ (kể cả thời gian chờ thử lại) mỗi ô |

CSV có thêm cột `correct` (rỗng khi không chấm được) để tự tính lại hoặc tách nhóm ngoài hệ thống, ví
dụ tách **cảnh báo gieo** và **cảnh báo tự nhiên** bằng cách đối chiếu `file_path` + `start_line` với
`case-study/ground-truth/vulnshop-seeded.yaml`.

## 5. Các yếu tố ảnh hưởng tính hợp lệ (nêu trong chương 4)

- **Không lộ đáp án qua mã:** đã kiểm 30/30 bản ghi CODE_CONTEXT của run `ab3e8b0b` không chứa chú
  thích "SEEDED…", "false positive", "lỗi thật"… (snippet chỉ là thân hàm, không kèm Javadoc). Tên gói
  `vn.hust.datn.casestudy.vulnshop` vẫn xuất hiện, như nhau ở cả ba cấu hình.
- **Trích dẫn của hai cấu hình đối chứng dễ hợp lệ hơn:** Evidence ID nằm sẵn trong đề bài, nên tỉ lệ
  trích dẫn hợp lệ của chúng không so trực tiếp được với Evidence-Agent (phải tự tìm ID qua tool).
- **`CitationValidator` chỉ kiểm ID có tồn tại**, không kiểm nội dung bằng chứng có ủng hộ nhận định
  hay không.
- **Một model nhỏ, miễn phí.** Kết luận về chênh lệch giữa ba cấu hình chỉ đúng cho model này.
- **Nhãn FP-05/FP-06 được gán lại thành TP** sau khi đã thấy câu trả lời của Agent (xem
  `case-study/vulnshop/SEEDED.md` mục 6).
- **34 câu trả lời Agent cũ trên Dashboard** (user study thử) chạy khi embedding còn tắt. Chúng không
  thuộc thực nghiệm này.
- Đổi prompt, model hay embedding giữa chừng là trộn hai điều kiện vào một bảng: phải xoá các ô đã chạy
  (`DELETE FROM experiment_result WHERE analysis_run_id = …`) rồi chạy lại từ đầu.

## 6. Kết quả (chạy xong 27/11/2026)

Đủ **306/306 ô**, 0 ô hỏng. Mọi ô cùng `gemini-3.5-flash-lite`, prompt `v2`. 34 cảnh báo đều có nhãn
MANUAL: 19 lỗi thật (TP), 15 báo nhầm (FP). Người dùng chốt (27/11/2026): **giữ nguyên kết quả này
để báo cáo**, không chỉnh prompt rồi chạy lại trên chính tập đo (tránh "dạy thuộc đề").

### 6.1. Bảng tổng hợp

| Chỉ số | LLM-Code | LLM-Static | Evidence-Agent |
|---|---|---|---|
| Độ chính xác (102 ô) | 73,5% | **85,3%** | 84,3% |
| Theo lần lặp 1 · 2 · 3 | 73,5 · 73,5 · 73,5% | 85,3 · 85,3 · 85,3% | 82,4 · 85,3 · 85,3% |
| Độ lệch chuẩn giữa các lần | 0 | 0 | 1,7 điểm |
| Đa số 3 lần, theo cảnh báo (34) | 25/34 = 73,5% | 29/34 = 85,3% | 29/34 = 85,3% |
| Recall lớp TP (bắt được lỗi thật) | 78,9% | 91,2% | **93,0%** |
| Nhận đúng báo nhầm (specificity) | 66,7% | **77,8%** | 73,3% |
| Tỉ lệ "Thiếu bằng chứng" | 8,8% | 0% | 0% |
| Độ ổn định (cùng verdict cả 3 lần) | 100% | 94,1% | 91,2% |
| Unsupported Claim Rate | 0% | 0% | 0% |
| Trích dẫn hợp lệ | 100% | 100% | 100% |
| Lượt gọi model / ô | 1,0 | 1,0 | 4,4 |
| Tool call / ô | 0 | 0 | 3,9 |
| Token vào / ra mỗi ô | 656 / 233 | 921 / 282 | 14 652 / 801 |
| Độ trễ / ô | 2,7 giây | 4,9 giây | 14,9 giây |

### 6.2. Kiểm định McNemar (chính xác, hai phía)

| Cặp | Theo ô (102 cặp, coi 3 lần lặp là độc lập) | Theo cảnh báo (34 cặp, đa số 3 lần) |
|---|---|---|
| LLM-Code vs LLM-Static | chỉ Code đúng 6, chỉ Static đúng 18, **p = 0,023** | 2 vs 6, p = 0,29 |
| LLM-Static vs Evidence-Agent | 5 vs 4, **p = 1,0** | 2 vs 2, p = 1,0 |
| LLM-Code vs Evidence-Agent | 7 vs 18, **p = 0,043** | 2 vs 6, p = 0,29 |

Ba lần lặp trên CÙNG một cảnh báo không độc lập (cùng đề bài), nên cột "theo ô" đánh giá cao độ tin cậy;
cột "theo cảnh báo" mới là đơn vị độc lập, và với 34 cảnh báo thì không cặp nào khác biệt có ý nghĩa ở
mức 0,05. Đọc đúng: **biết nội dung cảnh báo có xu hướng giúp (+12 điểm), còn chuỗi bằng chứng không
làm tăng độ chính xác tổng thể so với LLM-Static trên tập này.**

### 6.3. Phân tích lỗi theo cảnh báo

Chỉ liệt kê cảnh báo có ít nhất một ô sai; số là số lần đúng trên 3.

| Cảnh báo | Nhãn | Code | Static | Agent | Nhận xét |
|---|---|---|---|---|---|
| EV-0058 `WEAK_MESSAGE_DIGEST_MD5` (MD5 làm checksum, FP-02) | FP | 3 | 3 | **0** | Agent đọc đúng thân hàm (`get_code_context`), thấy tên `checksumForDeduplication`, vẫn kết luận "MD5 yếu ⇒ lỗi thật" |
| EV-0186 `RCN_REDUNDANT_NULLCHECK…` (FP-04) | FP | 3 | 3 | 1 | Agent viết "cảnh báo chính xác: kiểm null là thừa" rồi chọn TP |
| EV-0107 `NP_NULL_ON_SOME_PATH…` | FP | 3 | **0** | 2 | Cần thông tin ngoài dòng; Agent khá hơn Static |
| EV-01D7 `DM_CONVERT_CASE` (FP-05 → TP) | TP | 3 | 1 | 2 | Như trên |
| EV-01B0 `PREDICTABLE_RANDOM` | FP | 3 | 2 | 3 | |
| EV-0019, EV-0094, EV-00AA `SPRING_ENDPOINT` | FP | 0 | 3 | 3 | LLM-Code không biết có cảnh báo, chỉ thấy một endpoint bình thường: trả `INSUFFICIENT_EVIDENCE` cả 9/9 ô (toàn bộ 9 ô "thiếu bằng chứng" của LLM-Code nằm ở đây) |
| EV-00E8, EV-0190, EV-01C0 | TP | 0 | 3 | 3 | Lỗi chỉ lộ ra khi biết quy tắc (equals/hashCode, CRLF log, abs(hashCode)) |
| EV-0022 `SPRING_ENDPOINT` (`loginRedirect`) | FP | 0 | 0 | 0 | Nhãn FP theo quy tắc A (cảnh báo chỉ nói "có endpoint"); cả ba thấy open redirect thật nên nói TP |
| EV-00CA `SQL_INJECTION_SPRING_JDBC` (FP-01) | FP | 0 | 0 | 0 | Lý do báo nhầm nằm ngoài dòng (enum `SortField`) — hạn chế đã biết (`viec-con-mo.md` mục 1) |
| EV-01A0 `CRLF_INJECTION_LOGS` (FP-06 → TP) | TP | 0 | 0 | 0 | Cả ba nói FP. Nhãn được gán lại SAU khi xem Agent — xem SEEDED.md mục 6 |

### 6.4. Đọc kết quả

1. **Nội dung cảnh báo là thông tin có giá trị nhất** (LLM-Code → LLM-Static: +11,8 điểm, ý nghĩa
   theo ô p = 0,023). Không biết có cảnh báo, model bỏ sót các lỗi chỉ lộ ra khi biết quy tắc và hay
   trả "thiếu bằng chứng".
2. **Evidence-Agent ngang LLM-Static về độ chính xác** (84,3% vs 85,3%, p = 1,0) với chi phí token gấp
   ~16 lần và độ trễ gấp ~3 lần. Agent bắt lỗi thật tốt hơn một chút (recall 93,0% vs 91,2%) nhưng
   nhận báo nhầm kém hơn (73,3% vs 77,8%).
3. **Kiểu lỗi riêng của Agent: nhầm "công cụ phát hiện đúng mẫu mã" với "lỗi thật"** (EV-0058,
   EV-0186): có đủ thông tin, nêu đúng sự thật trong nhận định, nhưng chọn sai verdict. Giả thuyết: system
   prompt dài nhấn mạnh "test xanh không chứng minh báo nhầm" kéo model về phía TP; ngữ cảnh đầy kết quả
   tool làm loãng ý chính với một model nhỏ. Chưa kiểm chứng.
4. **Bằng chứng giúp ở đúng nhóm ca cần thông tin ngoài dòng** (EV-0107, EV-01D7), khớp với thiết kế.
   vulnshop có hàm nhỏ, tự chứa: thân hàm thường đã đủ để phán đoán, và bằng chứng test phần lớn là
   liên kết `CONTRADICTS` (yếu theo chính thiết kế) — nên lợi thế của Agent trên tập này nhỏ.
5. **Unsupported Claim Rate = 0% ở cả ba cấu hình**: model luôn gắn ID có thật. Vì `CitationValidator`
   chỉ kiểm ID tồn tại (mục 5), con số này không nói được nhận định có đúng với bằng chứng hay không — cần
   chấm tay độ đúng của lời giải thích (kế hoạch §6) để bổ sung.
6. Ba ca cả ba cấu hình đều sai 9/9 đều liên quan tới **nhãn** hoặc **giới hạn đã biết**, không phải tới
   lượng bằng chứng: quy tắc gán nhãn `SPRING_ENDPOINT`, ngữ cảnh ngoài dòng, và nhãn gán lại.
