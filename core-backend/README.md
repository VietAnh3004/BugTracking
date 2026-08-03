# core-backend

Quản lý dự án và mã nguồn, chạy phân tích tĩnh, thu thập bằng chứng kiểm thử, lưu Evidence Store,
hậu kiểm trích dẫn và ghi nhận human review.

## Vai trò trong hệ thống

Service này sở hữu dữ liệu. `agent-service` không có kết nối cơ sở dữ liệu — nó lấy bằng chứng
qua Evidence Tool API do đây expose, nên mỗi lượt truy xuất của agent đều log và đo được.

**Hậu kiểm trích dẫn nằm ở đây**, ngoài process của agent, để bước kiểm tra thực sự độc lập với
LLM. Xem [ADR 0001](../docs/adr/0001-tach-agent-service-khoi-core-backend.md).

## Cấu trúc

```
controller/   Nhận request, validate DTO, gọi service. Không logic nghiệp vụ.
service/      Toàn bộ logic nghiệp vụ. Không biết gì về HTTP.
repository/   Truy cập dữ liệu (Spring Data JPA).
domain/       Entity, value object, enum.
dto/          Record request/response, tách biệt entity.
mapper/       MapStruct, entity <-> DTO.
config/       Bean, security, CORS, OpenAPI. Nhóm cấu hình nằm trong config/properties.
exception/    Exception nghiệp vụ + @RestControllerAdvice xử lý tập trung.
storage/      Cổng ra object storage. Là HẠ TẦNG, không phải nghiệp vụ — xem package-info.
```

Quy ước:

- **Không trả entity JPA trực tiếp ra API** — luôn qua DTO, tránh lộ trường nội bộ và lỗi
  lazy-loading khi serialize.
- **Constructor injection**, không `@Autowired` trên field.
- **Không `try/catch` trong controller** — mọi lỗi nổi lên `GlobalExceptionHandler` và trả về
  cùng một định dạng RFC 7807.
- **Schema chỉ thay đổi qua Flyway**, `ddl-auto` luôn là `validate`.

## Yêu cầu môi trường

**JDK 21.** Không dùng JDK mới hơn: SpotBugs và các formatter dựa trên javac không chạy được
trên JDK 25 — xem [ADR 0003](../docs/adr/0003-chuan-hoa-jdk-21.md).

## Xác thực

Hai `SecurityFilterChain` tách biệt theo đường dẫn:

| Nhóm endpoint | Cách xác thực |
|---|---|
| `/api/v1/tools/**` | Header `X-Service-Token` (bí mật chia sẻ với agent-service) |
| Còn lại | JWT Bearer, access token 15 phút + refresh token xoay vòng |

Refresh token **xoay vòng và phát hiện tái sử dụng**: dùng lại một token đã xoay sẽ thu hồi cả họ.
Đánh đổi đầy đủ ở [ADR 0006](../docs/adr/0006-xac-thuc-jwt-va-refresh-token-xoay-vong.md).

Quy tắc phân quyền duy nhất: **tài nguyên không thuộc về bạn ⇒ 404, không phải 403** — lọc theo
`ownerId` ngay trong câu truy vấn, không "tìm rồi so sánh trong Java".

## Lưu mã nguồn

Upload zip → giải nén an toàn (chống zip-slip ba tầng, chống zip-bomb) → mỗi file một object trên
MinIO (`snapshots/{id}/{path}`) + chỉ mục phẳng trong bảng `snapshot_file`. Postgres là nguồn sự
thật; object storage được phép có rác. Xem
[ADR 0007](../docs/adr/0007-luu-snapshot-theo-tung-file-tren-object-storage.md).

## Chạy local

Cần Postgres + MinIO (`docker compose -f ../infra/docker-compose.yml --env-file ../infra/.env up -d postgres minio`).

```bash
cp .env.example .env      # điền giá trị thật
mvn spring-boot:run
```

Spring Boot **không tự đọc file `.env`** (dự án không dùng thư viện dotenv) — nó chỉ là tài liệu
cho biết cần những biến nào. Khi chạy bằng `java -jar`, phải export trước:

```bash
set -a && . ./.env && set +a && java -jar target/core-backend-0.1.0.jar
```

Thiếu biến bắt buộc thì ứng dụng **dừng ngay lúc khởi động** kèm tên biến còn thiếu. Điều này
KHÔNG tự nhiên có: `@ConfigurationProperties` bind `${VAR}` không giải được thành chuỗi nguyên văn
thay vì báo lỗi, nên cần `UnresolvedPlaceholderValidator` ép — xem Javadoc lớp đó.

- API: http://localhost:8080/api/v1
- OpenAPI: http://localhost:8080/api/v1/openapi
- Swagger UI: http://localhost:8080/swagger-ui.html

## Kiểm tra chất lượng

```bash
mvn spotless:apply    # sửa format
mvn verify            # test + kiểm tra format
```

Integration test dùng Testcontainers nên cần Docker. Trên Windows, mỗi phiên Git Bash phải export
`DOCKER_HOST="tcp://127.0.0.1:2375"` trước — lý do đầy đủ trong `CLAUDE.md` ở gốc repo.

Test class phải đặt tên `*Test.java`: `pom.xml` chỉ cấu hình Surefire, file `*IT.java` sẽ **không
bao giờ chạy** mà build vẫn báo thành công.
