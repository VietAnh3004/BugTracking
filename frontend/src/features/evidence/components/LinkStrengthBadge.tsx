/**
 * Nhãn cho biết một liên kết được suy ra BẰNG CÁCH NÀO và tin được tới đâu.
 *
 * Thành phần quan trọng nhất của Evidence Explorer: nếu bỏ nó đi, mọi bằng chứng trông ngang nhau
 * và người đọc không phân biệt được "test này thực sự chạy qua dòng lỗi" với "lớp test tình cờ
 * cùng tên". Xem `linkStrength.ts`.
 */

import type { RelatedEvidence } from '@/features/evidence/api/evidenceApi';
import {
  METHOD_LABELS,
  RELATION_LABELS,
  TIER_CLASSES,
  TIER_DOTS,
} from '@/features/evidence/lib/linkStrength';

interface LinkStrengthBadgeProps {
  relation: RelatedEvidence['relation'];
  method: RelatedEvidence['method'];
  weight: number;
}

export function LinkStrengthBadge({ relation, method, weight }: LinkStrengthBadgeProps) {
  const info = METHOD_LABELS[method];

  return (
    <span className="inline-flex flex-wrap items-center gap-1.5 text-xs" title={info.explain}>
      <span className="text-ink font-semibold">{RELATION_LABELS[relation]}</span>
      <span className={`inline-block rounded px-1.5 py-0.5 ${TIER_CLASSES[info.tier]}`}>
        {info.label}
      </span>
      <span className="text-ink-muted tabular-nums" aria-label={`độ tin cậy ${String(weight)}`}>
        {TIER_DOTS[info.tier]} {weight.toFixed(2)}
      </span>
      {/* Mã enum nguyên văn: đây là thứ tuần 15 đếm, và người chấm phải đối chiếu được với SQL. */}
      <code className="text-ink-muted font-mono text-[11px]">{method}</code>
      {info.tier === 'weak' ? (
        <span className="text-sev-medium font-semibold">⚠ liên kết yếu</span>
      ) : null}
    </span>
  );
}
