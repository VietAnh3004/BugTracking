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
export type Project = components['schemas']['ProjectResponse'];

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => apiClient.get<ProjectPage>('/api/v1/projects'),
  });
}

/**
 * Một dự án — để trang chi tiết hiện được TÊN dự án thay vì chỉ có UUID trên URL.
 *
 * Khoá `['projects', id]` nằm dưới `['projects']` có chủ đích: tạo/xoá dự án invalidate `['projects']`
 * thì bản chi tiết cũng được làm mới theo, không phải nhớ invalidate riêng.
 */
export function useProject(projectId: string) {
  return useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => apiClient.get<Project>(`/api/v1/projects/${projectId}`),
  });
}
