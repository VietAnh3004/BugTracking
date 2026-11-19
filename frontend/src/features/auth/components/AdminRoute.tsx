/**
 * Guard cho khu vực quản trị. Đứng ĐỘC LẬP, không lồng trong `ProtectedRoute`: người chưa đăng nhập
 * phải được đưa tới `/admin/login` (form dành cho admin), không phải `/login` của người dùng thường.
 *
 * Người đã đăng nhập nhưng KHÔNG phải ADMIN nhận đúng trang 404 như mọi đường dẫn không tồn tại — không
 * có thông báo "bạn không đủ quyền", để không lộ rằng ở đây có một trang quản trị. Đây chỉ là lớp trình
 * bày: chốt chặn thật nằm ở backend (`/api/v1/admin/**` yêu cầu ROLE_ADMIN), nên sửa role trong
 * devtools cũng không lấy được dữ liệu.
 */

import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { NotFoundPage } from '@/app/NotFoundPage';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { LoadingState } from '@/shared/ui/states';

export function AdminRoute() {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'bootstrapping') {
    return <LoadingState label="Đang khôi phục phiên đăng nhập…" shape="lines" rows={3} />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/admin/login" replace state={{ from: location }} />;
  }
  if (user?.role !== 'ADMIN') {
    return <NotFoundPage />;
  }
  return <Outlet />;
}
