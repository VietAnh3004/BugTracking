/**
 * Trang một dự án: xem các snapshot đã có và tải repo mới lên.
 *
 * Chỉ ráp giao diện; dữ liệu do các component con tự lo qua hook riêng của chúng (§1bis.3).
 */

import { useParams } from 'react-router-dom';

import { MembersPanel } from '@/features/projects/components/MembersPanel';
import { useProject } from '@/features/projects/hooks/useProjects';
import { SnapshotList } from '@/features/snapshots/components/SnapshotList';
import { UploadZone } from '@/features/snapshots/components/UploadZone';
import { PageHeader } from '@/shared/ui/PageHeader';
import { CARD } from '@/shared/ui/styles';

export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();

  // Không thể xảy ra với route `/projects/:projectId`, nhưng `useParams` khai kiểu optional nên
  // phải xử lý tường minh thay vì dùng `!`.
  if (projectId === undefined) {
    return <p className="text-red-700">Thiếu định danh dự án trên đường dẫn.</p>;
  }

  return <ProjectDetail projectId={projectId} />;
}

function ProjectDetail({ projectId }: { projectId: string }) {
  const project = useProject(projectId);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        back={{ to: '/projects', label: 'Tất cả dự án' }}
        // Tên dự án chưa về thì để tiêu đề trung tính chứ không để trống: một khoảng trắng ở chỗ
        // tiêu đề trông như trang hỏng.
        title={project.data?.name ?? 'Dự án'}
        description={
          project.data?.description ??
          'Chọn một snapshot để duyệt mã nguồn, chạy phân tích và xem cảnh báo.'
        }
      />

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <section className={`${CARD} overflow-hidden`}>
          <div className="border-border border-b px-5 py-4">
            <h2 className="text-ink m-0 text-base font-semibold">Snapshot</h2>
            <p className="text-ink-muted mt-0.5 mb-0 text-sm">
              Mỗi lần tải lên là một snapshot bất biến của mã nguồn.
            </p>
          </div>
          <SnapshotList projectId={projectId} />
        </section>

        {project.data?.role === 'OWNER' ? (
          <div className="flex flex-col gap-6">
            <section className={`${CARD} p-5`}>
              <h2 className="text-ink mt-0 mb-1 text-base font-semibold">Tải mã nguồn lên</h2>
              <p className="text-ink-muted mt-0 mb-4 text-sm">
                Nén thư mục dự án (có <code className="font-mono text-xs">pom.xml</code>) thành một
                file zip.
              </p>
              <UploadZone projectId={projectId} />
            </section>
            <MembersPanel projectId={projectId} />
          </div>
        ) : project.data?.role === 'MEMBER' ? (
          <section className={`${CARD} text-ink-muted p-5 text-sm`}>
            <h2 className="text-ink mt-0 mb-1 text-base font-semibold">Dự án được chia sẻ</h2>
            Bạn là thành viên: xem mã và bằng chứng, hỏi Agent, đánh giá, và gán nhãn với vai trò
            người gán thứ hai. Chỉ chủ dự án tải mã và chạy phân tích được.
          </section>
        ) : null}
      </div>
    </div>
  );
}
