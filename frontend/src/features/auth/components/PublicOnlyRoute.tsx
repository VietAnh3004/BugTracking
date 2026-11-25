/**
 * Route chỉ dành cho người CHƯA đăng nhập (đăng nhập, đăng ký).
 *
 * Cũng phải chờ hết `bootstrapping` như `ProtectedRoute` — và đây là lý do kỹ thuật, không phải
 * cho đối xứng: nếu guard này coi `bootstrapping` là "chưa đăng nhập" thì nó cho hiện trang đăng
 * nhập, còn `ProtectedRoute` lại coi là "chưa xác định" và chờ; hai guard đẩy qua đẩy lại giữa
 * `/login` và trang đích ngay khi bootstrap kết thúc, tạo ra một lần nhấp nháy điều hướng mà
 * không ai giải thích được.
 *
 * Đăng nhập xong LUÔN về trang chủ (danh sách dự án), không quay lại trang đang mở dở — người dùng
 * chọn 23/11/2026. Trước đó app quay lại trang trước khi bị đá sang /login, kể cả trang có từ lúc
 * bấm Đăng xuất: đăng xuất ở một trang cảnh báo rồi đăng nhập lại là rơi thẳng vào trang đó.
 */

import { Navigate, Outlet } from 'react-router-dom';

import { useAuth } from '@/features/auth/hooks/useAuth';
import { LoadingState } from '@/shared/ui/states';

export function PublicOnlyRoute() {
  const { status } = useAuth();

  if (status === 'bootstrapping') {
    return <LoadingState label="Đang kiểm tra phiên đăng nhập…" shape="lines" rows={3} />;
  }

  if (status === 'authenticated') {
    return <Navigate to="/projects" replace />;
  }

  return <Outlet />;
}
