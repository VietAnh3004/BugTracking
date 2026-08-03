/**
 * Test cơ chế tự làm mới access token khi gặp 401.
 *
 * Test quan trọng nhất là `goi refresh DUNG MOT LAN`: nó canh một lỗi BẢO MẬT chứ không phải lỗi
 * hiệu năng. Refresh token xoay vòng + backend phát hiện tái sử dụng (ADR 0006), nên gọi refresh
 * hai lần với cùng một token khiến backend thu hồi CẢ HỌ và đá người dùng ra giữa chừng.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiClient, setAccessToken, setAuthHooks } from '@/shared/lib/apiClient';

/** Đếm số lần từng URL bị gọi, và cho phép đổi hành vi giữa chừng (401 rồi 200 sau khi refresh). */
function createFetchStub() {
  const calls: string[] = [];
  let unauthorized = true;

  const stub = vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    calls.push(url);

    if (unauthorized) {
      return Promise.resolve({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ title: 'Unauthorized' }),
      } as Response);
    }
    return Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ ok: true }),
    } as Response);
  });

  return {
    stub,
    calls,
    allowAfterRefresh: () => {
      unauthorized = false;
    },
  };
}

beforeEach(() => {
  setAccessToken('access-token-cu');
});

afterEach(() => {
  vi.restoreAllMocks();
  setAuthHooks(null);
  setAccessToken(null);
});

describe('tự làm mới access token', () => {
  it('ba request song song cùng nhận 401 chỉ gọi refresh ĐÚNG MỘT LẦN', async () => {
    const fetchStub = createFetchStub();
    const refresh = vi.fn(async () => {
      // Refresh mất một nhịp — nếu không có single-flight, hai request còn lại sẽ kịp gọi thêm.
      await Promise.resolve();
      fetchStub.allowAfterRefresh();
      return { accessToken: 'access-token-moi' };
    });
    setAuthHooks({ refresh, onSessionExpired: vi.fn() });

    await Promise.all([
      apiClient.get('/api/v1/projects'),
      apiClient.get('/api/v1/snapshots/a/files'),
      apiClient.get('/api/v1/auth/me'),
    ]);

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('thử lại request bằng access token MỚI sau khi refresh', async () => {
    const fetchStub = createFetchStub();
    setAuthHooks({
      refresh: async () => {
        fetchStub.allowAfterRefresh();
        return { accessToken: 'access-token-moi' };
      },
      onSessionExpired: vi.fn(),
    });

    await apiClient.get('/api/v1/projects');

    const lastCall = fetchStub.stub.mock.calls.at(-1);
    const headers = lastCall?.[1]?.headers as Record<string, string> | undefined;
    expect(headers?.Authorization).toBe('Bearer access-token-moi');
  });

  it('403 KHÔNG kích hoạt refresh (thiếu quyền, không phải hết hạn)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 403,
      json: () => Promise.resolve({ title: 'Forbidden' }),
    } as Response);
    const refresh = vi.fn();
    setAuthHooks({ refresh, onSessionExpired: vi.fn() });

    await apiClient.get('/api/v1/projects').catch(() => undefined);

    expect(refresh).not.toHaveBeenCalled();
  });

  it('skipAuthRefresh chặn đệ quy khi chính endpoint auth trả 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ title: 'Unauthorized' }),
    } as Response);
    const refresh = vi.fn();
    setAuthHooks({ refresh, onSessionExpired: vi.fn() });

    await apiClient
      .post('/api/v1/auth/login', { email: 'a@b.c', password: 'sai' }, { skipAuthRefresh: true })
      .catch(() => undefined);

    expect(refresh).not.toHaveBeenCalled();
  });

  it('refresh thất bại thì báo hết phiên và ném lỗi 401 gốc ra ngoài', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ title: 'Unauthorized' }),
    } as Response);
    const onSessionExpired = vi.fn();
    setAuthHooks({
      refresh: () => Promise.reject(new Error('refresh token đã bị thu hồi')),
      onSessionExpired,
    });

    const error = await apiClient.get('/api/v1/projects').catch((e: unknown) => e);

    expect(onSessionExpired).toHaveBeenCalledTimes(1);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
  });

  /**
   * Sau khi một lượt refresh đã settle, lượt 401 TIẾP THEO (vd access token mới cũng hết hạn sau
   * 15 phút) phải mở được một lời gọi refresh mới. Quên dọn `inFlightRefresh` thì mọi request sau
   * đó dùng lại kết quả cũ và người dùng kẹt ở trạng thái không bao giờ làm mới được nữa.
   */
  it('lượt 401 sau đó vẫn mở được lời gọi refresh mới', async () => {
    const fetchStub = createFetchStub();
    const refresh = vi.fn(async () => {
      fetchStub.allowAfterRefresh();
      return { accessToken: 'access-token-moi' };
    });
    setAuthHooks({ refresh, onSessionExpired: vi.fn() });

    await apiClient.get('/api/v1/projects');
    expect(refresh).toHaveBeenCalledTimes(1);

    // Dựng lại tình huống 401 một lần nữa.
    vi.restoreAllMocks();
    const secondStub = createFetchStub();
    const secondRefresh = vi.fn(async () => {
      secondStub.allowAfterRefresh();
      return { accessToken: 'access-token-moi-2' };
    });
    setAuthHooks({ refresh: secondRefresh, onSessionExpired: vi.fn() });

    await apiClient.get('/api/v1/projects');

    expect(secondRefresh).toHaveBeenCalledTimes(1);
  });
});
