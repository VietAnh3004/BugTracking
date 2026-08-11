# ADR 0011 — Human Review gắn vào cảnh báo, và `message_id` là biến thực nghiệm

- **Trạng thái**: Chấp nhận
- **Ngày**: 10/08/2026
- **Liên quan**: [ADR 0010](0010-vong-lap-agent-va-hau-kiem-trich-dan.md) (`message.verdict` là thứ
  người dùng chấp nhận hay bác bỏ), [ADR 0004](0004-unified-evidence-model-hai-tang.md)
  (`evidence_record` là thứ được neo vào)

## Bối cảnh

Hết tuần 12-13, vòng lặp **máy** đã khép: cảnh báo → bằng chứng → Agent trả lời → hậu kiểm trích
dẫn → người dùng đọc. Nhưng hệ thống chưa ghi lại được **người dùng nghĩ gì** sau khi đọc.

Đó không phải một tính năng cho đẹp. Ba chỉ số trung tâm của kế hoạch §6 đều cần dữ liệu này:

| Chỉ số | Cần gì |
|---|---|
| Human acceptance rate | Quyết định của người, so với `message.verdict` của Agent |
| So sánh nhóm dùng Agent ↔ nhóm đối chứng | Cùng một bộ cảnh báo, hai cách làm việc |
| Đối chiếu với ground truth (tuần 15) | Quyết định của người **và** verdict của Agent, cùng một khoá |

Bảng `human_review` được nhắc tới từ tuần 3 (kế hoạch §2, §6) nhưng chưa bao giờ tồn tại;
`HumanReviewService` có trong sơ đồ kiến trúc §1 và chưa có dòng mã nào.

## Quyết định

### 1. `human_review` neo vào CẢNH BÁO, không neo vào câu trả lời của Agent

Đây là quyết định định hình cả tuần 14 và tuần 16, nên nó đứng đầu.

Cách hiển nhiên hơn là neo review vào `message`: người dùng đọc một câu trả lời rồi nói "đồng ý"
hay "không đồng ý". Nó **hỏng ở đúng chỗ đồ án này cần đo**:

- **Nhóm đối chứng không có chỗ ghi.** User study (§6) cho một nửa số người làm việc **không dùng
  Agent**. Những người đó không sinh ra `message` nào — neo vào `message` nghĩa là một nửa dữ liệu
  thực nghiệm không tồn tại.
- **Một cảnh báo có thể được hỏi nhiều lần.** Ba câu trả lời cho cùng một cảnh báo sẽ thành ba
  hàng review, và "Human acceptance rate" đếm trùng theo mức độ tò mò của người dùng.
- **Không so được với ground truth.** Nhãn chuẩn của tuần 15 gán cho **cảnh báo**. Hai bảng neo
  vào hai thứ khác nhau thì mọi so sánh phải đi vòng, và vòng đó không phải lúc nào cũng có.

Nên:

```
human_review ── evidence_record_id ──> evidence_record   (cảnh báo)
             ── reviewer_id        ──> app_user
             ── message_id (NULL)  ──> message           (tuỳ chọn, xem quyết định 2)
UNIQUE (evidence_record_id, reviewer_id)
```

`UNIQUE (evidence_record_id, reviewer_id)` — một người, một cảnh báo, một quyết định. Đổi ý thì
UPDATE. Không có ràng buộc này thì một cú F5 kèm bấm lại là đủ để đếm trùng, và không ai thấy.

`analysis_run_id` phi chuẩn hoá có chủ đích, cùng lý do với `evidence_link.analysis_run_id` ở V7:
mọi truy vấn thống kê đều lọc theo run, và nó biến "một review không vắt qua hai run" thành thứ
kiểm được ở tầng DB.

### 2. `message_id` là BIẾN THỰC NGHIỆM, không phải metadata — và backend không tin frontend

`human_review.message_id` trỏ tới câu trả lời của Agent mà người dùng đã đọc **trước khi** quyết.
`NULL` nghĩa là không dùng Agent.

Một cột nullable trông như metadata trang trí. Thực tế nó là **biến độc lập của toàn bộ user
study**: chia dữ liệu thành nhóm can thiệp và nhóm đối chứng. Sai một hàng là nhiễm một điểm dữ
liệu, và nhiễm theo hướng không phát hiện được về sau.

Vì vậy `HumanReviewService.validateMessage` kiểm **ba** điều, sai một là 400 chứ không lặng lẽ ghi
NULL:

1. message tồn tại;
2. nó thuộc một `conversation` của **chính người đang review** (không phải của người khác);
3. conversation đó thuộc **đúng run** đang xét.

