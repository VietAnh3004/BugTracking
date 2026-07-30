import { ProjectList } from '@/features/projects/components/ProjectList';
import { BackendStatus } from '@/features/system/components/BackendStatus';

export function App() {
  return (
    <main
      style={{
        fontFamily: 'system-ui, sans-serif',
        margin: '0 auto',
        maxWidth: 880,
        padding: '2rem 1.5rem',
      }}
    >
      <h1 style={{ fontSize: '1.5rem', marginBottom: '0.25rem' }}>
        Nền tảng phân tích chương trình dựa trên bằng chứng
      </h1>
      <p style={{ color: '#6b7280', marginTop: 0 }}>Đồ án tốt nghiệp — Nguyễn Việt Anh, 20224923</p>

      <section
        style={{ borderTop: '1px solid #e5e7eb', marginTop: '1.5rem', paddingTop: '1.5rem' }}
      >
        <BackendStatus />
      </section>

      <section
        style={{ borderTop: '1px solid #e5e7eb', marginTop: '1.5rem', paddingTop: '1.5rem' }}
      >
        <h2 style={{ fontSize: '1.125rem', marginTop: 0 }}>Dự án</h2>
        <ProjectList />
      </section>
    </main>
  );
}
