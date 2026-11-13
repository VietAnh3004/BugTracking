import type { ReviewDecision } from '@/features/review/api/reviewApi';

/**
 * Quyết định của người dùng, dạng nhãn gọn cho bảng cảnh báo.
 *
 * `UNCERTAIN` và `INSUFFICIENT_EVIDENCE` dùng màu KHÁC NHAU dù cùng là "chưa quyết": cái đầu nói
 * về người ("tôi xem đủ rồi mà vẫn không chắc"), cái sau nói về hệ thống ("chưa cho tôi đủ bằng
 * chứng"). Tô cùng màu là gộp hai tín hiệu cải tiến hoàn toàn khác nhau.
 */
const STYLES: Record<ReviewDecision, { label: string; className: string }> = {
  ACCEPT: { label: 'Đúng là lỗi', className: 'bg-red-50 text-red-700 ring-red-600/20' },
  REJECT: { label: 'Báo nhầm', className: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  UNCERTAIN: { label: 'Chưa quyết', className: 'bg-amber-50 text-amber-800 ring-amber-600/20' },
  INSUFFICIENT_EVIDENCE: {
    label: 'Thiếu bằng chứng',
    className: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  },
};

export function DecisionBadge({ decision }: { decision?: ReviewDecision }) {
  if (decision === undefined) {
    // "Chưa ai xét" là một trạng thái có nghĩa, không phải ô trống — nó cho biết tiến độ.
    return <span className="text-ink-subtle text-xs">—</span>;
  }

  const style = STYLES[decision];
  return (
    <span
      className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset ${style.className}`}
    >
      {style.label}
    </span>
  );
}
