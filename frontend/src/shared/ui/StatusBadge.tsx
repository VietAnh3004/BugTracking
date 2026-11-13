/**
 * Nhãn trạng thái.
 *
 * Thuần trình bày: không gọi API, không biết gì về cảnh báo hay bằng chứng. Nhờ vậy nó dùng lại
 * được cho trạng thái service, verdict của agent và kết quả human review.
 *
 * Bảng dưới chứa TÊN CLASS NGUYÊN VĂN chứ không ghép chuỗi (`bg-${tone}-100`) — Tailwind quét
 * source bằng phân tích tĩnh, class ghép động không bao giờ được sinh ra và hỏng im lặng (nhãn vẫn
 * hiện, chỉ mất màu). Xem giải thích đầy đủ trong `src/index.css`.
 *
 * Dùng bảng màu sẵn có của Tailwind (`green-100`…) chứ không thêm token vào `@theme`: token riêng
 * dành cho ngữ nghĩa của dự án (brand, severity, surface, ink), còn bốn tone chung chung này không
 * cần một tên riêng trong hệ thiết kế.
 */

export type StatusTone = 'ok' | 'warning' | 'danger' | 'neutral';

const TONE_CLASSES: Record<StatusTone, string> = {
  ok: 'bg-green-100 text-green-800 ring-green-600/20',
  warning: 'bg-amber-100 text-amber-800 ring-amber-600/20',
  danger: 'bg-red-100 text-red-800 ring-red-600/20',
  neutral: 'bg-slate-100 text-slate-700 ring-slate-500/20',
};

/** Chấm màu đầu nhãn — đọc được trạng thái bằng màu kể cả khi lướt qua không đọc chữ. */
const DOT_CLASSES: Record<StatusTone, string> = {
  ok: 'bg-green-500',
  warning: 'bg-amber-500',
  danger: 'bg-red-500',
  neutral: 'bg-slate-400',
};

interface StatusBadgeProps {
  tone: StatusTone;
  label: string;
}

export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONE_CLASSES[tone]}`}
    >
      <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${DOT_CLASSES[tone]}`} />
      {label}
    </span>
  );
}
