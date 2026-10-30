/**
 * Test panel nạp artifact. `uploadClient` được mock ở tầng module, cùng lý do với
 * `UploadZone.test.tsx`: thứ đáng kiểm là hành vi giao diện (gom file, field gửi đi, đọc đúng
 * `status` của run), không phải bản thân XHR.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { ArtifactUploadPanel } from '@/features/warnings/components/ArtifactUploadPanel';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import type { AnalysisRun } from '@/features/warnings/api/warningApi';

const uploadFormMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/uploadClient', () => ({
  uploadForm: uploadFormMock,
}));

function renderPanel() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <ArtifactUploadPanel snapshotId="snap-1" onClose={() => undefined} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function xml(name: string) {
  return new File(['<BugCollection/>'], name, { type: 'text/xml', lastModified: 1 });
}

function run(overrides: Partial<AnalysisRun>): AnalysisRun {
  return {
    id: 'run-1',
    snapshotId: 'snap-1',
    status: 'SUCCEEDED',
    findingCount: 34,
    locationCount: 103,
    unresolvedLocationCount: 0,
    linkCount: 210,
    codeContextCount: 30,
    unlinkedWarningCount: 0,
    ...overrides,
  } as AnalysisRun;
}

function chooseFiles(...files: File[]) {
  return userEvent.upload(screen.getByLabelText('Chọn file'), files);
}

function selectedNames(): string[] {
  const list = screen.queryByRole('list', { name: 'File đã chọn' });
  if (list === null) {
    return [];
  }
  return within(list)
    .getAllByRole('listitem')
    .map((item) => item.querySelector('.font-mono')?.textContent ?? '');
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('ArtifactUploadPanel', () => {
  it('chưa chọn file thì không nạp được', () => {
    renderPanel();
    expect(screen.getByRole('button', { name: 'Nạp 0 file' })).toBeDisabled();
  });

  /** Hộp thoại chọn file chỉ chọn trong MỘT thư mục — phải gom được qua nhiều lần chọn. */
  it('gom file qua nhiều lần chọn và không thêm trùng', async () => {
    renderPanel();

    await chooseFiles(xml('spotbugs.xml'), xml('jacoco.xml'));
    await chooseFiles(xml('TEST-A.xml'), xml('spotbugs.xml'));

    expect(selectedNames()).toEqual(['spotbugs.xml', 'jacoco.xml', 'TEST-A.xml']);
    expect(screen.getByRole('button', { name: 'Nạp 3 file' })).toBeEnabled();
  });

  it('kéo thả file vào vùng cũng thêm vào danh sách', () => {
    renderPanel();

    const dropZone = screen.getByText('Kéo thả file vào đây, hoặc').parentElement as HTMLElement;
    fireEvent.drop(dropZone, { dataTransfer: { files: [xml('semgrep.sarif')] } });

    expect(selectedNames()).toEqual(['semgrep.sarif']);
  });

  it('bỏ được một file khỏi danh sách', async () => {
    renderPanel();
    await chooseFiles(xml('spotbugs.xml'), xml('jacoco.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Bỏ jacoco.xml' }));

    expect(selectedNames()).toEqual(['spotbugs.xml']);
  });

  /** Backend nhận MỘT field lặp `artifacts` — đặt tên khác là backend nhận về danh sách rỗng. */
  it('gửi mọi file chung field artifacts tới đúng endpoint', async () => {
    uploadFormMock.mockResolvedValue(run({}));
    renderPanel();
    await chooseFiles(xml('spotbugs.xml'), xml('TEST-A.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Nạp 2 file' }));

    await waitFor(() => {
      expect(uploadFormMock).toHaveBeenCalledTimes(1);
    });
    const [path, formData] = uploadFormMock.mock.calls[0] as [string, FormData];
    expect(path).toBe('/api/v1/snapshots/snap-1/analysis-runs');
    expect((formData.getAll('artifacts') as File[]).map((file) => file.name)).toEqual([
      'spotbugs.xml',
      'TEST-A.xml',
    ]);
  });

  it('nạp xong thì hiện số liệu và link sang danh sách cảnh báo', async () => {
    uploadFormMock.mockResolvedValue(run({}));
    renderPanel();
    await chooseFiles(xml('spotbugs.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Nạp 1 file' }));

    expect(await screen.findByText(/34 cảnh báo · 30 ngữ cảnh mã nguồn/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem danh sách cảnh báo →' })).toHaveAttribute(
      'href',
      '/snapshots/snap-1/runs/run-1/warnings',
    );
    expect(selectedNames()).toEqual([]);
  });

  /**
   * Backend trả 201 kể cả khi run FAILED — "không ném lỗi" KHÔNG có nghĩa là thành công. Giữ
   * danh sách file để người dùng sửa rồi thử lại.
   */
  it('run FAILED hiện lý do hỏng và giữ nguyên danh sách file', async () => {
    uploadFormMock.mockResolvedValue(
      run({ status: 'FAILED', findingCount: 0, errorMessage: 'Khong co canh bao tinh nao' }),
    );
    renderPanel();
    await chooseFiles(xml('jacoco.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Nạp 1 file' }));

    expect(await screen.findByText('Phân tích thất bại')).toBeInTheDocument();
    expect(screen.getByText('Khong co canh bao tinh nao')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Xem danh sách cảnh báo →' })).toBeNull();
    expect(selectedNames()).toEqual(['jacoco.xml']);
  });

  it('báo vị trí không khớp với snapshot khi có', async () => {
    uploadFormMock.mockResolvedValue(run({ unresolvedLocationCount: 5, locationCount: 103 }));
    renderPanel();
    await chooseFiles(xml('spotbugs.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Nạp 1 file' }));

    expect(await screen.findByText(/5\/103 vị trí không khớp/)).toBeInTheDocument();
  });

  it('ánh xạ lỗi invalid-artifact của backend sang thông điệp dễ hiểu', async () => {
    uploadFormMock.mockRejectedValue(
      new ApiError(400, {
        type: 'https://datn.hust.vn/problems/invalid-artifact',
        detail: "Khong nhan dang duoc 'anh.png'",
      }),
    );
    renderPanel();
    await chooseFiles(xml('anh.xml'));

    await userEvent.click(screen.getByRole('button', { name: 'Nạp 1 file' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Có file không phải kết quả của analyzer được hỗ trợ: Khong nhan dang duoc 'anh.png'",
    );
  });
});
