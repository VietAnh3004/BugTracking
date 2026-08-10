import type { ReviewDecision } from '@/features/review/api/reviewApi';

/**
 * Quyết định của người dùng, dạng nhãn gọn cho bảng cảnh báo.
 *
 * `UNCERTAIN` và `INSUFFICIENT_EVIDENCE` dùng màu KHÁC NHAU dù cùng là "chưa quyết": cái đầu nói
 * về người ("tôi xem đủ rồi mà vẫn không chắc"), cái sau nói về hệ thống ("chưa cho tôi đủ bằng
 * chứng"). Tô cùng màu là gộp hai tín hiệu cải tiến hoàn toàn khác nhau.
 */
const STYLES: Record<ReviewDecision, { label: string; className: string }> = {
  ACCEPT: { label: 'Đúng là lỗi', className: 'bg-red-100 text-red-800' },
  REJECT: { label: 'Báo nhầm', className: 'bg-emerald-100 text-emerald-800' },
  UNCERTAIN: { label: 'Chưa quyết', className: 'bg-amber-100 text-amber-800' },
  INSUFFICIENT_EVIDENCE: { label: 'Thiếu bằng chứng', className: 'bg-slate-200 text-slate-700' },
};

export function DecisionBadge({ decision }: { decision?: ReviewDecision }) {
  if (decision === undefined) {
    // "Chưa ai xét" là một trạng thái có nghĩa, không phải ô trống — nó cho biết tiến độ.
    return <span className="text-ink-muted text-xs">—</span>;
  }

  const style = STYLES[decision];
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${style.className}`}>
      {style.label}
    </span>
  );
}
