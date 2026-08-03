/** Lời gọi API snapshot. Kiểu lấy từ `api-types.ts` nên FE/BE không thể lệch shape. */

import { apiClient } from '@/shared/lib/apiClient';
import { uploadFile } from '@/shared/lib/uploadClient';

import type { components } from '@/shared/lib/api-types';

export type Snapshot = components['schemas']['SnapshotResponse'];
export type SnapshotFile = components['schemas']['SnapshotFileResponse'];
export type FileContent = components['schemas']['FileContentResponse'];
export type SnapshotPage = components['schemas']['PageResponseSnapshotResponse'];

export function listSnapshots(projectId: string): Promise<SnapshotPage> {
  return apiClient.get<SnapshotPage>(`/api/v1/projects/${projectId}/snapshots`);
}

export function getSnapshot(snapshotId: string): Promise<Snapshot> {
  return apiClient.get<Snapshot>(`/api/v1/snapshots/${snapshotId}`);
}

export function listSnapshotFiles(snapshotId: string): Promise<SnapshotFile[]> {
  return apiClient.get<SnapshotFile[]>(`/api/v1/snapshots/${snapshotId}/files`);
}

/**
 * `path` đi qua query param (không phải path variable) nên PHẢI `encodeURIComponent` NGUYÊN CHUỖI
 * ở đây — khác hẳn `toFilePathUrl` dùng cho URL của trình duyệt, nơi dấu `/` phải giữ nguyên để
 * splat route đọc được. Hai chỗ mã hoá khác nhau vì hai mục đích khác nhau, đừng gộp lại.
 */
export function readFileContent(
  snapshotId: string,
  path: string,
  range?: { fromLine?: number; toLine?: number },
): Promise<FileContent> {
  const params = new URLSearchParams({ path });
  if (range?.fromLine !== undefined) {
    params.set('fromLine', String(range.fromLine));
  }
  if (range?.toLine !== undefined) {
    params.set('toLine', String(range.toLine));
  }
  return apiClient.get<FileContent>(`/api/v1/snapshots/${snapshotId}/file?${params.toString()}`);
}

export function uploadSnapshot(
  projectId: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<Snapshot> {
  return uploadFile<Snapshot>(`/api/v1/projects/${projectId}/snapshots`, file, { onProgress });
}
