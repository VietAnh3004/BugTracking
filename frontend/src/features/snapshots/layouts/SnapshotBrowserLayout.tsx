/**
 * Khung duyệt snapshot: cây file bên trái, nội dung file bên phải (`Outlet`).
 *
 * Cây file đặt Ở ĐÂY chứ không trong `SourceViewerPage` vì layout route KHÔNG bị unmount khi đổi
 * route con — nhờ vậy chuyển từ file này sang file khác vẫn giữ nguyên thư mục đang mở và vị trí
 * cuộn của cây. Nếu cây nằm trong trang, mỗi lần bấm một file là cây dựng lại từ đầu và người dùng
 * mất chỗ đang đứng.
 */

import { Link, Outlet, useParams } from 'react-router-dom';

import { FileTree } from '@/features/snapshots/components/FileTree';
import { useSnapshot, useSnapshotFiles } from '@/features/snapshots/hooks/useSnapshots';
import { buildFileTree } from '@/features/snapshots/lib/buildFileTree';
import { fromRouteParam } from '@/features/snapshots/lib/filePathUrl';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { StatusBadge } from '@/shared/ui/StatusBadge';

export function SnapshotBrowserLayout() {
  const params = useParams<{ snapshotId: string; '*': string }>();
  const snapshotId = params.snapshotId ?? '';
  const activePath = fromRouteParam(params['*']);

  const snapshot = useSnapshot(snapshotId);
  const files = useSnapshotFiles(snapshotId);

  if (snapshot.isPending || files.isPending) {
    return <FullPageSpinner label="Đang tải cây file…" />;
  }

  if (files.isError) {
    return <SnapshotLoadError error={files.error} />;
  }
  if (snapshot.isError) {
    return <SnapshotLoadError error={snapshot.error} />;
  }

  const tree = buildFileTree(files.data);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <Link to={`/projects/${snapshot.data.projectId}`} className="text-brand text-sm">
          ← Về dự án
        </Link>
        <span className="text-ink-muted text-sm">
          {snapshot.data.fileCount} file · {snapshot.data.sourceRef ?? snapshot.data.id}
        </span>
      </div>

      {/* Cây cuộn độc lập với nội dung file: repo vài trăm file thì cột trái luôn dài hơn màn hình. */}
      <div className="grid gap-4 md:grid-cols-[minmax(0,18rem)_minmax(0,1fr)]">
        <nav
          aria-label="Cây file"
          className="border-border max-h-[70vh] overflow-auto border-r pr-2"
        >
          <FileTree nodes={tree} snapshotId={snapshotId} activePath={activePath} />
        </nav>

        <div className="min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

function SnapshotLoadError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.problem.type?.endsWith('snapshot-not-ready') === true) {
    // 409 chứ không phải danh sách rỗng: phân biệt "chưa giải nén xong" với "đúng là rỗng".
    return <StatusBadge tone="warning" label="Snapshot chưa giải nén xong, thử lại sau ít phút." />;
  }
  if (error instanceof ApiError && error.status === 404) {
    return <StatusBadge tone="danger" label="Không tìm thấy snapshot này." />;
  }
  return <StatusBadge tone="danger" label="Không tải được snapshot." />;
}
