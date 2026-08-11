/**
 * Một nhóm bằng chứng liên quan, kèm trạng thái RỖNG CÓ NGHĨA.
 *
 * ==================== "Không có bằng chứng" là một KẾT QUẢ ====================
 * Nhóm rỗng KHÔNG được biến mất im lặng. Sự vắng mặt của bằng chứng chính là cơ sở của verdict
 * `INSUFFICIENT_EVIDENCE` — một trong ba verdict của hệ thống. Giấu nó đi thì người dùng (và AI
 * Agent ở tuần 12-13) không phân biệt được "đã kiểm và không thấy gì" với "chưa kiểm".
 *
 * Vì vậy chữ ở đây dùng mực THƯỜNG, không phải xám nhạt: xám nhạt đọc như "không có gì đáng chú ý
 * ở đây", trong khi đây là một khẳng định.
 * =============================================================================
 */

import type { RelatedEvidence } from '@/features/evidence/api/evidenceApi';
import { RelatedEvidenceCard } from '@/features/evidence/components/RelatedEvidenceCard';

interface RelatedEvidenceGroupProps {
  title: string;
  items: RelatedEvidence[];
  emptyMessage: string;
  snapshotId: string;
  runId: string;
  /** Vị trí của bằng chứng đang xem — chỉ để dựng link cho thẻ cấp file, xem `RelatedEvidenceCard`. */
  anchorFilePath?: string | null;
  anchorStartLine?: number | null;
}

export function RelatedEvidenceGroup({
  title,
  items,
  emptyMessage,
  snapshotId,
  runId,
  anchorFilePath,
  anchorStartLine,
}: RelatedEvidenceGroupProps) {
  return (
    <section className="mb-6">
      <h3 className="text-ink mb-2 text-sm font-semibold tracking-wide uppercase">
        {title} <span className="text-ink-muted tabular-nums">({items.length})</span>
      </h3>

      {items.length === 0 ? (
        <p className="border-border text-ink m-0 rounded border border-dashed p-3 text-sm">
          {emptyMessage}
        </p>
      ) : (
        <ul className="m-0 grid list-none gap-2 p-0">
          {items.map((item) => (
            <RelatedEvidenceCard
              key={`${item.shortId}-${item.relation}-${item.method}`}
              item={item}
              snapshotId={snapshotId}
              runId={runId}
              anchorFilePath={anchorFilePath}
              anchorStartLine={anchorStartLine}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
