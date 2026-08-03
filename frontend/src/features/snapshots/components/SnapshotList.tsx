/**
 * Danh sách snapshot của một dự án. Render đủ bốn trạng thái (đang tải / lỗi / rỗng / có dữ liệu),
 * và snapshot `FAILED` hiện luôn `errorMessage` — đó chính là lý do endpoint metadata không đòi
 * trạng thái `READY` (xem `SnapshotQueryService`).
 */

import { Link } from 'react-router-dom';

import { useProjectSnapshots } from '@/features/snapshots/hooks/useSnapshots';
import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';

import type { Snapshot } from '@/features/snapshots/api/snapshotApi';

const STATUS_TONES: Record<Snapshot['status'], StatusTone> = {
  READY: 'ok',
  PENDING: 'neutral',
  EXTRACTING: 'warning',
  FAILED: 'danger',
};

export function SnapshotList({ projectId }: { projectId: string }) {
  const { data, isPending, isError } = useProjectSnapshots(projectId);

  if (isPending) {
    return <StatusBadge tone="neutral" label="Đang tải danh sách snapshot…" />;
  }

  if (isError) {
    return <StatusBadge tone="danger" label="Không tải được danh sách snapshot" />;
  }

  if (data.items.length === 0) {
    return (
      <p className="text-ink-muted text-sm">
        Chưa có snapshot nào. Tải lên một file zip để bắt đầu.
      </p>
    );
  }

  return (
    <ul className="m-0 list-none p-0">
      {data.items.map((snapshot) => (
        <li key={snapshot.id} className="border-border border-b py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              {/* Chỉ snapshot READY mới duyệt được file — các trạng thái khác chưa có gì để xem. */}
              {snapshot.status === 'READY' ? (
                <Link to={`/snapshots/${snapshot.id}`} className="text-brand font-medium">
                  {snapshot.sourceRef ?? snapshot.id}
                </Link>
              ) : (
                <span className="font-medium">{snapshot.sourceRef ?? snapshot.id}</span>
              )}
              <p className="text-ink-muted mt-1 mb-0 text-sm">
                {snapshot.fileCount} file · {formatBytes(snapshot.totalSizeBytes)}
              </p>
              {snapshot.errorMessage !== undefined ? (
                <p className="mt-1 mb-0 text-sm text-red-700">{snapshot.errorMessage}</p>
              ) : null}
            </div>
            <StatusBadge tone={STATUS_TONES[snapshot.status]} label={snapshot.status} />
          </div>
        </li>
      ))}
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
