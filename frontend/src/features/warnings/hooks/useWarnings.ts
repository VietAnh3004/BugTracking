import { useQuery } from '@tanstack/react-query';

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
