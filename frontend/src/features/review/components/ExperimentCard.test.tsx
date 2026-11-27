/**
 * Thẻ thực nghiệm 3 cấu hình: ba trạng thái mà chủ dự án thật sự gặp khi chạy rải nhiều ngày —
 * chưa chạy, đang chạy, và đã dừng giữa chừng (thường vì hết hạn mức model).
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ExperimentStatus } from '@/features/review/api/experimentApi';
import { ExperimentCard } from '@/features/review/components/ExperimentCard';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const getExperimentMock = vi.hoisted(() => vi.fn());
const startExperimentMock = vi.hoisted(() => vi.fn());
const stopExperimentMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/review/api/experimentApi', () => ({
  getExperiment: getExperimentMock,
  startExperiment: startExperimentMock,
  stopExperiment: stopExperimentMock,
  experimentCsvPath: (runId: string) => `/api/v1/analysis-runs/${runId}/experiment/export.csv`,
}));

function metrics(config: 'LLM_CODE' | 'LLM_STATIC' | 'EVIDENCE_AGENT', accuracy?: number) {
  return {
    config,
    succeededCells: accuracy === undefined ? 0 : 3,
    failedCells: 0,
    accuracy,
    accuracyByRepeat: [accuracy ?? null, null, null] as number[],
  };
}

function status(overrides: Partial<ExperimentStatus> = {}): ExperimentStatus {
  return {
    analysisRunId: 'run-1',
    running: false,
    repeats: 3,
    totalCells: 9,
    succeededCells: 0,
    failedCells: 0,
    configs: [metrics('LLM_CODE'), metrics('LLM_STATIC'), metrics('EVIDENCE_AGENT')],
    ...overrides,
  };
}

function renderCard() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ExperimentCard runId="run-1" />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  getExperimentMock.mockReset();
  startExperimentMock.mockReset();
  stopExperimentMock.mockReset();
});

describe('ExperimentCard', () => {
  it('chưa chạy: nút Chạy, tỉ lệ hiện dấu gạch chứ không phải 0%', async () => {
    getExperimentMock.mockResolvedValue(status());

    renderCard();

    expect(await screen.findByRole('button', { name: 'Chạy' })).toBeEnabled();
    expect(screen.getByText('0 / 9 ô xong · 3 lần lặp mỗi ô')).toBeInTheDocument();
    expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tải CSV' })).toBeDisabled();
  });

  it('bấm Chạy gọi máy chủ rồi chuyển sang trạng thái đang chạy', async () => {
    getExperimentMock.mockResolvedValue(status());
    startExperimentMock.mockResolvedValue(
      status({ running: true, currentCell: 'EV-0001 · LLM_CODE · lần 1' }),
    );

    renderCard();
    await userEvent.click(await screen.findByRole('button', { name: 'Chạy' }));

    expect(startExperimentMock).toHaveBeenCalledWith('run-1');
    expect(await screen.findByTestId('experiment-current')).toHaveTextContent(
      'Đang chạy: EV-0001 · LLM_CODE · lần 1',
    );
    expect(screen.getByRole('button', { name: 'Dừng' })).toBeEnabled();
  });

  it('dừng vì hết hạn mức: hiện lý do, nút Chạy tiếp, và số liệu của ô đã xong', async () => {
    getExperimentMock.mockResolvedValue(
      status({
        succeededCells: 3,
        failedCells: 3,
        lastStopReason: 'Dừng sau 3 ô hỏng liên tiếp. Lỗi cuối: het han muc',
        configs: [metrics('LLM_CODE', 0.5), metrics('LLM_STATIC', 1), metrics('EVIDENCE_AGENT')],
      }),
    );

    renderCard();

    expect(await screen.findByTestId('experiment-stop-reason')).toHaveTextContent(
      'Dừng sau 3 ô hỏng liên tiếp',
    );
    expect(screen.getByRole('button', { name: 'Chạy tiếp' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Tải CSV' })).toBeEnabled();
    expect(screen.getByText('50.0%')).toBeInTheDocument();
    expect(screen.getByText('100.0%')).toBeInTheDocument();
  });

  it('bấm Dừng khi đang chạy', async () => {
    getExperimentMock.mockResolvedValue(status({ running: true }));
    stopExperimentMock.mockResolvedValue(status({ lastStopReason: 'Đã dừng theo yêu cầu.' }));

    renderCard();
    await userEvent.click(await screen.findByRole('button', { name: 'Dừng' }));

    expect(stopExperimentMock).toHaveBeenCalledWith('run-1');
    await waitFor(() =>
      expect(screen.getByTestId('experiment-stop-reason')).toHaveTextContent(
        'Đã dừng theo yêu cầu.',
      ),
    );
  });
});
