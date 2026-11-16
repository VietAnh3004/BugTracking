package vn.hust.datn.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Đăng ký xong KHÔNG đăng nhập luôn: phải mở link trong email trước (V13). Không trả token nào.
 *
 * @param email địa chỉ vừa được gửi link xác thực — để màn hình nói rõ "kiểm hộp thư X"
 */
public record RegisterResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {}
