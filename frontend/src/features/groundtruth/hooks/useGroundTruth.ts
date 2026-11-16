import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as api from '@/features/groundtruth/api/groundTruthApi';
import { reviewKeys } from '@/features/review/hooks/useReview';

import type { GroundTruthVerdict } from '@/features/groundtruth/api/groundTruthApi';

const keys = {
  sheet: (runId: string) => ['ground-truth', runId] as const,
};

export function useGroundTruthSheet(runId: string) {
  return useQuery({
    queryKey: keys.sheet(runId),
    queryFn: () => api.getSheet(runId),
    enabled: runId !== '',
  });
}

/**
 * Ghi/xoá nhãn rồi làm mới bảng VÀ chỉ số dashboard: độ chính xác của Agent và kappa đọc thẳng
 * từ bảng nhãn, để số cũ trong cache là hiện một con số không còn đúng.
 */
function useInvalidateAfterLabel(runId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: keys.sheet(runId) });
    void queryClient.invalidateQueries({ queryKey: reviewKeys.metrics(runId) });
  };
}

export function useSaveLabel(runId: string) {
  const invalidate = useInvalidateAfterLabel(runId);
  return useMutation({
    mutationFn: (input: { shortId: string; label: GroundTruthVerdict; rationale: string }) =>
      api.saveLabel(runId, input.shortId, input.label, input.rationale),
    onSuccess: invalidate,
  });
}

export function useDeleteLabel(runId: string) {
  const invalidate = useInvalidateAfterLabel(runId);
  return useMutation({
    mutationFn: (shortId: string) => api.deleteLabel(runId, shortId),
    onSuccess: invalidate,
  });
}

export function useImportLabels(runId: string) {
  const invalidate = useInvalidateAfterLabel(runId);
  return useMutation({
    mutationFn: (file: File) => api.importLabels(runId, file),
    onSuccess: invalidate,
  });
}
