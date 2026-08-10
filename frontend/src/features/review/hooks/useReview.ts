import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  type Review,
  type RunMetrics,
  type SubmitReview,
  getRunMetrics,
  listReviews,
  submitReview,
} from '@/features/review/api/reviewApi';

export const reviewKeys = {
  list: (runId: string) => ['reviews', runId] as const,
  metrics: (runId: string) => ['run-metrics', runId] as const,
};

/**
 * Quyết định của CHÍNH người dùng trong một lần phân tích.
 *
 * Tải một lần cho cả bảng cảnh báo thay vì hỏi từng dòng: bảng có tới 34 dòng, và 34 request chỉ
 * để tô trạng thái là cách chắc chắn làm màn hình giật.
 */
export function useReviews(runId: string) {
  return useQuery<Review[]>({
    queryKey: reviewKeys.list(runId),
    queryFn: () => listReviews(runId),
    enabled: runId !== '',
  });
}

export function useRunMetrics(runId: string) {
  return useQuery<RunMetrics>({
    queryKey: reviewKeys.metrics(runId),
    queryFn: () => getRunMetrics(runId),
    enabled: runId !== '',
  });
}

/**
 * Ghi một quyết định.
 *
 * Invalidate CẢ chỉ số: một quyết định mới làm đổi tiến độ review, phân bố quyết định và mức khớp
 * với Agent. Không invalidate thì dashboard hiện số cũ và người dùng tưởng thao tác không ăn.
 */
export function useSubmitReview(runId: string) {
  const queryClient = useQueryClient();

  return useMutation<Review, Error, { shortId: string; body: SubmitReview }>({
    mutationFn: ({ shortId, body }) => submitReview(runId, shortId, body),
    onSuccess: (saved) => {
      queryClient.setQueryData<Review[]>(reviewKeys.list(runId), (current) => {
        const others = (current ?? []).filter((r) => r.shortId !== saved.shortId);
        return [saved, ...others];
      });
      void queryClient.invalidateQueries({ queryKey: reviewKeys.metrics(runId) });
    },
  });
}
