import { ProjectList } from '@/features/projects/components/ProjectList';
import { BackendStatus } from '@/features/system/components/BackendStatus';

export function App() {
  return (
    <main className="mx-auto max-w-[880px] px-6 py-8 font-sans">
      <h1 className="mb-1 text-2xl font-semibold">
        Nền tảng phân tích chương trình dựa trên bằng chứng
      </h1>
      <p className="text-ink-muted mt-0">Đồ án tốt nghiệp — Nguyễn Việt Anh, 20224923</p>

      <section className="border-border mt-6 border-t pt-6">
        <BackendStatus />
      </section>

      <section className="border-border mt-6 border-t pt-6">
        <h2 className="mt-0 text-lg font-semibold">Dự án</h2>
        <ProjectList />
      </section>
    </main>
  );
}
