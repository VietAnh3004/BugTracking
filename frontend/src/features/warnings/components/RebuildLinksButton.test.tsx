/**
 * Nút chạy lại liên kết: gọi đúng endpoint, nói ra SỐ liên kết sau khi dựng lại, và báo lỗi đọc
 * được thay vì im lặng.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { RebuildLinksButton } from '@/features/warnings/components/RebuildLinksButton';
import { ApiError } from '@/shared/lib/apiClient';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const postMock = vi.hoisted(() => vi.fn());

vi.mock('@/shared/lib/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/apiClient')>();
  return { ...actual, apiClient: { ...actual.apiClient, post: postMock } };
});

function renderButton() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RebuildLinksButton runId="run-1" />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  postMock.mockReset();
});

describe('RebuildLinksButton', () => {
  it('gọi endpoint dựng lại liên kết của đúng run và hiện số liệu trả về', async () => {
    postMock.mockResolvedValue({
      runId: 'run-1',
      deletedCount: 130,
      linkCount: 132,
      unlinkedWarningCount: 0,
      countByMethod: {},
    });
    renderButton();

    await userEvent.click(screen.getByRole('button', { name: 'Chạy lại liên kết' }));

    expect(postMock).toHaveBeenCalledWith('/api/v1/analysis-runs/run-1/links/rebuild');
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã dựng lại 132 liên kết (xoá 130 cũ) · 0 cảnh báo không có liên kết.',
    );
  });

  it('lỗi từ backend hiện thành câu đọc được', async () => {
    postMock.mockRejectedValue(
      new ApiError(409, {
        type: 'https://datn.hust.vn/problems/run-not-queryable',
        title: 'Conflict',
        status: 409,
        detail: 'Lần phân tích chưa hoàn tất.',
      }),
    );
    renderButton();

    await userEvent.click(screen.getByRole('button', { name: 'Chạy lại liên kết' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Lần phân tích chưa hoàn tất.');
  });
});
