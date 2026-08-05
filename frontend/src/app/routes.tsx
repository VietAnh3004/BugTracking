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
import { ProtectedRoute } from '@/features/auth/components/ProtectedRoute';
import { PublicOnlyRoute } from '@/features/auth/components/PublicOnlyRoute';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterPage } from '@/features/auth/pages/RegisterPage';
import { ProjectDetailPage } from '@/features/projects/pages/ProjectDetailPage';
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
      // Hai guard dưới đây là route KHÔNG CÓ `path` (pathless layout route): chúng chỉ bọc nhóm
      // route con để áp điều kiện truy cập, không tự chiếm một đoạn URL nào. Nhờ vậy `/projects`
      // vẫn là `/projects` chứ không thành `/protected/projects`.
      {
        element: <PublicOnlyRoute />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          // Trang chủ chưa có nội dung riêng: điều hướng thẳng tới danh sách dự án thay vì dựng
          // một dashboard rỗng. `replace` để nút Back không kẹt trong vòng lặp chuyển hướng.
          { index: true, element: <Navigate to="/projects" replace /> },
          { path: 'projects', element: <ProjectsPage /> },
          { path: 'projects/:projectId', element: <ProjectDetailPage /> },
          {
            // Đặt NGOÀI `snapshots/:snapshotId` (khung duyệt mã nguồn) dù URL lồng nhau: màn hình
            // cảnh báo là một bảng chiếm toàn bộ bề ngang, không cần cây file bên trái. Vẫn giữ
            // `snapshotId` trên URL để dựng được link sang source viewer mà không phải hỏi backend
            // "run này thuộc snapshot nào".
            path: 'snapshots/:snapshotId/runs/:runId/warnings',
            lazy: async () => {
              const { WarningsPage } = await import('@/features/warnings/pages/WarningsPage');
              return { Component: WarningsPage };
            },
            errorElement: <RouteErrorBoundary />,
          },
          {
            // `lazy` theo route: phần duyệt mã nguồn là mảng nặng nhất của ứng dụng (cây file +
            // source viewer) nhưng người dùng chỉ mở nó sau khi đã đăng nhập và chọn snapshot —
            // không nên nằm trong bundle đầu tiên của màn hình đăng nhập.
            path: 'snapshots/:snapshotId',
            lazy: async () => {
              const { SnapshotBrowserLayout } =
                await import('@/features/snapshots/layouts/SnapshotBrowserLayout');
              return { Component: SnapshotBrowserLayout };
            },
            // errorElement RIÊNG: lỗi khi đọc một file (vd nội dung hỏng) không được hạ luôn khung
            // ứng dụng — người dùng vẫn phải còn cây file để chọn file khác.
            errorElement: <RouteErrorBoundary />,
            children: [
              {
                // Splat route: đường dẫn file chứa dấu `/` nên phải là `files/*`. URL đọc được
                // như trên GitHub, và `#L42` còn rảnh cho số dòng.
                path: 'files/*',
                lazy: async () => {
                  const { SourceViewerPage } =
                    await import('@/features/snapshots/pages/SourceViewerPage');
                  return { Component: SourceViewerPage };
                },
              },
              {
                index: true,
                lazy: async () => {
                  const { SourceViewerPage } =
                    await import('@/features/snapshots/pages/SourceViewerPage');
                  return { Component: SourceViewerPage };
                },
              },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
