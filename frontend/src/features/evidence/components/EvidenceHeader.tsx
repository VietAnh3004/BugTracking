/**
 * Phần đầu Evidence Explorer: bằng chứng này là gì, ở đâu, và (nếu là cảnh báo) luật nào báo.
 *
 * Presentational thuần.
 */

import { Link } from 'react-router-dom';

import type { EvidenceDetail } from '@/features/evidence/api/evidenceApi';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';

export function EvidenceHeader({ evidence }: { evidence: EvidenceDetail }) {
  const warning = evidence.warning;

  return (
    <header className="border-border mb-6 border-b pb-4">
      <div className="mb-2 flex flex-wrap items-center gap-2">
        <span className="text-ink font-mono text-sm font-semibold">{evidence.shortId}</span>
        <span className="text-ink-muted font-mono text-xs">{evidence.type}</span>
        {warning === undefined ? null : <SeverityBadge severity={warning.severityNormalized} />}
        {warning === undefined ? null : (
          <span className="text-ink-muted text-xs">
            {warning.tool} · <code className="font-mono">{warning.ruleIdNormalized}</code>
          </span>
        )}
        {warning?.cweIds === undefined
          ? null
          : warning.cweIds.map((cwe) => (
              <span key={cwe} className="text-ink-muted font-mono text-xs">
                CWE-{cwe}
              </span>
            ))}
      </div>

      <h2 className="text-ink mt-0 mb-2 text-lg font-semibold">{evidence.summary}</h2>

      {evidence.symbol === undefined ? null : (
        <p className="text-ink-muted mt-0 mb-1 font-mono text-xs break-all">{evidence.symbol}</p>
      )}

      {evidence.filePath === undefined ? (
        // filePath NULL nghĩa là không đối chiếu được với snapshot. Nói thẳng ra chứ không lặng lẽ
        // bỏ dòng này đi — đó là thông tin người dùng cần để chẩn đoán.
        <p className="text-ink m-0 text-xs italic">
          Không đối chiếu được vị trí với mã nguồn đã tải lên.
        </p>
      ) : (
        <Link
          to={`/snapshots/${evidence.snapshotId}/files/${toFilePathUrl(evidence.filePath)}${toLineHash(evidence.startLine, evidence.endLine)}`}
          className="text-brand font-mono text-xs break-all"
        >
          ↗ {evidence.filePath}
          {evidence.startLine === undefined ? '' : `:${String(evidence.startLine)}`}
        </Link>
      )}

      {/* Nhiều vị trí: 4/29 cảnh báo thật của vulnshop có, một cái trỏ tới ba file khác nhau. */}
      {warning === undefined || warning.locations.length <= 1 ? null : (
        <details className="mt-2">
          <summary className="text-ink-muted cursor-pointer text-xs">
            {warning.locations.length} vị trí
          </summary>
          <ul className="text-ink-muted m-0 mt-1 list-none p-0 font-mono text-xs">
            {warning.locations.map((location) => (
              <li key={location.ordinal} className="break-all">
                {location.filePath ?? `${location.pathRaw} (không đối chiếu được)`}
                {location.startLine === undefined ? '' : `:${String(location.startLine)}`}
              </li>
            ))}
          </ul>
        </details>
      )}
    </header>
  );
}
