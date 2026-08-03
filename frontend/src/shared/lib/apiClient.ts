/**
 * Client HTTP duy nhất của ứng dụng.
 *
 * Không component nào được gọi `fetch` trực tiếp: base URL, token và cách bóc lỗi phải nằm ở
 * một chỗ, nếu không mỗi màn hình sẽ xử lý lỗi một kiểu.
 *
 * Module này KHÔNG được biết tới React, React Router hay `localStorage`. Mọi thứ nó cần từ tầng
 * ứng dụng đều được TIÊM VÀO qua `setAuthHooks` — nhờ vậy nó test được bằng JUnit-style thuần, và
 * không có chuyện `apiClient` tự gọi `navigate()` (điều hướng là việc của route guard, xem
 * `ProtectedRoute`).
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
 * localStorage đọc được bằng JavaScript nên một lỗ hổng XSS là đủ để lấy token. Đánh đổi và các
 * biện pháp bù được ghi đầy đủ ở `docs/adr/0006-xac-thuc-jwt-va-refresh-token-xoay-vong.md`.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/**
 * Cầu nối một chiều từ tầng ứng dụng xuống client. Tiêm hàm thay vì import trực tiếp để `apiClient`
 * không phụ thuộc ngược lên `features/auth`.
 */
export interface AuthHooks {
  /**
   * Đổi refresh token lấy access token mới. Trả `null` khi không có gì để đổi (chưa đăng nhập).
   * Ném lỗi khi refresh token đã hết hạn hoặc bị thu hồi.
   */
  refresh: () => Promise<{ accessToken: string } | null>;
  /** Phiên đã chết hẳn: dọn token, xoá cache, đặt trạng thái `anonymous`. KHÔNG điều hướng ở đây. */
  onSessionExpired: () => void;
}

let authHooks: AuthHooks | null = null;

export function setAuthHooks(hooks: AuthHooks | null): void {
  authHooks = hooks;
}

/**
 * Lời gọi refresh đang bay, dùng chung cho MỌI request cùng gặp 401 một lúc.
 *
 * ==================== ĐÂY LÀ YÊU CẦU ĐÚNG ĐẮN, KHÔNG PHẢI TỐI ƯU ====================
 * Refresh token của hệ thống này XOAY VÒNG và backend PHÁT HIỆN TÁI SỬ DỤNG (ADR 0006). Nếu ba
 * request song song cùng nhận 401 và mỗi cái tự gọi `/auth/refresh` với CÙNG một refresh token,
 * backend sẽ thấy token đó bị dùng lại hai lần sau khi đã xoay — kết luận là token bị lộ và
 * THU HỒI CẢ HỌ. Hậu quả với người dùng: đang thao tác bình thường thì bị đăng xuất, và nó xảy ra
 * đúng lúc tải nhiều dữ liệu song song nhất (mở một trang có vài query cùng lúc).
 *
 * React StrictMode gọi effect hai lần trong dev cũng tạo ra đúng tình huống này.
 * ====================================================================================
 */
let inFlightRefresh: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (inFlightRefresh !== null) {
    return inFlightRefresh;
  }

  const hooks = authHooks;
  if (hooks === null) {
    return Promise.resolve(false);
  }

  inFlightRefresh = hooks
    .refresh()
    .then((tokens) => {
      if (tokens === null) {
        hooks.onSessionExpired();
        return false;
      }
      accessToken = tokens.accessToken;
      return true;
    })
    .catch(() => {
      hooks.onSessionExpired();
      return false;
    })
    .finally(() => {
      // Dọn sau khi đã settle để lượt 401 TIẾP THEO mở một lời gọi refresh mới. Gán null ở đây an
      // toàn vì các request đang chờ đã giữ tham chiếu tới chính promise này rồi.
      inFlightRefresh = null;
    });

  return inFlightRefresh;
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /**
   * Tắt cơ chế tự refresh cho request này. BẮT BUỘC dùng cho mọi endpoint `/api/v1/auth/**`:
   * chính `/auth/refresh` mà nhận 401 lại đi gọi `/auth/refresh` là đệ quy vô hạn.
   */
  skipAuthRefresh?: boolean;
}

function sendRequest(path: string, options: RequestOptions): Promise<Response> {
  const { body, headers, skipAuthRefresh: _skipAuthRefresh, ...rest } = options;

  return fetch(`${appConfig.apiBaseUrl}${path}`, {
    ...rest,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(accessToken !== null ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await sendRequest(path, options);

  // CHỈ 401 mới kích hoạt refresh. 403 nghĩa là "đã xác thực nhưng không đủ quyền" — làm mới token
  // không đổi được gì, chỉ tốn thêm một vòng gọi mạng và làm xoay refresh token vô ích.
  const shouldRefresh =
    response.status === 401 && options.skipAuthRefresh !== true && authHooks !== null;

  if (shouldRefresh && (await refreshOnce())) {
    // Thử lại ĐÚNG MỘT LẦN. Nếu vẫn 401 sau khi đã có access token mới thì vấn đề không phải hết
    // hạn (vd thiếu quyền trên tài nguyên đó) — thử lại tiếp chỉ tạo vòng lặp.
    return toResult<T>(await sendRequest(path, options));
  }

  return toResult<T>(response);
}

async function toResult<T>(response: Response): Promise<T> {
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
  get: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions): Promise<T> =>
    request<T>(path, { ...options, method: 'DELETE' }),
};
