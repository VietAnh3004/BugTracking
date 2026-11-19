/**
 * Chạy lại EvidenceLinker cho một lần phân tích.
 *
 * Dùng khi đã sửa thuật toán liên kết (khớp dòng, quy ước tên, phân loại frame) và muốn áp lên dữ
 * liệu cũ mà không chạy lại analyzer. Evidence ID KHÔNG đổi — điều kiện để các trích dẫn Agent đã
 * đưa ra vẫn trỏ đúng chỗ.
 *
 * Kết quả nói ra SỐ liên kết trước/sau chứ không chỉ "xong": chạy lại mà số không đổi cũng là một
 * thông tin (heuristic vừa sửa không chạm tới run này), và một lần chạy lại làm số tụt mạnh là
 * dấu hiệu sửa hỏng — người dùng phải thấy được điều đó ngay.
 */

import { useRebuildLinks } from '@/features/warnings/hooks/useWarnings';
import { ApiError } from '@/shared/lib/apiClient';
import { buttonClass } from '@/shared/ui/styles';

export function RebuildLinksButton({ runId }: { runId: string }) {
  const rebuild = useRebuildLinks(runId);

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        onClick={() => rebuild.mutate()}
        disabled={rebuild.isPending}
        title="Tính lại liên kết giữa cảnh báo, mã nguồn, test và độ phủ. Không chạy lại analyzer, Evidence ID giữ nguyên."
        className={buttonClass('ghost')}
      >
        {rebuild.isPending ? 'Đang chạy lại liên kết…' : 'Chạy lại liên kết'}
      </button>

      {rebuild.isSuccess ? (
        <p role="status" className="text-ink-muted m-0 text-xs">
          Đã dựng lại {rebuild.data.linkCount} liên kết (xoá {rebuild.data.deletedCount} cũ) ·{' '}
          {rebuild.data.unlinkedWarningCount} cảnh báo không có liên kết.
        </p>
      ) : null}

      {rebuild.isError ? (
        <p role="alert" className="text-danger m-0 text-xs">
          {rebuild.error instanceof ApiError
            ? (rebuild.error.problem.detail ?? rebuild.error.message)
            : 'Không gọi được backend.'}
        </p>
      ) : null}
    </div>
  );
}
