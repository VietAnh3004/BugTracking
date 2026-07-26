/**
 * Client HTTP duy nhất của ứng dụng.
 *
 * Không component nào được gọi `fetch` trực tiếp: base URL, token và cách bóc lỗi phải nằm ở
 * một chỗ, nếu không mỗi màn hình sẽ xử lý lỗi một kiểu.
 */

import { appConfig } from '@/shared/config/runtime';

/** Lỗi trả về từ backend theo RFC 7807 (Problem Details). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  fieldErrors?: Record<string, string>;
}

/**
 * Lỗi API đã bóc tách, ném ra cho tầng gọi xử lý.
 *
 * Gán field tường minh thay vì dùng parameter property của TypeScript: `erasableSyntaxOnly`
 * đang bật, nên cú pháp nào không xoá được bằng cách bỏ chú thích kiểu đều bị cấm.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Yêu cầu thất bại với mã ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/**
 * Token lưu trong bộ nhớ chứ không phải localStorage.
 *
 * localStorage đọc được bằng JavaScript nên một lỗ hổng XSS là đủ để lấy token. Đánh đổi: người
 * dùng phải đăng nhập lại khi tải lại trang — chấp nhận được với ứng dụng phân tích nội bộ.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options;

  const response = await fetch(`${appConfig.apiBaseUrl}${path}`, {
    ...rest,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  // 204 No Content không có body để parse.
  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/** Đọc body lỗi, chịu được cả trường hợp backend trả về HTML hoặc chuỗi rỗng. */
async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return { status: response.status, title: response.statusText };
  }
}

export const apiClient = {
  get: <T>(path: string): Promise<T> => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown): Promise<T> => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' }),
};
