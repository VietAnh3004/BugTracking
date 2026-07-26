# ADR 0002 — Frontend đọc cấu hình lúc chạy, không lúc build

- **Trạng thái**: Chấp nhận
- **Ngày**: 26/07/2026

## Bối cảnh

Yêu cầu: frontend và backend phải deploy độc lập lên hai server khác nhau, phát hành phiên bản
riêng, không bên nào phụ thuộc build-time vào bên kia.

Cách mặc định của Vite là dùng `import.meta.env.VITE_API_URL`. Biến này được thay thế lúc
**build**, nên URL backend bị nướng cứng vào bundle.

## Quyết định

URL backend đọc lúc **chạy** qua `window.__APP_CONFIG__`:

- `public/config.js` đặt `window.__APP_CONFIG__` với giá trị mặc định cho môi trường dev.
- `index.html` nạp file này bằng thẻ `<script>` thường (không `type="module"`, vì script module
  bị hoãn và sẽ chạy sau bundle).
- Trong container, `docker-entrypoint.sh` sinh lại `config.js` từ biến `API_BASE_URL` rồi mới
  khởi động nginx; thiếu biến thì container dừng ngay với thông báo rõ ràng.
- `nginx.conf` đặt `Cache-Control: no-store` riêng cho `/config.js`.
- `src/shared/config/runtime.ts` là nơi duy nhất đọc giá trị đó.

## Lý do

Với `VITE_*`, mỗi môi trường cần một image riêng và đổi domain backend phải build lại toàn bộ
frontend. Cách này cho phép **build một image, chạy mọi môi trường** — chỉ đổi biến môi trường
container.

Việc `/config.js` không được cache là bắt buộc chứ không phải tối ưu: nếu trình duyệt giữ bản
cũ, ứng dụng sẽ gọi vào backend của môi trường trước đó, và triệu chứng rất khó chẩn đoán.

## Hệ quả kèm theo

- **Không cấu hình proxy trong `vite.config.ts`.** Proxy khiến trình duyệt thấy mọi request là
  same-origin, nên suốt quá trình phát triển sẽ không gặp lỗi CORS nào — rồi vỡ đúng lúc deploy
  tách domain. Dev gọi thẳng `http://localhost:8080` để CORS được kiểm tra thật từ ngày đầu.
- **Không đặt secret nào ở frontend.** Mọi thứ trong bundle đều công khai với người dùng, kể cả
  biến đặt tên là `SECRET`.
