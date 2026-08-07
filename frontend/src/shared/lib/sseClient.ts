/**
 * Đọc luồng Server-Sent Events từ backend.
 *
 * ================= VÌ SAO KHÔNG DÙNG `EventSource` =================
 * `EventSource` là API sẵn có của trình duyệt cho SSE, nhưng nó **không gửi được header tuỳ ý**
 * — tức là không gắn được `Authorization: Bearer`. Cách duy nhất để xác thực với nó là nhét token
 * vào query string, và query string thì nằm trong log của mọi reverse proxy trên đường đi. Nó
 * cũng chỉ làm được `GET`, trong khi hỏi một câu là `POST`.
 *
 * Vì vậy: `fetch` + đọc `ReadableStream` thủ công. Mất khả năng tự kết nối lại của `EventSource`,
 * nhưng ở đây không cần — một lượt hỏi đáp là một lần, kết nối đứt thì hỏi lại.
 * ====================================================================
 *
 * Module này KHÔNG biết tới React. Nó nhận một callback và gọi lại; việc dựng lại state là của
 * hook ở tầng trên, giống hệt ranh giới mà `apiClient` giữ.
 */

import {
  ApiError,
  authorizationHeader,
  parseProblem,
  refreshAccessToken,
  resolveUrl,
} from '@/shared/lib/apiClient';

/** Một khung SSE đã tách tên và payload. */
export interface SseFrame {
  name: string;
  data: string;
}

export interface SseRequest {
  path: string;
  body: unknown;
  signal?: AbortSignal;
}

/**
 * Gửi một `POST` và đọc luồng SSE trả về, gọi `onFrame` cho từng khung.
 *
 * Làm mới access token **trước** khi mở luồng, cùng lý do với `uploadClient`: không thể "thử lại
 * khi gặp 401" giữa một luồng đã chạy được nửa chừng — người dùng sẽ thấy câu trả lời bị cắt cụt,
 * và lượt hỏi đó đã tiêu token thật ở phía nhà cung cấp LLM rồi.
 */
export async function streamSse(
  request: SseRequest,
  onFrame: (frame: SseFrame) => void,
): Promise<void> {
  await refreshAccessToken();

  const response = await fetch(resolveUrl(request.path), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authorizationHeader(),
    },
    body: JSON.stringify(request.body),
    signal: request.signal,
  });

  if (!response.ok) {
    throw new ApiError(response.status, parseProblem(response.status, await response.text()));
  }
  if (response.body === null) {
    throw new ApiError(response.status, {
      status: response.status,
      title: 'Máy chủ không trả về luồng dữ liệu',
    });
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    // `stream: true` bắt buộc: một ký tự tiếng Việt có thể bị cắt làm đôi giữa hai chunk, và giải
    // mã từng chunk độc lập sẽ cho ra ký tự thay thế (U+FFFD) ngay giữa câu trả lời.
    buffer += decoder.decode(value, { stream: true });
    buffer = drain(buffer, onFrame);
  }

  // Khung cuối thường KHÔNG có dòng trống theo sau — mà khung cuối chính là `answer`. Đánh rơi nó
  // nghĩa là mọi lượt hỏi đáp đều kết thúc mà câu trả lời không bao giờ hiện ra.
  const trailing = parseFrame(buffer);
  if (trailing !== null) {
    onFrame(trailing);
  }
}

/** Cắt mọi khung hoàn chỉnh khỏi buffer, trả về phần dư chưa đủ một khung. */
function drain(buffer: string, onFrame: (frame: SseFrame) => void): string {
  let rest = buffer;
  for (;;) {
    const boundary = rest.indexOf('\n\n');
    if (boundary === -1) {
      return rest;
    }
    const frame = parseFrame(rest.slice(0, boundary));
    rest = rest.slice(boundary + 2);
    if (frame !== null) {
      onFrame(frame);
    }
  }
}

function parseFrame(block: string): SseFrame | null {
  let name: string | null = null;
  let data: string | null = null;

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      // Nối nhiều dòng `data:` theo đặc tả. Backend hiện chỉ gửi một dòng, nhưng gộp ở đây rẻ
      // hơn nhiều so với việc một ngày nào đó nuốt mất nửa payload mà không có thông báo lỗi.
      const chunk = line.slice('data:'.length).trimStart();
      data = data === null ? chunk : `${data}\n${chunk}`;
    }
  }

  return name !== null && data !== null ? { name, data } : null;
}
