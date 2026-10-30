/**
 * Hiển thị danh sách dự án. Component chỉ ráp giao diện; logic lấy dữ liệu nằm trong
 * `useProjects`. Cả ba trạng thái bất đồng bộ đều render, không chỉ trường hợp thành công —
 * và trường hợp danh sách rỗng cũng có thông điệp riêng (khác với "đang tải").
 */

import { Link } from 'react-router-dom';

import { DeleteProjectButton } from '@/features/projects/components/DeleteProjectButton';
import { useProjects } from '@/features/projects/hooks/useProjects';
import { ApiError } from '@/shared/lib/apiClient';
import { StatusBadge } from '@/shared/ui/StatusBadge';

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
    return <StatusBadge tone="neutral" label="Chưa có dự án nào." />;
  }

  return (
    <ul className="m-0 list-none p-0">
      {data.items.map((project) => (
        <li
          key={project.id}
          className="border-border flex flex-wrap items-start justify-between gap-x-4 gap-y-2 border-b py-2"
        >
          <div>
            <Link to={`/projects/${project.id}`} className="text-brand font-semibold">
              {project.name}
            </Link>
            {project.description ? (
              <p className="text-ink-muted mt-1 mb-0 text-sm">{project.description}</p>
            ) : null}
          </div>
          <DeleteProjectButton projectId={project.id} projectName={project.name} />
        </li>
      ))}
    </ul>
  );
}
