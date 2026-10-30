import { useMutation, useQueryClient } from '@tanstack/react-query';

import { snapshotKeys } from '@/features/snapshots/hooks/useSnapshots';
import { apiClient } from '@/shared/lib/apiClient';

export function useDeleteProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (projectId: string) => apiClient.delete<void>(`/api/v1/projects/${projectId}`),
    onSuccess: (_data, projectId) => {
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
      // Gỡ hẳn chứ không invalidate: invalidate sẽ refetch danh sách snapshot của một dự án đã
      // không còn, và nhận về 404.
      queryClient.removeQueries({ queryKey: snapshotKeys.byProject(projectId) });
    },
  });
}
