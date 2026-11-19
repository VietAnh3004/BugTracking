/**
 * Khu vực quản trị qua `routes` + `AuthProvider` thật, chỉ giả `fetch`. Mỗi test kiểm cả HIỂN THỊ lẫn
 * REQUEST đi ra: nút "Xoá" hiện đúng mà không gọi đúng endpoint thì vẫn là nút hỏng.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const REFRESH_TOKEN_KEY = 'datn.refreshToken';
const HEALTH = { body: { status: 'UP' } };

const USERS = [
  {
    id: 'u-admin',
    email: 'ADMIN-TEST',
    displayName: 'Quản trị viên',
    role: 'ADMIN',
    emailVerified: true,
    hasPassword: true,
    googleLinked: false,
    createdAt: '2026-11-01T00:00:00Z',
    ownedProjectCount: 0,
    conversationCount: 0,
  },
  {
    id: 'u-1',
    email: 'sinhvien@test.local',
    displayName: 'Sinh Vien',
    role: 'USER',
    emailVerified: true,
    hasPassword: false,
    googleLinked: true,
    createdAt: '2026-11-10T00:00:00Z',
    ownedProjectCount: 3,
    conversationCount: 7,
  },
];

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

function signedInAs(role: 'USER' | 'ADMIN') {
  window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
  return fakeAuthTokens({ role });
}

function deleteCalls(fetchSpy: ReturnType<typeof mockFetchByUrl>) {
  return fetchSpy.mock.calls.filter(
    ([input, init]) => String(input).includes('/admin/users/') && init?.method === 'DELETE',
  );
}

describe('guard khu vực quản trị', () => {
  it('người chưa đăng nhập vào /admin bị đưa tới /admin/login, không phải /login', async () => {
    mockFetchByUrl({ '/actuator/health': HEALTH });
    const { router } = renderRoute('/admin');

    await screen.findByRole('heading', { name: 'Quản trị' });
    expect(router.state.location.pathname).toBe('/admin/login');
  });

  it('người dùng thường vào /admin thấy 404 như đường dẫn không tồn tại', async () => {
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: signedInAs('USER') },
      '/actuator/health': HEALTH,
    });
    renderRoute('/admin');

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy trang' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Quản lý tài khoản')).not.toBeInTheDocument();
  });

  it('người dùng thường KHÔNG gọi API quản trị', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/refresh': { body: signedInAs('USER') },
      '/actuator/health': HEALTH,
    });
    renderRoute('/admin');
    await screen.findByRole('heading', { name: 'Không tìm thấy trang' });

    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/api/v1/admin'))).toBe(
      false,
    );
  });
});

describe('đăng nhập quản trị', () => {
  it('dùng ô văn bản (không phải email) và đăng nhập qua /auth/login', async () => {
    const tokens = fakeAuthTokens({ role: 'ADMIN', email: 'ADMIN-TEST' });
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/login': { body: tokens },
      '/api/v1/admin/users': { body: USERS },
      '/actuator/health': HEALTH,
    });
    const { router } = renderRoute('/admin/login');

    const username = await screen.findByLabelText('Tên đăng nhập');
    expect(username).toHaveAttribute('type', 'text');
    await userEvent.type(username, 'ADMIN-TEST');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'mat-khau-bat-ky');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'));
    const loginCall = fetchSpy.mock.calls.find(([input]) => String(input).includes('/auth/login'));
    expect(JSON.parse(String(loginCall?.[1]?.body))).toEqual({
      email: 'ADMIN-TEST',
      password: 'mat-khau-bat-ky',
    });
  });

  it('sai thông tin thì báo lỗi chung, không nói sai ở đâu', async () => {
    mockFetchByUrl({
      '/api/v1/auth/login': { status: 401, body: { title: 'Unauthorized' } },
      '/actuator/health': HEALTH,
    });
    renderRoute('/admin/login');

    await userEvent.type(await screen.findByLabelText('Tên đăng nhập'), 'ai-do');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'sai');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Tên đăng nhập hoặc mật khẩu không đúng.',
    );
  });
});

describe('trang quản lý tài khoản', () => {
  function renderAdmin(extra: Parameters<typeof mockFetchByUrl>[0] = {}) {
    // `extra` ĐỨNG TRƯỚC: mockFetchByUrl khớp theo thứ tự khai báo và theo `includes`, nên
    // '/admin/users/u-1' phải đứng trước '/admin/users' — nếu không, DELETE bị khớp nhầm vào mục
    // danh sách (200) và test xoá xanh dù không hề kiểm câu trả lời của DELETE.
    const fetchSpy = mockFetchByUrl({
      ...extra,
      '/api/v1/auth/refresh': { body: signedInAs('ADMIN') },
      '/api/v1/admin/users': { body: USERS },
      '/actuator/health': HEALTH,
    });
    return { fetchSpy, ...renderRoute('/admin') };
  }

  it('hiện danh sách kèm số dự án và hội thoại', async () => {
    renderAdmin();

    const row = (await screen.findByText('sinhvien@test.local')).closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText('Google')).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText('3')).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText('7')).toBeInTheDocument();
  });

  it('tài khoản admin không có nút Xoá', async () => {
    renderAdmin();
    await screen.findByText('sinhvien@test.local');

    expect(
      screen.getByRole('button', { name: 'Xoá tài khoản sinhvien@test.local' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Xoá tài khoản ADMIN-TEST' }),
    ).not.toBeInTheDocument();
  });

  it('khung xác nhận liệt kê thứ sẽ mất; nút xoá chỉ bật khi gõ ĐÚNG email', async () => {
    const { fetchSpy } = renderAdmin();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Xoá tài khoản sinhvien@test.local' }),
    );

    expect(screen.getByText(/Không thể hoàn tác/)).toBeInTheDocument();
    expect(screen.getByText(/nhãn ground truth/)).toBeInTheDocument();
    const confirmButton = screen.getByRole('button', { name: 'Xoá vĩnh viễn' });
    expect(confirmButton).toBeDisabled();

    const input = screen.getByLabelText('Gõ lại email của tài khoản để xác nhận');
    await userEvent.type(input, 'sinhvien@test.loca');
    expect(confirmButton).toBeDisabled();
    await userEvent.type(input, 'l');
    expect(confirmButton).toBeEnabled();
    expect(deleteCalls(fetchSpy)).toHaveLength(0);
  });

  it('xác nhận xong thì gọi DELETE đúng tài khoản rồi tải lại danh sách', async () => {
    const { fetchSpy } = renderAdmin({ '/api/v1/admin/users/u-1': { status: 204 } });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Xoá tài khoản sinhvien@test.local' }),
    );
    await userEvent.type(
      screen.getByLabelText('Gõ lại email của tài khoản để xác nhận'),
      'sinhvien@test.local',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Xoá vĩnh viễn' }));

    await waitFor(() => expect(deleteCalls(fetchSpy)).toHaveLength(1));
    expect(String(deleteCalls(fetchSpy)[0]?.[0])).toContain('/api/v1/admin/users/u-1');
    // Khung xác nhận đóng lại sau khi xoá xong.
    await waitFor(() => expect(screen.queryByText(/Không thể hoàn tác/)).not.toBeInTheDocument());
  });

  it('backend từ chối thì hiện đúng lý do và giữ khung xác nhận', async () => {
    renderAdmin({
      '/api/v1/admin/users/u-1': {
        status: 409,
        body: {
          title: 'Thao tác quản trị không được phép',
          detail: 'Không thể xoá tài khoản quản trị',
        },
      },
    });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Xoá tài khoản sinhvien@test.local' }),
    );
    await userEvent.type(
      screen.getByLabelText('Gõ lại email của tài khoản để xác nhận'),
      'sinhvien@test.local',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Xoá vĩnh viễn' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể xoá tài khoản quản trị');
    expect(screen.getByRole('button', { name: 'Xoá vĩnh viễn' })).toBeInTheDocument();
  });

  it('gõ vào ô tìm kiếm thì gọi lại với q (sau khi ngừng gõ)', async () => {
    const { fetchSpy } = renderAdmin();
    await screen.findByText('sinhvien@test.local');

    await userEvent.type(screen.getByLabelText('Tìm tài khoản theo email hoặc tên'), 'sinh');

    await waitFor(() =>
      expect(
        fetchSpy.mock.calls.some(([input]) => String(input).includes('/admin/users?q=sinh')),
      ).toBe(true),
    );
  });
});

const PROJECTS = [
  {
    id: 'p-free',
    name: 'du-an-free',
    tier: 'FREE',
    ownerId: 'u-1',
    ownerEmail: 'sinhvien@test.local',
    ownerDisplayName: 'Sinh Vien',
    memberCount: 2,
    snapshotCount: 4,
    conversationCount: 9,
    createdAt: '2026-11-10T00:00:00Z',
  },
  {
    id: 'p-premium',
    name: 'du-an-premium',
    tier: 'PREMIUM',
    ownerId: 'u-1',
    ownerEmail: 'sinhvien@test.local',
    ownerDisplayName: 'Sinh Vien',
    memberCount: 0,
    snapshotCount: 1,
    conversationCount: 0,
    createdAt: '2026-11-11T00:00:00Z',
  },
];

function tierCalls(fetchSpy: ReturnType<typeof mockFetchByUrl>) {
  return fetchSpy.mock.calls.filter(
    ([input, init]) => String(input).includes('/admin/projects/') && init?.method === 'PATCH',
  );
}

describe('trang quản lý dự án (Free/Premium)', () => {
  function renderProjects(extra: Parameters<typeof mockFetchByUrl>[0] = {}) {
    // `extra` đứng trước vì mockFetchByUrl khớp theo thứ tự khai báo (xem renderAdmin ở trên).
    const fetchSpy = mockFetchByUrl({
      ...extra,
      '/api/v1/auth/refresh': { body: signedInAs('ADMIN') },
      '/api/v1/admin/projects': { body: PROJECTS },
      '/api/v1/admin/users': { body: USERS },
      '/actuator/health': HEALTH,
    });
    return { fetchSpy, ...renderRoute('/admin/projects') };
  }

  it('hiện hạng, chủ dự án và số đếm của từng dự án', async () => {
    renderProjects();

    const free = (await screen.findByText('du-an-free')).closest('tr') as HTMLElement;
    expect(within(free).getByText('Free')).toBeInTheDocument();
    expect(within(free).getByText('sinhvien@test.local')).toBeInTheDocument();
    expect(within(free).getByText('9')).toBeInTheDocument();
    const premium = screen.getByText('du-an-premium').closest('tr') as HTMLElement;
    expect(within(premium).getByText('Premium')).toBeInTheDocument();
  });

  it('có thanh tab chuyển giữa Tài khoản và Dự án', async () => {
    renderProjects();
    await screen.findByText('du-an-free');

    const nav = screen.getByRole('navigation', { name: 'Mục quản trị' });
    expect(within(nav).getByRole('link', { name: 'Tài khoản' })).toHaveAttribute('href', '/admin');
    expect(within(nav).getByRole('link', { name: 'Dự án' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('nâng dự án Free lên Premium gọi đúng PATCH với tier PREMIUM', async () => {
    const { fetchSpy } = renderProjects({
      '/api/v1/admin/projects/p-free': { body: { ...PROJECTS[0], tier: 'PREMIUM' } },
    });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Nâng lên Premium: du-an-free' }),
    );

    await waitFor(() => expect(tierCalls(fetchSpy)).toHaveLength(1));
    const [url, init] = tierCalls(fetchSpy)[0] ?? [];
    expect(String(url)).toContain('/api/v1/admin/projects/p-free');
    expect(JSON.parse(String(init?.body))).toEqual({ tier: 'PREMIUM' });
  });

  it('hạ dự án Premium về Free gọi PATCH với tier FREE', async () => {
    const { fetchSpy } = renderProjects({
      '/api/v1/admin/projects/p-premium': { body: { ...PROJECTS[1], tier: 'FREE' } },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Hạ về Free: du-an-premium' }));

    await waitFor(() => expect(tierCalls(fetchSpy)).toHaveLength(1));
    expect(JSON.parse(String(tierCalls(fetchSpy)[0]?.[1]?.body))).toEqual({ tier: 'FREE' });
  });

  it('backend từ chối thì hiện đúng lý do', async () => {
    renderProjects({
      '/api/v1/admin/projects/p-free': {
        status: 404,
        body: { title: 'Không tìm thấy', detail: 'Dự án không còn tồn tại' },
      },
    });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Nâng lên Premium: du-an-free' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Dự án không còn tồn tại');
  });

  it('người dùng thường vào /admin/projects thấy 404 và không gọi API quản trị', async () => {
    const fetchSpy = mockFetchByUrl({
      '/api/v1/auth/refresh': { body: signedInAs('USER') },
      '/actuator/health': HEALTH,
    });
    renderRoute('/admin/projects');

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy trang' }),
    ).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([input]) => String(input).includes('/api/v1/admin'))).toBe(
      false,
    );
  });
});

describe('menu tài khoản', () => {
  it('admin thấy mục "Quản lý tài khoản"; người thường thì không', async () => {
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: signedInAs('ADMIN') },
      '/api/v1/admin/users': { body: USERS },
      '/actuator/health': HEALTH,
    });
    renderRoute('/admin');
    await userEvent.click(await screen.findByRole('button', { name: /^Tài khoản:/ }));
    expect(screen.getByRole('menuitem', { name: 'Quản lý tài khoản' })).toBeInTheDocument();
  });

  it('người thường không có mục quản trị trong menu', async () => {
    mockFetchByUrl({
      '/api/v1/auth/refresh': { body: signedInAs('USER') },
      '/api/v1/projects': { body: { items: [], page: 0, size: 20, total: 0 } },
      '/actuator/health': HEALTH,
    });
    renderRoute('/projects');
    await userEvent.click(await screen.findByRole('button', { name: /^Tài khoản:/ }));
    expect(screen.queryByRole('menuitem', { name: 'Quản lý tài khoản' })).not.toBeInTheDocument();
  });
});
