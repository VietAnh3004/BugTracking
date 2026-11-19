/**
 * Khung ứng dụng: thanh trên cùng (tên ứng dụng, danh tính người dùng), vùng `Outlet` cho route con
 * và chân trang trạng thái backend.
 *
 * Là layout route (không phải component thường) nên nó KHÔNG bị unmount khi chuyển giữa các route
 * con — trạng thái trong khung giữ nguyên khi đổi trang, và `BackendStatus` không phải gọi lại API
 * mỗi lần điều hướng.
 *
 * Bề ngang ở đây chỉ là TRẦN chung (1400px). Ràng buộc thật của từng nhóm route nằm ở
 * `NarrowContent` / `WideContent` — tách ra vì Evidence Explorer và bảng cảnh báo cần rộng hơn
 * hẳn, còn trang đăng nhập thì không nên giãn ra cả màn hình.
 *
 * Thanh trên cùng `sticky`: các màn hình chính (bảng 34 cảnh báo, file mã nguồn vài trăm dòng)
 * đều dài, và người dùng vẫn cần thấy mình đang ở ứng dụng nào và đăng xuất ở đâu khi đã cuộn sâu
 * (đăng xuất và cài đặt tài khoản nằm trong `AccountMenu`, mở khi bấm vào tên).
 */

import { Link, Outlet } from 'react-router-dom';

import { AccountMenu } from '@/features/auth/components/AccountMenu';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { BackendStatus } from '@/features/system/components/BackendStatus';
import { IconLogo } from '@/shared/ui/icons';

export function RootLayout() {
  const { status, user } = useAuth();

  return (
    <div className="flex min-h-screen flex-col font-sans">
      <header className="border-border bg-surface/90 sticky top-0 z-30 border-b backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-[1400px] items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/projects" className="group flex min-w-0 items-center gap-3">
            <span className="bg-brand-solid grid size-9 shrink-0 place-items-center rounded-lg text-white shadow-sm">
              <IconLogo className="size-5" />
            </span>
            <span className="min-w-0">
              <h1 className="text-ink group-hover:text-brand m-0 truncate text-[15px] leading-tight font-semibold">
                Nền tảng phân tích chương trình dựa trên bằng chứng
              </h1>
              <p className="text-ink-muted m-0 hidden truncate text-xs sm:block">
                Đồ án tốt nghiệp — Nguyễn Việt Anh, 20224923
              </p>
            </span>
          </Link>

          {status === 'authenticated' && user !== null ? (
            <div className="shrink-0">
              <AccountMenu user={user} />
            </div>
          ) : null}
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1400px] flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>

      <footer className="border-border bg-surface border-t">
        <div className="mx-auto w-full max-w-[1400px] px-4 py-3 sm:px-6">
          <BackendStatus />
        </div>
      </footer>
    </div>
  );
}
