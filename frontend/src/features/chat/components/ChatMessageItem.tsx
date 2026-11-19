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
        <p className="bg-brand-solid m-0 max-w-[85%] rounded-2xl rounded-br-md px-4 py-2.5 text-sm whitespace-pre-wrap text-white shadow-xs">
          {message.content}
        </p>
      </li>
    );
  }

  return (
    <li className="border-border bg-surface rounded-2xl rounded-bl-md border p-4 shadow-xs sm:p-5">
      {message.verdict != null && (
        <VerdictBadge verdict={message.verdict} downgradedFrom={message.downgradedFromVerdict} />
      )}

      <p className="text-ink mt-3 text-sm whitespace-pre-wrap">{message.content}</p>

      {message.claims.length > 0 && (
        <ul className="mt-3 space-y-2">
          {message.claims.map((claim) => (
            <ClaimCard key={claim.ordinal} claim={claim} snapshotId={snapshotId} runId={runId} />
          ))}
        </ul>
      )}

      {message.contradictingShortIds.length > 0 && (
        <section className="mt-3">
          <h3 className="text-ink-muted text-xs font-semibold tracking-wider uppercase">
            Bằng chứng đi ngược lại kết luận
          </h3>
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
          <h3 className="text-ink-muted text-xs font-semibold tracking-wider uppercase">
            Nên kiểm tra thêm
          </h3>
          <ul className="text-ink mt-1 list-inside list-disc space-y-0.5 text-xs">
            {message.suggestedNextChecks.map((check) => (
              <li key={check}>{check}</li>
            ))}
          </ul>
        </section>
      )}
    </li>
  );
}
