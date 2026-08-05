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
  HIGH: 'bg-sev-high text-white',
  MEDIUM: 'bg-sev-medium text-white',
  LOW: 'bg-sev-low text-white',
  INFO: 'bg-sev-info text-white',
};

export function SeverityBadge({ severity }: { severity: WarningSeverity }) {
  return (
    <span
      className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${SEVERITY_CLASSES[severity]}`}
    >
      {severity}
    </span>
  );
}
