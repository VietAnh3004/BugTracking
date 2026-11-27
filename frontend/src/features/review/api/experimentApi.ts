/**
 * Thực nghiệm 3 cấu hình (LLM-Code / LLM-Static / Evidence-Agent) — docs/thuc-nghiem.md.
 * Kiểu lấy từ `api-types.ts` nên FE/BE không lệch shape.
 */

import { apiClient } from '@/shared/lib/apiClient';
import type { components } from '@/shared/lib/api-types';

export type ExperimentStatus = components['schemas']['ExperimentStatusResponse'];
export type ExperimentConfigMetrics = components['schemas']['ConfigMetrics'];
export type ExperimentConfig = ExperimentConfigMetrics['config'];

const base = (runId: string) => `/api/v1/analysis-runs/${runId}/experiment`;

export function getExperiment(runId: string): Promise<ExperimentStatus> {
  return apiClient.get<ExperimentStatus>(base(runId));
}

/** Chạy, hoặc chạy tiếp từ ô dở (ô đã xong được bỏ qua, ô hỏng được chạy lại). */
export function startExperiment(runId: string): Promise<ExperimentStatus> {
  return apiClient.post<ExperimentStatus>(base(runId));
}

export function stopExperiment(runId: string): Promise<ExperimentStatus> {
  return apiClient.post<ExperimentStatus>(`${base(runId)}/stop`);
}

export function experimentCsvPath(runId: string): string {
  return `${base(runId)}/export.csv`;
}
