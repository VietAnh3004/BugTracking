/**
 * Lời gọi API xác thực và tài khoản. Kiểu lấy từ `api-types.ts` (sinh từ OpenAPI đã commit) nên
 * FE/BE không thể lệch shape mà `tsc` không phát hiện.
 *
 * Các lời gọi CÔNG KHAI (chưa đăng nhập) đều truyền `skipAuthRefresh: true`. Không có nó, một
 * `/auth/login` sai mật khẩu (401) sẽ khiến `apiClient` đi gọi `/auth/refresh`, và bản thân
 * `/auth/refresh` nhận 401 lại gọi tiếp chính nó — đệ quy vô hạn ngay trong màn hình đăng nhập.
 * Ba lời gọi của người ĐÃ đăng nhập (`/auth/me…`) thì không: access token hết hạn giữa chừng vẫn
 * phải được refresh như mọi API khác.
 */

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type LoginRequest = components['schemas']['LoginRequest'];
export type RegisterRequest = components['schemas']['RegisterRequest'];
export type RegisterResponse = components['schemas']['RegisterResponse'];
export type AuthTokens = components['schemas']['AuthTokensResponse'];
export type CurrentUser = components['schemas']['UserResponse'];
export type AuthConfig = components['schemas']['AuthConfigResponse'];
export type ChangePasswordRequest = components['schemas']['ChangePasswordRequest'];

const SKIP_REFRESH = { skipAuthRefresh: true } as const;

export function login(body: LoginRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/login', body, SKIP_REFRESH);
}

/** Không trả token: phải mở link trong email xác thực trước đã. */
export function register(body: RegisterRequest): Promise<RegisterResponse> {
  return apiClient.post<RegisterResponse>('/api/v1/auth/register', body, SKIP_REFRESH);
}

export function refresh(refreshToken: string): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/refresh', { refreshToken }, SKIP_REFRESH);
}

/**
 * Thu hồi CẢ HỌ refresh token phía server — không thể làm thuần client bằng cách xoá localStorage:
 * token đã phát vẫn hợp lệ cho tới khi hết hạn nếu server không biết người dùng đã đăng xuất.
 */
export function logout(refreshToken: string): Promise<void> {
  return apiClient.post<void>('/api/v1/auth/logout', { refreshToken }, SKIP_REFRESH);
}

/** Cấu hình công khai — hiện chỉ có Client ID của Google (vắng mặt = ẩn nút Google). */
export function fetchAuthConfig(): Promise<AuthConfig> {
  return apiClient.get<AuthConfig>('/api/v1/auth/config', SKIP_REFRESH);
}

/** Dùng link xác thực; thành công thì backend trả luôn một phiên đăng nhập. */
export function verifyEmail(token: string): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/verify-email', { token }, SKIP_REFRESH);
}

/** Luôn 204 dù email có tài khoản hay không — backend cố ý không tiết lộ. */
export function resendVerification(email: string): Promise<void> {
  return apiClient.post<void>('/api/v1/auth/resend-verification', { email }, SKIP_REFRESH);
}

/** Luôn 204 dù email có tài khoản hay không — backend cố ý không tiết lộ. */
export function forgotPassword(email: string): Promise<void> {
  return apiClient.post<void>('/api/v1/auth/forgot-password', { email }, SKIP_REFRESH);
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return apiClient.post<void>('/api/v1/auth/reset-password', { token, newPassword }, SKIP_REFRESH);
}

/** @param credential Google ID token do nút "Đăng nhập bằng Google" trả về */
export function loginWithGoogle(credential: string): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/google', { credential }, SKIP_REFRESH);
}

export function updateProfile(displayName: string): Promise<CurrentUser> {
  return apiClient.patch<CurrentUser>('/api/v1/auth/me', { displayName });
}

/** Mọi phiên cũ bị đóng; response là phiên MỚI thay cho phiên đang dùng. */
export function changePassword(body: ChangePasswordRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/me/password', body);
}
