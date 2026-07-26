#!/bin/sh
# Sinh cấu hình runtime từ biến môi trường rồi mới khởi động nginx.
#
# Đây là mấu chốt để frontend deploy độc lập: cùng một image chạy được ở dev, staging và
# production, chỉ khác giá trị API_BASE_URL. Nếu URL được nướng vào bundle lúc build thì mỗi
# môi trường phải build lại một image riêng.
set -eu

if [ -z "${API_BASE_URL:-}" ]; then
    echo "LỖI: thiếu biến môi trường API_BASE_URL." >&2
    echo "Đặt URL mà TRÌNH DUYỆT gọi tới backend, ví dụ https://api.example.com" >&2
    echo "(không phải hostname nội bộ trong mạng docker)." >&2
    exit 1
fi

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${API_BASE_URL}",
};
EOF

echo "Cấu hình runtime: apiBaseUrl=${API_BASE_URL}"
exec "$@"
