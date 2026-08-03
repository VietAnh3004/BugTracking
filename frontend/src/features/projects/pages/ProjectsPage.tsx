/**
 * Trang danh sách dự án. Chỉ ráp tiêu đề + form + component danh sách; mọi logic dữ liệu nằm trong
 * hook riêng của từng component (§1bis.3: component trang không tự gọi API).
 */

import { CreateProjectForm } from '@/features/projects/components/CreateProjectForm';
import { ProjectList } from '@/features/projects/components/ProjectList';

export function ProjectsPage() {
  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="mt-0 mb-3 text-lg font-semibold">Tạo dự án mới</h2>
        <CreateProjectForm />
      </section>

      <section>
        <h2 className="mt-0 mb-3 text-lg font-semibold">Dự án</h2>
        <ProjectList />
      </section>
    </div>
  );
}
