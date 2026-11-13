import type { Claim } from '@/features/chat/api/chatApi';
import { CitationChip } from '@/features/chat/components/CitationChip';

interface ClaimCardProps {
  claim: Claim;
  snapshotId: string;
  runId: string;
}

/**
 * Một nhận định kèm những Evidence ID chống lưng cho nó.
 *
 * Nhận định KHÔNG có trích dẫn hợp lệ vẫn hiện ra, được đánh dấu rõ. Xoá đi thì Unsupported Claim
 * Rate luôn bằng 0 và luôn vô nghĩa — và người dùng mất luôn khả năng thấy Agent đang nói gì mà
 * không có căn cứ.
 */
export function ClaimCard({ claim, snapshotId, runId }: ClaimCardProps) {
  const unsupported = claim.downgradedFromVerdict != null;

  return (
    <li
      className={`rounded-xl border p-3 ${
        unsupported ? 'border-amber-300 bg-amber-50/60' : 'border-border bg-surface-muted'
      }`}
    >
      <p className="text-ink text-sm">{claim.text}</p>

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {claim.citations.length === 0 ? (
          <span className="text-xs text-amber-700">Không có trích dẫn nào</span>
        ) : (
          claim.citations.map((citation, index) => (
            <CitationChip
              // Cùng một chuỗi có thể được trích dẫn hai lần trong một nhận định, nên khoá phải
              // kèm vị trí — dùng riêng chuỗi sẽ làm React gộp nhầm hai phần tử khác nhau.
              key={`${citation.citedShortIdRaw}-${index}`}
              citation={citation}
              snapshotId={snapshotId}
              runId={runId}
            />
          ))
        )}
        {claim.confidence != null && (
          <span className="text-ink-subtle ml-auto text-xs">
            Agent tự đánh giá độ chắc chắn {Math.round(claim.confidence * 100)}%
          </span>
        )}
      </div>

      {unsupported && (
        <p className="mt-2 text-xs text-amber-800">
          Nhận định này không có trích dẫn nào tra được về bằng chứng thật, nên đã bị hạ xuống “chưa
          đủ bằng chứng”.
        </p>
      )}
    </li>
  );
}
