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
import { NarrowContent } from '@/app/layouts/NarrowContent';
import { RootLayout } from '@/app/layouts/RootLayout';
import { WideContent } from '@/app/layouts/WideContent';
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
          {
            element: <NarrowContent />,
            children: [
              { path: 'login', element: <LoginPage /> },
              { path: 'register', element: <RegisterPage /> },
            ],
          },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          // Trang chủ chưa có nội dung riêng: điều hướng thẳng tới danh sách dự án thay vì dựng
          // một dashboard rỗng. `replace` để nút Back không kẹt trong vòng lặp chuyển hướng.
          { index: true, element: <Navigate to="/projects" replace /> },
          {
            // Màn hình một cột: giữ bề ngang đọc được, không giãn theo màn hình.
            element: <NarrowContent />,
            children: [
              { path: 'projects', element: <ProjectsPage /> },
              { path: 'projects/:projectId', element: <ProjectDetailPage /> },
            ],
          },
          {
            // Các màn hình dưới đây cố ý dùng toàn bộ bề ngang: bảng cảnh báo có 6 cột và
            // `min-w-[64rem]`, Evidence Explorer có hai cột kèm khối mã, dashboard xếp thẻ chỉ số
            // hai cột, còn khung duyệt mã nguồn có cây file bên trái. Tất cả đều chật trong 880px.
            element: <WideContent />,
            children: [
              {
                // Đặt NGOÀI `snapshots/:snapshotId` (khung duyệt mã nguồn) dù URL lồng nhau: màn
                // hình cảnh báo là một bảng chiếm toàn bộ bề ngang, không cần cây file bên trái.
                // Vẫn giữ `snapshotId` trên URL để dựng được link sang source viewer mà không phải
                // hỏi backend "run này thuộc snapshot nào".
                path: 'snapshots/:snapshotId/runs/:runId/warnings',
                lazy: async () => {
                  const { WarningsPage } = await import('@/features/warnings/pages/WarningsPage');
                  return { Component: WarningsPage };
                },
                errorElement: <RouteErrorBoundary />,
              },
              {
                // Khoá là `shortId` (EV-001A) chứ không phải UUID: đó là thứ LLM viết ra trong
                // citation, nên tuần 12-13 trỏ thẳng vào URL này được mà không phải tra đổi khoá.
                //
                // Đặt tên đoạn là `evidence` chứ không `warnings` vì citation trỏ tới evidence
                // BẤT KỲ LOẠI NÀO — một COVERAGE_INFORMATION cũng phải mở được bằng chính URL này.
                path: 'snapshots/:snapshotId/runs/:runId/evidence/:shortId',
                lazy: async () => {
                  const { EvidenceExplorerPage } =
                    await import('@/features/evidence/pages/EvidenceExplorerPage');
                  return { Component: EvidenceExplorerPage };
                },
                errorElement: <RouteErrorBoundary />,
              },
              {
                // Dashboard đứng cạnh `warnings` chứ không lồng trong nó: nó nói về CẢ lần phân
                // tích (chỉ số trích dẫn, chi phí Agent, tiến độ review), không phải về danh sách
                // cảnh báo. Lồng vào sẽ buộc mọi liên kết tới nó phải đi qua bộ lọc của bảng.
                path: 'snapshots/:snapshotId/runs/:runId/dashboard',
                lazy: async () => {
                  const { DashboardPage } = await import('@/features/review/pages/DashboardPage');
                  return { Component: DashboardPage };
                },
                errorElement: <RouteErrorBoundary />,
              },
              {
                // Phiên hỏi đáp nằm trên query string (`?conversation=`), không phải trên path:
                // nó là một BỘ LỌC của cùng một màn hình, không phải một tài nguyên khác. Nhờ vậy
                // `?focus=EV-001A` — lối vào từ Evidence Explorer — ghép được vào cùng URL mà
                // không sinh thêm một nhánh route nào.
                path: 'snapshots/:snapshotId/runs/:runId/chat',
                lazy: async () => {
                  const { ChatPage } = await import('@/features/chat/pages/ChatPage');
                  return { Component: ChatPage };
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
                // errorElement RIÊNG: lỗi khi đọc một file (vd nội dung hỏng) không được hạ luôn
                // khung ứng dụng — người dùng vẫn phải còn cây file để chọn file khác.
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
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
