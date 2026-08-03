/**
 * Canh lối vào đầu tiên của toàn bộ luồng nghiệp vụ: không tạo được dự án thì không có chỗ upload
 * repo, và cũng không xem được file nào. Thiếu màn hình này thì backend có đủ endpoint mà ứng dụng
 * vẫn không dùng được từ trình duyệt — đúng thứ đã lọt lưới khi kiểm chứng end-to-end bằng `curl`.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { CreateProjectForm } from '@/features/projects/components/CreateProjectForm';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const postMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/apiClient')>();
  return { ...actual, apiClient: { ...actual.apiClient, post: postMock } };
});

function renderForm() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <CreateProjectForm />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('CreateProjectForm', () => {
  it('gửi tên dự án tới đúng endpoint', async () => {
    postMock.mockResolvedValue({ id: 'p1', name: 'vulnshop' });
    renderForm();

    await userEvent.type(screen.getByLabelText('Tên dự án'), 'vulnshop');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo dự án' }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith('/api/v1/projects', {
        name: 'vulnshop',
        description: undefined,
      });
    });
  });

  /** Mô tả rỗng phải thành `undefined`, nếu không backend lưu chuỗi rỗng thay vì NULL. */
  it('không gửi chuỗi rỗng cho mô tả bỏ trống', async () => {
    postMock.mockResolvedValue({ id: 'p1' });
    renderForm();

    await userEvent.type(screen.getByLabelText('Tên dự án'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo dự án' }));

    await waitFor(() => {
      expect(postMock.mock.calls[0]?.[1]).toEqual({ name: 'x', description: undefined });
    });
  });

  it('gửi kèm mô tả khi có nhập', async () => {
    postMock.mockResolvedValue({ id: 'p1' });
    renderForm();

    await userEvent.type(screen.getByLabelText('Tên dự án'), 'vulnshop');
    await userEvent.type(screen.getByLabelText('Mô tả (không bắt buộc)'), 'case study');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo dự án' }));

    await waitFor(() => {
      expect(postMock.mock.calls[0]?.[1]).toEqual({ name: 'vulnshop', description: 'case study' });
    });
  });

  it('xoá trắng form sau khi tạo thành công để tạo tiếp được ngay', async () => {
    postMock.mockResolvedValue({ id: 'p1' });
    renderForm();

    const nameInput = screen.getByLabelText('Tên dự án');
    await userEvent.type(nameInput, 'vulnshop');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo dự án' }));

    await waitFor(() => {
      expect(nameInput).toHaveValue('');
    });
  });

  /** Backend trả `fieldErrors` cho lỗi validate — hiện chúng thay vì một thông điệp chung chung. */
  it('hiển thị fieldErrors của backend', async () => {
    postMock.mockRejectedValue(
      new ApiError(400, {
        type: 'https://datn.hust.vn/problems/validation-failed',
        fieldErrors: { name: 'Tên dự án không được để trống' },
      }),
    );
    renderForm();

    await userEvent.type(screen.getByLabelText('Tên dự án'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo dự án' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Tên dự án không được để trống');
  });
});
