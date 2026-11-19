/**
 * Phần đầu Evidence Explorer: bằng chứng này là gì, ở đâu, và (nếu là cảnh báo) luật nào báo.
 *
 * Presentational thuần.
 */

import { Link } from 'react-router-dom';

import type { EvidenceDetail } from '@/features/evidence/api/evidenceApi';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';
import { IconAlert, IconChat, IconFile } from '@/shared/ui/icons';
import { buttonClass, CARD } from '@/shared/ui/styles';

export function EvidenceHeader({ evidence, runId }: { evidence: EvidenceDetail; runId: string }) {
  const warning = evidence.warning;

  // Thứ tự đọc theo câu hỏi người dùng mang tới màn hình này (19/11/2026):
  //   1. LỖI GÌ — mức độ, luật, câu mô tả;   2. Ở ĐÂU — file:line, to và bấm được;
  //   3. mã định danh (Evidence ID, loại) — cần khi trích dẫn, nên mờ hơn và đứng cuối.
  return (
    <header className={`${CARD} flex flex-col gap-3 p-4 sm:p-4`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          {/* `== null` ở mọi chỗ dereference — xem ghi chú `jackson.default-property-inclusion`
              trong `application.yml` của core-backend. */}
          {warning == null ? null : <SeverityBadge severity={warning.severityNormalized} />}
          {warning == null ? null : (
            <code className="text-ink font-mono text-xs font-medium break-all">
              {warning.ruleIdNormalized}
            </code>
          )}
          {warning == null ? null : <span className="text-ink-subtle text-xs">{warning.tool}</span>}
          {warning?.cweIds == null
            ? null
            : warning.cweIds.map((cwe) => (
                <span
                  key={cwe}
                  className="border-border-strong text-ink-muted text-2xs rounded-sm border px-1.5 py-0.5 font-mono"
                >
                  CWE-{cwe}
                </span>
              ))}
        </div>

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

      <h2 className="text-ink m-0 max-w-4xl text-base leading-snug font-semibold [overflow-wrap:anywhere]">
        {evidence.summary}
      </h2>

      {evidence.filePath == null ? (
        // filePath NULL nghĩa là không đối chiếu được với snapshot. Nói thẳng ra chứ không lặng lẽ
        // bỏ dòng này đi — đó là thông tin người dùng cần để chẩn đoán.
        <p className="text-warning m-0 inline-flex items-center gap-1.5 text-sm">
          <IconAlert className="size-4" />
          Không đối chiếu được vị trí với mã nguồn đã tải lên.
        </p>
      ) : (
        <Link
          to={`/snapshots/${evidence.snapshotId}/files/${toFilePathUrl(evidence.filePath)}${toLineHash(evidence.startLine, evidence.endLine)}`}
          className="text-brand inline-flex items-center gap-1.5 self-start font-mono text-sm font-medium break-all hover:underline"
        >
          <IconFile className="size-4" />
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

      <div className="border-border text-ink-subtle flex flex-wrap items-center gap-x-3 gap-y-1 border-t pt-3 font-mono text-xs">
        <span className="text-ink font-semibold">{evidence.shortId}</span>
        <span>{evidence.type}</span>
        {evidence.symbol == null ? null : <span className="break-all">{evidence.symbol}</span>}
      </div>
    </header>
  );
}
