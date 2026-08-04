# infra

Hạ tầng chạy local: PostgreSQL 16 + pgvector, MinIO (object storage), và ba service của hệ thống.

```bash
cp .env.example .env      # điền giá trị thật
docker compose --env-file .env up -d --build
```

Sau ~2 phút (lần đầu lâu hơn vì phải tải dependency Maven và npm):

- Frontend: http://localhost:3000
- Backend: http://localhost:8080/api/v1 · Swagger UI: http://localhost:8080/swagger-ui.html

`docker-compose.yml` chỉ để tiện chạy local và demo. Nó **không** phải điều kiện để hệ thống
hoạt động: mỗi service khởi động được đơn lẻ chỉ với biến môi trường của nó, và deploy được lên
các server khác nhau. File compose chỉ nối sẵn các biến đó lại.

## Ba cách chạy, chọn theo việc đang làm

| Cách | Lệnh | Khi nào dùng |
|---|---|---|
| **Toàn bộ bằng Docker** | `docker compose --env-file .env up -d --build` | Demo, kiểm chứng deploy, không cần sửa code |
| **Chỉ hạ tầng** | `docker compose --env-file .env up -d postgres minio` | Đang phát triển: chạy backend/frontend trực tiếp trên máy để có hot-reload |
| **Một service cụ thể** | `docker compose --env-file .env up -d --build core-backend` | Sau khi sửa backend, muốn chạy lại đúng nó |

Sửa code rồi chạy lại **bắt buộc có `--build`** — không có cờ này, compose dùng lại image cũ và
bạn sẽ test nhầm bản trước đó mà không hề biết.

## Hai biến dễ làm hỏng lúc khởi động

**`API_BASE_URL` là URL mà TRÌNH DUYỆT gọi tới**, không phải hostname trong mạng Docker. Đặt
`http://core-backend:8080` sẽ chạy được ở tầng mạng Docker nhưng trình duyệt của người dùng không
phân giải nổi tên đó. Giá trị đúng cho máy local là `http://localhost:8080`.

**`CORS_ORIGINS` phải chứa đúng origin mà trình duyệt đang mở.** Chạy frontend bằng Docker thì đó
là `http://localhost:3000`; chạy `npm run dev` thì là `http://localhost:5173`. Sai một cổng là mọi
request bị chặn CORS ngay màn hình đầu. Mặc định trong `.env.example` đã có cả hai.

## Kiểm chứng

```bash
# pgvector đã bật chưa
docker compose exec postgres psql -U evidence -d evidence_agent -c "\dx"

# tất cả service khoẻ chưa
docker compose ps
```

Đã xác nhận (26/07/2026): `vector` và `pg_trgm` bật đúng, và `core-backend` khởi động thành
công với Flyway tạo bảng `project` + `repository_snapshot` trên chính Postgres này.

## Nếu không kéo được image lớn từ Docker Hub

Gặp phải ngày 26/07/2026: `docker pull pgvector/pgvector:pg16` và `postgres:16-alpine` liên tục
đứt giữa chừng (`httpReadSeeker: failed open: ... EOF`) dù image nhỏ như `hello-world` vẫn tải
được — tức là kết nối tới CDN gốc của Docker Hub không giữ được cho layer lớn, không phải lỗi
cấu hình compose hay đăng nhập registry.

**Cách đã dùng và hiệu quả**: kéo qua mirror công khai của Google, sau đó gắn lại tên gốc để
`docker-compose.yml` không cần sửa gì:

```bash
docker pull mirror.gcr.io/pgvector/pgvector:pg16
docker pull mirror.gcr.io/minio/minio:RELEASE.2025-04-22T22-12-26Z

docker tag mirror.gcr.io/pgvector/pgvector:pg16 pgvector/pgvector:pg16
docker tag mirror.gcr.io/minio/minio:RELEASE.2025-04-22T22-12-26Z minio/minio:RELEASE.2025-04-22T22-12-26Z
```

`mirror.gcr.io` là cache đọc-qua (read-through) của Google cho toàn bộ Docker Hub công khai, bao
gồm cả image không thuộc namespace `library` (như `pgvector/pgvector`). Chỉ cần tải một lần —
sau đó image nằm trong cache Docker cục bộ và mọi lần `docker compose up` sau đều không cần
mạng nữa.

Nếu cách này cũng không ổn (mirror bị chặn ở mạng của bạn), thử theo thứ tự:

1. Tăng số lần Docker tự thử lại từng layer: Docker Desktop → Settings → Docker Engine, thêm
   `{ "max-download-attempts": 10 }` rồi Apply & Restart.
2. Đổi sang mạng khác hoặc giờ mạng ổn định hơn — image chỉ cần tải một lần.
3. Phương án cuối: cài PostgreSQL trực tiếp trên máy và tự cài extension pgvector. Chấp nhận
   đánh đổi mất tính "dựng lại được trong 30 phút" của dự án.
