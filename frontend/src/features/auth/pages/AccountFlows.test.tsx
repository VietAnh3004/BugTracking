/**
 * Các màn hình tài khoản chạy qua `routes` + `AuthProvider` thật, chỉ giả `fetch`.
 *
 * Mỗi test kiểm cả ĐIỀU HƯỚNG lẫn REQUEST đi ra (đúng endpoint, đúng thân), không chỉ chữ trên
 * màn hình: một trang hiện "Đã gửi" mà không gọi backend vẫn trông y hệt trang đúng.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const HEALTH = { body: { status: 'UP' } };
const NO_GOOGLE = { body: {} };
const REFRESH_TOKEN_KEY = 'datn.refreshToken';

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

/** Thân JSON của lời gọi `fetch` đầu tiên tới URL chứa `fragment`. */
function bodyOf(fetchSpy: ReturnType<typeof mockFetchByUrl>, fragment: string): unknown {
  const call = fetchSpy.mock.calls.find(([input]) => String(input).includes(fragment));
  if (call === undefined) {
    throw new Error(`Không có lời gọi nào tới ${fragment}`);
  }
  const init = call[1];
  return JSON.parse(String(init?.body));
}

function calledWithMethod(
  fetchSpy: ReturnType<typeof mockFetchByUrl>,
  fragment: string,
  method: string,
): boolean {
  return fetchSpy.mock.calls.some(
    ([input, init]) => String(input).includes(fragment) && init?.method === method,
  );
}

describe('Đăng ký', () => {
  it('đăng ký xong hiện "kiểm tra hộp thư" và KHÔNG đăng nhập', async () => {
    mockFetchByUrl({
      '/api/v1/auth/register': { status: 201, body: { email: 'moi@test.local' } },
      '/api/v1/auth/config': NO_GOOGLE,
      '/actuator/health': HEALTH,
    });
    const { router } = renderRoute('/register');

    await userEvent.type(await screen.findByLabelText('Email'), 'moi@test.local');
    await userEvent.type(screen.getByLabelText('Tên hiển thị'), 'Nguoi Moi');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'mat-khau-du-dai-8');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByRole('heading', { name: 'Kiểm tra hộp thư' })).toBeInTheDocument();
    expect(screen.getByText('moi@test.local')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/register');
    expect(window.localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
  });

  /** Trạng thái đạt/chưa đạt của từng dòng, đọc qua `data-met` để không phụ thuộc class màu. */
  function requirementStates(): Record<string, boolean> {
    const list = screen.getByRole('list', { name: 'Yêu cầu mật khẩu' });
    return Object.fromEntries(
      within(list)
        .getAllByRole('listitem')
        .map((item) => [
          item.textContent?.replace(/ \((đã|chưa) đạt\)$/, '') ?? '',
          item.dataset.met === 'true',
        ]),
    );
  }

  it('ghi đủ yêu cầu mật khẩu, yêu cầu nào đạt thì đánh dấu ngay lúc gõ', async () => {
    mockFetchByUrl({ '/api/v1/auth/config': NO_GOOGLE, '/actuator/health': HEALTH });
    renderRoute('/register');

    const password = await screen.findByLabelText('Mật khẩu');
    expect(requirementStates()).toEqual({
      'Tối thiểu 8 ký tự': false,
      'Có ít nhất một chữ cái': false,
      'Có ít nhất một chữ số': false,
    });

    await userEvent.type(password, 'abc');
    expect(requirementStates()).toEqual({
      'Tối thiểu 8 ký tự': false,
      'Có ít nhất một chữ cái': true,
      'Có ít nhất một chữ số': false,
    });

    await userEvent.type(password, 'defgh1');
    expect(requirementStates()).toEqual({
      'Tối thiểu 8 ký tự': true,
      'Có ít nhất một chữ cái': true,
      'Có ít nhất một chữ số': true,
    });
    // Trình đọc màn hình nghe được trạng thái, không chỉ thấy màu.
    expect(screen.getByText('Có ít nhất một chữ số')).toHaveTextContent('(đã đạt)');
  });

  it('mật khẩu thiếu chữ số thì KHÔNG gọi backend, chỉ tô đỏ dòng chưa đạt — không có dòng cảnh báo', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/config': NO_GOOGLE,
      '/actuator/health': HEALTH,
    });
    renderRoute('/register');

    await userEvent.type(await screen.findByLabelText('Email'), 'moi@test.local');
    await userEvent.type(screen.getByLabelText('Tên hiển thị'), 'Nguoi Moi');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'chi-co-chu-cai');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(screen.getByText('Có ít nhất một chữ số').closest('li')).toHaveClass('text-red-600');
    expect(screen.getByText('Có ít nhất một chữ cái').closest('li')).toHaveClass(
      'text-emerald-600',
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Mật khẩu')).toHaveFocus();
    expect(calledWithMethod(fetchSpy, '/register', 'POST')).toBe(false);
  });

  it('backend từ chối mật khẩu thì hiện đúng câu trong fieldErrors', async () => {
    mockFetchByUrl({
      '/api/v1/auth/register': {
        status: 400,
        body: {
          type: 'https://datn.hust.vn/problems/validation-failed',
          title: 'Dữ liệu đầu vào không hợp lệ',
          fieldErrors: { email: 'Email không đúng định dạng' },
        },
      },
      '/api/v1/auth/config': NO_GOOGLE,
      '/actuator/health': HEALTH,
    });
    renderRoute('/register');

    await userEvent.type(await screen.findByLabelText('Email'), 'moi@test.local');
    await userEvent.type(screen.getByLabelText('Tên hiển thị'), 'Nguoi Moi');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'mat-khau-du-dai-8');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email không đúng định dạng');
  });
});

