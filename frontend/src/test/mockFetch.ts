/**
 * Giả lập `fetch` theo TỪNG endpoint thay vì trả một response duy nhất cho mọi lời gọi.
 *
 * Cần thiết vì một lần render route đã gọi ít nhất hai API khác nhau (health của `BackendStatus`,
 * `/auth/refresh` của `AuthProvider`, rồi API nghiệp vụ của trang). Dùng `mockResolvedValue` chung
 * sẽ khiến `/auth/refresh` nhận về payload của health check và test "thành công" vì lý do sai.
 */

import { vi } from 'vitest';

export interface MockResponse {
  status?: number;
  body?: unknown;
  /** Promise không bao giờ resolve — mô phỏng request đang bay, để quan sát trạng thái chờ. */
  neverResolves?: boolean;
}

/**
 * @param routes ánh xạ "một đoạn của URL" → response. Đoạn khớp ĐẦU TIÊN được dùng, nên khai
 *   đường dẫn cụ thể trước đường dẫn tổng quát.
 */
export function mockFetchByUrl(routes: Record<string, MockResponse>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();

    for (const [fragment, response] of Object.entries(routes)) {
      if (!url.includes(fragment)) {
        continue;
      }
      if (response.neverResolves === true) {
        return new Promise<Response>(() => {});
      }
      const status = response.status ?? 200;
      return Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(response.body ?? {}),
      } as Response);
    }

    // Không khai thì hỏng ồn ào: một endpoint bị gọi ngoài dự kiến phải lộ ra trong log test, chứ
    // không lặng lẽ nhận response rỗng rồi làm test pass/fail vì lý do không liên quan.
    return Promise.reject(new Error(`mockFetchByUrl: chưa khai response cho URL "${url}"`));
  });
}

/** Bộ token giả đúng shape `AuthTokensResponse` của backend. */
export function fakeAuthTokens(overrides: { displayName?: string } = {}) {
  return {
    accessToken: 'access-token-gia',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    refreshToken: 'refresh-token-gia',
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      email: 'nguoidung@test.local',
      displayName: overrides.displayName ?? 'Nguoi Dung Test',
      role: 'USER' as const,
      createdAt: '2026-01-01T00:00:00Z',
    },
  };
}
