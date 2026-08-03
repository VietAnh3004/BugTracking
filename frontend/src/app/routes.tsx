/**
 * Bảng định tuyến của ứng dụng, tách khỏi `router.tsx` có chủ đích: đây là DỮ LIỆU thuần, nên test
 * dựng được `createMemoryRouter(routes, { initialEntries: [...] })` trên CHÍNH mảng route thật.
 * Nếu để lẫn với `createBrowserRouter` (thứ đòi History API của trình duyệt), test sẽ phải chép
 * lại cấu trúc route — và bản chép đó lệch dần khỏi bản thật mà không ai biết.
 *
 * ======================= QUYẾT ĐỊNH: KHÔNG DÙNG `loader` / `action` =======================
 * React Router data router có sẵn cơ chế nạp dữ liệu theo route, nhưng dự án này cố tình KHÔNG
 * dùng. §1bis yêu cầu mọi request đi qua `apiClient` + TanStack Query; thêm `loader` là dựng lên
 * tầng cache THỨ HAI chạy song song, và hai tầng cache không biết nhau chính là nguồn của lớp bug
 * "sửa xong rồi mà màn hình vẫn hiện dữ liệu cũ" (mutate làm TanStack Query invalidate, nhưng
 * `loader` giữ bản cũ của nó, hoặc ngược lại). Dữ liệu lấy trong component qua hook, chấm hết.
 *
 * Vẫn chọn data router (thay vì `<BrowserRouter>` + `<Routes>`) vì ba thứ khác của nó:
 * `errorElement` theo từng route (lỗi ở một màn hình không làm trắng cả trang), `lazy` theo route,
 * và route-là-dữ liệu như nói ở trên.
 * =========================================================================================
 */

import { Navigate, type RouteObject } from 'react-router-dom';

import { NotFoundPage } from '@/app/NotFoundPage';
import { RouteErrorBoundary } from '@/app/RouteErrorBoundary';
import { RootLayout } from '@/app/layouts/RootLayout';
import { ProjectsPage } from '@/features/projects/pages/ProjectsPage';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    // Đặt ở route gốc nên mọi lỗi ném ra từ route con đều rơi vào đây thay vì làm trắng trang.
    // Các route con nặng (source viewer, tuần sau) sẽ khai errorElement riêng để lỗi của chúng
    // không hạ luôn khung ứng dụng.
    errorElement: <RouteErrorBoundary />,
    children: [
      // Trang chủ chưa có nội dung riêng: điều hướng thẳng tới danh sách dự án thay vì dựng một
      // dashboard rỗng. `replace` để nút Back không kẹt trong vòng lặp chuyển hướng.
      { index: true, element: <Navigate to="/projects" replace /> },
      { path: 'projects', element: <ProjectsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
