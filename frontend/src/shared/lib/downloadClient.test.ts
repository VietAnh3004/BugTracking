/**
 * `downloadClient` là ngoại lệ THỨ BA được phép không đi qua `apiClient`. Mỗi ngoại lệ như vậy là
 * một chỗ có thể lệch khỏi ba quy tắc chung (base URL, gắn token, bóc ProblemDetail), nên bộ test
 * này canh đúng ba thứ đó chứ không canh việc "file có tải về không".
 *
 * Ca dễ mất nhất: **thiếu header `Authorization`**. Nó không làm hỏng gì lúc chạy test thủ công
 * với backend đang mở, nhưng trên môi trường thật backend trả 401 và người dùng nhận một file HTML
 * lỗi mang đuôi `.csv`. Đó chính là lý do không dùng thẻ `<a download>` trỏ thẳng vào URL.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/lib/apiClient';
import { downloadFile } from '@/shared/lib/downloadClient';

/** Ghi lại tên file mà trình duyệt được yêu cầu lưu. */
let savedFileName: string | null = null;

beforeEach(() => {
  savedFileName = null;
  setAccessToken('access-token-gia');

  // happy-dom không có `createObjectURL`; thay bằng bản giả để quan sát cả việc thu hồi.
  URL.createObjectURL = vi.fn(() => 'blob:gia');
  URL.revokeObjectURL = vi.fn();

  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    savedFileName = this.download;
  });
});

afterEach(() => {
  setAccessToken(null);
  vi.restoreAllMocks();
});

function mockDownloadResponse(headers: Record<string, string>, status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    blob: () => Promise.resolve(new Blob(['a,b\n1,2'])),
    text: () => Promise.resolve('{"title":"khong duoc phep"}'),
    headers: { get: (name: string) => headers[name] ?? null },
  } as unknown as Response);
}

describe('downloadFile', () => {
  it('gắn header Authorization — thiếu nó thì người dùng nhận file lỗi mang đuôi .csv', async () => {
    const fetchSpy = mockDownloadResponse({
      'Content-Disposition': 'attachment; filename="bao-cao.csv"',
    });

    await downloadFile('/api/v1/analysis-runs/run-1/export.csv', 'du-phong.csv');

    const init = fetchSpy.mock.calls[0]?.[1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer access-token-gia');
  });

  it('dùng tên file backend đề xuất trong Content-Disposition', async () => {
    mockDownloadResponse({ 'Content-Disposition': 'attachment; filename="bao-cao-run-1.csv"' });

    await downloadFile('/api/v1/analysis-runs/run-1/export.csv', 'du-phong.csv');

    expect(savedFileName).toBe('bao-cao-run-1.csv');
  });

  /** Header vắng hoặc sai khuôn thì vẫn phải lưu được, chỉ là bằng tên dự phòng. */
  it('không có Content-Disposition thì dùng tên dự phòng', async () => {
    mockDownloadResponse({});

    await downloadFile('/api/v1/analysis-runs/run-1/export.json', 'du-phong.json');

    expect(savedFileName).toBe('du-phong.json');
  });

  it('header không đúng khuôn cũng rơi về tên dự phòng thay vì lưu tên rác', async () => {
    mockDownloadResponse({ 'Content-Disposition': 'attachment' });

    await downloadFile('/api/v1/analysis-runs/run-1/export.csv', 'du-phong.csv');

    expect(savedFileName).toBe('du-phong.csv');
  });

  /**
   * Lỗi phải ném ra dạng `ApiError` đã bóc ProblemDetail, giống hệt `apiClient` — nếu không, màn
   * hình gọi nó sẽ phải xử lý lỗi theo một kiểu thứ hai.
   */
  it('response lỗi thì ném ApiError mang thông điệp của backend', async () => {
    mockDownloadResponse({}, 403);

    await expect(
      downloadFile('/api/v1/analysis-runs/run-1/export.csv', 'du-phong.csv'),
    ).rejects.toThrow('khong duoc phep');
  });

  /** Mỗi object URL giữ nguyên blob trong bộ nhớ tới khi tab đóng; tải lại nhiều lần là bình thường. */
  it('thu hồi object URL sau khi tải xong', async () => {
    mockDownloadResponse({});

    await downloadFile('/api/v1/analysis-runs/run-1/export.csv', 'du-phong.csv');

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:gia');
  });
});
