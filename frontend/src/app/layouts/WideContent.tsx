/**
 * Nhóm route cố ý dùng TOÀN BỘ bề ngang cho phép (trần 1400px đặt ở `RootLayout`).
 *
 * Không đặt `max-w` riêng — nếu chỉ cần "không hẹp" thì về lý thuyết bỏ trống chỗ này cũng chạy.
 * Nó tồn tại để "route này rộng" là một điều được KHAI BÁO, thay vì suy ra từ việc thiếu
 * {@link NarrowContent}. Một ý định ngầm thì lần sau ai đó thêm route sẽ đặt nhầm nhóm mà không
 * có gì nhắc.
 */

import { Outlet } from 'react-router-dom';

export function WideContent() {
  return (
    <div className="w-full">
      <Outlet />
    </div>
  );
}
