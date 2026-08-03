/**
 * Lời gọi API xác thực. Kiểu lấy từ `api-types.ts` (sinh từ OpenAPI đã commit) nên FE/BE không thể
 * lệch shape mà `tsc` không phát hiện.
 *
 * MỌI lời gọi ở đây đều truyền `skipAuthRefresh: true`. Không có nó, một `/auth/login` sai mật
 * khẩu (401) sẽ khiến `apiClient` đi gọi `/auth/refresh`, và bản thân `/auth/refresh` nhận 401 lại
 * gọi tiếp chính nó — đệ quy vô hạn ngay trong màn hình đăng nhập.
 */

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type LoginRequest = components['schemas']['LoginRequest'];
export type RegisterRequest = components['schemas']['RegisterRequest'];
export type AuthTokens = components['schemas']['AuthTokensResponse'];
export type CurrentUser = components['schemas']['UserResponse'];

const SKIP_REFRESH = { skipAuthRefresh: true } as const;

export function login(body: LoginRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/login', body, SKIP_REFRESH);
}

export function register(body: RegisterRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/register', body, SKIP_REFRESH);
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
