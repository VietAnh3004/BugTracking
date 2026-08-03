/**
 * Helper render một route bằng CHÍNH mảng `routes` thật của ứng dụng.
 *
 * Cố tình không nhận vào một cấu trúc route riêng cho test: nếu test tự dựng route, nó chứng minh
 * được "React Router hoạt động" chứ không chứng minh được "định tuyến của ỨNG DỤNG này đúng" — và
 * bản chép sẽ lệch dần khỏi bản thật mà không có gì báo.
 *
 * Dùng `AuthProvider` THẬT (không phải context giả): giá trị của các test route guard nằm ở chỗ
 * chúng chạy qua đúng máy trạng thái `bootstrapping → authenticated/anonymous` của bản production.
 * Thay nó bằng provider giả là bỏ mất chính thứ cần kiểm.
 *
 * `createMemoryRouter` thay cho `createBrowserRouter` vì môi trường test không có History API của
 * trình duyệt thật; `initialEntries` đóng vai trò "người dùng mở thẳng URL này" (deep link).
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';

import { routes } from '@/app/routes';
import { AuthProvider } from '@/features/auth/context/AuthProvider';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import { render } from '@testing-library/react';

/**
 * Trả về cả `router` bên cạnh kết quả render, vì nhiều hành vi định tuyến CHỈ quan sát được qua
 * `router.state.location` chứ không qua DOM. Ví dụ thật đã gặp: khi `ProtectedRoute` sai (điều
 * hướng lúc đang `bootstrapping` thay vì chờ), giao diện vẫn hiện một spinner — nhưng là spinner
 * của `PublicOnlyRoute` ở `/login`, nên test bám vào DOM sẽ XANH GIẢ. Chỉ có đường dẫn hiện tại
 * mới phân biệt được "vẫn ở trang cũ" với "đã bị đá đi nơi khác".
 */
export function renderRoute(initialPath: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const queryClient = createTestQueryClient();

  const result = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  );

  return { ...result, router };
}
