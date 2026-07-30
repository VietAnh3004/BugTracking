package vn.hust.datn.probe;

import java.util.UUID;

import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chỉ tồn tại trong test scope. Nằm ngoài package {@code vn.hust.datn.evidence} nên {@code
 * @SpringBootApplication} KHÔNG quét được nó — nếu không, 3 endpoint {@code _probe} này sẽ rò vào
 * spec OpenAPI công khai (đã xảy ra thật, xem ADR liên quan tuần 5-7). Test cần nó phải nạp tường
 * minh bằng {@code @Import(ExceptionHandlerProbeController.class)}.
 *
 * <p>Mục đích duy nhất: kích hoạt từng nhánh của {@link
 * vn.hust.datn.evidence.exception.GlobalExceptionHandler} bằng request thật qua MockMvc, tách
 * biệt khỏi controller nghiệp vụ — không phải chờ có {@code ProjectController} mới test được xử
 * lý lỗi chung.
 */
@RestController
@Validated
public class ExceptionHandlerProbeController {

    /** GET với path variable kiểu UUID — gọi bằng chuỗi không phải UUID để kích hoạt type mismatch. */
    @GetMapping("/api/v1/_probe/by-id/{id}")
    public String byId(@org.springframework.web.bind.annotation.PathVariable UUID id) {
        return id.toString();
    }

    /** POST nhận body JSON — gửi body hỏng cú pháp để kích hoạt not-readable. */
    @PostMapping("/api/v1/_probe/echo")
    public EchoBody echo(@RequestBody EchoBody body) {
        return body;
    }

    /** RequestParam có ràng buộc Bean Validation ở mức method (khác `@Valid` trên body). */
    @GetMapping("/api/v1/_probe/validated")
    public String validated(@RequestParam @Size(min = 3, message = "phải có ít nhất 3 ký tự") String value) {
        return value;
    }

    public record EchoBody(String text) {}
}
