import { useQuery } from '@tanstack/react-query';

import { type EvidenceDetail, getEvidenceDetail } from '@/features/evidence/api/evidenceApi';

export const evidenceKeys = {
  detail: (runId: string, shortId: string) => ['evidence', runId, shortId] as const,
};

/**
 * Chi tiết một bằng chứng.
 *
 * `staleTime: Infinity` vì bằng chứng của một `analysis_run` là BẤT BIẾN — run đã SUCCEEDED thì
 * không có gì đổi nữa. Ngoại lệ duy nhất là sau khi chạy lại linker, và khi đó phải invalidate
 * tường minh chứ không dựa vào thời gian hết hạn.
 *
 * `enabled` chặn lời gọi khi tham số route còn rỗng: thiếu nó, lần render đầu sẽ gọi
 * `/analysis-runs//evidence/` và nhận về một lỗi vô nghĩa (cùng bẫy đã ghi ở `useWarnings`).
 */
export function useEvidenceDetail(runId: string, shortId: string) {
  return useQuery<EvidenceDetail>({
    queryKey: evidenceKeys.detail(runId, shortId),
    queryFn: () => getEvidenceDetail(runId, shortId),
    staleTime: Infinity,
    enabled: runId !== '' && shortId !== '',
  });
}
