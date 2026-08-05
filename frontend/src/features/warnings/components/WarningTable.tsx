/**
 * Bảng cảnh báo. Thuần trình bày — nhận dữ liệu đã tải, không gọi API.
 *
 * Điểm quan trọng nhất của component này KHÔNG phải là bảng: đó là cách xử lý hàng có
 * `filePath === null`. Đó là cảnh báo mà chuẩn hoá đường dẫn KHÔNG đối chiếu được với snapshot.
 * Bằng chứng vẫn hợp lệ và vẫn phải hiện ra (không bao giờ giấu), nhưng nó không bấm được, và
 * người dùng phải thấy rõ vì sao. Giấu đi hoặc render như một link chết là biến thất bại chuẩn
 * hoá đường dẫn trở lại thành thứ hỏng trong im lặng — đúng thứ mà `unresolved_location_count`
 * sinh ra để chống.
 */

import { Link } from 'react-router-dom';

import { toFilePathUrl } from '@/features/snapshots/lib/filePathUrl';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';

import type { Warning } from '@/features/warnings/api/warningApi';

interface WarningTableProps {
  warnings: Warning[];
  snapshotId: string;
}

export function WarningTable({ warnings, snapshotId }: WarningTableProps) {
  return (
    <table className="w-full border-collapse text-sm">
      <thead>
        <tr className="border-border text-ink-muted border-b text-left">
          <th className="py-2 pr-3 font-medium">Mức</th>
          <th className="py-2 pr-3 font-medium">Công cụ</th>
          <th className="py-2 pr-3 font-medium">Luật</th>
          <th className="py-2 pr-3 font-medium">Vị trí</th>
          <th className="py-2 font-medium">Tóm tắt</th>
        </tr>
      </thead>
      <tbody>
        {warnings.map((warning) => (
          <tr key={warning.evidenceId} className="border-border border-b align-top">
            <td className="py-2 pr-3">
              <SeverityBadge severity={warning.severityNormalized} />
            </td>
            <td className="text-ink-muted py-2 pr-3">{warning.tool}</td>
            <td className="py-2 pr-3">
              <span className="font-mono text-xs">{warning.ruleIdNormalized}</span>
              <span className="text-ink-muted ml-2 text-xs">{warning.shortId}</span>
            </td>
            <td className="py-2 pr-3">
              <WarningLocation warning={warning} snapshotId={snapshotId} />
            </td>
            <td className="py-2">{warning.summary}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function WarningLocation({ warning, snapshotId }: { warning: Warning; snapshotId: string }) {
  if (warning.filePath === undefined || warning.filePath === null) {
    return (
      <span
        className="text-ink-muted text-xs italic"
        title="Đường dẫn analyzer khai không khớp file nào trong snapshot"
      >
        chưa đối chiếu được với snapshot
      </span>
    );
  }

  // `toFilePathUrl` mã hoá TỪNG đoạn: `encodeURIComponent` cả chuỗi biến `/` thành `%2F` và phá
  // splat route `files/*`. Hash `#L42` để `useLineTarget` cuộn tới đúng dòng.
  const hash = warning.startLine === undefined ? '' : `#L${String(warning.startLine)}`;
  return (
    <Link
      to={`/snapshots/${snapshotId}/files/${toFilePathUrl(warning.filePath)}${hash}`}
      className="text-brand font-mono text-xs break-all"
    >
      {warning.filePath}
      {warning.startLine === undefined ? '' : `:${String(warning.startLine)}`}
    </Link>
  );
}
