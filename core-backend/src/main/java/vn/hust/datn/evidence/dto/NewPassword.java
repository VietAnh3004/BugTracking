package vn.hust.datn.evidence.dto;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Quy tắc cho MỌI mật khẩu người dùng tự đặt: đăng ký, đặt lại qua email, đổi/đặt trong trang tài
 * khoản. Gom vào một chỗ để ba DTO không lệch nhau — lệch là có đường vòng: bị chặn ở đăng ký thì
 * vào "quên mật khẩu" đặt mật khẩu yếu.
 *
 * <p>Frontend hiển thị cùng danh sách này trong {@code frontend/src/features/auth/lib/passwordRules.ts}
 * để người dùng thấy từng yêu cầu đạt hay chưa lúc đang gõ. Đổi quy tắc thì đổi CẢ HAI chỗ; phía
 * frontend chỉ là hiển thị, chốt chặn thật nằm ở đây.
 *
 * <p>Không áp lên mật khẩu ĐĂNG NHẬP hay mật khẩu hiện tại: tài khoản tạo trước khi có quy tắc
 * chữ số vẫn phải đăng nhập được bằng mật khẩu cũ.
 *
 * <p>Không giới hạn độ dài tối đa: BCrypt tự cắt ở 72 byte. Chữ cái tính theo Unicode
 * ({@code \p{L}}) nên "mậtkhẩu1" hợp lệ; chữ số chỉ tính 0-9, khớp với regex của frontend.
 *
 * <p>Annotation ghép (composed constraint): mỗi ràng buộc thành phần báo lỗi bằng thông điệp riêng,
 * nên người dùng biết chính xác thiếu gì thay vì một câu "không hợp lệ" chung chung.
 */
@NotBlank(message = "Mật khẩu không được để trống") @Size(min = 8, message = "Mật khẩu phải dài tối thiểu 8 ký tự") @Pattern(regexp = "(?s).*\\p{L}.*", message = "Mật khẩu phải có ít nhất một chữ cái")
@Pattern(regexp = "(?s).*[0-9].*", message = "Mật khẩu phải có ít nhất một chữ số")
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
public @interface NewPassword {

    /**
     * Mô tả cho OpenAPI. springdoc KHÔNG đọc ràng buộc nằm bên trong annotation ghép, nên DTO phải
     * tự khai {@code @Schema(minLength = 8, description = DESCRIPTION)} — thiếu là spec mất cả
     * {@code minLength} lẫn quy tắc chữ cái/chữ số.
     */
    String DESCRIPTION = "Tối thiểu 8 ký tự, có ít nhất một chữ cái và một chữ số.";

    String message() default "Mật khẩu không đạt yêu cầu";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
