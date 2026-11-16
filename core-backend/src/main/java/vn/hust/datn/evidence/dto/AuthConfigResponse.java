package vn.hust.datn.evidence.dto;

/**
 * Cấu hình đăng nhập công khai cho frontend.
 *
 * <p>Client ID của Google lấy từ BACKEND chứ không khai riêng cho frontend: một biến
 * {@code GOOGLE_CLIENT_ID} duy nhất, không thể có chuyện frontend hiện nút Google cho một Client ID
 * mà backend không chấp nhận. Client ID là thông tin công khai (nằm sẵn trong trang của mọi web dùng
 * Google Sign-In), không phải bí mật.
 *
 * @param googleClientId vắng mặt = đăng nhập Google đang tắt, frontend ẩn nút
 */
public record AuthConfigResponse(String googleClientId) {}
