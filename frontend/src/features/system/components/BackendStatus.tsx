/**
 * Hiển thị trạng thái kết nối backend, gọn trong một dòng ở chân trang.
 *
 * Component chỉ ráp giao diện; toàn bộ logic lấy dữ liệu nằm trong `useBackendHealth`.
 * Cả ba trạng thái bất đồng bộ đều được render, không chỉ trường hợp thành công.
 */

import { useBackendHealth } from '@/features/system/hooks/useBackendHealth';
import { appConfig } from '@/shared/config/runtime';
import { ApiError } from '@/shared/lib/apiClient';
import { StatusBadge } from '@/shared/ui/StatusBadge';

export function BackendStatus() {
  const { data, isPending, isError, error } = useBackendHealth();

  if (isPending) {
    return <StatusBadge tone="neutral" label="Đang kiểm tra kết nối…" />;
  }

  if (isError) {
    // Lỗi CORS và lỗi mạng đều tới đây dưới dạng TypeError của fetch, không có mã HTTP —
    // nói rõ điều đó ra để đỡ mất thời gian đoán khi cấu hình sai.
    const reason =
      error instanceof ApiError
        ? `HTTP ${error.status}: ${error.message}`
        : 'Không gọi được backend (kiểm tra backend đã chạy chưa và CORS_ORIGINS có chứa origin này không)';

    return (
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <StatusBadge tone="danger" label="Mất kết nối backend" />
        <p className="text-danger m-0 text-xs">{reason}</p>
        <p className="text-ink-subtle m-0 font-mono text-xs">API: {appConfig.apiBaseUrl}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
      <StatusBadge
        tone={data.status === 'UP' ? 'ok' : 'warning'}
        label={`Backend: ${data.status}`}
      />
      <p className="text-ink-subtle m-0 font-mono text-xs">API: {appConfig.apiBaseUrl}</p>
    </div>
  );
}
