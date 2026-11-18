# Gửi email từ `no-reply@vsleuth.id.vn` qua Resend

Thư xác thực tài khoản, đặt lại mật khẩu và báo đổi mật khẩu (ADR 0012) gửi từ tên miền của đồ án
thay vì một địa chỉ Gmail cá nhân, và **vào hộp thư chính chứ không vào spam**. Viết 18/11/2026,
**chưa** cấu hình thật — làm xong bước nào thì đánh dấu ở mục 0.

## 0. Trạng thái

| Việc | Trạng thái |
|---|---|
| Tên miền `vsleuth.id.vn` | ✅ Đã mua (PA Vietnam) |
| Mã: thư HTML + văn bản thuần, fail-fast khi thiếu `MAIL_FROM` | ✅ Xong (18/11/2026) |
| Compose truyền `MAIL_HOST`/`MAIL_PORT` | ✅ Xong — trước đó container luôn gọi `smtp.gmail.com` |
| Tài khoản Resend + thêm tên miền (mục 2) | ✅ Xong (18/11/2026) |
| Bốn bản ghi DNS ở PA + Verify (mục 3) | ⬜ Đang làm |
| Kiểm thư thật qua SPF/DKIM/DMARC (mục 5) | ⬜ Chưa làm |

## 1. Quyết định (người dùng chọn, 18/11/2026)

| Hạng mục | Lựa chọn |
|---|---|
| Dịch vụ gửi | **Resend** — có SMTP chuẩn nên mã không phụ thuộc nhà cung cấp; cấu hình DNS gọn |
| Người gửi | **`VSleuth <no-reply@vsleuth.id.vn>`** |
| Định dạng | **HTML có thương hiệu + bản văn bản thuần** trong cùng một thư (`multipart/alternative`) |

Gmail SMTP (cách cũ của ADR 0012) không làm được việc này: Gmail bắt địa chỉ "Từ" phải là chính
tài khoản Gmail, nên thư luôn hiện người gửi là một địa chỉ `@gmail.com` cá nhân.

Hạn mức miễn phí của Resend (khoảng 100 thư/ngày, 3.000 thư/tháng theo hiểu biết lúc viết — kiểm
lại trên trang của họ) thừa cho đồ án: mỗi người dùng chỉ nhận vài thư, và hệ thống đã giãn cách
60 giây giữa hai thư cùng loại.

## 2. Tạo tài khoản và thêm tên miền trên Resend

1. Đăng ký ở https://resend.com.
2. **Domains → Add Domain** → nhập `vsleuth.id.vn`. Chọn vùng (region) gần Việt Nam nhất trong
   danh sách họ đưa ra.
3. Resend hiện ra **một bảng các bản ghi DNS** cần thêm. Để nguyên tab đó — mục 3 chép từ đây.

## 3. Thêm bản ghi DNS ở PA Vietnam

Vào trang quản lý DNS của `vsleuth.id.vn` trên PA Vietnam (**DNS → Cấu hình bản ghi tên miền**),
thêm **đúng từng bản ghi** Resend đưa ra. **Chép giá trị từ màn hình Resend, đừng chép từ tài liệu
này** — khoá DKIM là riêng cho tài khoản của bạn.

Màn hình Resend thật (xem 18/11/2026) cho **bốn** bản ghi — khác điều tài liệu này ghi lúc đầu
(TXT + MX cho `send`): Resend nay trỏ phần SPF bằng **CNAME** về máy chủ của họ.

| Loại | Host | Giá trị (chép từ Resend) | Để làm gì |
|---|---|---|---|
| TXT | `resend._domainkey` | `p=MIGfMA...` | **DKIM** — khoá công khai. Bên nhận dùng nó kiểm chữ ký số trên mỗi thư: thư thật sự do tên miền này gửi và không bị sửa trên đường đi |
| CNAME | `rsend` | `rsend-...mta.net` | **SPF** + đường nhận thư báo lỗi (bounce) — ở tên miền con, không đụng thư của tên miền gốc |
| CNAME | `send` | `send...mta.net` | Như trên |
| TXT | `_dmarc` | `v=DMARC1; p=none;` | **DMARC** (Resend ghi "Recommended") |

