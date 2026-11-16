/**
 * Thành viên dự án (chia sẻ dự án) và vai trò của người đang dùng app với một dự án.
 *
 * Chỉ chủ dự án gọi được các endpoint thành viên — thành viên gọi sẽ nhận 404. Giao diện chỉ hiện
 * panel thành viên cho chủ dự án, nhưng quyền thật vẫn do backend kiểm.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useProject } from '@/features/projects/hooks/useProjects';
import { useSnapshot } from '@/features/snapshots/hooks/useSnapshots';
import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type ProjectMember = components['schemas']['ProjectMemberResponse'];
export type ProjectRole = components['schemas']['ProjectResponse']['role'];

const memberKeys = {
  list: (projectId: string) => ['projects', projectId, 'members'] as const,
};

export function useProjectMembers(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: memberKeys.list(projectId),
    queryFn: () => apiClient.get<ProjectMember[]>(`/api/v1/projects/${projectId}/members`),
    enabled,
  });
}

export function useAddMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) =>
      apiClient.post<ProjectMember>(`/api/v1/projects/${projectId}/members`, { email }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: memberKeys.list(projectId) }),
  });
}

export function useRemoveMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete<void>(`/api/v1/projects/${projectId}/members/${userId}`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: memberKeys.list(projectId) }),
  });
}

/**
 * Vai trò của người dùng với dự án chứa một snapshot — để các màn hình dưới snapshot (cây mã,
 * cảnh báo, gán nhãn) ẩn những nút thành viên không dùng được.
 *
 * `undefined` khi chưa tải xong: nơi dùng phải coi đó là "chưa biết" và KHÔNG hiện nút chỉ-chủ,
 * để thành viên không thấy nút nhấp nháy rồi biến mất.
 */
export function useSnapshotRole(snapshotId: string): ProjectRole | undefined {
  const snapshot = useSnapshot(snapshotId);
  const project = useProject(snapshot.data?.projectId ?? '');
  return snapshot.data === undefined ? undefined : project.data?.role;
}
