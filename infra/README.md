# infra

Hạ tầng chạy local: PostgreSQL 16 + pgvector, MinIO (object storage), và ba service của hệ thống.

```bash
cp .env.example .env      # điền giá trị thật
docker compose up -d
```

`docker-compose.yml` chỉ để tiện chạy local và demo. Nó **không** phải điều kiện để hệ thống
hoạt động: mỗi service khởi động được đơn lẻ chỉ với biến môi trường của nó, và deploy được lên
các server khác nhau. File compose chỉ nối sẵn các biến đó lại.

## Kiểm chứng

```bash
# pgvector đã bật chưa
docker compose exec postgres psql -U evidence -d evidence_agent -c "\dx"

# tất cả service khoẻ chưa
docker compose ps
```

## Vấn đề đã gặp: không kéo được image lớn

**Ngày 26/07/2026.** `docker pull pgvector/pgvector:pg16` và `postgres:16-alpine` liên tục đứt
giữa chừng:

```
failed to copy: httpReadSeeker: failed open: failed to do request:
Get "https://production.cloudfront.docker.com/...": EOF
```

Đã thử 40 lần, tất cả đều hỏng. Chẩn đoán: `docker pull hello-world` (vài KB) thành công, còn
image hàng trăm MB thì hỏng — kết nối không giữ được cho layer lớn, không phải lỗi cấu hình
compose hay đăng nhập registry.

Cách xử lý theo thứ tự nên thử:

1. **Tăng số lần Docker tự thử lại từng layer.** Docker Desktop → Settings → Docker Engine, thêm
   vào JSON rồi Apply & Restart:
   ```json
   { "max-download-attempts": 10 }
   ```
   Docker sẽ tự nối lại từng layer hỏng thay vì bỏ cả lượt pull.

2. **Dùng registry mirror gần hơn.** Cũng trong Docker Engine JSON:
   ```json
   { "registry-mirrors": ["https://<mirror-cua-ban>"] }
   ```

3. **Kéo image vào lúc mạng ổn định hơn**, hoặc qua mạng khác. Image chỉ cần tải một lần, sau
   đó nằm trong cache cục bộ.

4. **Nếu vẫn không được**: chạy PostgreSQL cài trực tiếp trên máy và tự cài extension pgvector.
   Đây là phương án cuối vì làm mất tính "dựng lại được trong 30 phút" của dự án.

Cho tới khi kéo được image, phần hạ tầng chưa được kiểm chứng end-to-end — cụ thể là
`CREATE EXTENSION vector` và việc Flyway chạy migration V1.
