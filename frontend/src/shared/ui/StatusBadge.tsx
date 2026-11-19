/**
 * Nhãn trạng thái.
 *
 * Thuần trình bày: không gọi API, không biết gì về cảnh báo hay bằng chứng. Nhờ vậy nó dùng lại
 * được cho trạng thái service, verdict của agent và kết quả human review.
 *
 * Bảng dưới chứa TÊN CLASS NGUYÊN VĂN chứ không ghép chuỗi (`bg-${tone}`) — Tailwind quét source
 * bằng phân tích tĩnh, class ghép động không bao giờ được sinh ra và hỏng im lặng (nhãn vẫn hiện,
 * chỉ mất màu). Xem giải thích đầy đủ trong `src/index.css`.
 *
 * Bốn tone dùng màu NGỮ NGHĨA của hệ thiết kế (`success`, `warning`, `danger`): cùng một trạng thái
 * mang cùng một màu ở mọi màn hình. Màu không bao giờ đứng một mình — nhãn luôn có chữ.
 */

export type StatusTone = 'ok' | 'warning' | 'danger' | 'neutral';

const TONE_CLASSES: Record<StatusTone, string> = {
  ok: 'bg-success/10 text-success ring-success/25',
  warning: 'bg-warning/10 text-warning ring-warning/25',
  danger: 'bg-danger/10 text-danger ring-danger/25',
  neutral: 'bg-surface-sunken text-ink-muted ring-border-strong',
};

/** Chấm màu đầu nhãn — đọc được trạng thái bằng màu kể cả khi lướt qua không đọc chữ. */
const DOT_CLASSES: Record<StatusTone, string> = {
  ok: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-ink-subtle',
};

interface StatusBadgeProps {
  tone: StatusTone;
  label: string;
}

export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex h-5 items-center gap-1.5 rounded-sm px-1.5 text-xs font-medium ring-1 ring-inset ${TONE_CLASSES[tone]}`}
    >
      <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${DOT_CLASSES[tone]}`} />
      {label}
    </span>
  );
}
