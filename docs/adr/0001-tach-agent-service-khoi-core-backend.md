# ADR 0001 — Tách AI Agent thành service riêng, không truy cập cơ sở dữ liệu

- **Trạng thái**: Chấp nhận
- **Ngày**: 26/07/2026

## Bối cảnh

Đề cương chỉ định backend là Java Spring Boot. Tuy nhiên phần AI Agent cần embedding, tool-use
và cấu trúc phản hồi bắt buộc — những thứ có hệ sinh thái chín hơn nhiều ở Python.

Câu hỏi thứ hai: agent nên truy vấn thẳng cơ sở dữ liệu để lấy bằng chứng, hay gọi qua một API?

## Quyết định

Hai service tách rời:

- **core-backend (Spring Boot)** — quản lý dự án, adapter phân tích tĩnh, thu thập bằng chứng
  kiểm thử, Evidence Store, hậu kiểm trích dẫn, human review.
- **agent-service (FastAPI)** — vòng lặp agent, năm evidence tool, retrieval, `LlmProvider`.

**Agent-service không có kết nối cơ sở dữ liệu.** Mọi bằng chứng lấy qua Evidence Tool API của
core-backend.

**Việc hậu kiểm trích dẫn nằm ở core-backend**, ngoài process của agent.

## Lý do

Cho agent truy vấn DB trực tiếp thì nhanh hơn một chút, nhưng năm tool trong thiết kế sẽ chỉ còn
là các hàm nội bộ. Đặt chúng sau một ranh giới HTTP đổi lấy ba thứ đáng giá hơn:

1. **Đo được.** Mỗi lượt truy xuất bằng chứng là một request có thể log kèm độ trễ, tham số và
   số kết quả. Đây chính là dữ liệu cần cho phần phân tích ở chương 4 — loại bằng chứng nào
   agent thực sự dùng, gọi bao nhiêu lần trước khi kết luận.
2. **Cấu hình thực nghiệm là thật.** LLM-Code và LLM-Static được cài đặt bằng cách không cấp
   tool nào cho agent, chứ không phải bằng cách sửa prompt. Khác biệt giữa ba cấu hình vì vậy
   nằm ở khả năng truy xuất chứ không ở cách diễn đạt câu lệnh.
3. **Hậu kiểm thực sự độc lập.** Đề cương yêu cầu bước kiểm tra trích dẫn "độc lập với LLM".
   Nếu nó chạy cùng process với agent thì đó chỉ là một lời khẳng định; đặt ở service khác thì
   đó là một sự thật kiểm chứng được — điều sẽ bị hỏi khi bảo vệ.

## Đánh đổi

- Thêm độ trễ mạng cho mỗi lượt gọi tool. Chấp nhận được: chi phí đó nhỏ so với thời gian chờ
  LLM sinh token.
- Hai service phải deploy thay vì một. Docker Compose xử lý gọn ở mức local.
- Lệch nhẹ so với đề cương (vốn chỉ nói Spring Boot). Cần thông báo với GVHD; core-backend vẫn
  là Spring Boot đúng như cam kết.
