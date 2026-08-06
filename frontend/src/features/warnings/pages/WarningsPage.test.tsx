/**
 * Bốn trạng thái bắt buộc của màn hình cảnh báo, cộng hai nhánh mà backend cố tình thiết kế để FE
 * phân biệt được: run FAILED (đọc được lý do) và 409 "run chưa xong" (khác hẳn danh sách rỗng).
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { WarningsPage } from '@/features/warnings/pages/WarningsPage';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const getAnalysisRunMock = vi.hoisted(() => vi.fn());
const listWarningsMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/warnings/api/warningApi', () => ({
  getAnalysisRun: getAnalysisRunMock,
  listWarnings: listWarningsMock,
  listAnalysisRuns: vi.fn(),
}));

function run(overrides: Record<string, unknown> = {}) {
  return {
    id: 'run-1',
    snapshotId: 'snap-1',
    status: 'SUCCEEDED',
    findingCount: 33,
    locationCount: 90,
    unresolvedLocationCount: 0,
    linkCount: 120,
    codeContextCount: 30,
    unlinkedWarningCount: 0,
    startedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  };
}

function renderPage() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/snapshots/snap-1/runs/run-1/warnings']}>
        <Routes>
          <Route path="/snapshots/:snapshotId/runs/:runId/warnings" element={<WarningsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('WarningsPage', () => {
  it('hiển thị bảng cảnh báo và tổng số khi tải xong', async () => {
    getAnalysisRunMock.mockResolvedValue(run());
    listWarningsMock.mockResolvedValue({
      items: [
        {
          evidenceId: 'e1',
          shortId: 'EV-001A',
          analysisRunId: 'run-1',
          filePath: 'src/App.java',
          startLine: 10,
          summary: 'Loi bao mat',
          tool: 'spotbugs',
          ruleId: 'R1',
          ruleIdNormalized: 'R1',
          severityNormalized: 'HIGH',
          hasPrimaryLocation: true,
        },
      ],
      page: 0,
      size: 50,
      total: 33,
    });

    renderPage();

    expect(await screen.findByText('Loi bao mat')).toBeInTheDocument();
    expect(screen.getByText(/Hiển thị 1\/33 cảnh báo/)).toBeInTheDocument();
  });

  /**
   * Tỉ lệ vị trí đối chiếu được phải hiện ngay trên đầu màn hình. Đây là cách duy nhất để hỏng
   * chuẩn hoá đường dẫn không trở lại thành thứ hỏng trong im lặng.
   */
  it('hiện tỉ lệ vị trí đối chiếu được ngay trên đầu màn hình', async () => {
    getAnalysisRunMock.mockResolvedValue(run({ locationCount: 90, unresolvedLocationCount: 12 }));
    listWarningsMock.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage();

    expect(await screen.findByText('78/90 vị trí đối chiếu được với mã nguồn')).toBeInTheDocument();
  });

  /**
   * Bộ đếm liên kết cùng lý do tồn tại: một liên kết KHÔNG sinh ra được trông y hệt "dự án này
   * không có bằng chứng liên quan".
   *
   * Test này cũng canh một cách hỏng cụ thể đã gặp: nếu mock (hoặc API) thiếu hai trường thì phép
   * trừ ra `NaN` và màn hình hiện "NaN/33" — mà không assertion nào bắt được nếu chỉ kiểm số cảnh
   * báo.
   */
  it('hiện tỉ lệ cảnh báo có bằng chứng liên quan, không để lọt NaN', async () => {
    getAnalysisRunMock.mockResolvedValue(
      run({ findingCount: 34, linkCount: 123, unlinkedWarningCount: 4 }),
    );
    listWarningsMock.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage();

    expect(
      await screen.findByText('30/34 cảnh báo có bằng chứng liên quan (123 liên kết)'),
    ).toBeInTheDocument();
  });

  it('danh sách rỗng nói rõ là rỗng, không để màn hình trắng', async () => {
    getAnalysisRunMock.mockResolvedValue(run());
    listWarningsMock.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage();

    expect(await screen.findByText(/Không có cảnh báo nào khớp bộ lọc/)).toBeInTheDocument();
  });

  /** Run FAILED: màn hình này là chỗ DUY NHẤT người dùng đọc được `errorMessage`. */
  it('run FAILED hiện lý do hỏng thay vì một bảng trống', async () => {
    getAnalysisRunMock.mockResolvedValue(
      run({ status: 'FAILED', errorMessage: 'Khong doi chieu duoc vi tri nao', findingCount: 0 }),
    );
    listWarningsMock.mockRejectedValue(
      new ApiError(409, { type: 'run-not-queryable', title: 'Run chưa thể truy vấn' }),
    );

    renderPage();

    expect(await screen.findByText(/Khong doi chieu duoc vi tri nao/)).toBeInTheDocument();
  });

  /**
   * 409 chứ không phải danh sách rỗng — "chưa chạy xong" và "đúng là không có cảnh báo" phải nói
   * ra được hai câu khác nhau.
   */
  it('phân biệt "run chưa xong" với "không có cảnh báo nào"', async () => {
    getAnalysisRunMock.mockResolvedValue(run({ status: 'RUNNING', findingCount: 0 }));
    listWarningsMock.mockRejectedValue(
      new ApiError(409, { type: 'run-not-queryable', title: 'Run chưa thể truy vấn' }),
    );

    renderPage();

    expect(await screen.findByText(/chưa hoàn tất nên chưa có cảnh báo/)).toBeInTheDocument();
    expect(screen.queryByText(/Không có cảnh báo nào khớp bộ lọc/)).not.toBeInTheDocument();
  });

  it('run không tồn tại hiện thông báo riêng, không phải lỗi chung chung', async () => {
    getAnalysisRunMock.mockRejectedValue(new ApiError(404, { title: 'not found' }));

    renderPage();

    expect(await screen.findByText('Không tìm thấy lần phân tích này.')).toBeInTheDocument();
  });
});
