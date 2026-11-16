/** Lời gọi API gán nhãn ground truth. Kiểu lấy từ `api-types.ts` (sinh từ OpenAPI). */

import { apiClient } from '@/shared/lib/apiClient';
import { downloadFile } from '@/shared/lib/downloadClient';
import { uploadFile } from '@/shared/lib/uploadClient';

import type { components } from '@/shared/lib/api-types';

export type GroundTruthSheet = components['schemas']['GroundTruthSheetResponse'];
export type GroundTruthRow = components['schemas']['GroundTruthRow'];
export type GroundTruthLabel = components['schemas']['GroundTruthLabelView'];
export type GroundTruthVerdict = GroundTruthLabel['label'];
export type GroundTruthImportResult = components['schemas']['GroundTruthImportResponse'];

export function getSheet(runId: string): Promise<GroundTruthSheet> {
  return apiClient.get<GroundTruthSheet>(`/api/v1/analysis-runs/${runId}/ground-truth`);
}

/** Nguồn nhãn do backend quyết định theo vai trò — request KHÔNG khai nguồn. */
export function saveLabel(
  runId: string,
  shortId: string,
  label: GroundTruthVerdict,
  rationale: string,
): Promise<GroundTruthLabel> {
  return apiClient.put<GroundTruthLabel>(
    `/api/v1/analysis-runs/${runId}/evidence/${shortId}/ground-truth`,
    { label, rationale },
  );
}

export function deleteLabel(runId: string, shortId: string): Promise<void> {
  return apiClient.delete<void>(`/api/v1/analysis-runs/${runId}/evidence/${shortId}/ground-truth`);
}

export function exportLabels(runId: string): Promise<void> {
  return downloadFile(
    `/api/v1/analysis-runs/${runId}/ground-truth/export.yaml`,
    `ground-truth-${runId}.yaml`,
  );
}

export function importLabels(runId: string, file: File): Promise<GroundTruthImportResult> {
  return uploadFile<GroundTruthImportResult>(
    `/api/v1/analysis-runs/${runId}/ground-truth/import`,
    file,
  );
}
