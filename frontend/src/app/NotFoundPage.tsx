import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div>
      <h2 className="mt-0 mb-2 text-lg font-semibold">Không tìm thấy trang</h2>
      <p className="text-ink-muted">Đường dẫn bạn mở không tồn tại hoặc đã bị đổi.</p>
      <Link to="/projects" className="text-brand mt-4 inline-block">
        Về danh sách dự án
      </Link>
    </div>
  );
}
