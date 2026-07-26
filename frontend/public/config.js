// Cấu hình đọc lúc CHẠY, không phải lúc build.
//
// Nếu dùng import.meta.env.VITE_* thì URL backend bị nướng cứng vào bundle và mỗi môi trường
// phải build lại một image riêng — frontend hết deploy độc lập được. File này được nạp bằng thẻ
// <script> trước ứng dụng, nên đổi giá trị chỉ cần khởi động lại container.
//
// Giá trị dưới đây chỉ dùng khi chạy dev. Trong container, docker-entrypoint.sh ghi đè file này
// từ biến môi trường API_BASE_URL.
window.__APP_CONFIG__ = {
  apiBaseUrl: 'http://localhost:8080',
};
