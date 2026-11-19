import type { ReviewDecision } from '@/features/review/api/reviewApi';

/**
 * Quyết định của người dùng, dạng nhãn gọn cho bảng cảnh báo.
 *
 * `UNCERTAIN` và `INSUFFICIENT_EVIDENCE` dùng màu KHÁC NHAU dù cùng là "chưa quyết": cái đầu nói
 * về người ("tôi xem đủ rồi mà vẫn không chắc"), cái sau nói về hệ thống ("chưa cho tôi đủ bằng
 * chứng"). Tô cùng màu là gộp hai tín hiệu cải tiến hoàn toàn khác nhau.
 */
const STYLES: Record<ReviewDecision, { label: string; className: string }> = {
  ACCEPT: { label: 'Đúng là lỗi', className: 'bg-danger/10 text-danger ring-danger/25' },
  REJECT: { label: 'Báo nhầm', className: 'bg-success/10 text-success ring-success/25' },
  UNCERTAIN: { label: 'Chưa quyết', className: 'bg-warning/10 text-warning ring-warning/25' },
  INSUFFICIENT_EVIDENCE: {
    label: 'Thiếu bằng chứng',
    className: 'bg-surface-sunken text-ink-muted ring-border-strong',
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
      className={`inline-flex h-5 items-center rounded-sm px-1.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset ${style.className}`}
    >
      {style.label}
    </span>
  );
}
