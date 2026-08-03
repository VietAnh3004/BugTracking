/**
 * Test đầu tiên của frontend. Ngoài việc kiểm `apiClient`, nó còn là bằng chứng cho hai thứ của
 * hạ tầng test: (1) `src/test/setup.ts` gán `window.__APP_CONFIG__` đủ sớm — file này import
 * `apiClient`, thứ import `runtime.ts`, thứ gọi `loadAppConfig()` ở top level và ném lỗi nếu
 * thiếu cấu hình; (2) môi trường jsdom có sẵn `fetch` để stub.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiClient, setAccessToken } from '@/shared/lib/apiClient';

function mockFetchOnce(response: Partial<Response> & { json?: () => Promise<unknown> }) {
  const spy = vi.spyOn(globalThis, 'fetch');
  spy.mockResolvedValueOnce({
    ok: true,
    status: 200,
    json: () => Promise.resolve({}),
    ...response,
  } as Response);
  return spy;
}

afterEach(() => {
  vi.restoreAllMocks();
  setAccessToken(null);
});

describe('apiClient', () => {
  it('ghép base URL từ cấu hình runtime, không tự ghép trong component', async () => {
    const spy = mockFetchOnce({ json: () => Promise.resolve({ items: [] }) });

    await apiClient.get('/api/v1/projects');

    expect(spy).toHaveBeenCalledWith('http://api.test.local/api/v1/projects', expect.anything());
  });

  it('gắn Authorization khi đã có access token', async () => {
    const spy = mockFetchOnce({});
    setAccessToken('token-gia-cho-test');

    await apiClient.get('/api/v1/auth/me');

    const init = spy.mock.calls[0]?.[1];
    expect(init?.headers).toMatchObject({ Authorization: 'Bearer token-gia-cho-test' });
  });

  it('không gắn Authorization khi chưa đăng nhập', async () => {
    const spy = mockFetchOnce({});

    await apiClient.get('/api/v1/projects');

    const init = spy.mock.calls[0]?.[1];
    expect(init?.headers).not.toHaveProperty('Authorization');
  });

  it('bóc ProblemDetail của backend thành ApiError giữ nguyên status', async () => {
    mockFetchOnce({
      ok: false,
      status: 409,
      json: () =>
        Promise.resolve({
          type: 'https://datn.hust.vn/problems/snapshot-not-ready',
          detail: 'Snapshot chưa sẵn sàng',
        }),
    });

    const error = await apiClient.get('/api/v1/snapshots/x/files').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).problem.type).toContain('snapshot-not-ready');
  });

  /**
   * Backend có thể trả HTML (trang lỗi của reverse proxy) hoặc body rỗng thay vì JSON. Không có
   * nhánh này, `response.json()` ném SyntaxError và tầng gọi nhận một lỗi hoàn toàn không liên
   * quan tới nguyên nhân thật.
   */
  it('vẫn tạo được ApiError khi body lỗi không phải JSON', async () => {
    mockFetchOnce({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    });

    const error = (await apiClient.get('/api/v1/projects').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
  });

  it('không parse body cho 204 No Content', async () => {
    const jsonSpy = vi.fn(() => Promise.resolve({}));
    mockFetchOnce({ ok: true, status: 204, json: jsonSpy });

    const result = await apiClient.delete('/api/v1/projects/x');

    expect(result).toBeUndefined();
    expect(jsonSpy).not.toHaveBeenCalled();
  });
});
