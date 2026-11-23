/** Lời gọi API Human Review. Kiểu lấy từ `api-types.ts` nên FE/BE không thể lệch shape. */

import { apiClient } from '@/shared/lib/apiClient';
import type { components } from '@/shared/lib/api-types';

export type Review = components['schemas']['ReviewResponse'];
export type SubmitReview = components['schemas']['SubmitReviewRequest'];
export type ReviewDecision = Review['decision'];
export type RunMetrics = components['schemas']['RunMetricsResponse'];
export type LatestAgentAnswer = components['schemas']['LatestAgentAnswerResponse'];

export function listReviews(runId: string): Promise<Review[]> {
  return apiClient.get<Review[]>(`/api/v1/analysis-runs/${runId}/reviews`);
}

/**
 * PUT chứ không POST: gửi lại cùng nội dung phải cho cùng kết quả, và một người chỉ có đúng một
 * quyết định cho một cảnh báo (backend ép bằng UNIQUE). Đổi ý thì gọi lại chính hàm này.
 */
export function submitReview(runId: string, shortId: string, body: SubmitReview): Promise<Review> {
  return apiClient.put<Review>(
    `/api/v1/analysis-runs/${runId}/evidence/${encodeURIComponent(shortId)}/review`,
    body,
  );
}

/**
 * Câu trả lời Agent mới nhất mà chính người dùng đã nhận về cảnh báo này. Mọi trường vắng mặt nghĩa
 * là chưa hỏi.
 */
export function getLatestAgentAnswer(runId: string, shortId: string): Promise<LatestAgentAnswer> {
  return apiClient.get<LatestAgentAnswer>(
    `/api/v1/analysis-runs/${runId}/evidence/${encodeURIComponent(shortId)}/latest-agent-answer`,
  );
}

export function getRunMetrics(runId: string): Promise<RunMetrics> {
  return apiClient.get<RunMetrics>(`/api/v1/analysis-runs/${runId}/metrics`);
}
