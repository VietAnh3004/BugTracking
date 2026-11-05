import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import * as warningApi from '@/features/warnings/api/warningApi';

import type { WarningFilters } from '@/features/warnings/api/warningApi';

export const warningKeys = {
  runsBySnapshot: (snapshotId: string) => ['analysis-runs', 'snapshot', snapshotId] as const,
  run: (runId: string) => ['analysis-runs', runId] as const,
  warnings: (runId: string, filters: WarningFilters, page: number) =>
    ['analysis-runs', runId, 'warnings', filters, page] as const,
};

export function useAnalysisRuns(snapshotId: string) {
  return useQuery({
    queryKey: warningKeys.runsBySnapshot(snapshotId),
    queryFn: () => warningApi.listAnalysisRuns(snapshotId),
  });
}

export function useAnalysisRun(runId: string) {
  return useQuery({
    queryKey: warningKeys.run(runId),
    queryFn: () => warningApi.getAnalysisRun(runId),
  });
}

/**
 * Kết quả của một run đã kết thúc là BẤT BIẾN — cùng lý do với cây file snapshot. `staleTime`
 * vô hạn tránh gọi lại API mỗi lần người dùng chuyển tab rồi quay lại.
 *
 * `enabled` chờ `runId`: hook được gọi trước khi route param sẵn sàng sẽ bắn một request tới
 * `/analysis-runs//warnings` và nhận 404 vô nghĩa.
 */
export function useWarnings(runId: string, filters: WarningFilters, page = 0) {
  return useQuery({
    queryKey: warningKeys.warnings(runId, filters, page),
    queryFn: () => warningApi.listWarnings(runId, filters, page),
    staleTime: Infinity,
    enabled: runId !== '',
  });
}

/** Nạp artifact analyzer — tiến trình giữ ở `useState`, cùng lý do với `useUploadSnapshot`. */
export function useIngestArtifacts(snapshotId: string) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState(0);

  const mutation = useMutation({
    mutationFn: (files: File[]) => {
      setProgress(0);
      return warningApi.ingestArtifacts(snapshotId, files, setProgress);
    },
    onSuccess: () => {
      // Kể cả run FAILED cũng là một hàng mới trong danh sách run của snapshot.
      void queryClient.invalidateQueries({ queryKey: warningKeys.runsBySnapshot(snapshotId) });
    },
  });

  return { ...mutation, progress };
}

/** Hỏi lại mỗi 2 giây — khớp nhịp mà Javadoc `AnalysisRunController.runInSandbox` đề nghị. */
const SANDBOX_POLL_MS = 2000;

/**
 * Chạy analyzer trong Docker sandbox rồi THEO DÕI run cho tới khi nó rời `RUNNING`.
 *
 * Run được theo dõi là run vừa bấm chạy ở trang này, HOẶC run mới nhất của snapshot nếu nó còn
 * `RUNNING` — để tải lại trang (hay mở tab khác) giữa lúc đang phân tích vẫn thấy tiến trình, thay
 * vì một nút "Phân tích" bấm được lần nữa và xếp thêm một lượt chạy vào hàng đợi.
 */
export function useSandboxRun(snapshotId: string) {
  const queryClient = useQueryClient();
  const runs = useAnalysisRuns(snapshotId);

  const start = useMutation({
    mutationFn: () => warningApi.startSandboxRun(snapshotId),
    onSuccess: (run) => {
      queryClient.setQueryData(warningKeys.run(run.id), run);
      void queryClient.invalidateQueries({ queryKey: warningKeys.runsBySnapshot(snapshotId) });
    },
  });

  const latest = runs.data?.items[0];
  const watchedId = start.data?.id ?? (latest?.status === 'RUNNING' ? latest.id : '');

  const watched = useQuery({
    queryKey: warningKeys.run(watchedId),
    queryFn: () => warningApi.getAnalysisRun(watchedId),
    enabled: watchedId !== '',
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? SANDBOX_POLL_MS : false),
  });

  // Run vừa rời RUNNING: làm mới danh sách run để link "Cảnh báo (N)" trỏ sang run mới.
  const status = watchedId === '' ? undefined : watched.data?.status;
  useEffect(() => {
    if (status !== undefined && status !== 'RUNNING') {
      void queryClient.invalidateQueries({ queryKey: warningKeys.runsBySnapshot(snapshotId) });
    }
  }, [status, snapshotId, queryClient]);

  const run = watchedId === '' ? undefined : watched.data;
  return {
    start: () => start.mutate(),
    startError: start.error,
    run,
    running: start.isPending || run?.status === 'RUNNING',
    // Kết quả cuối chỉ hiện cho run bấm chạy TẠI trang này; run do nơi khác chạy thì link
    // "Cảnh báo (N)" đã đủ báo là xong.
    finishedHere: start.data !== undefined && run !== undefined && run.status !== 'RUNNING',
  };
}
