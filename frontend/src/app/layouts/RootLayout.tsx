/**
 * Khung ứng dụng: tiêu đề, thanh trạng thái backend, và vùng `Outlet` cho route con.
 *
 * Là layout route (không phải component thường) nên nó KHÔNG bị unmount khi chuyển giữa các route
 * con — trạng thái trong khung (sau này là thanh điều hướng, phiên đăng nhập) giữ nguyên khi đổi
 * trang, và `BackendStatus` không phải gọi lại API mỗi lần điều hướng.
 */

import { Link, Outlet } from 'react-router-dom';

import { BackendStatus } from '@/features/system/components/BackendStatus';

export function RootLayout() {
  return (
    <div className="mx-auto max-w-[880px] px-6 py-8 font-sans">
      <header className="border-border border-b pb-4">
        <Link to="/projects" className="text-ink no-underline">
          <h1 className="mb-1 text-2xl font-semibold">
            Nền tảng phân tích chương trình dựa trên bằng chứng
          </h1>
        </Link>
        <p className="text-ink-muted mt-0">Đồ án tốt nghiệp — Nguyễn Việt Anh, 20224923</p>
      </header>

      <main className="pt-6">
        <Outlet />
      </main>

      <footer className="border-border mt-8 border-t pt-4">
        <BackendStatus />
      </footer>
    </div>
  );
}
