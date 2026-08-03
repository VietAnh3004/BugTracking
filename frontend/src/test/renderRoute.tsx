/**
 * Helper render một route bằng CHÍNH mảng `routes` thật của ứng dụng.
 *
 * Cố tình không nhận vào một cấu trúc route riêng cho test: nếu test tự dựng route, nó chứng minh
 * được "React Router hoạt động" chứ không chứng minh được "định tuyến của ỨNG DỤNG này đúng" — và
 * bản chép sẽ lệch dần khỏi bản thật mà không có gì báo.
 *
 * `createMemoryRouter` thay cho `createBrowserRouter` vì môi trường test không có History API của
 * trình duyệt thật; `initialEntries` đóng vai trò "người dùng mở thẳng URL này" (deep link).
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';

import { routes } from '@/app/routes';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import { render } from '@testing-library/react';

export function renderRoute(initialPath: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const queryClient = createTestQueryClient();

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}
