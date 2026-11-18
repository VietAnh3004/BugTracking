/**
 * Trang danh sách dự án. Chỉ ráp tiêu đề + form + component danh sách; mọi logic dữ liệu nằm trong
 * hook riêng của từng component (§1bis.3: component trang không tự gọi API).
 *
 * Danh sách đứng trước, form tạo mới ở cột phải: người dùng quay lại trang này chủ yếu để MỞ một
 * dự án đã có, tạo mới là việc làm một lần.
 */

import { CreateProjectForm } from '@/features/projects/components/CreateProjectForm';
import { ProjectList } from '@/features/projects/components/ProjectList';
import { InfoTip } from '@/shared/ui/InfoTip';
import { PageHeader } from '@/shared/ui/PageHeader';
import { CARD } from '@/shared/ui/styles';

export function ProjectsPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Dự án"
        description="Mỗi dự án chứa các snapshot mã nguồn đã tải lên và kết quả phân tích của chúng."
      />

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <section aria-label="Danh sách dự án">
          <ProjectList />
        </section>

        <section className={`${CARD} p-5`}>
          <div className="mb-4 flex items-center gap-1.5">
            <h2 className="text-ink m-0 text-base font-semibold">Tạo dự án mới</h2>
            <InfoTip label="Giải thích: Tạo dự án mới">
              Đặt tên rồi tải mã nguồn dạng zip lên ở bước tiếp theo.
            </InfoTip>
          </div>
          <CreateProjectForm />
        </section>
      </div>
    </div>
  );
}
