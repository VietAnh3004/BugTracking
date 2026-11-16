/**
 * Trang gán nhãn: hai chế độ theo vai trò mà BACKEND trả về, và lưu nhãn luôn kèm lý do.
 *
 * Tính "mù" của người gán thứ hai do server bảo đảm (nó không gửi nhãn của chủ dự án xuống); test
 * ở đây canh phần giao diện: thành viên không thấy nút xuất/nạp file và đọc đúng lời dặn gán mù.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { GroundTruthPage } from '@/features/groundtruth/pages/GroundTruthPage';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import type { GroundTruthSheet } from '@/features/groundtruth/api/groundTruthApi';

const getMock = vi.hoisted(() => vi.fn());
const putMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/apiClient')>();
  return { ...actual, apiClient: { ...actual.apiClient, get: getMock, put: putMock } };
});

function sheet(overrides: Partial<GroundTruthSheet>): GroundTruthSheet {
  return {
    runId: 'run-1',
    role: 'OWNER',
    source: 'MANUAL',
    rows: [
      {
        shortId: 'EV-0001',
        tool: 'spotbugs',
        ruleId: 'WEAK_MESSAGE_DIGEST_MD5',
        severity: 'HIGH',
        filePath: 'src/PasswordHasher.java',
        startLine: 53,
        summary: 'MD5 is weak',
        seededHint: {
          label: 'TRUE_POSITIVE',
          rationale: 'TP-05: MD5 bam mat khau',
          labeler: 'seed',
          labeledAt: '2026-11-16T00:00:00Z',
        },
      },
    ],
    ...overrides,
  };
}

function renderPage() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/snapshots/s1/runs/run-1/ground-truth']}>
        <Routes>
          <Route
            path="/snapshots/:snapshotId/runs/:runId/ground-truth"
            element={<GroundTruthPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  getMock.mockReset();
  putMock.mockReset();
});

describe('GroundTruthPage', () => {
  it('chủ dự án: thấy gợi ý SEEDED và nút xuất/nạp file', async () => {
    getMock.mockResolvedValue(sheet({}));
    renderPage();

    expect(await screen.findByText(/người gán CHÍNH/)).toBeInTheDocument();
    expect(screen.getByText(/TP-05: MD5 bam mat khau/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xuất file nhãn' })).toBeInTheDocument();
    expect(screen.getByText('Nạp file nhãn')).toBeInTheDocument();
  });

  it('thành viên: lời dặn gán mù, không có nút xuất/nạp file', async () => {
    const memberRows = sheet({}).rows.map((row) => ({ ...row, seededHint: undefined }));
    getMock.mockResolvedValue(
      sheet({ role: 'MEMBER', source: 'BLIND_SECOND_RATER', rows: memberRows }),
    );
    renderPage();

    expect(await screen.findByText(/người gán THỨ HAI và gán MÙ/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Xuất file nhãn' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Gợi ý SEEDED/)).not.toBeInTheDocument();
  });

  it('chưa viết lý do thì không lưu được; có lý do thì gửi đúng nhãn và lý do', async () => {
    getMock.mockResolvedValue(sheet({}));
    putMock.mockResolvedValue({
      label: 'FALSE_POSITIVE',
      rationale: 'Doc ma thay chi la checksum',
      labeler: 'toi',
      labeledAt: '2026-11-16T00:00:00Z',
    });
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Báo nhầm' }));
    expect(screen.getByRole('button', { name: 'Lưu nhãn' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Lý do cho EV-0001'), 'Doc ma thay chi la checksum');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nhãn' }));

    expect(putMock).toHaveBeenCalledWith(
      '/api/v1/analysis-runs/run-1/evidence/EV-0001/ground-truth',
      {
        label: 'FALSE_POSITIVE',
        rationale: 'Doc ma thay chi la checksum',
      },
    );
  });
});
