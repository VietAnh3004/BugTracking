# User study — thiết kế và tài liệu chuẩn bị

Kế hoạch §6 yêu cầu **chuẩn bị form + kịch bản từ tuần 14, không để đến tuần 16**. Thư mục này là
kết quả của yêu cầu đó. Buổi thử thật diễn ra ở tuần 15-16 (02/11–15/11/2026).

| File | Dùng khi nào |
|---|---|
| [phieu-dong-y.md](phieu-dong-y.md) | Đầu buổi, trước khi làm bất cứ việc gì |
| [kich-ban-buoi-thu.md](kich-ban-buoi-thu.md) | Người điều phối đọc theo, từng bước |
| [bo-canh-bao.md](bo-canh-bao.md) | Bộ 8 cảnh báo cố định + cách chia nhóm |
| [phieu-danh-gia.md](phieu-danh-gia.md) | Cuối buổi — Likert 1–5 và câu hỏi mở |

## Câu hỏi nghiên cứu mà buổi thử này trả lời

**RQ3**: bằng chứng có cấu trúc kèm trích dẫn kiểm chứng được có giúp lập trình viên phán đoán
cảnh báo phân tích tĩnh **nhanh hơn và đúng hơn** so với đọc output thô của công cụ không?

Với 5–8 người, đây là **nghiên cứu định tính có số liệu mô tả**, không phải kiểm định thống kê.
Báo cáo phải nói thẳng điều đó thay vì gắn p-value vào một mẫu 6 người.

## Thiết kế: trong-đối-tượng (within-subject), có cân bằng thứ tự

Mỗi người xử lý **cả 8 cảnh báo**: 4 cảnh báo với Evidence Agent, 4 cảnh báo chỉ với output thô
của công cụ.

Vì sao không chia người thành hai nhóm (between-subject):

- **Cỡ mẫu quá nhỏ.** 6 người chia đôi còn 3 người mỗi nhánh. Chênh lệch trình độ giữa ba người
  bất kỳ lớn hơn nhiều so với hiệu ứng đang muốn đo, nên kết quả sẽ nói về **người tham gia** chứ
  không nói về **công cụ**.
- **Trong-đối-tượng cho phép mỗi người tự làm đối chứng cho chính mình.** So sánh trong cùng một
  người loại được biến nhiễu lớn nhất.

Đánh đổi phải khai báo trong báo cáo: **hiệu ứng lan (carry-over)**. Người dùng học được cách suy
nghĩ từ Agent ở block đầu rồi áp dụng cho block sau, khiến chênh lệch bị **thu hẹp** — tức là
thiên lệch theo hướng *bất lợi* cho giả thuyết, không phải hướng có lợi. Đó là lý do chấp nhận
được. Cân bằng thứ tự (nửa số người bắt đầu bằng block có Agent, nửa còn lại ngược lại) để hiệu
ứng này không dồn về một phía.

## Dữ liệu thu được và chỗ nó nằm

| Thứ đo | Nguồn | Ghi chú |
|---|---|---|
| Quyết định (Accept/Reject/Uncertain/Insufficient) | `human_review.decision` | |
| Có dùng Agent hay không | `human_review.message_id` (NULL = đối chứng) | Backend kiểm ba điều kiện, xem [ADR 0011](../adr/0011-human-review-gan-vao-canh-bao.md) |
| Lý do | `human_review.note` | Bắt buộc với `REJECT` và `UNCERTAIN` (quy ước của buổi thử, không phải ràng buộc DB) |
| **Time to understand** | **Người điều phối bấm giờ**, ghi tay vào biên bản | **KHÔNG** lấy từ `human_review.seconds_spent` |
| Response usefulness | [phieu-danh-gia.md](phieu-danh-gia.md), Likert 1–5 | |

> `human_review.seconds_spent` do trình duyệt tự đo nên không đáng tin: người tham gia đổi tab, đọc
> lại, hoặc dừng để hỏi. Nó được ghi lại để đối chiếu, nhưng **"Time to understand" của kế hoạch
> §6 là số bấm giờ thủ công**. Trộn hai nguồn này vào một cột trong báo cáo là sai số liệu.

## Ngưỡng dừng và phương án rút gọn

Kế hoạch §7 đã ghi user study là mục **thứ ba** trong danh sách cắt nếu trượt tiến độ: giảm còn
3–4 người và báo cáo hoàn toàn định tính. Nếu đến 08/11/2026 mà chưa mời đủ 5 người, chuyển sang
phương án đó ngay thay vì kéo dài — dữ liệu của tuần 17-18 cần thời gian phân tích.
