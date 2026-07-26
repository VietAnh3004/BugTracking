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

## Chạy local

```bash
cp .env.example .env      # điền giá trị thật
mvn spring-boot:run
```

Thiếu biến bắt buộc thì ứng dụng **dừng ngay lúc khởi động** kèm tên biến còn thiếu, thay vì
chạy rồi lỗi giữa chừng.

- API: http://localhost:8080/api/v1
- OpenAPI: http://localhost:8080/api/v1/openapi
- Swagger UI: http://localhost:8080/swagger-ui.html

## Kiểm tra chất lượng

```bash
mvn spotless:apply    # sửa format
mvn verify            # test + kiểm tra format
```
