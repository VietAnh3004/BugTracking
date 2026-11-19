import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as adminApi from '@/features/admin/api/adminApi';

/** Khoá gốc `['admin', 'users']`: mọi biến thể tìm kiếm nằm dưới nó nên một lần invalidate làm mới hết. */
const USERS_KEY = ['admin', 'users'] as const;

export function useAdminUsers(query: string) {
  return useQuery({
    queryKey: [...USERS_KEY, query.trim()],
    queryFn: () => adminApi.listUsers(query),
    // Giữ danh sách cũ trong lúc gõ tìm kiếm: bảng không nhấp nháy về trạng thái tải ở mỗi phím.
    placeholderData: (previous) => previous,
  });
}

export function useDeleteAdminUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) => adminApi.deleteUser(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: USERS_KEY });
      // Dự án của người bị xoá cũng biến mất khỏi cache dự án (nếu admin từng xem trong phiên này).
      void queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}
