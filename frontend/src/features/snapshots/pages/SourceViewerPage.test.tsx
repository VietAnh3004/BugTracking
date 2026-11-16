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

/**
 * Tìm phần tử `<code>` của một dòng theo TOÀN BỘ chữ của dòng đó.
 *
 * File có ngôn ngữ được tô màu thì một dòng bị chia thành nhiều `<span>` (từ khoá, tên lớp, dấu
 * câu…), nên `findByText('class App {')` — vốn chỉ so chữ nằm TRỰC TIẾP trong một phần tử — không
 * còn thấy. So `textContent` của cả `<code>` vẫn kiểm đúng điều cần kiểm: người đọc thấy nguyên
 * văn dòng mã.
 */
function codeLine(text: string) {
  return (_content: string, element: Element | null) =>
    element?.tagName === 'CODE' && element.textContent === text;
}

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

    expect(await screen.findByText(codeLine('class App {'))).toBeInTheDocument();
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

  it('làm nổi CẢ KHOẢNG dòng được trỏ bởi #L41-L43', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'a.txt',
      fromLine: 40,
      toLine: 44,
      totalLines: 100,
      truncated: true,
      lines: ['dong 40', 'dong 41', 'dong 42', 'dong 43', 'dong 44'],
    });

    const { container } = renderViewer('/snapshots/s1/files/a.txt#L41-L43');

    await screen.findByText('dong 42');
    for (const line of [41, 42, 43]) {
      expect(container.querySelector(`#L${String(line)}`)?.className).toContain(
        'bg-line-highlight',
      );
    }
    // Hai dòng kề ngoài khoảng KHÔNG được tô — nếu không thì "vùng bằng chứng" mất hết nghĩa.
    expect(container.querySelector('#L40')?.className).not.toContain('bg-line-highlight');
    expect(container.querySelector('#L44')?.className).not.toContain('bg-line-highlight');
  });

  /**
   * Bề mặt XSS THẬT của dự án, không phải rủi ro lý thuyết: source viewer render nội dung của file
   * do người dùng upload, và một repo độc hại chứa file `.html` với payload là kịch bản hoàn toàn
   * hiện thực trong chính luồng nghiệp vụ (ADR 0006).
   *
   * React tự escape khi render text node, nên test này canh việc KHÔNG ai mở cửa bằng
   * `dangerouslySetInnerHTML`. Từ khi có tô màu cú pháp, file `.html` đi qua bộ tách token
   * (`lib/highlight.ts`) — chính chỗ một thư viện trả chuỗi HTML sẽ mở lỗ hổng — nên test này giờ
   * canh luôn cả đường đó.
   */
  it('render nội dung file như TEXT, không diễn giải thành HTML', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'evil.html',
      fromLine: 1,
      toLine: 2,
      totalLines: 2,
      truncated: false,
      lines: ['<img src=x onerror="alert(1)">', '<script>alert(2)</script>'],
    });

    const { container } = renderViewer('/snapshots/s1/files/evil.html');

    expect(await screen.findByText(codeLine('<img src=x onerror="alert(1)">'))).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
  });

  /**
   * Comment khối trải nhiều dòng là ca dễ tô sai nhất: tô từng dòng độc lập thì dòng giữa
   * Javadoc bị hiểu là code. vulnshop có Javadoc ở gần như mọi method.
   */
  it('tô màu Java, kể cả dòng GIỮA một comment khối', async () => {
    readFileContentMock.mockResolvedValue({
      path: 'src/A.java',
      fromLine: 1,
      toLine: 4,
      totalLines: 4,
      truncated: false,
      lines: ['/**', ' * class khong phai tu khoa o day', ' */', 'public class A {}'],
    });

    const { container } = renderViewer('/snapshots/s1/files/src/A.java');

    await screen.findByText(codeLine('public class A {}'));
    const giuaComment = container.querySelector('#L2 code span');
    expect(giuaComment?.className).toContain('italic');
    const tuKhoa = Array.from(container.querySelectorAll('#L4 code span')).find(
      (span) => span.textContent === 'public',
    );
    expect(tuKhoa?.className).toContain('text-rose-700');
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
