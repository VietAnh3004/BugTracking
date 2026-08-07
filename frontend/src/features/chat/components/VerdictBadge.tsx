import type { Verdict } from '@/features/chat/api/chatApi';

/**
 * Ba kết luận của Agent.
 *
 * `INSUFFICIENT_EVIDENCE` cố ý dùng màu trung tính chứ không phải màu lỗi: "chưa đủ bằng chứng"
 * là một câu trả lời HỢP LỆ và hữu ích, không phải một sự cố. Tô đỏ nó sẽ dạy người dùng coi sự
 * thận trọng là hỏng hóc — trong khi chính nó là thứ đồ án này muốn Agent làm được.
 */
const STYLES: Record<Verdict, { label: string; className: string }> = {
  LIKELY_TRUE_POSITIVE: {
    label: 'Nhiều khả năng là lỗi thật',
    className: 'bg-red-100 text-red-800 ring-red-600/20',
  },
  LIKELY_FALSE_POSITIVE: {
    label: 'Nhiều khả năng báo nhầm',
    className: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20',
  },
  INSUFFICIENT_EVIDENCE: {
    label: 'Chưa đủ bằng chứng',
    className: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  },
};

interface VerdictBadgeProps {
  verdict: Verdict;
  /** Verdict gốc trước khi bị hạ cấp; khác null thì hiện thêm lời giải thích. */
  downgradedFrom?: Verdict | null;
}

export function VerdictBadge({ verdict, downgradedFrom }: VerdictBadgeProps) {
  const style = STYLES[verdict];
  const wasDowngraded = downgradedFrom != null;

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <span
        className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset ${style.className}`}
      >
        {style.label}
      </span>
      {wasDowngraded && (
        <span
          className="text-xs text-amber-700"
          title={`Agent kết luận ${STYLES[downgradedFrom].label.toLowerCase()}, nhưng không nhận định nào có trích dẫn tra được về bằng chứng thật trong lần phân tích này.`}
        >
          đã hạ cấp từ “{STYLES[downgradedFrom].label}”
        </span>
      )}
    </span>
  );
}