Điều kiện 2 và 3 nghe thừa vì frontend chỉ gửi id nó vừa nhận. Chúng không thừa: frontend là thứ
người tham gia user study chạy trên máy của họ, và một `messageId` bịa ra hoặc chép nhầm từ tab
khác sẽ đi thẳng vào cột quyết định nhóm.

Ở tầng giao diện, hệ quả tương ứng: `ReviewPanel` **bỏ hẳn khoá** `messageId` khỏi JSON khi không
có, thay vì gửi `null`. "Không có" và "có, giá trị rỗng" là hai chuyện khác nhau khi thứ đang gửi
là biến thực nghiệm.

### 3. Bốn lựa chọn, và `UNCERTAIN` ≠ `INSUFFICIENT_EVIDENCE`

`ACCEPT` / `REJECT` / `UNCERTAIN` / `INSUFFICIENT_EVIDENCE`.

Hai giá trị cuối cùng nghe như một. Chúng cố ý tách:

- `UNCERTAIN` — *"tôi đã xem đủ bằng chứng và vẫn không quyết được"*. Tín hiệu về **độ khó của
  cảnh báo**.
- `INSUFFICIENT_EVIDENCE` — *"hệ thống chưa cho tôi đủ bằng chứng để xem"*. Tín hiệu về **thiếu
  sót của pipeline bằng chứng**.

Gộp lại thì mất đúng thông tin cho biết nên cải thiện Agent hay cải thiện pipeline — và đó là một
trong những câu hỏi mà đồ án này đặt ra. Trên giao diện chúng có màu và nhãn khác nhau, không chỉ
khác giá trị trong DB.

Cả bốn cùng đối xứng với ba verdict của Agent ở chỗ **không có lựa chọn "bỏ qua"**: một cảnh báo
đã mở ra mà không quyết được vẫn phải để lại dấu vết, nếu không tiến độ review đọc thành "chưa
xem" trong khi thực tế là "xem rồi mà chịu".

### 4. `UNCERTAIN` không tính là khớp, nhưng **ở lại trong mẫu số**

Khi tính mức khớp giữa người và Agent, `UNCERTAIN` luôn đếm là **không khớp**.

Cách khác — loại nó khỏi cả tử số lẫn mẫu số — làm chỉ số đẹp lên theo đúng mức độ khó của bộ dữ
liệu: bộ cảnh báo càng khó, càng nhiều `UNCERTAIN` bị loại, tỉ lệ khớp càng cao. Đó là một phép đo
tự khen. Giữ nó trong mẫu số nghĩa là "Agent thuyết phục được người dùng" mới tính là khớp, và đó
đúng là thứ cần đo.

Quyết định này ghi thẳng vào comment của `RunMetricsService` vì nó không suy ra được từ SQL.

### 5. Tỉ lệ có mẫu số 0 trả `null`, không trả `0`

`RunMetricsResponse` dùng `Double` bọc chứ không `double` nguyên thuỷ, và có đúng một hàm chia:

```java
public static Double ratio(long numerator, long denominator) {
    return denominator == 0 ? null : (double) numerator / denominator;
}
```

"Chưa ai review" và "review hết mà 0% khớp" là hai chuyện khác hẳn. Cùng một triết lý với "rỗng là
một kết luận" của tuần 10-11 và với 409-thay-vì-danh-sách-rỗng của màn hình cảnh báo.

Giao diện phải giữ được sự phân biệt đó, và ở đây có **một cạm bẫy thật đã gặp**: backend từng gửi
`null` thật, nhưng `openapi-typescript` sinh ra kiểu `unsupportedClaimRate?: number` — tức
`number | undefined`, **không có** `null`. Viết `value === undefined ? '—' : ...` thì TypeScript
xanh, còn `null * 100` trong JavaScript bằng `0`, nên màn hình hiện **`0.0%`**. Người chấm đọc
"0% khớp với Agent" trong khi sự thật là "chưa ai review".

Bản vá đầu chỉ chữa triệu chứng (`formatMetric.ts` kiểm `== null`). Cùng cái gốc đó sau đó làm
**trắng nguyên trang** Evidence Explorer — `testCase: null` lọt qua `=== undefined` rồi
`null.className`. Nên nay chữa ở gốc: `spring.jackson.default-property-inclusion: non_null`, để
JSON đi ra **khớp với hợp đồng OpenAPI** thay vì bắt từng component tự nhớ kiểm cả hai dạng. Các
chỗ **dereference** giá trị vẫn giữ `== null` làm lớp phòng thủ thứ hai, vì hậu quả ở đó là trắng
trang chứ không phải thiếu một dòng.

