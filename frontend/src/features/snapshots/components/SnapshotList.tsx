/**
 * Danh sách snapshot của một dự án. Render đủ bốn trạng thái (đang tải / lỗi / rỗng / có dữ liệu),
 * và snapshot `FAILED` hiện luôn `errorMessage` — đó chính là lý do endpoint metadata không đòi
 * trạng thái `READY` (xem `SnapshotQueryService`).
 */

import { Link } from 'react-router-dom';

import { useProjectSnapshots } from '@/features/snapshots/hooks/useSnapshots';
import { IconChevronRight, IconFolder } from '@/shared/ui/icons';
import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';

import type { Snapshot } from '@/features/snapshots/api/snapshotApi';

const STATUS: Record<Snapshot['status'], { tone: StatusTone; label: string }> = {
  READY: { tone: 'ok', label: 'Sẵn sàng' },
  PENDING: { tone: 'neutral', label: 'Đang chờ' },
  EXTRACTING: { tone: 'warning', label: 'Đang giải nén' },
  FAILED: { tone: 'danger', label: 'Lỗi' },
};

export function SnapshotList({ projectId }: { projectId: string }) {
  const { data, isPending, isError } = useProjectSnapshots(projectId);

  if (isPending) {
    return (
      <div className="px-4 py-3">
        <LoadingState label="Đang tải danh sách snapshot…" shape="lines" rows={3} />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-3">
        <ErrorState
          title="Không tải được danh sách snapshot."
          hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
        />
      </div>
    );
  }

  if (data.items.length === 0) {
    return (
      <EmptyState
        icon={<IconFolder className="size-5" />}
        title="Chưa có snapshot nào."
        description="Tải lên một file zip mã nguồn ở khung bên phải để bắt đầu."
      />
    );
  }

  return (
    <ul className="divide-border m-0 list-none divide-y p-0">
      {data.items.map((snapshot) => {
        const status = STATUS[snapshot.status];
        const name = snapshot.sourceRef ?? snapshot.id;
        const ready = snapshot.status === 'READY';
        return (
          <li
            key={snapshot.id}
            className={`relative flex items-center gap-3 px-4 py-2.5 ${ready ? 'hover:bg-surface-sunken' : ''}`}
          >
            <IconFolder className="text-ink-subtle size-4" />
            <div className="min-w-0 flex-1">
              {/* Chỉ snapshot READY mới duyệt được file — các trạng thái khác chưa có gì để xem.
                  Link phủ cả hàng (`after:absolute`) để bấm chỗ nào trên hàng cũng mở được. */}
              {ready ? (
                <Link
                  to={`/snapshots/${snapshot.id}`}
                  className="text-ink hover:text-brand block truncate font-semibold after:absolute after:inset-0"
                >
                  {name}
                </Link>
              ) : (
                <span className="text-ink block truncate font-semibold">{name}</span>
              )}
              <p className="text-ink-muted mt-0.5 mb-0 text-xs">
                {snapshot.fileCount} file · {formatBytes(snapshot.totalSizeBytes)} · tải lên{' '}
                {new Date(snapshot.createdAt).toLocaleString('vi-VN')}
              </p>
              {snapshot.errorMessage !== undefined ? (
                <p className="text-danger mt-1 mb-0 text-sm">{snapshot.errorMessage}</p>
              ) : null}
            </div>
            <StatusBadge tone={status.tone} label={status.label} />
            {ready ? <IconChevronRight className="text-ink-subtle size-4" /> : null}
          </li>
        );
      })}
    </ul>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${String(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
