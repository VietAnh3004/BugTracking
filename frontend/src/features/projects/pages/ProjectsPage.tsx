/**
 * Trang danh sách dự án. Chỉ ráp tiêu đề + component danh sách; mọi logic dữ liệu nằm trong
 * `ProjectList`/`useProjects` (§1bis.3: component trang không tự gọi API).
 */

import { ProjectList } from '@/features/projects/components/ProjectList';

export function ProjectsPage() {
  return (
    <section>
      <h2 className="mt-0 mb-3 text-lg font-semibold">Dự án</h2>
      <ProjectList />
    </section>
  );
}
