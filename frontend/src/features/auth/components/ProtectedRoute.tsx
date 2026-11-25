/**
 * Chặn route cần đăng nhập.
 *
 * ================================ CẠM BẪY QUAN TRỌNG NHẤT ================================
 * Khi `status === 'bootstrapping'` PHẢI render trạng thái chờ, TUYỆT ĐỐI KHÔNG `<Navigate
 * to="/login" />`.
 *
 * Lúc mới tải trang, ứng dụng có refresh token trong localStorage nhưng chưa đổi xong lấy access
 * token — người dùng thực tế ĐÃ đăng nhập, chỉ là chưa xác minh xong. Điều hướng ngay lúc đó
 * khiến MỌI lần F5 trên một deep link (`/projects/x/snapshots/y/files/...#L42`) nhảy về trang
 * đăng nhập và mất luôn đường dẫn đang xem — đúng thứ mà tiêu chí nghiệm thu tuần 5-7 kiểm.
 * ========================================================================================
 *
 * Không mang theo trang đang mở sang /login: đăng nhập xong luôn về trang chủ (xem `PublicOnlyRoute`).
 */

import { Navigate, Outlet } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { LoadingState } from '@/shared/ui/states';

export function ProtectedRoute() {
  const { status } = useAuth();

  if (status === 'bootstrapping') {
    return <LoadingState label="Đang khôi phục phiên đăng nhập…" shape="lines" rows={3} />;
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