### 6. `RunMetricsService` là nguồn tính toán DUY NHẤT; export gọi lại nó

`RunExportService.toJson` không tự tính chỉ số nào — nó gọi `RunMetricsService`. Hai chỗ cùng tính
một chỉ số sớm muộn cũng lệch, và lúc đó không ai biết con số nào trong báo cáo là đúng.

### 7. Export cả CSV lẫn JSON, và CSV phải có BOM UTF-8

Hai định dạng phục vụ hai việc không thay thế nhau:

- **CSV** — một dòng một cảnh báo, nạp thẳng vào công cụ thống kê ở tuần 17-18.
- **JSON** — đủ claim/citation/trace để truy một con số ngược về đúng bằng chứng sinh ra nó.

CSV **bắt buộc có BOM UTF-8**: `note` là tiếng Việt do người dùng gõ, và Excel trên Windows không
có BOM thì mở ra là chữ rác. Người chấm sẽ mở bằng Excel.

CSV cũng không được ghép chuỗi bằng tay — `note` có thể chứa dấu phẩy, nháy kép và xuống dòng. Dự
án thêm `commons-csv` cho đúng việc này, và test đọc lại bằng chính thư viện đó thứ mình vừa ghi.

Truy vấn export dùng LATERAL join để nhiều người review không nhân bản hàng cảnh báo.

### 8. `seconds_spent` được ghi, nhưng KHÔNG phải "Time to understand"

Client đo và gửi lên, nên nó không đáng tin: người dùng đổi tab, để máy chạy, hoặc mở panel rồi đi
pha cà phê. Vẫn ghi vì user study cần một con số tham khảo, nhưng **"Time to understand" của kế
hoạch §6 đo trong phiên có người quan sát**, không lấy từ cột này. Javadoc, JSDoc và tài liệu user
study đều nói rõ điều đó — một con số có tên nghe uy tín mà không ai ghi chú xuất xứ sẽ đi thẳng
vào báo cáo.

DB ép `CHECK (seconds_spent IS NULL OR seconds_spent > 0)`: 0 giây là dữ liệu rác, không phải "rất
nhanh". Hệ quả ở giao diện: bấm ngay khi vừa mở panel thì **không gửi trường này** thay vì gửi 0 —
nếu không, thao tác thường gặp nhất sẽ ăn 400.

### 9. `ground_truth_label` dựng ở V9 dù tuần 15 mới nạp dữ liệu

Khoá chính là `(evidence_record_id, source)` chứ không phải riêng `evidence_record_id`: tuần 15
cần **hai** người gán nhãn độc lập để tính Cohen's kappa, nên `BLIND_SECOND_RATER` phải là một
hàng riêng chứ không phải một cột thêm vào sau.

`rationale NOT NULL` (và `CHECK (btrim(rationale) <> '')`) chặn đúng cái bẫy circularity mà kế
hoạch §5 cảnh báo: nhãn chuẩn phải dựa trên **đọc mã**, và bắt viết lý do khiến việc gán nhãn theo
output của Agent lộ ra ngay lúc gán.

Nhãn `BLIND_SECOND_RATER` **không** tính vào độ chính xác — nó tồn tại để đo mức đồng thuận giữa
hai người gán nhãn, dùng nó làm chuẩn nữa là đếm hai lần cùng một ý kiến.

Đây là một bảng trống có chủ đích cho tới tuần 15. Dashboard hiện rõ "chưa có dữ liệu" thay vì một
ô `0%`, và §K của kế hoạch ghi nó là món nợ đã biết — một bảng chết trông y hệt một bảng đang dùng.

## Hệ quả

- Luồng end-to-end khép kín: upload → phân tích → bằng chứng → hỏi Agent → hậu kiểm trích dẫn →
  **người quyết định** → chỉ số → báo cáo tải về được.
- Tuần 15-16 có đủ chỗ ghi dữ liệu: `human_review.message_id` chia nhóm, `ground_truth_label` chờ
  nhãn, `RunMetricsService` đã có sẵn phép so sánh.
- Đánh đổi đã chấp nhận: review neo vào cảnh báo nên **không** ghi được "người dùng nghĩ gì về
  từng câu trả lời cụ thể" (ví dụ đánh giá chất lượng một lời giải thích). Nếu cần, đó là một bảng
  khác (`message_feedback`), không phải thêm cột vào `human_review`.
- Người dùng ẩn danh không review được: `reviewer_id` là FK tới `app_user`. Với user study 5-8
  người thì mỗi người dùng một tài khoản là chấp nhận được, và nó giữ được `UNIQUE` cùng khả năng
  tách nhóm.