describe('Đăng nhập', () => {
  it('email chưa xác thực thì hiện nút gửi lại, bấm là gọi resend-verification', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/login': {
        status: 403,
        body: {
          type: 'https://datn.hust.vn/problems/email-not-verified',
          detail: 'Email chưa được xác thực.',
        },
      },
      '/api/v1/auth/resend-verification': { status: 204 },
      '/api/v1/auth/config': NO_GOOGLE,
      '/actuator/health': HEALTH,
    });
    renderRoute('/login');

    await userEvent.type(await screen.findByLabelText('Email'), 'chua@test.local');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'mat-khau-du-dai-8');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi lại email xác thực' }));

    expect(await screen.findByText(/Đã gửi lại/)).toBeInTheDocument();
    expect(bodyOf(fetchSpy, '/resend-verification')).toEqual({ email: 'chua@test.local' });
  });

  it('có link "Quên mật khẩu?" sang trang quên mật khẩu', async () => {
    mockFetchByUrl({ '/api/v1/auth/config': NO_GOOGLE, '/actuator/health': HEALTH });
    const { router } = renderRoute('/login');

    await userEvent.click(await screen.findByRole('link', { name: 'Quên mật khẩu?' }));

    expect(router.state.location.pathname).toBe('/forgot-password');
  });

  it('backend chưa cấu hình Google thì không hiện khối Google', async () => {
    mockFetchByUrl({ '/api/v1/auth/config': NO_GOOGLE, '/actuator/health': HEALTH });
    renderRoute('/login');

    await screen.findByRole('heading', { name: 'Đăng nhập' });
    expect(screen.queryByText('hoặc')).not.toBeInTheDocument();
  });
});

describe('Xác thực email', () => {
  it('mở link là gửi token trong fragment lên backend rồi vào thẳng danh sách dự án', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/verify-email': { body: fakeAuthTokens() },
      '/api/v1/projects': { body: { items: [], page: 0, size: 20, total: 0 } },
      '/actuator/health': HEALTH,
    });
    const { router } = renderRoute('/verify-email#token=abc123');

    expect(await screen.findByRole('heading', { name: 'Dự án' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/projects');
    expect(bodyOf(fetchSpy, '/verify-email')).toEqual({ token: 'abc123' });
  });

  it('link hết hạn thì báo lỗi kèm đường quay về đăng nhập', async () => {
    mockFetchByUrl({
      '/api/v1/auth/verify-email': {
        status: 400,
        body: { detail: 'Liên kết không hợp lệ hoặc đã hết hạn. Hãy yêu cầu gửi lại email.' },
      },
      '/actuator/health': HEALTH,
    });
    renderRoute('/verify-email#token=het-han');

    expect(await screen.findByRole('alert')).toHaveTextContent('đã hết hạn');
    expect(screen.getByRole('link', { name: 'Tới trang đăng nhập' })).toBeInTheDocument();
  });

  it('link thiếu token thì không gọi backend', async () => {
    const fetchSpy = mockFetchByUrl({ '/actuator/health': HEALTH });
    renderRoute('/verify-email');

    expect(await screen.findByRole('alert')).toHaveTextContent('thiếu mã');
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/verify-email'))).toBe(
      false,
    );
  });
});

