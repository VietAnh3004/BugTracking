/**
 * `errorElement` của route gốc — thay cho màn hình trắng khi có lỗi không bắt được.
 *
 * Phân biệt hai loại lỗi vì cách xử lý của người dùng khác hẳn nhau: lỗi HTTP do router ném ra
 * (`isRouteErrorResponse`) là "đường dẫn/tài nguyên có vấn đề", còn lỗi JavaScript là "ứng dụng
 * hỏng". Gộp chung thành một thông điệp chỉ khiến người dùng không biết nên thử lại hay báo lỗi.
 */

import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';

import { buttonClass, CARD } from '@/shared/ui/styles';

export function RouteErrorBoundary() {
  const error = useRouteError();

  const { title, detail } = describeError(error);

  return (
    <div className="mx-auto max-w-[880px] px-4 py-10 font-sans sm:px-6" role="alert">
      <div className={`${CARD} p-4`}>
        <h1 className="text-ink mt-0 mb-2 text-xl font-semibold">{title}</h1>
        <p className="text-ink-muted m-0 text-sm break-words">{detail}</p>
        <Link to="/projects" className={`${buttonClass('primary')} mt-5`}>
          Về danh sách dự án
        </Link>
      </div>
    </div>
  );
}

function describeError(error: unknown): { title: string; detail: string } {
  if (isRouteErrorResponse(error)) {
    return {
      title: `Lỗi ${error.status}`,
      detail: error.statusText || 'Không truy cập được tài nguyên này.',
    };
  }

  // Chỉ hiện message của Error thật; giá trị lạ (chuỗi, object) không đưa nguyên văn ra giao diện.
  return {
    title: 'Đã xảy ra lỗi',
    detail:
      error instanceof Error
        ? error.message
        : 'Lỗi không xác định. Thử tải lại trang hoặc quay về danh sách dự án.',
  };
}
