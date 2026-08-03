/**
 * Test source viewer. Render qua `MemoryRouter` với đúng hình dạng route thật (`snapshots/:id/files/*`)
 * để splat route được phân giải y như trong ứng dụng.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { SourceViewerPage } from '@/features/snapshots/pages/SourceViewerPage';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const readFileContentMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/snapshots/api/snapshotApi', () => ({
  readFileContent: readFileContentMock,
}));

function renderViewer(url: string) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/snapshots/:snapshotId/files/*" element={<SourceViewerPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('SourceViewerPage', () => {
  it('hiển thị nội dung file kèm số dòng bắt đầu từ 1', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'src/App.java',
      fromLine: 1,
      toLine: 3,
      totalLines: 3,
      truncated: false,
      lines: ['class App {', '  int x;', '}'],
    });

    renderViewer('/snapshots/s1/files/src/App.java');

    expect(await screen.findByText('class App {')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  /**
   * Số dòng thật = `fromLine` + vị trí trong lát. Dùng chỉ số mảng làm số dòng sẽ hiển thị 1,2,3
   * cho một lát bắt đầu từ dòng 10 — và mọi evidence trỏ vào file này bị highlight lệch.
   */
  it('đánh số dòng theo fromLine khi chỉ đọc một khoảng giữa file', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'big.txt',
      fromLine: 10,
      toLine: 12,
      totalLines: 100,
      truncated: true,
      lines: ['dong 10', 'dong 11', 'dong 12'],
    });

    renderViewer('/snapshots/s1/files/big.txt');

    expect(await screen.findByText('dong 10')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.queryByText('1')).not.toBeInTheDocument();
  });

  it('hiện banner khi nội dung bị cắt bớt', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'huge.txt',
      fromLine: 1,
      toLine: 2000,
      totalLines: 12431,
      truncated: true,
      lines: ['x'],
    });

    renderViewer('/snapshots/s1/files/huge.txt');

    expect(await screen.findByText(/trên tổng 12431 dòng/)).toBeInTheDocument();
  });

  it('làm nổi đúng dòng được trỏ bởi #L42', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'a.txt',
      fromLine: 40,
      toLine: 43,
      totalLines: 100,
      truncated: true,
      lines: ['dong 40', 'dong 41', 'dong 42', 'dong 43'],
    });

    const { container } = renderViewer('/snapshots/s1/files/a.txt#L42');

    await screen.findByText('dong 42');
    const highlighted = container.querySelector('#L42');
    expect(highlighted?.className).toContain('bg-line-highlight');
  });

  it('file nhị phân hiện thông báo thay vì rác đã ép sang UTF-8', async () => {
    readFileContentMock.mockRejectedValue(
      new ApiError(415, { type: 'https://datn.hust.vn/problems/binary-file' }),
    );

    renderViewer('/snapshots/s1/files/app.bin');

    expect(await screen.findByText(/File nhị phân/)).toBeInTheDocument();
  });

  it('file vượt ngưỡng lưu trữ báo rõ là chỉ có chỉ mục, không có nội dung', async () => {
    readFileContentMock.mockRejectedValue(
      new ApiError(409, { type: 'https://datn.hust.vn/problems/file-content-not-stored' }),
    );

    renderViewer('/snapshots/s1/files/large.txt');

    expect(await screen.findByText(/vượt ngưỡng lưu trữ/)).toBeInTheDocument();
  });

  /** Đường dẫn có dấu cách/tiếng Việt phải tới API dưới dạng đã giải mã, không phải %20. */
  it('gọi API bằng đường dẫn đã giải mã của splat route', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'tài liệu/ghi chú.md',
      fromLine: 1,
      toLine: 1,
      totalLines: 1,
      truncated: false,
      lines: ['noi dung'],
    });

    renderViewer('/snapshots/s1/files/t%C3%A0i%20li%E1%BB%87u/ghi%20ch%C3%BA.md');

    await screen.findByText('noi dung');
    expect(readFileContentMock).toHaveBeenCalledWith('s1', 'tài liệu/ghi chú.md', undefined);
  });
});
