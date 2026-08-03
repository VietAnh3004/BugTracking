# ADR 0006 — Xác thực JWT stateless + refresh token xoay vòng, access token trong bộ nhớ

- **Trạng thái**: Chấp nhận
- **Ngày**: 03/08/2026

## Bối cảnh

Tuần 5-7 biến dự án từ "có schema và hợp đồng API" thành ứng dụng web có người dùng thật. Cần chốt
cơ chế xác thực trước khi viết bất kỳ màn hình nào, vì lựa chọn này quyết định cả phía backend
(filter chain, thu hồi phiên) lẫn phía frontend (lưu token ở đâu, guard hoạt động thế nào).

Hai ràng buộc có sẵn, không thương lượng:

1. **FE và BE deploy độc lập lên hai server khác domain** ([ADR 0002](0002-frontend-cau-hinh-luc-chay.md)).
2. **`/api/v1/tools/**` dùng service token riêng, không dùng JWT người dùng** ([ADR 0005](0005-evidence-tool-api-la-rpc.md)) —
   `experiments/runner.py` chạy batch, không có phiên người dùng nào để gắn vào.

## Quyết định

### 1. Access token là JWT HS256, hạn 15 phút, giữ TRONG BỘ NHỚ ở frontend

Không lưu access token vào `localStorage` hay cookie. Nó sống trong một biến của module
`shared/lib/apiClient.ts` và biến mất khi tải lại trang.

### 2. Refresh token là chuỗi ngẫu nhiên (opaque), xoay vòng, lưu trong `localStorage`

- 32 byte `SecureRandom`, lưu ở DB dưới dạng **SHA-256 hex** (không phải BCrypt — xem mục Hệ quả).
- Mỗi lần dùng thì bị thu hồi và phát ra token mới (xoay vòng).
- Dùng lại một token đã xoay ⇒ **thu hồi cả họ** (`family_id`), coi như token đã bị lộ.
- Phía frontend, `shared/lib/tokenStorage.ts` là **nơi duy nhất** chạm `localStorage`.

### 3. Không dùng cookie `HttpOnly`

Đây là lựa chọn gây tranh cãi nhất nên ghi rõ lý do ở mục dưới.

## Vì sao không dùng cookie `HttpOnly` — lập luận đầy đủ

Cookie `HttpOnly` thường được coi là "cách đúng" để chống XSS đánh cắp token. Hai lý do khiến nó
**không** phù hợp ở đây:

**Thứ nhất, nó xung đột trực tiếp với ADR 0002.** FE và BE nằm khác domain, nên cookie phải khai
`SameSite=None; Secure` mới gửi kèm được request cross-site. Kéo theo: bắt buộc HTTPS ở **mọi**
môi trường kể cả máy phát triển, phải thêm phòng thủ CSRF (vì `SameSite=None` mở lại đúng lớp tấn
công mà `SameSite` sinh ra để chặn), và một số trình duyệt chặn cookie bên thứ ba theo mặc định —
tức là ứng dụng có thể hỏng ở máy người dùng vì thiết lập trình duyệt, không phải vì lỗi code. Đổi
lại toàn bộ chi phí đó chỉ để bảo vệ một thứ mà mục dưới đây cho thấy nó không thực sự bảo vệ.

**Thứ hai, `HttpOnly` KHÔNG miễn nhiễm XSS — nó chỉ ngăn TRÍCH XUẤT token, không ngăn LẠM DỤNG.**
Kẻ tấn công chạy được JavaScript trong trang vẫn gọi được API thay mặt người dùng (trình duyệt tự
đính cookie vào mọi request), đọc được toàn bộ dữ liệu trả về, và thực hiện được mọi hành động
người dùng có quyền. Nó chỉ không mang được token sang máy khác để dùng lâu dài. Nói cách khác:
`HttpOnly` hạ mức thiệt hại của XSS từ "chiếm phiên vĩnh viễn" xuống "chiếm phiên trong lúc nạn
nhân còn mở tab" — hữu ích, nhưng không phải lá chắn như hay được hiểu.

**Bù đắp đã chọn** cho phần rủi ro còn lại:

