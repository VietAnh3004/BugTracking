/**
 * Danh sách snapshot của một dự án. Render đủ bốn trạng thái (đang tải / lỗi / rỗng / có dữ liệu),
 * và snapshot `FAILED` hiện luôn `errorMessage` — đó chính là lý do endpoint metadata không đòi
 * trạng thái `READY` (xem `SnapshotQueryService`).
 */

import { Link } from 'react-router-dom';

import { useProjectSnapshots } from '@/features/snapshots/hooks/useSnapshots';
import { IconChevronRight, IconFolder } from '@/shared/ui/icons';
import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';

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
      <div className="px-5 py-4">
        <StatusBadge tone="neutral" label="Đang tải danh sách snapshot…" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="px-5 py-4">
        <StatusBadge tone="danger" label="Không tải được danh sách snapshot" />
      </div>
    );
  }

  if (data.items.length === 0) {
    return (
      <p className="text-ink-muted m-0 px-5 py-10 text-center text-sm">
        Chưa có snapshot nào. Tải lên một file zip để bắt đầu.
      </p>
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
            className={`relative flex items-center gap-3 px-5 py-3.5 ${ready ? 'hover:bg-surface-muted' : ''}`}
          >
            <span className="bg-surface-sunken text-ink-muted grid size-9 shrink-0 place-items-center rounded-lg">
              <IconFolder className="size-4.5" />
            </span>
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
                <p className="mt-1 mb-0 text-sm text-red-700">{snapshot.errorMessage}</p>
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
