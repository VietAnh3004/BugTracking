import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type AdminUser = components['schemas']['AdminUserView'];

export function listUsers(query: string): Promise<AdminUser[]> {
  const q = query.trim();
  return apiClient.get<AdminUser[]>(
    `/api/v1/admin/users${q === '' ? '' : `?q=${encodeURIComponent(q)}`}`,
  );
}

export function deleteUser(userId: string): Promise<void> {
  return apiClient.delete<void>(`/api/v1/admin/users/${userId}`);
}

export type AdminProject = components['schemas']['AdminProjectView'];
export type ProjectTier = AdminProject['tier'];

export function listProjects(query: string): Promise<AdminProject[]> {
  const q = query.trim();
  return apiClient.get<AdminProject[]>(
    `/api/v1/admin/projects${q === '' ? '' : `?q=${encodeURIComponent(q)}`}`,
  );
}

export function changeProjectTier(projectId: string, tier: ProjectTier): Promise<AdminProject> {
  return apiClient.patch<AdminProject>(`/api/v1/admin/projects/${projectId}`, { tier });
}
