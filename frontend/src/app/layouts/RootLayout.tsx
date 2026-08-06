/**
 * Khung ứng dụng: tiêu đề, danh tính người dùng, và vùng `Outlet` cho route con.
 *
 * Là layout route (không phải component thường) nên nó KHÔNG bị unmount khi chuyển giữa các route
 * con — trạng thái trong khung giữ nguyên khi đổi trang, và `BackendStatus` không phải gọi lại API
 * mỗi lần điều hướng.
 *
 * Bề ngang ở đây chỉ là TRẦN chung (1400px). Ràng buộc thật của từng nhóm route nằm ở
 * `NarrowContent` / `WideContent` — tách ra vì Evidence Explorer và bảng cảnh báo cần rộng hơn
 * 880px, còn trang đăng nhập thì không nên giãn ra cả màn hình.
 */

import { Link, Outlet } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { BackendStatus } from '@/features/system/components/BackendStatus';

export function RootLayout() {
  const { status, user, signOut } = useAuth();

  return (
    <div className="mx-auto w-full max-w-[1400px] px-6 py-8 font-sans">
      <header className="border-border flex items-start justify-between border-b pb-4">
        <div>
          <Link to="/projects" className="text-ink no-underline">
            <h1 className="mb-1 text-2xl font-semibold">
              Nền tảng phân tích chương trình dựa trên bằng chứng
            </h1>
          </Link>
          <p className="text-ink-muted mt-0">Đồ án tốt nghiệp — Nguyễn Việt Anh, 20224923</p>
        </div>

        {status === 'authenticated' && user !== null ? (
          <div className="flex shrink-0 flex-col items-end gap-1">
            <span className="text-ink-muted text-sm">{user.displayName}</span>
            <button
              type="button"
              onClick={() => void signOut()}
              className="text-brand cursor-pointer text-sm underline"
            >
              Đăng xuất
            </button>
          </div>
        ) : null}
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