- Access token 15 phút thay vì 12 giờ (giá trị ban đầu của tuần 1-4).
- Refresh token xoay vòng: token bị đánh cắp chỉ dùng được **một lần**.
- Phát hiện tái sử dụng: khi cả nạn nhân lẫn kẻ tấn công cùng dùng một token, hệ thống nhận ra và
  thu hồi cả họ — nạn nhân bị đăng xuất, nhưng kẻ tấn công cũng mất quyền.
- Coi việc **chặn XSS từ gốc** là biện pháp chính, không phải chỗ lưu token (xem mục kế tiếp).

## Bề mặt XSS thật của dự án này, gọi đúng tên

Đây không phải rủi ro lý thuyết: **source viewer render nội dung file do người dùng upload**. Một
repo độc hại chứa file `.html`/`.js` với payload là kịch bản hoàn toàn hiện thực trong chính luồng
nghiệp vụ của đồ án.

Quy tắc cứng, ghi vào comment đầu mỗi component liên quan:

> **Không bao giờ dùng `dangerouslySetInnerHTML` cho bất kỳ dữ liệu nào bắt nguồn từ file upload.**

React tự escape khi render text node, nên chỉ cần không mở cửa bằng `dangerouslySetInnerHTML` là
bề mặt này đóng. Khi tuần 10-11 thêm tô màu cú pháp, thư viện highlight nào sinh chuỗi HTML rồi
đưa vào `dangerouslySetInnerHTML` đều bị loại — phải chọn loại sinh ra cây React element.

## Hệ quả

**Tích cực**

- Backend stateless đúng nghĩa với access token: không tra DB mỗi request, scale ngang không cần
  sticky session.
- Vẫn thu hồi được phiên (qua bảng `refresh_token`) — thứ mà JWT thuần không làm được.
- Không cần CSRF token: không có cookie nào được trình duyệt tự đính vào request.
- Chạy được trên HTTP ở máy phát triển, không phải dựng chứng chỉ tự ký.

**Tiêu cực, chấp nhận có ý thức**

- Refresh token trong `localStorage` **đọc được bằng JavaScript**. Đây là đánh đổi trung tâm của
  ADR này, đã lập luận ở trên.
- Tải lại trang phải gọi `/auth/refresh` một lần để lấy access token mới → có một khoảnh khắc
  `bootstrapping` mà giao diện chưa biết người dùng đã đăng nhập hay chưa. Route guard **bắt buộc**
  chờ hết khoảnh khắc này thay vì điều hướng ngay; xử lý sai sẽ khiến mọi lần F5 trên deep link
  nhảy về trang đăng nhập. Đã canh bằng test `ProtectedRoute.test.tsx`.
- Refresh token lưu SHA-256 chứ không phải BCrypt: token là 256 bit ngẫu nhiên nên không có gì để
  brute-force, và BCrypt có salt ngẫu nhiên nên **không tra cứu theo giá trị được** — sẽ phải quét
  toàn bảng mỗi lần refresh. Hàm băm chậm ở đây là chi phí không mua được gì.
- Backend phải khai `@Transactional(noRollbackFor = ...)` ở luồng refresh: thao tác thu hồi được
  ghi *trước* khi ném exception báo 401, mà rollback mặc định của Spring sẽ xoá mất chính thao tác
  bảo mật đó. Đã gặp thật và ghi vào `CLAUDE.md`.

## Phương án đã cân nhắc và loại

| Phương án | Lý do loại |
|---|---|
| Session + cookie phía server | Backend có trạng thái, cần lưu session dùng chung khi scale; và vẫn vướng `SameSite=None` như trên |
| JWT dài hạn, không refresh token | Không thu hồi được. Người dùng đăng xuất mà token vẫn hợp lệ tới lúc hết hạn |
| Refresh token không xoay vòng | Token bị lộ dùng được tới khi hết hạn, và không có cách nào phát hiện |
| Lưu access token vào `localStorage` cho tiện | Bỏ đi lợi ích duy nhất còn lại: access token không sống sót qua XSS đọc storage một lần |
