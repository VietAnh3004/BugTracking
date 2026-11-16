/**
 * Trạng thái tìm-theo-nghĩa của một lần phân tích — chỉ hiện khi CÓ VẤN ĐỀ.
 *
 * Embedding hỏng không làm hỏng lần phân tích (tìm kiếm lùi về so chữ), nhưng không được im lặng:
 * Agent nhận "không tìm thấy" từ một câu hỏi tự do rồi kết luận "chưa đủ bằng chứng" nhầm, và
 * người dùng phải thấy được vì sao. Chủ dự án có nút sinh lại; thành viên chỉ đọc lý do.
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { warningKeys } from '@/features/warnings/hooks/useWarnings';
import { ApiError, apiClient } from '@/shared/lib/apiClient';
import { buttonClass } from '@/shared/ui/styles';

import type { AnalysisRun } from '@/features/warnings/api/warningApi';

export function EmbeddingNotice({ run, isOwner }: { run: AnalysisRun; isOwner: boolean }) {
  const queryClient = useQueryClient();
  const rebuild = useMutation({
    mutationFn: () => apiClient.post<AnalysisRun>(`/api/v1/analysis-runs/${run.id}/embeddings`),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: warningKeys.run(run.id) }),
  });

  if (run.embeddingError == null && !rebuild.isError) {
    return null;
  }

  const reason = rebuild.isError
    ? rebuild.error instanceof ApiError
      ? (rebuild.error.problem.detail ?? rebuild.error.message)
      : 'Không gọi được backend.'
    : run.embeddingError;

  return (
    <div
      role="status"
      className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
    >
      <p className="m-0 max-w-3xl">
        <strong>Tìm theo nghĩa chưa chạy cho lần phân tích này</strong> — Agent chỉ tìm được bằng so
        khớp chữ. Lý do: <span className="font-mono text-xs break-all">{reason}</span>
      </p>
      {isOwner ? (
        <button
          type="button"
          disabled={rebuild.isPending}
          onClick={() => rebuild.mutate()}
          className={buttonClass('secondary', 'sm')}
        >
          {rebuild.isPending ? 'Đang sinh vector…' : 'Sinh lại vector'}
        </button>
      ) : null}
    </div>
  );
}
