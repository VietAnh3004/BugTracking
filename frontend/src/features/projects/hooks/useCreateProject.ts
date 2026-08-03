import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type CreateProjectRequest = components['schemas']['CreateProjectRequest'];
export type ProjectResponse = components['schemas']['ProjectResponse'];

export function useCreateProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateProjectRequest) =>
      apiClient.post<ProjectResponse>('/api/v1/projects', body),
    onSuccess: () => {
      // Invalidate thay vì tự chèn vào cache: danh sách có phân trang và sắp xếp phía server, tự
      // chèn tay sẽ cho thứ tự khác với lần tải lại kế tiếp.
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}
