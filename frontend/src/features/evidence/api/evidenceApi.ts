import { apiClient } from '@/shared/lib/apiClient';
import type { components } from '@/shared/lib/api-types';

/**
 * Kiểu lấy từ OpenAPI, KHÔNG khai lại bằng tay: FE và BE không bao giờ lệch kiểu mà không ai biết,
 * vì CI chạy `npm run api:generate` rồi `git diff --exit-code`.
 */
export type EvidenceDetail = components['schemas']['EvidenceDetailResponse'];
export type RelatedEvidence = components['schemas']['RelatedEvidenceView'];
export type WarningDetail = components['schemas']['WarningDetailView'];
export type EvidenceLinkMethod = RelatedEvidence['method'];
export type EvidenceLinkRelation = RelatedEvidence['relation'];
export type EvidenceType = EvidenceDetail['type'];

/**
 * Một lượt gọi lấy đủ neo + cả bốn nhóm bằng chứng liên quan.
 *
 * Gọi đường REST của giao diện, TUYỆT ĐỐI không gọi `/api/v1/tools/**`: chuỗi filter của nhóm tool
 * chỉ nhận `X-Service-Token` và không bật CORS, nên trình duyệt bị chặn ở bước preflight với thông
 * điệp "blocked by CORS policy" — người debug sẽ đi sửa CORS trong khi vấn đề là chọn nhầm
 * endpoint (xem `CLAUDE.md`).
 */
export function getEvidenceDetail(runId: string, shortId: string): Promise<EvidenceDetail> {
  return apiClient.get<EvidenceDetail>(
    `/api/v1/analysis-runs/${runId}/evidence/${encodeURIComponent(shortId)}`,
  );
}
