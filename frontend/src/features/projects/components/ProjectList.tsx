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

export function ProjectList() {
  const { data, isPending, isError, error } = useProjects();

  if (isPending) {
    return <StatusBadge tone="neutral" label="Đang tải danh sách dự án…" />;
  }

  if (isError) {
    const reason =
      error instanceof ApiError
        ? `HTTP ${error.status}: ${error.message}`
        : 'Không gọi được backend';
    return <StatusBadge tone="danger" label={reason} />;
  }

  if (data.items === undefined || data.items.length === 0) {
    return (
      <div className="border-border-strong bg-surface flex flex-col items-center rounded-xl border border-dashed px-6 py-12 text-center">
        <span className="bg-surface-sunken text-ink-muted mb-3 grid size-11 place-items-center rounded-xl">
          <IconBox className="size-5" />
        </span>
        <StatusBadge tone="neutral" label="Chưa có dự án nào." />
        <p className="text-ink-muted mt-2 mb-0 text-sm">
          Tạo dự án đầu tiên ở khung bên cạnh để bắt đầu.
        </p>
      </div>
    );
  }

  return (
    <ul className="m-0 grid list-none gap-3 p-0">
      {data.items.map((project) => (
        <li
          key={project.id}
          className={`${CARD} group hover:border-brand-ring flex flex-wrap items-center justify-between gap-x-4 gap-y-3 p-4 transition-colors`}
        >
          <div className="flex min-w-0 items-start gap-3">
            <span className="bg-brand-subtle text-brand grid size-10 shrink-0 place-items-center rounded-lg">
              <IconBox className="size-5" />
            </span>
            <div className="min-w-0">
              <Link
                to={`/projects/${project.id}`}
                className="text-ink hover:text-brand inline-flex items-center gap-1 text-base font-semibold"
              >
                {project.name}
                <IconArrowRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
              </Link>
              {project.description ? (
                <p className="text-ink-muted mt-0.5 mb-0 text-sm">{project.description}</p>
              ) : null}
              <p className="text-ink-subtle mt-1 mb-0 text-xs">
                Tạo ngày {formatDate(project.createdAt)}
              </p>
            </div>
          </div>
          <DeleteProjectButton projectId={project.id} projectName={project.name} />
        </li>
      ))}
    </ul>
  );
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleDateString('vi-VN');
}
