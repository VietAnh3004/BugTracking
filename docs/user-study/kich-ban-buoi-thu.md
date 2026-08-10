# Kịch bản buổi thử

*Người điều phối đọc theo. Phần trong ngoặc vuông là hành động, không đọc lên.*

Tổng thời lượng dự kiến: **45–60 phút**.

---

## 0. Chuẩn bị trước khi người tham gia tới (10 phút)

- [ ] Postgres + MinIO + core-backend + agent-service đang chạy; `/actuator/health` trả `UP`.
- [ ] Snapshot vulnshop đã nạp, lần phân tích đã `SUCCEEDED`, số liệu khớp `expected-analysis.json`.
- [ ] Danh sách kiểm ở [bo-canh-bao.md](bo-canh-bao.md) đã chạy xong.
- [ ] Tài khoản `nguoidung-NN` đã tạo, đã đăng nhập sẵn trên máy dùng cho buổi thử.
- [ ] Đồng hồ bấm giờ, biên bản trống, bộ thứ tự cảnh báo đã xáo cho người này.
- [ ] Bảng nhãn kỳ vọng **đóng lại** — người điều phối không mở trong lúc quan sát.

---

## 1. Mở đầu (5 phút)

> "Cảm ơn bạn đã dành thời gian. Hôm nay bạn sẽ xem 8 cảnh báo mà công cụ phân tích tĩnh sinh ra
> trên một ứng dụng Java nhỏ, và với mỗi cảnh báo, quyết định xem đó là lỗi thật hay báo nhầm.
>
> Có một điều tôi muốn nói trước, và nó quan trọng hơn mọi thứ còn lại: **hôm nay tôi đánh giá
> công cụ, không đánh giá bạn.** Không có điểm, không ai xem bạn đúng bao nhiêu câu. Nếu bạn không
> quyết được, đó là một câu trả lời hợp lệ — và nói thật ra thì với tôi nó còn có ích hơn một câu
> đoán bừa.
>
> Nếu trợ lý AI trả lời sai và bạn phát hiện ra, hãy nói ngay. Đó là dữ liệu quý nhất của buổi
> hôm nay."

[Đưa phiếu đồng ý. Chờ đọc và xác nhận. **Không tiếp tục nếu chưa có xác nhận.**]

> "Trong lúc làm, nếu bạn nói ra suy nghĩ của mình thì tôi ghi lại được nhiều hơn — nhưng không bắt
> buộc, và đừng để việc nói làm bạn chậm lại."

---

## 2. Làm quen giao diện (5 phút) — dùng một cảnh báo NGOÀI bộ 8

[Mở một cảnh báo bất kỳ **không** nằm trong bộ 8, ví dụ một `SPRING_ENDPOINT`.]

> "Đây là danh sách cảnh báo. Mỗi dòng có mức nghiêm trọng, công cụ nào báo, luật gì, và ở dòng
> nào. Cái mã dạng `EV-001A` này là Evidence ID — bấm vào mở ra màn hình bằng chứng."

[Chỉ lần lượt: ngữ cảnh mã, test liên quan, độ phủ, và panel đánh giá.]

> "Bốn nút này là quyết định của bạn:
>
> - **Đúng là lỗi** — cảnh báo có căn cứ, nên sửa mã.
> - **Báo nhầm** — công cụ báo sai.
> - **Chưa quyết được** — bạn đã xem đủ bằng chứng rồi mà vẫn không chắc.
> - **Thiếu bằng chứng** — hệ thống chưa đưa ra đủ thứ để bạn phán xét.
>
> Hai cái cuối nghe giống nhau nhưng nói hai chuyện khác nhau. 'Chưa quyết được' là *bài này khó*.
> 'Thiếu bằng chứng' là *công cụ của tôi chưa đủ tốt*. Với tôi, cái thứ hai là góp ý trực tiếp,
> nên đừng ngại chọn nó.
>
> Ô ghi chú ở dưới: nếu bạn chọn 'Báo nhầm' hoặc 'Chưa quyết được', viết giúp tôi một câu vì sao."

[Với block **có Agent**, giới thiệu thêm màn hình hỏi đáp:]

> "Ở đây bạn hỏi được bằng tiếng Việt hoặc tiếng Anh, ví dụ 'test nào liên quan tới cảnh báo này?'.
> Câu trả lời sẽ kèm các mã `EV-...` bấm được — bấm vào là nhảy tới đúng bằng chứng nó dựa vào.
>
> **Bấm kiểm tra vài cái.** Trợ lý này có thể sai, và cách duy nhất để biết là đi theo trích dẫn."

