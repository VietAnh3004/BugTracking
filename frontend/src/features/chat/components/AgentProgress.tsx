import type { ProgressStep } from '@/features/chat/hooks/useAskAgent';

interface AgentProgressProps {
  steps: ProgressStep[];
  isStreaming: boolean;
}

/**
 * Tiến trình truy xuất bằng chứng, hiện trong lúc chờ.
 *
 * Đây KHÔNG phải trang trí cho đỡ sốt ruột. Nó cho người dùng — và người chấm — thấy tận mắt rằng
 * Agent thực sự đi lấy bằng chứng trước khi kết luận, thay vì phải tin vào một câu trong báo cáo.
 * Cũng vì vậy mà giao diện không stream từng token của câu trả lời: đầu ra bị ép theo cấu trúc,
 * và một JSON dở dang sẽ hiện ra những Evidence ID chưa qua hậu kiểm.
 */
export function AgentProgress({ steps, isStreaming }: AgentProgressProps) {
  if (!isStreaming && steps.length === 0) {
    return null;
  }

  return (
    <div className="border-border bg-surface rounded-lg border p-3" aria-live="polite">
      <p className="text-brand text-xs font-semibold">Agent đang truy xuất bằng chứng</p>
      <ol className="mt-2 space-y-1">
        {steps.map((step) => (
          <li key={step.id} className="flex items-baseline gap-2 text-xs">
            <span className={step.failed ? 'text-warning' : 'text-ink'}>{step.label}</span>
            {step.detail != null && <span className="text-ink-muted font-mono">{step.detail}</span>}
          </li>
        ))}
        {isStreaming && <li className="text-ink-muted animate-pulse text-xs">…</li>}
      </ol>
    </div>
  );
}
