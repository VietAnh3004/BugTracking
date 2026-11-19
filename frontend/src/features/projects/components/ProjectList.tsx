/**
 * Hiển thị danh sách dự án. Component chỉ ráp giao diện; logic lấy dữ liệu nằm trong
 * `useProjects`. Cả ba trạng thái bất đồng bộ đều render, không chỉ trường hợp thành công —
 * và trường hợp danh sách rỗng cũng có thông điệp riêng (khác với "đang tải").
 */

import { Link } from 'react-router-dom';

import { DeleteProjectButton } from '@/features/projects/components/DeleteProjectButton';
import { useProjects } from '@/features/projects/hooks/useProjects';
import { ApiError } from '@/shared/lib/apiClient';
import { IconArrowRight, IconBox } from '@/shared/ui/icons';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { CARD } from '@/shared/ui/styles';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';

export function ProjectList() {
  const { data, isPending, isError, error } = useProjects();

  if (isPending) {
    return <LoadingState label="Đang tải danh sách dự án…" shape="lines" rows={4} />;
  }

  if (isError) {
    return (
      <ErrorState
        title="Không tải được danh sách dự án."
        message={
          error instanceof ApiError
            ? `HTTP ${error.status}: ${error.message}`
            : 'Không gọi được backend'
        }
        hint="Kiểm tra trạng thái backend ở chân trang rồi tải lại trang."
      />
    );
  }

  if (data.items === undefined || data.items.length === 0) {
    return (
      <div className={CARD}>
        <EmptyState
          icon={<IconBox className="size-5" />}
          title="Chưa có dự án nào."
          description="Tạo dự án đầu tiên ở khung bên cạnh, rồi tải mã nguồn dạng zip lên để phân tích."
        />
      </div>
    );
  }

  return (
    // Một danh sách dày trong MỘT khung, ngăn bằng đường kẻ — kiểu danh sách repo của IDE, không
    // phải lưới thẻ rời nhau kiểu landing page.
    <ul className={`${CARD} divide-border m-0 list-none divide-y p-0`}>
      {data.items.map((project) => (
        <li
          key={project.id}
          className="group hover:bg-surface-sunken flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3 transition-colors"
        >
          <div className="flex min-w-0 items-start gap-2.5">
            <IconBox className="text-ink-subtle mt-0.5 size-4" />
            <div className="min-w-0">
              <Link
                to={`/projects/${project.id}`}
                className="text-ink hover:text-brand inline-flex items-center gap-1 text-sm font-semibold"
              >
                {project.name}
                <IconArrowRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
              </Link>
              {project.tier === 'PREMIUM' ? (
                <span className="ml-2 align-middle">
                  <StatusBadge tone="ok" label="Premium" />
                </span>
              ) : null}
              {project.description ? (
                <p className="text-ink-muted mt-0.5 mb-0 text-sm">{project.description}</p>
              ) : null}
              <p className="text-ink-subtle mt-1 mb-0 text-xs">
                Tạo ngày {formatDate(project.createdAt)}
              </p>
            </div>
          </div>
          {project.role === 'OWNER' ? (
            <DeleteProjectButton projectId={project.id} projectName={project.name} />
          ) : (
            <StatusBadge tone="neutral" label="Được chia sẻ" />
          )}
        </li>
      ))}
    </ul>
  );
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleDateString('vi-VN');
}