> "Có gì chưa rõ không? [chờ] Từ giờ tôi sẽ không trả lời câu hỏi về cảnh báo nữa, chỉ trả lời câu
> hỏi về giao diện thôi nhé."

---

## 3. Block thứ nhất (15 phút)

[Theo bảng chia nhóm ở [bo-canh-bao.md](bo-canh-bao.md). Với mỗi cảnh báo, theo đúng thứ tự đã xáo:]

1. [Mở cảnh báo, **bấm giờ**.]
2. [Quan sát im lặng. Ghi lại: có mở Evidence Explorer không, có hỏi Agent không, hỏi câu gì, có
   bấm vào trích dẫn không.]
3. [**Dừng giờ** khi người tham gia bấm một trong bốn nút. Ghi số giây vào biên bản.]
4. Nếu ghi chú trống mà quyết định là "Báo nhầm"/"Chưa quyết được": > "Bạn viết giúp mình một câu
   lý do được không?"

**Người điều phối tuyệt đối không**: gợi ý đáp án, xác nhận đúng/sai, phản ứng bằng nét mặt khi
người tham gia nói sai, hay giải thích thay cho Agent.

Nếu bị hỏi thẳng "cái này đúng hay sai vậy?": > "Mình sẽ nói sau khi xong hết nhé."

---

## 4. Nghỉ (2 phút) và chuyển điều kiện

> "Nghỉ một chút. Phần sau bạn sẽ [dùng / không dùng] trợ lý AI."

[Với block đối chứng: **đóng tab chat**, nhắc rõ không mở lại. Kiểm tra sau buổi thử bằng dashboard
— cột "Nhóm đối chứng" phải đúng bằng 4.]

---

## 5. Block thứ hai (15 phút)

[Giống mục 3, điều kiện ngược lại.]

---

## 6. Phiếu đánh giá và phỏng vấn ngắn (8 phút)

[Đưa [phieu-danh-gia.md](phieu-danh-gia.md). Để người tham gia điền một mình.]

Sau khi điền xong, hỏi thêm (ghi tay, không ghi âm):

1. "Có cảnh báo nào bạn thấy khó nhất không? Vì sao?"
2. "Lúc có trợ lý AI, bạn có bấm vào các mã `EV-...` không? Bấm rồi thì nó có khớp với thứ trợ lý
   nói không?"
3. "Có lúc nào bạn thấy trợ lý nói sai hoặc nói thừa không?"
4. "Nếu đây là công cụ bạn dùng hằng ngày, thứ đầu tiên bạn muốn sửa là gì?"

Câu 2 và 3 là quan trọng nhất: chúng đo **mức tin cậy có kiểm chứng**, thứ mà Likert không đo được.

---

## 7. Kết thúc

> "Xong rồi, cảm ơn bạn nhiều. Giờ mình nói được đáp án nếu bạn muốn nghe."

[Chỉ tiết lộ nhãn **sau khi** đã điền xong phiếu. Tiết lộ sớm làm hỏng phần đánh giá.]

[Ngay sau khi người tham gia rời đi, trong khi còn nhớ:]

- [ ] Hoàn tất biên bản: thời gian từng cảnh báo, câu hỏi đã đặt cho Agent, quan sát đáng chú ý.
- [ ] Mở dashboard của lần phân tích: kiểm **"Có dùng Agent" = 4** và **"Nhóm đối chứng" = 4**.
      Lệch nghĩa là điều kiện bị vi phạm — ghi vào biên bản, đừng sửa dữ liệu.
- [ ] Tải CSV, lưu vào `experiments/user-study/nguoidung-NN.csv`.

## Sự cố có thể gặp

| Sự cố | Xử lý |
|---|---|
| agent-service chết giữa buổi | Ghi lại cảnh báo đang dở, chuyển nó sang block đối chứng, **ghi vào biên bản** |
| Người tham gia lỡ mở chat trong block đối chứng | Không sửa dữ liệu. Ghi vào biên bản và loại cảnh báo đó khỏi phần so sánh |
| Một cảnh báo mất/không mở được | Bỏ qua, ghi lại, tiếp tục. Không thay bằng cảnh báo khác giữa chừng |
| Người tham gia muốn dừng | Dừng ngay, cảm ơn, hỏi có muốn xoá dữ liệu không |
