/**
 * Lời gọi API xác thực. Kiểu lấy từ `api-types.ts` (sinh từ OpenAPI đã commit) nên FE/BE không thể
 * lệch shape mà `tsc` không phát hiện.
 */

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type LoginRequest = components['schemas']['LoginRequest'];
export type RegisterRequest = components['schemas']['RegisterRequest'];
export type AuthTokens = components['schemas']['AuthTokensResponse'];
export type CurrentUser = components['schemas']['UserResponse'];

export function login(body: LoginRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/login', body);
}

export function register(body: RegisterRequest): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/register', body);
}

export function refresh(refreshToken: string): Promise<AuthTokens> {
  return apiClient.post<AuthTokens>('/api/v1/auth/refresh', { refreshToken });
}

/**
 * Thu hồi CẢ HỌ refresh token phía server — không thể làm thuần client bằng cách xoá localStorage:
 * token đã phát vẫn hợp lệ cho tới khi hết hạn nếu server không biết người dùng đã đăng xuất.
 */
export function logout(refreshToken: string): Promise<void> {
  return apiClient.post<void>('/api/v1/auth/logout', { refreshToken });
}
