/**
 * Nhãn mức độ nghiêm trọng, ánh xạ 1-1 với enum `WarningSeverity` của backend.
 *
 * Bảng dưới chứa TÊN CLASS NGUYÊN VĂN, không ghép chuỗi `` `bg-sev-${severity.toLowerCase()}` ``:
 * Tailwind quét source bằng phân tích TĨNH nên class ghép động không bao giờ được sinh ra, và hỏng
 * IM LẶNG — nhãn vẫn hiện, chỉ mất màu. Xem giải thích đầy đủ ở khối `@theme static` trong
 * `src/index.css`.
 *
 * Khác `StatusBadge` (bốn tone chung chung) vì đây là thang ngữ nghĩa riêng của dự án và có thứ tự
 * — dùng chung sẽ mất đúng cái ý nghĩa đó.
 */

import type { WarningSeverity } from '@/features/warnings/api/warningApi';

const SEVERITY_CLASSES: Record<WarningSeverity, string> = {
  HIGH: 'bg-sev-high/10 text-sev-high ring-sev-high/25',
  MEDIUM: 'bg-sev-medium/10 text-sev-medium ring-sev-medium/25',
  LOW: 'bg-sev-low/10 text-sev-low ring-sev-low/25',
  INFO: 'bg-sev-info/10 text-sev-info ring-sev-info/25',
};

const DOT_CLASSES: Record<WarningSeverity, string> = {
  HIGH: 'bg-sev-high',
  MEDIUM: 'bg-sev-medium',
  LOW: 'bg-sev-low',
  INFO: 'bg-sev-info',
};

export function SeverityBadge({ severity }: { severity: WarningSeverity }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-semibold tracking-wide ring-1 ring-inset ${SEVERITY_CLASSES[severity]}`}
    >
      <span
        aria-hidden="true"
        className={`size-1.5 shrink-0 rounded-full ${DOT_CLASSES[severity]}`}
      />
      {severity}
    </span>
  );
}
