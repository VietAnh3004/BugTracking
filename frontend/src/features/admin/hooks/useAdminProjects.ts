import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as adminApi from '@/features/admin/api/adminApi';

/** Khoá gốc `['admin', 'projects']`: mọi biến thể tìm kiếm nằm dưới nó nên một lần invalidate làm mới hết. */
const PROJECTS_KEY = ['admin', 'projects'] as const;

export function useAdminProjects(query: string) {
  return useQuery({
    queryKey: [...PROJECTS_KEY, query.trim()],
    queryFn: () => adminApi.listProjects(query),
    // Giữ danh sách cũ trong lúc gõ tìm kiếm: bảng không nhấp nháy về trạng thái tải ở mỗi phím.
    placeholderData: (previous) => previous,
  });
}

export function useChangeProjectTier() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (vars: { projectId: string; tier: adminApi.ProjectTier }) =>
      adminApi.changeProjectTier(vars.projectId, vars.tier),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PROJECTS_KEY });
      // Hạng hiện ở danh sách/chi tiết dự án và quyết định ô chọn model ở trang chat: làm mới cả
      // hai để admin (nếu cũng là thành viên) thấy ngay, người khác thấy ở lần tải kế tiếp.
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
      void queryClient.invalidateQueries({ queryKey: ['conversations'] });
    },
  });
}
