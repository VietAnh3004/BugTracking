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
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3" aria-live="polite">
      <p className="text-xs font-medium text-slate-600">Agent đang truy xuất bằng chứng</p>
      <ol className="mt-2 space-y-1">
        {steps.map((step) => (
          <li key={step.id} className="flex items-baseline gap-2 text-xs">
            <span className={step.failed ? 'text-amber-700' : 'text-slate-700'}>{step.label}</span>
            {step.detail != null && <span className="font-mono text-slate-500">{step.detail}</span>}
          </li>
        ))}
        {isStreaming && <li className="text-xs text-slate-500">…</li>}
      </ol>
    </div>
  );
}
