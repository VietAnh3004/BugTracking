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
  ok: 'bg-green-100 text-green-800',
  warning: 'bg-yellow-100 text-yellow-800',
  danger: 'bg-red-100 text-red-800',
  neutral: 'bg-slate-200 text-slate-700',
};

interface StatusBadgeProps {
  tone: StatusTone;
  label: string;
}

export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-sm font-semibold ${TONE_CLASSES[tone]}`}
    >
      {label}
    </span>
  );
}
