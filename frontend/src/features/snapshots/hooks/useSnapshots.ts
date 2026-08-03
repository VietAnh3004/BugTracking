import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import * as snapshotApi from '@/features/snapshots/api/snapshotApi';

export const snapshotKeys = {
  byProject: (projectId: string) => ['snapshots', 'project', projectId] as const,
  detail: (snapshotId: string) => ['snapshots', snapshotId] as const,
  files: (snapshotId: string) => ['snapshots', snapshotId, 'files'] as const,
  fileContent: (snapshotId: string, path: string, fromLine?: number, toLine?: number) =>
    ['snapshots', snapshotId, 'file', path, fromLine ?? null, toLine ?? null] as const,
};

export function useProjectSnapshots(projectId: string) {
  return useQuery({
    queryKey: snapshotKeys.byProject(projectId),
    queryFn: () => snapshotApi.listSnapshots(projectId),
  });
}

export function useSnapshot(snapshotId: string) {
  return useQuery({
    queryKey: snapshotKeys.detail(snapshotId),
    queryFn: () => snapshotApi.getSnapshot(snapshotId),
  });
}

export function useSnapshotFiles(snapshotId: string) {
  return useQuery({
    queryKey: snapshotKeys.files(snapshotId),
    queryFn: () => snapshotApi.listSnapshotFiles(snapshotId),
    // Cây file của một snapshot KHÔNG BAO GIỜ đổi (snapshot là ảnh chụp bất biến), nên không cần
    // làm mới lại lần nào trong phiên làm việc.
    staleTime: Infinity,
  });
}

export function useFileContent(
  snapshotId: string,
  path: string,
  range?: { fromLine?: number; toLine?: number },
) {
  return useQuery({
    queryKey: snapshotKeys.fileContent(snapshotId, path, range?.fromLine, range?.toLine),
    queryFn: () => snapshotApi.readFileContent(snapshotId, path, range),
    // Nội dung file cũng bất biến như cây file.
    staleTime: Infinity,
    enabled: path !== '',
  });
}

/**
 * Upload có tiến trình. Tiến trình giữ ở `useState` riêng chứ không nhét vào TanStack Query: nó
 * cập nhật hàng chục lần mỗi giây, đưa vào cache của query sẽ làm invalidate liên tục.
 */
export function useUploadSnapshot(projectId: string) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState(0);

  const mutation = useMutation({
    mutationFn: (file: File) => {
      setProgress(0);
      return snapshotApi.uploadSnapshot(projectId, file, setProgress);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: snapshotKeys.byProject(projectId) });
    },
  });

  return { ...mutation, progress };
}
