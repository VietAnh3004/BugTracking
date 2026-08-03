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
 * `state={{ from: location }}` để sau khi đăng nhập quay lại đúng trang đang định mở, thay vì
 * luôn đổ về `/projects`.
 */

import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';

export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'bootstrapping') {
    return <FullPageSpinner label="Đang khôi phục phiên đăng nhập…" />;
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
