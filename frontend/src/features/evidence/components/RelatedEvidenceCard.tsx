/**
 * Một thẻ bằng chứng liên quan: nó là gì, liên kết bằng cách nào, và vì sao.
 *
 * Presentational thuần — không gọi API, không biết route nào ngoài hai đường dẫn nhận qua props.
 */

import { Link } from 'react-router-dom';

import type { RelatedEvidence } from '@/features/evidence/api/evidenceApi';
import { LinkStrengthBadge } from '@/features/evidence/components/LinkStrengthBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';

interface RelatedEvidenceCardProps {
  item: RelatedEvidence;
  snapshotId: string;
  runId: string;
}

export function RelatedEvidenceCard({ item, snapshotId, runId }: RelatedEvidenceCardProps) {
  return (
    <li className="border-border rounded border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <Link
          to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${item.shortId}`}
          className="text-brand font-mono text-xs font-semibold"
        >
          {item.shortId}
        </Link>
        <span className="text-ink-muted font-mono text-[11px]">{item.type}</span>
      </div>

      <p className="text-ink mt-1 mb-2 text-sm">{item.summary}</p>

      {/*
        `== null` chứ không `=== undefined`: xem ghi chú ở `application.yml` của core-backend.
        Backend nay bỏ hẳn trường null, nhưng chỗ này DEREFERENCE giá trị nên vẫn kiểm cả hai —
        một `"testCase": null` lọt qua đây là trắng nguyên trang (`null.className`), chứ không
        phải thiếu mất một dòng.
      */}
      {item.testCase == null ? null : (
        <dl className="text-ink-muted mb-2 text-xs">
          <div className="flex gap-1">
            <dt className="font-semibold">Test:</dt>
            <dd className="font-mono break-all">
              {item.testCase.className}#{item.testCase.methodName}
            </dd>
          </div>
          <div className="flex gap-1">
            <dt className="font-semibold">Trạng thái:</dt>
            <dd>{item.testCase.status}</dd>
          </div>
          {item.testCase.failureMessage == null ? null : (
            <div className="flex gap-1">
              <dt className="font-semibold">Lỗi:</dt>
              {/* Text node: React tự escape. Thông điệp lỗi đến từ mã người dùng (ADR 0006). */}
              <dd className="font-mono break-all">{item.testCase.failureMessage}</dd>
            </div>
          )}
        </dl>
      )}

      <LinkStrengthBadge relation={item.relation} method={item.method} weight={item.weight} />

      {/*
        `rationale` là câu giải thích DUY NHẤT người dùng đọc được cho một liên kết. Với CONTRADICTS
        nó còn mang câu cảnh báo "test xanh KHÔNG chứng minh cảnh báo sai" — thứ mà bỏ đi là mời
        người đọc rơi đúng vào cái bẫy vulnshop gài.
      */}
      {item.rationale == null ? null : (
        <p className="text-ink-muted mt-2 mb-0 text-xs italic">{item.rationale}</p>
      )}

      {item.filePath == null ? null : (
        <Link
          to={`/snapshots/${snapshotId}/files/${toFilePathUrl(item.filePath)}${toLineHash(item.startLine, item.endLine)}`}
          className="text-brand mt-2 inline-block font-mono text-xs"
        >
          ↗ {item.filePath}
          {item.startLine == null ? '' : `:${String(item.startLine)}`}
        </Link>
      )}
    </li>
  );
}
