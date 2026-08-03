/**
 * Trang một dự án: tải repo lên và xem các snapshot đã có.
 *
 * Chỉ ráp giao diện; dữ liệu do các component con tự lo qua hook riêng của chúng (§1bis.3).
 */

import { useParams } from 'react-router-dom';

import { SnapshotList } from '@/features/snapshots/components/SnapshotList';
import { UploadZone } from '@/features/snapshots/components/UploadZone';

export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // Không thể xảy ra với route `/projects/:projectId`, nhưng `useParams` khai kiểu optional nên
  // phải xử lý tường minh thay vì dùng `!`.
  if (projectId === undefined) {
    return <p className="text-red-700">Thiếu định danh dự án trên đường dẫn.</p>;
  }

  return (
    <section className="flex flex-col gap-6">
      <div>
        <h2 className="mt-0 mb-3 text-lg font-semibold">Tải mã nguồn lên</h2>
        <UploadZone projectId={projectId} />
      </div>

      <div>
        <h2 className="mt-0 mb-3 text-lg font-semibold">Snapshot</h2>
        <SnapshotList projectId={projectId} />
      </div>
    </section>
  );
}
