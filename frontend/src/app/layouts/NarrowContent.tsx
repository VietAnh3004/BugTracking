/**
 * Ràng buộc bề ngang của phần NỘI DUNG, tách khỏi `RootLayout` có chủ đích.
 *
 * Trước tuần 10-11, `max-w-[880px]` nằm trên khung ngoài của `RootLayout` — hợp lý khi mọi màn
 * hình đều là văn bản một cột. Nhưng bảng cảnh báo đã phải `overflow-x-auto` với `min-w-[64rem]`,
 * và Evidence Explorer có hai cột kèm khối mã Java (dòng dài, thụt lề sâu) nên 880px là chật
 * THẬT, không phải sở thích.
 *
 * Đây là layout route KHÔNG CÓ PATH: nó chỉ bọc nhóm route con để áp bề ngang, không chiếm đoạn
 * URL nào — `/projects` vẫn là `/projects`. Nhờ vậy không test định tuyến nào phải sửa. Cùng cơ
 * chế mà `ProtectedRoute`/`PublicOnlyRoute` đang dùng.
 *
 * Vì sao không dựng hai `RootLayout` với prop bề ngang: chuyển giữa trang hẹp và trang rộng sẽ
 * REMOUNT khung, và `BackendStatus` gọi lại API mỗi lần điều hướng — đúng thứ mà comment đầu
 * `RootLayout` viết ra để bảo vệ.
 */

import { Outlet } from 'react-router-dom';

export function NarrowContent() {
  return (
    <div className="mx-auto w-full max-w-[880px]">
      <Outlet />
    </div>
  );
}