Trên PA: mỗi bản ghi một lần **"Thêm bản ghi"**, TTL để 3600, xong hết thì bấm **"Lưu cấu hình"** —
"Thêm bản ghi" mới chỉ tạo dòng trên màn hình, chưa lưu gì.

**Giá trị trên màn hình Resend bị rút gọn** (`p=MIGfMA [...] wIDAQAB`): `[...]` là phần bị ẩn. Bấm
vào ô để sao chép bản đầy đủ — gõ lại theo phần nhìn thấy là thiếu ký tự, và Resend chỉ báo "chưa
xác minh" chứ không nói vì sao. Dán nguyên văn: không thêm nháy kép, không thêm dấu chấm cuối.

**DMARC** cho bên nhận biết phải làm gì với thư mạo danh tên miền này. `p=none` nghĩa là "chỉ quan
sát, chưa chặn" — mức an toàn để bắt đầu. Gmail và Yahoo đòi phải **có** bản ghi DMARC với người gửi
thư số lượng lớn; có sẵn từ đầu thì thư đỡ bị xếp vào spam. Khi đã chạy ổn vài tuần có thể nâng lên
`p=quarantine`.

> **Cạm bẫy ô "Tên" ở trang DNS:** nhiều trang quản lý DNS **tự nối thêm** `.vsleuth.id.vn` vào
> sau tên bạn nhập. Nhập `resend._domainkey.vsleuth.id.vn` vào một trang như vậy sẽ tạo ra
> `resend._domainkey.vsleuth.id.vn.vsleuth.id.vn` — Resend không bao giờ tìm thấy, và không báo vì
> sao. Nhập phần **trước** tên miền (`resend._domainkey`), rồi kiểm lại bằng lệnh ở dưới.

Kiểm từ máy bạn (Git Bash hoặc PowerShell), sau vài phút tới vài giờ:

```bash
nslookup -type=TXT   resend._domainkey.vsleuth.id.vn
nslookup -type=CNAME rsend.vsleuth.id.vn
nslookup -type=CNAME send.vsleuth.id.vn
nslookup -type=TXT   _dmarc.vsleuth.id.vn
```

Cả bốn phải trả về đúng giá trị đã nhập. CNAME mà trả về đuôi `...mta.net.vsleuth.id.vn` nghĩa là
trang DNS tự nối tên miền vào sau GIÁ TRỊ — sửa bằng cách thêm dấu chấm cuối (`...mta.net.`). Rồi
quay lại Resend bấm **Verify** — tên miền chuyển sang **Verified**.

Để **tắt** mục "Enable Receiving" trên Resend: hệ thống chỉ gửi, không nhận thư.

## 4. Tạo API key và cấu hình hệ thống

Resend → **API Keys → Create API Key**: quyền **Sending access**, giới hạn ở tên miền
`vsleuth.id.vn`. Key hiện **một lần duy nhất** (dạng `re_...`) — chép ngay vào `infra/.env.vps`:

```bash
MAIL_HOST=smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=resend                  # đúng chữ "resend", không phải email
MAIL_PASSWORD=re_xxxxxxxxxxxxxxxx     # API key vừa tạo
MAIL_FROM=no-reply@vsleuth.id.vn      # BẮT BUỘC, phải thuộc tên miền đã Verified
MAIL_FROM_NAME=VSleuth
```

Rồi khởi động lại backend: `dc up -d` (docs/deploy-vps.md mục 9).

