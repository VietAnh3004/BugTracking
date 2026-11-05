/**
 * Luồng phân tích tự động: bấm → backend trả ngay run RUNNING (202) → hỏi lại tới khi xong.
 * `apiClient` mock theo URL; nhịp polling 2 giây là thật nên vài test chờ tối đa vài giây.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { SandboxRunButton, SandboxRunStatus } from '@/features/warnings/components/SandboxRun';
import { useSandboxRun } from '@/features/warnings/hooks/useWarnings';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import type { AnalysisRun } from '@/features/warnings/api/warningApi';

const getMock = vi.hoisted(() => vi.fn());
const postMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/apiClient')>();
  return { ...actual, apiClient: { ...actual.apiClient, get: getMock, post: postMock } };
});

const POLL_WAIT = { timeout: 4000 };

function Harness() {
  const sandbox = useSandboxRun('snap-1');
  return (
    <>
      <SandboxRunButton sandbox={sandbox} />
      <SandboxRunStatus sandbox={sandbox} snapshotId="snap-1" />
    </>
  );
}

function renderHarness() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <Harness />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function run(overrides: Partial<AnalysisRun>): AnalysisRun {
  return {
    id: 'run-1',
    snapshotId: 'snap-1',
    status: 'RUNNING',
    findingCount: 0,
    locationCount: 0,
    unresolvedLocationCount: 0,
    linkCount: 0,
    codeContextCount: 0,
    unlinkedWarningCount: 0,
    artifactSource: 'DOCKER_SANDBOX',
    startedAt: new Date().toISOString(),
    ...overrides,
  };
}

/** Danh sách run của snapshot + chuỗi trạng thái lần lượt trả về cho `GET /analysis-runs/run-1`. */
function mockBackend(runsList: AnalysisRun[], runStates: AnalysisRun[]) {
  let call = 0;
  getMock.mockImplementation((path: string) => {
    if (path === '/api/v1/snapshots/snap-1/analysis-runs') {
      return Promise.resolve({ items: runsList, total: runsList.length });
    }
    if (path === '/api/v1/analysis-runs/run-1') {
      const state = runStates[Math.min(call, runStates.length - 1)];
      call += 1;
      return Promise.resolve(state);
    }
    return Promise.reject(new Error(`URL ngoai du kien: ${path}`));
  });
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('Phân tích tự động (sandbox)', () => {
  it('bấm nút thì gọi đúng endpoint và khoá nút trong lúc chạy', async () => {
    mockBackend([], [run({})]);
    postMock.mockResolvedValue(run({}));
    renderHarness();

    await userEvent.click(await screen.findByRole('button', { name: 'Phân tích tự động' }));

    expect(postMock).toHaveBeenCalledWith('/api/v1/snapshots/snap-1/analysis-runs/sandbox');
    expect(await screen.findByRole('button', { name: 'Đang phân tích…' })).toBeDisabled();
    expect(screen.getByText(/Đang phân tích tự động/)).toBeInTheDocument();
  });

  it('hỏi lại trạng thái cho tới khi xong rồi hiện kết quả kèm link', async () => {
    mockBackend(
      [],
      [
        run({}),
        run({ status: 'SUCCEEDED', findingCount: 33, codeContextCount: 30, linkCount: 99 }),
      ],
    );
    postMock.mockResolvedValue(run({}));
    renderHarness();

    await userEvent.click(await screen.findByRole('button', { name: 'Phân tích tự động' }));

    expect(
      await screen.findByText(/Phân tích xong: 33 cảnh báo · 30 ngữ cảnh mã nguồn/, {}, POLL_WAIT),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem danh sách cảnh báo →' })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/runs/run-1/warnings',
    );
    expect(screen.getByRole('button', { name: 'Phân tích tự động' })).toBeEnabled();
  });

  it('run FAILED hiện lý do hỏng', async () => {
    mockBackend([], [run({ status: 'FAILED', errorMessage: 'mvn test het thoi gian' })]);
    postMock.mockResolvedValue(run({}));
    renderHarness();

    await userEvent.click(await screen.findByRole('button', { name: 'Phân tích tự động' }));

    expect(
      await screen.findByText('Phân tích tự động thất bại', {}, POLL_WAIT),
    ).toBeInTheDocument();
    expect(screen.getByText('mvn test het thoi gian')).toBeInTheDocument();
  });

  /**
   * Tải lại trang giữa lúc đang phân tích: phải thấy tiến trình, KHÔNG phải một nút bấm được lần
   * nữa — bấm lần hai là xếp thêm một lượt chạy vài phút vào hàng đợi một luồng của backend.
   */
  it('mở trang khi snapshot đang có run RUNNING thì hiện tiến trình và khoá nút', async () => {
    mockBackend([run({})], [run({})]);
    renderHarness();

    expect(await screen.findByRole('button', { name: 'Đang phân tích…' })).toBeDisabled();
    expect(screen.getByText(/Đang phân tích tự động/)).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it('sandbox chưa bật (503) thì nói rõ và chỉ sang đường nạp tay', async () => {
    mockBackend([], []);
    postMock.mockRejectedValue(
      new ApiError(503, { type: 'https://datn.hust.vn/problems/sandbox-unavailable' }),
    );
    renderHarness();

    await userEvent.click(await screen.findByRole('button', { name: 'Phân tích tự động' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sandbox chưa bật hoặc Docker không phản hồi',
    );
  });

  it('mã nguồn không phân tích được (409) thì hiện lý do cụ thể của backend', async () => {
    mockBackend([], []);
    postMock.mockRejectedValue(
      new ApiError(409, {
        type: 'https://datn.hust.vn/problems/unsupported-source-tree',
        detail: 'Khong tim thay pom.xml',
      }),
    );
    renderHarness();

    await userEvent.click(await screen.findByRole('button', { name: 'Phân tích tự động' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Khong tim thay pom.xml');
    });
  });
});
