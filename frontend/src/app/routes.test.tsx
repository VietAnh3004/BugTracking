/**
 * Test định tuyến chạy trên chính mảng `routes` thật (xem `src/test/renderRoute.tsx`).
 *
 * Từ D5, các route nghiệp vụ nằm sau `ProtectedRoute` nên mỗi test phải dựng sẵn một phiên đăng
 * nhập hợp lệ. Test riêng cho hành vi của guard nằm ở `features/auth/components/ProtectedRoute.test.tsx`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

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

  /**
   * Lối vào hội thoại là một deep link mang sẵn `?focus=` từ Evidence Explorer. Test canh việc
   * route khớp và trang dựng được — chứ không kiểm nội dung hội thoại, việc đó thuộc test của
   * chính các component.
   */
  it('mở được trang hỏi Agent kèm cảnh báo đang xét trên query string', async () => {
    renderRoute('/snapshots/snap-1/runs/run-1/chat?focus=EV-001A');

    expect(await screen.findByRole('heading', { name: 'Hỏi Agent' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Phiên mới về EV-001A/ })).toBeInTheDocument();
  });

  it('hiển thị tên người dùng khi đã đăng nhập; đăng xuất nằm trong menu, không bày sẵn', async () => {
    renderRoute('/projects');

    expect(await screen.findByText('Nguoi Dung Test')).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Đăng xuất' })).not.toBeInTheDocument();
  });
});

describe('/styleguide', () => {
  /**
   * Trang kiểm giao diện phải dựng được mà KHÔNG cần đăng nhập và không gọi API nào — nó là chỗ mở
   * ra sau mỗi lần đổi token để soi toàn bộ component, kể cả khi backend chưa chạy.
   */
  it('dựng được toàn bộ component mà không cần đăng nhập', async () => {
    window.localStorage.clear();
    renderRoute('/styleguide');

    expect(await screen.findByRole('heading', { name: 'Styleguide' })).toBeInTheDocument();
    // Dữ liệu mẫu là cảnh báo thật của vulnshop, hiện qua đúng WarningTable của ứng dụng.
    expect(screen.getByRole('link', { name: /UserRepository\.java:35/ })).toBeInTheDocument();
    expect(screen.getAllByRole('status').length).toBeGreaterThan(0);
  });
});

describe('menu tài khoản', () => {
  async function openMenu() {
    const trigger = await screen.findByRole('button', { name: 'Tài khoản: Nguoi Dung Test' });
    await userEvent.click(trigger);
    return trigger;
  }

  it('bấm vào tên KHÔNG chuyển trang mà mở hai lựa chọn', async () => {
    const { router } = renderRoute('/projects');

    const trigger = await openMenu();

    expect(router.state.location.pathname).toBe('/projects');
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    const menu = screen.getByRole('menu', { name: 'Tài khoản' });
    expect(
      within(menu)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['Cài đặt tài khoản', 'Đăng xuất']);
    // Mở ra thì focus nằm ở mục đầu, bàn phím đi tiếp được ngay.
    expect(screen.getByRole('menuitem', { name: 'Cài đặt tài khoản' })).toHaveFocus();
  });

  it('"Cài đặt tài khoản" sang /account và đóng menu', async () => {
    const { router } = renderRoute('/projects');

    await openMenu();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cài đặt tài khoản' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/account'));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('"Đăng xuất" gọi logout và đưa về trang đăng nhập', async () => {
    const { router } = renderRoute('/projects');

    await openMenu();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Đăng xuất' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(window.localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
  });

  it('↓ chuyển mục, Esc đóng menu và trả focus về nút', async () => {
    renderRoute('/projects');

    const trigger = await openMenu();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Đăng xuất' })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Cài đặt tài khoản' })).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('bấm ra ngoài thì đóng menu', async () => {
    renderRoute('/projects');

    await openMenu();
    await userEvent.click(screen.getByRole('heading', { name: 'Dự án' }));

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
