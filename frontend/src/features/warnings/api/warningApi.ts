/** Lời gọi API cảnh báo tĩnh. Kiểu lấy từ `api-types.ts` nên FE/BE không thể lệch shape. */

import { apiClient } from '@/shared/lib/apiClient';

import type { components } from '@/shared/lib/api-types';

export type AnalysisRun = components['schemas']['AnalysisRunResponse'];
export type Warning = components['schemas']['WarningView'];
export type WarningPage = components['schemas']['PageResponseWarningView'];
export type AnalysisRunPage = components['schemas']['PageResponseAnalysisRunResponse'];

export type WarningTool = Warning['tool'];
export type WarningSeverity = Warning['severityNormalized'];

export interface WarningFilters {
  tool?: WarningTool;
  severity?: WarningSeverity;
  filePath?: string;
}

export function listAnalysisRuns(snapshotId: string): Promise<AnalysisRunPage> {
  return apiClient.get<AnalysisRunPage>(`/api/v1/snapshots/${snapshotId}/analysis-runs`);
}

export function getAnalysisRun(runId: string): Promise<AnalysisRun> {
  return apiClient.get<AnalysisRun>(`/api/v1/analysis-runs/${runId}`);
}

/**
 * KHÔNG gửi tham số `sort`: thứ tự đã cố định theo `warning.severity_rank` ở tầng SQL. Sắp theo
 * `severityNormalized` (chuỗi) sẽ ra HIGH, INFO, LOW, MEDIUM — sai mà nhìn qua không thấy vì HIGH
 * vẫn đứng đầu. Backend cũng chủ động bỏ `sort` nếu client cứ gửi.
 */
export function listWarnings(
  runId: string,
  filters: WarningFilters = {},
  page = 0,
  size = 50,
): Promise<WarningPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.tool !== undefined) {
    params.set('tool', filters.tool);
  }
  if (filters.severity !== undefined) {
    params.set('severity', filters.severity);
  }
  if (filters.filePath !== undefined && filters.filePath !== '') {
    params.set('filePath', filters.filePath);
  }
  return apiClient.get<WarningPage>(`/api/v1/analysis-runs/${runId}/warnings?${params.toString()}`);
}
