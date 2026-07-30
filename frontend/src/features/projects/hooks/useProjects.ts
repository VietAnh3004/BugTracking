/**
 * Danh sách dự án — màn hình FE thật đầu tiên gọi API nghiệp vụ (không phải health check).
 *
 * Kiểu dữ liệu lấy từ `api-types.ts` (sinh bằng `npm run api:generate` từ `docs/api/openapi.json`
 * đã commit), nên FE và BE không thể lệch shape mà không bị `tsc` phát hiện.
 */

import { useQuery } from '@tanstack/react-query';

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type ProjectPage = components['schemas']['PageResponseProjectResponse'];

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => apiClient.get<ProjectPage>('/api/v1/projects'),
  });
}
