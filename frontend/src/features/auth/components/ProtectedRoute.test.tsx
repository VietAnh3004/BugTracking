/**
 * Test route guard, chạy qua đúng `AuthProvider` thật và mảng `routes` thật.
 *
 * Test đầu tiên là quan trọng nhất của cả D5: nó canh lỗi "F5 ở deep link luôn nhảy về trang đăng
 * nhập" — lỗi chỉ xuất hiện khi người dùng ĐÃ đăng nhập và tải lại trang, nên rất dễ lọt qua khâu
 * thử tay (lập trình viên thường bấm qua lại trong ứng dụng chứ ít khi F5).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor } from '@testing-library/react';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const REFRESH_TOKEN_KEY = 'datn.refreshToken';
const HEALTH_RESPONSE = { body: { status: 'UP' } };

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe('ProtectedRoute', () => {
  /**
   * Assert vào ĐƯỜNG DẪN, không phải vào DOM. Bản test đầu tiên của commit này chỉ kiểm "có
   * `role="status"`" và "không có tiêu đề Đăng nhập" — nó XANH GIẢ: khi cố tình phá
   * `ProtectedRoute` thành `<Navigate to="/login">` lúc bootstrapping, người dùng bị đá sang
   * `/login`, nhưng ở đó `PublicOnlyRoute` cũng đang `bootstrapping` nên CŨNG hiện spinner và
   * CŨNG chưa hiện form — hai guard che lỗi cho nhau, test vẫn pass. Đã kiểm chứng bằng cách phá
   * code thật: bản assert dưới đây đỏ đúng như mong đợi, bản cũ thì không.
   */
  it('đang bootstrapping thì chờ tại chỗ, KHÔNG đá về trang đăng nhập', async () => {
    // Có refresh token nhưng lời gọi /auth/refresh chưa trả về: đúng khoảnh khắc ngay sau khi F5.
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
    mockFetchByUrl({
      '/api/v1/auth/refresh': { neverResolves: true },
      '/actuator/health': HEALTH_RESPONSE,
    });

    const { router } = renderRoute('/projects');

    expect(await screen.findByRole('status')).toBeInTheDocument();
    // Điều kiện thật sự quan trọng: vẫn đứng nguyên ở đường dẫn ban đầu, chưa mất deep link.
    expect(router.state.location.pathname).toBe('/projects');
  });

  it('bootstrap xong và token còn hiệu lực thì vào thẳng trang được bảo vệ', async () => {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: fakeAuthTokens() },
      '/api/v1/projects': { body: { items: [], page: 0, size: 20, total: 0 } },
      '/actuator/health': HEALTH_RESPONSE,
    });

    renderRoute('/projects');

    expect(await screen.findByRole('heading', { name: 'Dự án' })).toBeInTheDocument();
  });

  it('không có refresh token thì chuyển sang trang đăng nhập', async () => {
    mockFetchByUrl({ '/actuator/health': HEALTH_RESPONSE });

    renderRoute('/projects');

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('refresh token đã bị thu hồi thì dọn sạch phiên và về trang đăng nhập', async () => {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-da-bi-thu-hoi');
    mockFetchByUrl({
      '/api/v1/auth/refresh': { status: 401, body: { type: 'refresh-token-reused' } },
      '/actuator/health': HEALTH_RESPONSE,
    });

    renderRoute('/projects');

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    // Token hỏng phải bị xoá, nếu không mỗi lần tải trang lại gọi /auth/refresh một cách vô ích.
    await waitFor(() => {
      expect(window.localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    });
  });
});

describe('PublicOnlyRoute', () => {
  it('đã đăng nhập mà mở /login thì chuyển về danh sách dự án', async () => {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: fakeAuthTokens() },
      '/api/v1/projects': { body: { items: [], page: 0, size: 20, total: 0 } },
      '/actuator/health': HEALTH_RESPONSE,
    });

    renderRoute('/login');

    expect(await screen.findByRole('heading', { name: 'Dự án' })).toBeInTheDocument();
  });

  /**
   * Nếu guard này coi `bootstrapping` là "chưa đăng nhập", nó cho hiện form đăng nhập ngay, rồi
   * bootstrap kết thúc và đẩy sang trang khác — một lần nhấp nháy điều hướng. Test bắt đúng
   * khoảnh khắc giữa chừng đó.
   */
  it('đang bootstrapping thì chưa hiện form đăng nhập', async () => {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
    mockFetchByUrl({
      '/api/v1/auth/refresh': { neverResolves: true },
      '/actuator/health': HEALTH_RESPONSE,
    });

    renderRoute('/login');

    expect(await screen.findByRole('status')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Đăng nhập' })).not.toBeInTheDocument();
  });
});
