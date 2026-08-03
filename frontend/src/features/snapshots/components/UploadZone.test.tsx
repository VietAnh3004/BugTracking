/**
 * Test luồng upload. `uploadClient` được mock ở tầng module: `XMLHttpRequest` thật không tồn tại
 * đầy đủ trong happy-dom, và cái đáng kiểm ở đây là hành vi của giao diện (tiến trình, ánh xạ lỗi
 * của backend sang thông điệp người dùng đọc được), không phải bản thân XHR.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { UploadZone } from '@/features/snapshots/components/UploadZone';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const uploadFileMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/uploadClient', () => ({
  uploadFile: uploadFileMock,
}));

function renderUploadZone() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <UploadZone projectId="project-1" />
    </QueryClientProvider>,
  );
}

function zipFile() {
  return new File(['PKnoi dung gia'], 'repo.zip', { type: 'application/zip' });
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('UploadZone', () => {
  /** Kéo-thả một mình là lỗi tiếp cận: phải có đường thao tác bằng bàn phím. */
  it('có nút chọn file thao tác được bằng bàn phím, không chỉ kéo thả', async () => {
    uploadFileMock.mockResolvedValue({ id: 's1', status: 'READY' });
    renderUploadZone();

    const input = screen.getByLabelText('Chọn file zip');

    await userEvent.upload(input, zipFile());

    await waitFor(() => {
      expect(uploadFileMock).toHaveBeenCalledTimes(1);
    });
  });

  /**
   * Nhánh kéo-thả đi qua `onDrop`/`dataTransfer`, hoàn toàn khác nhánh `<input type="file">` ở
   * trên — một nhánh chạy được không bảo đảm nhánh kia chạy được.
   */
  it('kéo thả file vào vùng cũng bắt đầu tải lên', async () => {
    uploadFileMock.mockResolvedValue({ id: 's1', status: 'READY' });
    renderUploadZone();

    const dropZone = screen.getByText(/Kéo thả file .zip/).parentElement;
    expect(dropZone).not.toBeNull();

    fireEvent.drop(dropZone as HTMLElement, { dataTransfer: { files: [zipFile()] } });

    await waitFor(() => {
      expect(uploadFileMock).toHaveBeenCalledTimes(1);
    });
  });

  it('gửi đúng endpoint upload của dự án đang mở', async () => {
    uploadFileMock.mockResolvedValue({ id: 's1', status: 'READY' });
    renderUploadZone();

    await userEvent.upload(screen.getByLabelText('Chọn file zip'), zipFile());

    await waitFor(() => {
      expect(uploadFileMock).toHaveBeenCalledWith(
        '/api/v1/projects/project-1/snapshots',
        expect.any(File),
        expect.objectContaining({ onProgress: expect.any(Function) as unknown }),
      );
    });
  });

  it('dịch lỗi archive-limit-exceeded của backend sang thông điệp người dùng đọc được', async () => {
    uploadFileMock.mockRejectedValue(
      new ApiError(413, { type: 'https://datn.hust.vn/problems/archive-limit-exceeded' }),
    );
    renderUploadZone();

    await userEvent.upload(screen.getByLabelText('Chọn file zip'), zipFile());

    expect(await screen.findByRole('alert')).toHaveTextContent(/vượt giới hạn cho phép/);
  });

  it('dịch lỗi invalid-archive sang thông điệp nói rõ file không phải zip hợp lệ', async () => {
    uploadFileMock.mockRejectedValue(
      new ApiError(400, { type: 'https://datn.hust.vn/problems/invalid-archive' }),
    );
    renderUploadZone();

    await userEvent.upload(screen.getByLabelText('Chọn file zip'), zipFile());

    expect(await screen.findByRole('alert')).toHaveTextContent(/không phải zip hợp lệ/);
  });

  it('hiển thị tiến trình khi đang tải', async () => {
    // Giữ promise treo để quan sát trạng thái đang tải, và bắn tiến trình qua callback thật.
    uploadFileMock.mockImplementation(
      (_path: string, _file: File, options: { onProgress?: (p: number) => void }) => {
        options.onProgress?.(42);
        return new Promise(() => {});
      },
    );
    renderUploadZone();

    await userEvent.upload(screen.getByLabelText('Chọn file zip'), zipFile());

    expect(await screen.findByRole('status')).toHaveTextContent('42%');
  });
});
