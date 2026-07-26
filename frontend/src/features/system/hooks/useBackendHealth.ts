/**
 * Trạng thái kết nối tới core-backend.
 *
 * Ngoài việc hiển thị cho người dùng, hook này còn là phép thử CORS liên tục: nó gọi thẳng sang
 * origin của backend, nên nếu `CORS_ORIGINS` cấu hình sai thì lỗi lộ ra ngay ở màn hình đầu tiên
 * chứ không đợi đến lúc dùng một tính năng cụ thể.
 */

import { useQuery } from '@tanstack/react-query';

import { apiClient } from '@/shared/lib/apiClient';

export interface HealthResponse {
  status: string;
}

export function useBackendHealth() {
  return useQuery({
    queryKey: ['backend-health'],
    queryFn: () => apiClient.get<HealthResponse>('/actuator/health'),
    refetchInterval: 30_000,
    retry: 1,
  });
}
