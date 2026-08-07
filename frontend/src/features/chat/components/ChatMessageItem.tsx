import type { ChatMessage } from '@/features/chat/api/chatApi';
import { CitationChip } from '@/features/chat/components/CitationChip';
import { ClaimCard } from '@/features/chat/components/ClaimCard';
import { VerdictBadge } from '@/features/chat/components/VerdictBadge';

interface ChatMessageItemProps {
  message: ChatMessage;
  snapshotId: string;
  runId: string;
}

export function ChatMessageItem({ message, snapshotId, runId }: ChatMessageItemProps) {
  if (message.role === 'USER') {
    return (
      <li className="flex justify-end">
        <p className="max-w-[85%] rounded-lg bg-sky-600 px-3 py-2 text-sm whitespace-pre-wrap text-white">
          {message.content}
        </p>
      </li>
    );
  }

  return (
    <li className="rounded-lg border border-slate-200 bg-white p-4">
      {message.verdict != null && (
        <VerdictBadge verdict={message.verdict} downgradedFrom={message.downgradedFromVerdict} />
      )}

      <p className="mt-3 text-sm whitespace-pre-wrap text-slate-800">{message.content}</p>

      {message.claims.length > 0 && (
        <ul className="mt-3 space-y-2">
          {message.claims.map((claim) => (
            <ClaimCard key={claim.ordinal} claim={claim} snapshotId={snapshotId} runId={runId} />
          ))}
        </ul>
      )}

      {message.contradictingShortIds.length > 0 && (
        <section className="mt-3">
          <h3 className="text-xs font-medium text-slate-600">Bằng chứng đi ngược lại kết luận</h3>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {message.contradictingShortIds.map((shortId) => (
              <CitationChip
                key={shortId}
                // Những ID này KHÔNG đi qua bảng claim_citation (chúng không phải nhận định), nên
                // không có kết quả hậu kiểm. Hiện dưới dạng liên kết thẳng và để Evidence Explorer
                // báo 404 nếu sai — tự suy ra resolution ở đây là bịa ra một con số cho báo cáo.
                citation={{ citedShortIdRaw: shortId, resolution: 'RESOLVED', shortId }}
                snapshotId={snapshotId}
                runId={runId}
              />
            ))}
          </div>
        </section>
      )}

      {message.suggestedNextChecks.length > 0 && (
        <section className="mt-3">
          <h3 className="text-xs font-medium text-slate-600">Nên kiểm tra thêm</h3>
          <ul className="mt-1 list-inside list-disc space-y-0.5 text-xs text-slate-700">
            {message.suggestedNextChecks.map((check) => (
              <li key={check}>{check}</li>
            ))}
          </ul>
        </section>
      )}
    </li>
  );
}
