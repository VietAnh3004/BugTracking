# ADR 0012 — Xác thực email, quên mật khẩu, đăng nhập Google, hồ sơ người dùng

- **Trạng thái**: Chấp nhận
- **Ngày**: 16/11/2026
- **Liên quan**: [ADR 0006](0006-xac-thuc-jwt-va-refresh-token-xoay-vong.md) (JWT + refresh token
  xoay vòng — mọi luồng mới ở đây kết thúc bằng đúng một phiên kiểu đó),
  [ADR 0002](0002-frontend-cau-hinh-luc-chay.md) (FE/BE deploy độc lập)

## Bối cảnh

Tới tuần 16, phần người dùng chỉ có đăng ký/đăng nhập bằng email + mật khẩu. Không có cách lấy
lại tài khoản khi quên mật khẩu, không sửa được tên, và một email bất kỳ (kể cả của người khác)
đăng ký được ngay. Từ khi có chia sẻ dự án (V10) — mời thành viên **theo email** — điều cuối cùng
thành lỗ hổng: ai cũng tạo được tài khoản mang email của người được mời.

## Quyết định (người dùng chọn, 16/11/2026)

| Hạng mục | Lựa chọn |
|---|---|
| Gửi email | Gmail SMTP + App Password (`spring.mail.*`). `MAIL_USERNAME` trống = không gửi, ghi thư vào log |
| Chưa xác thực email | **Chặn đăng nhập** bằng mật khẩu (403 `email-not-verified`). Tài khoản có trước V13 coi như đã xác thực |
| Google trùng email | **Tự liên kết** — chỉ khi Google báo `email_verified` |
| Ảnh đại diện | **Không lưu trên hệ thống**: ảnh Google nếu đã liên kết, không thì Gravatar theo email |

## Thiết kế và lý do

1. **Token trong email là token opaque một lần, lưu SHA-256** (`account_token`, V13), cùng cách
   với refresh token. Đánh dấu đã dùng bằng `UPDATE ... WHERE used_at IS NULL` để hai lần bấm
   song song chỉ một lần thành công. Phát link mới vô hiệu link cũ cùng loại.
2. **Token đặt ở fragment** (`/verify-email#token=…`): trình duyệt không gửi fragment lên máy chủ,
   nên token không vào access log của nginx/proxy, không lọt qua `Referer`.
3. **Chống dò tài khoản.** `resend-verification` và `forgot-password` luôn 204. Thư gửi **sau
   commit, trên luồng riêng** (`MailDispatcher`): gửi đồng bộ qua Gmail mất 1-3 giây, đủ để đo thời
   gian phản hồi mà biết email có tài khoản. "Chưa xác thực" chỉ lộ ra **sau** khi mật khẩu đúng.
4. **Giãn cách 60 giây** giữa hai thư cùng loại cho một người: chặn dùng form quên mật khẩu để dội
   thư vào hộp thư người khác.
5. **Đổi/đặt lại mật khẩu đóng MỌI phiên** (`revoked_reason = PASSWORD_CHANGED`), gửi thư báo
   "mật khẩu vừa đổi". Đổi mật khẩu trả về một phiên mới cho người đang thao tác. Sai mật khẩu hiện
   tại là **400 chứ không 401**: frontend coi 401 là hết phiên và tự đi refresh.
6. **Google: frontend lấy ID token, backend kiểm** chữ ký (JWKS của Google), `iss`, `aud` = đúng
   Client ID của ứng dụng, `exp`. Thiếu kiểm `aud` thì token Google cấp cho *bất kỳ* trang nào đăng
   nhập được vào đây. Nhận người dùng theo `sub` (bất biến), không theo email. Client ID lấy từ
   backend (`GET /auth/config`), một biến `GOOGLE_CLIENT_ID` duy nhất — không có chuyện frontend
   hiện nút cho một Client ID mà backend không nhận.
7. **Chống chiếm tài khoản đặt trước.** Kẻ xấu đăng ký trước bằng email nạn nhân, không xác thực
   được, rồi chờ nạn nhân đăng nhập Google để được liên kết. Vì vậy liên kết Google vào tài khoản
   **chưa xác thực** thì xoá mật khẩu cũ và đóng mọi phiên của nó.
8. **Tài khoản chỉ có Google không có mật khẩu** (`password_hash` nullable). Ràng buộc
   `ck_app_user_can_sign_in` giữ bất biến "đăng nhập được bằng ít nhất một cách". Họ đặt mật khẩu ở
   trang Tài khoản (không hỏi mật khẩu hiện tại) hoặc qua "Quên mật khẩu".

## Hệ quả

- **Đăng ký không còn trả token** — đổi hợp đồng `POST /auth/register` (201 `{email}`). Phiên đầu
  tiên đến từ `POST /auth/verify-email`.
- `/actuator/health` **tắt kiểm SMTP** (`management.health.mail.enabled: false`): chưa điền Gmail
  không được làm backend "DOWN" trong khi mọi API vẫn chạy.
- Test không bao giờ gửi thư thật hay gọi Google: `RecordingAccountMailer` và
  `FakeGoogleIdTokenVerifier` thay hai cổng `AccountMailer`/`GoogleIdTokenVerifier`. Phần kiểm token
  Google thật được test bằng khoá RSA tự sinh (`NimbusGoogleIdTokenVerifierTest`).
- Gmail giới hạn khoảng 500 thư/ngày cho tài khoản thường — thừa cho đồ án, không đủ cho sản phẩm.
- Gravatar nhận SHA-256 của email: ai có email của người dùng đều tính ra được URL ảnh đó. Đây là
  cách Gravatar vốn hoạt động, chấp nhận theo lựa chọn "không lưu ảnh".

## Cập nhật 18/11/2026 — gửi từ tên miền riêng, thư HTML

*(Không sửa nội dung quyết định gốc ở trên.)* Sau khi có tên miền `vsleuth.id.vn`, người dùng chọn
gửi thư qua **Resend** từ `VSleuth <no-reply@vsleuth.id.vn>` thay vì Gmail SMTP, và thư có **bản
HTML** kèm bản văn bản thuần. Mã vẫn chỉ nói SMTP chuẩn, nên Gmail vẫn dùng được trên máy dev — đổi
nhà cung cấp chỉ là đổi biến môi trường. Lý do, cấu hình DNS (SPF/DKIM/DMARC) và cách kiểm:
[docs/email.md](../email.md).

Hai hệ quả cần biết: (1) tên hiển thị do người dùng đặt nay đi vào HTML, nên mọi chuỗi phải qua
`EmailTemplate.esc` — thư đã ký DKIM mà chèn được link thì là thư lừa đảo trông chính chủ nhất;
(2) Resend dùng tên đăng nhập SMTP `resend` (không phải email), nên thiếu `MAIL_FROM` thì
core-backend dừng ngay lúc khởi động thay vì hỏng âm thầm lúc gửi.
