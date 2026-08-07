/**
 * Đọc khung SSE — chỗ dễ đánh rơi sự kiện nhất ở phía trình duyệt.
 *
 * Một khung bị đánh rơi KHÔNG có thông báo lỗi nào: người dùng chỉ thấy tiến trình chạy xong mà
 * câu trả lời không bao giờ hiện ra. Vì vậy các ca biên ở đây (khung cuối thiếu dòng trống, một
 * khung bị cắt làm đôi giữa hai chunk) quan trọng hơn ca thuận.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/lib/apiClient';
import { type SseFrame, streamSse } from '@/shared/lib/sseClient';

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}

function mockStream(chunks: string[], status = 200): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(() =>
      Promise.resolve(
        new Response(status === 200 ? streamOf(chunks) : 'khong phai json', {
          status,
          headers: { 'Content-Type': 'text/event-stream' },
        }),
      ),
    ),
  );
}

/** Đọc hết luồng đã mock ở trên và trả về mọi khung nhận được. */
async function collect(): Promise<SseFrame[]> {
  const frames: SseFrame[] = [];
  await streamSse({ path: '/api/v1/x', body: {} }, (frame) => frames.push(frame));
  return frames;
}

beforeEach(() => {
  setAccessToken('token-gia');
});

afterEach(() => {
  vi.unstubAllGlobals();
  setAccessToken(null);
});

describe('streamSse', () => {
  it('đọc được nhiều khung trong một chunk', async () => {
    mockStream(['event: status\ndata: {"a":1}\n\nevent: answer\ndata: {"b":2}\n\n']);

    const frames = await collect();

    expect(frames.map((f) => f.name)).toEqual(['status', 'answer']);
    expect(frames[0]?.data).toBe('{"a":1}');
  });

  /**
   * Mạng cắt chunk ở đâu là chuyện của mạng, không theo ranh giới khung. Xử lý từng chunk độc lập
   * sẽ làm hỏng đúng những khung dài nhất — mà khung dài nhất chính là `answer`.
   */
  it('ghép được một khung bị cắt làm đôi giữa hai chunk', async () => {
    mockStream(['event: ans', 'wer\ndata: {"verdi', 'ct":"OK"}\n\n']);

    const frames = await collect();

    expect(frames).toHaveLength(1);
    expect(frames[0]).toEqual({ name: 'answer', data: '{"verdict":"OK"}' });
  });

  /** Khung CUỐI thường không có dòng trống theo sau — và khung cuối chính là câu trả lời. */
  it('không đánh rơi khung cuối thiếu dòng trống', async () => {
    mockStream(['event: answer\ndata: {"b":2}']);

    const frames = await collect();

    expect(frames.map((f) => f.name)).toEqual(['answer']);
  });

  it('bỏ qua dòng giữ nhịp và khung thiếu data', async () => {
    mockStream([': giu nhip\n\nevent: status\n\nevent: status\ndata: {}\n\n']);

    const frames = await collect();

    expect(frames).toHaveLength(1);
  });

  /** Ký tự tiếng Việt bị cắt giữa hai chunk phải ghép lại đúng, không thành U+FFFD. */
  it('giải mã đúng ký tự nhiều byte bị cắt giữa hai chunk', async () => {
    const encoder = new TextEncoder();
    const full = encoder.encode('event: answer\ndata: {"s":"đủ"}\n\n');
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(
            new ReadableStream({
              start(controller) {
                controller.enqueue(full.slice(0, 24));
                controller.enqueue(full.slice(24));
                controller.close();
              },
            }),
            { status: 200 },
          ),
        ),
      ),
    );

    const frames = await collect();

    expect(frames[0]?.data).toBe('{"s":"đủ"}');
  });

  it('lỗi HTTP thành ApiError chứ không phải luồng rỗng im lặng', async () => {
    mockStream([], 503);

    await expect(collect()).rejects.toMatchObject({ status: 503 });
  });
});
