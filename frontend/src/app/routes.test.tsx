/**
 * Test định tuyến chạy trên chính mảng `routes` thật (xem `src/test/renderRoute.tsx`).
 *
 * Mọi test đều stub `fetch`: `RootLayout` gắn `BackendStatus`, thứ gọi API health ngay khi mount.
 * Không stub thì test vừa chậm vừa phụ thuộc việc backend có đang chạy trên máy hay không.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor } from '@testing-library/react';

import { renderRoute } from '@/test/renderRoute';

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve({ status: 'UP' }),
  } as Response);
});

afterEach(() => {
  vi.restoreAllMocks();
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
});
