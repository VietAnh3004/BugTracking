/**
 * Phần đầu Evidence Explorer: bằng chứng này là gì, ở đâu, và (nếu là cảnh báo) luật nào báo.
 *
 * Presentational thuần.
 */

import { Link } from 'react-router-dom';

import type { EvidenceDetail } from '@/features/evidence/api/evidenceApi';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';
import { IconChat, IconFile } from '@/shared/ui/icons';
import { buttonClass, CARD } from '@/shared/ui/styles';

export function EvidenceHeader({ evidence, runId }: { evidence: EvidenceDetail; runId: string }) {
  const warning = evidence.warning;

  return (
    <header className={`${CARD} p-5 sm:p-6`}>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="bg-ink text-surface rounded-md px-2 py-0.5 font-mono text-xs font-semibold">
          {evidence.shortId}
        </span>
        <span className="bg-surface-sunken text-ink-muted rounded-md px-2 py-0.5 font-mono text-[11px]">
          {evidence.type}
        </span>
        {/* `== null` ở mọi chỗ dereference — xem ghi chú `jackson.default-property-inclusion`
            trong `application.yml` của core-backend. */}
        {warning == null ? null : <SeverityBadge severity={warning.severityNormalized} />}
        {warning == null ? null : (
          <span className="text-ink-muted text-xs">
            {warning.tool} ·{' '}
            <code className="text-ink font-mono font-medium">{warning.ruleIdNormalized}</code>
          </span>
        )}
        {warning?.cweIds == null
          ? null
          : warning.cweIds.map((cwe) => (
              <span
                key={cwe}
                className="border-border text-ink-muted rounded-md border px-1.5 py-0.5 font-mono text-[11px]"
              >
                CWE-{cwe}
              </span>
            ))}
      </div>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <h2 className="text-ink m-0 max-w-4xl text-lg leading-snug font-semibold [overflow-wrap:anywhere]">
          {evidence.summary}
        </h2>

        {/* Lối vào hội thoại, mang sẵn `focus` để phiên mới neo đúng vào bằng chứng đang xem —
            người dùng không phải chép Evidence ID sang màn hình khác. */}
        <Link
          to={`/snapshots/${evidence.snapshotId}/runs/${runId}/chat?focus=${evidence.shortId}`}
          className={buttonClass('primary')}
        >
          <IconChat className="size-4" />
          Hỏi Agent về {evidence.shortId}
        </Link>
      </div>

      <div className="border-border mt-4 flex flex-col gap-1.5 border-t pt-4">
        {evidence.symbol == null ? null : (
          <p className="text-ink-muted m-0 font-mono text-xs break-all">{evidence.symbol}</p>
        )}

        {evidence.filePath == null ? (
          // filePath NULL nghĩa là không đối chiếu được với snapshot. Nói thẳng ra chứ không lặng lẽ
          // bỏ dòng này đi — đó là thông tin người dùng cần để chẩn đoán.
          <p className="text-ink m-0 text-xs italic">
            Không đối chiếu được vị trí với mã nguồn đã tải lên.
          </p>
        ) : (
          <Link
            to={`/snapshots/${evidence.snapshotId}/files/${toFilePathUrl(evidence.filePath)}${toLineHash(evidence.startLine, evidence.endLine)}`}
            className="text-brand inline-flex items-center gap-1.5 self-start font-mono text-xs font-medium break-all hover:underline"
          >
            <IconFile className="size-3.5" />
            {evidence.filePath}
            {evidence.startLine == null ? '' : `:${String(evidence.startLine)}`}
          </Link>
        )}

        {/* Nhiều vị trí: 4/29 cảnh báo thật của vulnshop có, một cái trỏ tới ba file khác nhau. */}
        {warning == null || warning.locations.length <= 1 ? null : (
          <details>
            <summary className="text-ink-muted cursor-pointer text-xs">
              {warning.locations.length} vị trí
            </summary>
            <ul className="text-ink-muted m-0 mt-1 list-none p-0 font-mono text-xs">
              {warning.locations.map((location) => (
                <li key={location.ordinal} className="break-all">
                  {location.filePath ?? `${location.pathRaw} (không đối chiếu được)`}
                  {location.startLine == null ? '' : `:${String(location.startLine)}`}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </header>
  );
}