Quên `MAIL_FROM` thì core-backend **không khởi động**, log ghi rõ
`MAIL_USERNAME 'resend' khong phai dia chi email ... phai dat MAIL_FROM`. Đó là chủ ý: thư gửi trên
luồng nền, nên lỗi lúc gửi sẽ chỉ là một dòng log không ai đọc trong khi người dùng chờ một email
không bao giờ tới.

Muốn thử ngay trên **máy dev** trước khi có VPS: điền đúng các biến trên vào `infra/.env` (hoặc
`core-backend/.env`) — Resend không quan tâm thư gửi đi từ máy nào. Chỉ nhớ `APP_PUBLIC_URL` trên
máy dev là `http://localhost:...`, nên link trong thư thử sẽ trỏ về máy bạn.

## 5. Kiểm chứng bằng một thư thật

1. Đăng ký một tài khoản bằng **địa chỉ Gmail của bạn**.
2. Thư phải tới **Hộp thư đến**, không phải Spam. Người gửi hiện **VSleuth**.
3. Mở thư: có khung trắng, tên VSleuth màu chàm ở đầu, nút "Xác thực email". Bấm nút → mở
   `https://vsleuth.id.vn/verify-email#token=...` và tài khoản được xác thực.
4. Trong Gmail: **⋮ → Hiện thư gốc (Show original)**. Ba dòng đầu phải là:

   ```
   SPF:   PASS
   DKIM:  'PASS' with domain vsleuth.id.vn
   DMARC: 'PASS'
   ```

   DKIM `PASS` nhưng **domain khác** `vsleuth.id.vn` (ví dụ `resend.dev`) nghĩa là thư chưa ký
   bằng tên miền của bạn — tên miền chưa Verified, hoặc `MAIL_FROM` không thuộc tên miền đó.
5. Tuỳ chọn: gửi thử tới địa chỉ do https://www.mail-tester.com cấp để nhận điểm (10/10 là tốt).

## 6. Cạm bẫy đã biết trước

| Cạm bẫy | Triệu chứng | Cách xử lý |
|---|---|---|
| Nhà cung cấp VPS chặn cổng SMTP gửi ra (nhiều nơi chặn mặc định 25/465/587 với tài khoản mới) | Log core-backend: `Couldn't connect to host ... port 587`, timeout | Resend có cổng thay thế (2587 cho STARTTLS) — đặt `MAIL_PORT=2587`. Hoặc xin nhà cung cấp mở cổng |
| `MAIL_FROM` không thuộc tên miền đã Verified | Resend từ chối, log có lỗi `550`/`403` từ máy chủ SMTP | Đặt `MAIL_FROM=...@vsleuth.id.vn` |
| Ô "Tên" ở trang DNS tự nối tên miền | Resend mãi "Pending" | Mục 3 |
| Người nhận bấm "Trả lời" | Thư trả lời không tới đâu cả — tên miền gốc không có MX | Chủ ý: chân thư ghi "vui lòng không trả lời". Muốn nhận phản hồi thì cần hộp thư thật cho tên miền |
| Đổi màu thương hiệu ở frontend | Thư vẫn màu cũ | Màu thư chép tay từ `frontend/src/index.css` vào `EmailTemplate` — đổi cả hai |

## 7. Mã liên quan

| Lớp | Vai trò |
|---|---|
| `service/account/AccountEmails` | Thư NÓI GÌ — nội dung ba loại thư |
| `service/account/EmailContent` | Nội dung chưa định dạng: lời chào, các đoạn, nút hành động |
| `service/account/EmailTemplate` | Thư TRÔNG thế nào — nơi DUY NHẤT dựng HTML và escape. Tên hiển thị do người dùng tự đặt phải qua đây, nếu không ai đó đặt tên là một thẻ `<a>` sẽ chèn được link lừa đảo vào thư đã ký DKIM của hệ thống (`EmailTemplateTest`) |
| `service/account/SmtpAccountMailer` | Gửi `multipart/alternative`; fail-fast khi thiếu `MAIL_FROM` |
