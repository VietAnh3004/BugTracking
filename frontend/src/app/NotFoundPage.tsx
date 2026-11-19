import { Link } from 'react-router-dom';

import { buttonClass, CARD } from '@/shared/ui/styles';

export function NotFoundPage() {
  return (
    <div className={`${CARD} mx-auto flex max-w-lg flex-col items-center px-6 py-10 text-center`}>
      <p className="text-brand m-0 font-mono text-sm font-semibold">404</p>
      <h2 className="text-ink mt-2 mb-2 text-xl font-semibold">Không tìm thấy trang</h2>
      <p className="text-ink-muted m-0 text-sm">Đường dẫn bạn mở không tồn tại hoặc đã bị đổi.</p>
      <Link to="/projects" className={`${buttonClass('primary')} mt-5`}>
        Về danh sách dự án
      </Link>
    </div>
  );
}
