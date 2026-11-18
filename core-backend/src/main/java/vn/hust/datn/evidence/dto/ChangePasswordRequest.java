package vn.hust.datn.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param currentPassword bắt buộc khi tài khoản ĐÃ có mật khẩu; tài khoản chỉ có Google thì bỏ
 *     trống — đó là lần ĐẶT mật khẩu đầu tiên. Cố ý KHÔNG gắn {@link NewPassword}: mật khẩu đặt
 *     trước khi có quy tắc chữ số vẫn phải dùng được để đổi sang mật khẩu mới.
 */
public record ChangePasswordRequest(
        String currentPassword,
        @NewPassword @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, description = NewPassword.DESCRIPTION) String newPassword) {}
