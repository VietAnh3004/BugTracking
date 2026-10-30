/**
 * Xoá dự án kéo theo dữ liệu đo của thực nghiệm (hội thoại, review) — không tạo lại được. Bộ test
 * này canh đúng một điều: không có đường nào gọi được DELETE mà không gõ đúng tên dự án.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { DeleteProjectButton } from '@/features/projects/components/DeleteProjectButton';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const deleteMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/apiClient')>();
  return { ...actual, apiClient: { ...actual.apiClient, delete: deleteMock } };
});

function renderButton() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <DeleteProjectButton projectId="p1" projectName="vulnshop" />
    </QueryClientProvider>,
  );
}

async function openConfirm() {
  await userEvent.click(screen.getByRole('button', { name: 'Xoá' }));
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('DeleteProjectButton', () => {
  it('khoá nút xoá vĩnh viễn cho tới khi gõ đúng tên dự án', async () => {
    renderButton();
    await openConfirm();

    const confirmButton = screen.getByRole('button', { name: 'Xoá vĩnh viễn' });
    expect(confirmButton).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'vulnsho');
    expect(confirmButton).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'p');
    expect(confirmButton).toBeEnabled();
  });

  /** Enter trong ô nhập submit form — đường này cũng phải bị chặn khi tên chưa khớp. */
  it('không gọi API khi nhấn Enter với tên sai', async () => {
    renderButton();
    await openConfirm();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'sai-ten{Enter}');

    expect(deleteMock).not.toHaveBeenCalled();
  });

  it('gọi DELETE tới đúng endpoint khi đã xác nhận', async () => {
    deleteMock.mockResolvedValue(undefined);
    renderButton();
    await openConfirm();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'vulnshop');
    await userEvent.click(screen.getByRole('button', { name: 'Xoá vĩnh viễn' }));

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith('/api/v1/projects/p1');
    });
  });

  it('huỷ thì đóng khung xác nhận và không gọi API', async () => {
    renderButton();
    await openConfirm();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'vulnshop');
    await userEvent.click(screen.getByRole('button', { name: 'Huỷ' }));

    expect(screen.queryByLabelText('Gõ tên dự án để xác nhận')).not.toBeInTheDocument();
    expect(deleteMock).not.toHaveBeenCalled();

    // Mở lại phải bắt gõ lại từ đầu, không giữ tên đã gõ lần trước.
    await openConfirm();
    expect(screen.getByLabelText('Gõ tên dự án để xác nhận')).toHaveValue('');
  });

  it('hiển thị lỗi của backend', async () => {
    deleteMock.mockRejectedValue(
      new ApiError(404, {
        type: 'https://datn.hust.vn/problems/not-found',
        detail: 'Không tìm thấy project',
      }),
    );
    renderButton();
    await openConfirm();

    await userEvent.type(screen.getByLabelText('Gõ tên dự án để xác nhận'), 'vulnshop');
    await userEvent.click(screen.getByRole('button', { name: 'Xoá vĩnh viễn' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không tìm thấy project');
  });
});