describe('Quên / đặt lại mật khẩu', () => {
  it('quên mật khẩu nói "NẾU email có tài khoản" — không khẳng định đã gửi', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/forgot-password': { status: 204 },
      '/actuator/health': HEALTH,
    });
    renderRoute('/forgot-password');

    await userEvent.type(await screen.findByLabelText('Email'), 'ai-do@test.local');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi link đặt lại' }));

    expect(await screen.findByText(/có tài khoản, một link đặt lại/)).toBeInTheDocument();
    expect(bodyOf(fetchSpy, '/forgot-password')).toEqual({ email: 'ai-do@test.local' });
  });

  it('hai lần nhập không khớp thì báo lỗi và KHÔNG gọi backend', async () => {
    const fetchSpy = mockFetchByUrl({ '/actuator/health': HEALTH });
    renderRoute('/reset-password#token=tk');

    await userEvent.type(await screen.findByLabelText('Mật khẩu mới'), 'mat-khau-moi-1');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'mat-khau-moi-2');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('không khớp');
    expect(calledWithMethod(fetchSpy, '/reset-password', 'POST')).toBe(false);
  });

  it('đặt mật khẩu mới thành công thì mời đăng nhập lại', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/reset-password': { status: 204 },
      '/actuator/health': HEALTH,
    });
    renderRoute('/reset-password#token=tk-dung');

    await userEvent.type(await screen.findByLabelText('Mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }));

    expect(await screen.findByRole('heading', { name: 'Đã đặt mật khẩu mới' })).toBeInTheDocument();
    expect(bodyOf(fetchSpy, '/reset-password')).toEqual({
      token: 'tk-dung',
      newPassword: 'mat-khau-moi-12345',
    });
  });
});

describe('Trang tài khoản', () => {
  function signedIn(user: Partial<ReturnType<typeof fakeAuthTokens>['user']> = {}) {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
    const tokens = fakeAuthTokens();
    return { ...tokens, user: { ...tokens.user, ...user } };
  }

  it('đổi tên hiển thị gọi PATCH /auth/me và cập nhật tên trên thanh trên cùng', async () => {
    const tokens = signedIn();
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/refresh': { body: tokens },
      '/api/v1/auth/me': { body: { ...tokens.user, displayName: 'Ten Moi Tinh' } },
      '/actuator/health': HEALTH,
    });
    renderRoute('/account');

    const input = await screen.findByLabelText('Tên hiển thị');
    await userEvent.clear(input);
    await userEvent.type(input, 'Ten Moi Tinh');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu tên' }));

    expect(await screen.findByText('Đã lưu tên hiển thị.')).toBeInTheDocument();
    expect(calledWithMethod(fetchSpy, '/api/v1/auth/me', 'PATCH')).toBe(true);
    expect(bodyOf(fetchSpy, '/api/v1/auth/me')).toEqual({ displayName: 'Ten Moi Tinh' });
    // Thanh trên cùng đọc từ context — không cập nhật context thì tên cũ vẫn nằm đó.
    expect(screen.getAllByText('Ten Moi Tinh').length).toBeGreaterThanOrEqual(2);
  });

  it('tài khoản chỉ có Google thì "Đặt mật khẩu" không hỏi mật khẩu hiện tại', async () => {
    const tokens = signedIn({ hasPassword: false, googleLinked: true });
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/refresh': { body: tokens },
      '/api/v1/auth/me/password': { body: tokens },
      '/actuator/health': HEALTH,
    });
    renderRoute('/account');

    expect(await screen.findByRole('heading', { name: 'Đặt mật khẩu' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Mật khẩu hiện tại')).not.toBeInTheDocument();
    expect(screen.getByText('Đã liên kết Google')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu' }));

    expect(await screen.findByText('Đã cập nhật mật khẩu.')).toBeInTheDocument();
    expect(bodyOf(fetchSpy, '/me/password')).toEqual({ newPassword: 'mat-khau-moi-12345' });
  });

  it('mật khẩu hiện tại sai thì hiện lỗi của backend, không đăng xuất', async () => {
    const tokens = signedIn();
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: tokens },
      '/api/v1/auth/me/password': {
        status: 400,
        body: {
          type: 'https://datn.hust.vn/problems/wrong-current-password',
          detail: 'Mật khẩu hiện tại không đúng.',
        },
      },
      '/actuator/health': HEALTH,
    });
    const { router } = renderRoute('/account');

    await userEvent.type(await screen.findByLabelText('Mật khẩu hiện tại'), 'sai-roi-123');
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'mat-khau-moi-12345');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Mật khẩu hiện tại không đúng.');
    expect(router.state.location.pathname).toBe('/account');
  });

  it('ảnh Gravatar lỗi (chưa đăng ký) thì hiện chữ cái đầu của tên', async () => {
    const tokens = signedIn({ displayName: 'Nguyen Viet Anh' });
    mockFetchByUrl({ '/api/v1/auth/refresh': { body: tokens }, '/actuator/health': HEALTH });
    const { container } = renderRoute('/account');

    await screen.findByRole('heading', { name: 'Tài khoản' });
    const images = container.querySelectorAll('img');
    expect(images.length).toBeGreaterThan(0);
    images.forEach((img) => fireEvent.error(img));

    await waitFor(() => expect(container.querySelectorAll('img')).toHaveLength(0));
    expect(screen.getAllByText('VA').length).toBeGreaterThan(0);
  });
});
