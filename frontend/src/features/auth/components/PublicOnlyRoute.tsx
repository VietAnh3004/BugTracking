/**
 * Route chỉ dành cho người CHƯA đăng nhập (đăng nhập, đăng ký).
 *
 * Cũng phải chờ hết `bootstrapping` như `ProtectedRoute` — và đây là lý do kỹ thuật, không phải
 * cho đối xứng: nếu guard này coi `bootstrapping` là "chưa đăng nhập" thì nó cho hiện trang đăng
 * nhập, còn `ProtectedRoute` lại coi là "chưa xác định" và chờ; hai guard đẩy qua đẩy lại giữa
 * `/login` và trang đích ngay khi bootstrap kết thúc, tạo ra một lần nhấp nháy điều hướng mà
 * không ai giải thích được.
 */

import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';

interface LocationState {
  from?: { pathname: string };
}

export function PublicOnlyRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'bootstrapping') {
    return <FullPageSpinner label="Đang kiểm tra phiên đăng nhập…" />;
  }

  if (status === 'authenticated') {
    // Quay lại đúng trang người dùng định mở trước khi bị đá sang /login (do ProtectedRoute đặt).
    const from = (location.state as LocationState | null)?.from?.pathname;
    return <Navigate to={from ?? '/projects'} replace />;
  }

  return <Outlet />;
}
