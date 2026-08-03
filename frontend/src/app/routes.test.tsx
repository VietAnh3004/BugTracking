/**
 * Test định tuyến chạy trên chính mảng `routes` thật (xem `src/test/renderRoute.tsx`).
 *
 * Từ D5, các route nghiệp vụ nằm sau `ProtectedRoute` nên mỗi test phải dựng sẵn một phiên đăng
 * nhập hợp lệ. Test riêng cho hành vi của guard nằm ở `features/auth/components/ProtectedRoute.test.tsx`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor } from '@testing-library/react';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const REFRESH_TOKEN_KEY = 'datn.refreshToken';

beforeEach(() => {
  window.localStorage.clear();
  window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
  mockFetchByUrl({
    '/api/v1/auth/refresh': { body: fakeAuthTokens() },
    '/api/v1/projects': { body: { items: [], page: 0, size: 20, total: 0 } },
    '/actuator/health': { body: { status: 'UP' } },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe('định tuyến', () => {
  it('mở /projects hiển thị trang danh sách dự án', async () => {
    renderRoute('/projects');

    expect(await screen.findByRole('heading', { name: 'Dự án' })).toBeInTheDocument();
  });

  it('trang chủ điều hướng sang /projects thay vì để trống', async () => {
    renderRoute('/');

    expect(await screen.findByRole('heading', { name: 'Dự án' })).toBeInTheDocument();
  });

  it('đường dẫn không tồn tại hiển thị trang 404, không phải màn hình trắng', async () => {
    renderRoute('/duong-dan-khong-ton-tai');

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy trang' }),
    ).toBeInTheDocument();
  });

  /**
   * Khung ứng dụng phải còn nguyên ở trang 404: đây là điểm khác nhau giữa "route con không khớp"
   * (người dùng vẫn ở trong ứng dụng, có đường quay lại) và "cả ứng dụng sập".
   */
  it('vẫn giữ khung ứng dụng khi ở trang 404', async () => {
    renderRoute('/duong-dan-khong-ton-tai');

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /Nền tảng phân tích chương trình/ }),
      ).toBeInTheDocument();
    });
  });

  /**
   * Deep link là yêu cầu thật của đồ án: URL tới một dòng mã cụ thể (`.../files/...#L42`) phải mở
   * được ở tab mới. Test này canh mắt xích đầu tiên — router xử lý được đường dẫn nhiều đoạn mà
   * không cần đi qua trang chủ trước.
   */
  it('mở thẳng một URL nhiều đoạn không làm sập router', async () => {
    renderRoute('/projects/abc/snapshots/def');

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy trang' }),
    ).toBeInTheDocument();
  });

  it('hiển thị tên người dùng và nút đăng xuất khi đã đăng nhập', async () => {
    renderRoute('/projects');

    expect(await screen.findByText('Nguoi Dung Test')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument();
  });
});
