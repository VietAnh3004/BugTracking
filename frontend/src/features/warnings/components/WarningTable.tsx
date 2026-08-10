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

import type { ReviewDecision } from '@/features/review/api/reviewApi';
import { DecisionBadge } from '@/features/review/components/DecisionBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';
import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';

import type { Warning } from '@/features/warnings/api/warningApi';

interface WarningTableProps {
  warnings: Warning[];
  snapshotId: string;
  /** Cần để dựng link sang Evidence Explorer; `WarningsPage` đã có sẵn từ tham số route. */
  runId: string;
  /**
   * Quyết định của người dùng, tra theo `shortId`.
   *
   * Truyền vào chứ không tự gọi API: component này thuần trình bày, và giữ nguyên tính chất đó
   * là điều kiện để test nó không cần dựng cả tầng mạng.
   */
  reviewByShortId?: Map<string, ReviewDecision>;
}

export function WarningTable({ warnings, snapshotId, runId, reviewByShortId }: WarningTableProps) {
  return (
    // `overflow-x-auto` + `min-w`: dưới ngưỡng đó thì bảng tự cuộn ngang, KHÔNG bóp cột cho vừa.
    <div className="overflow-x-auto">
      <table className="w-full min-w-[64rem] table-fixed border-collapse text-sm">
        {/*
         * `table-fixed` + colgroup là bắt buộc chứ không phải trang trí. Ở chế độ `table-auto` mặc
         * định, trình duyệt chia chỗ theo chiều rộng TỐI THIỂU của nội dung — mà cột Vị trí dùng
         * `break-all` nên chiều rộng tối thiểu của nó là ĐÚNG MỘT KÝ TỰ. Cột Tóm tắt (một câu dài,
         * ngắt theo từ nên tối thiểu lớn hơn hẳn) chiếm hết chỗ, còn đường dẫn rơi dọc từng chữ cái
         * và kéo hàng cao vống lên. Chốt cứng chiều rộng thì bố cục không còn phụ thuộc nội dung.
         */}
        <colgroup>
          <col className="w-24" />
          <col className="w-28" />
          <col className="w-64" />
          <col className="w-72" />
          <col />
          <col className="w-32" />
        </colgroup>
        <thead>
          <tr className="border-border text-ink-muted border-b text-left">
            <th className="py-2 pr-3 font-medium">Mức</th>
            <th className="py-2 pr-3 font-medium">Công cụ</th>
            <th className="py-2 pr-3 font-medium">Luật</th>
            <th className="py-2 pr-3 font-medium">Vị trí</th>
            <th className="py-2 pr-3 font-medium">Tóm tắt</th>
            <th className="py-2 font-medium">Đánh giá</th>
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
                <span className="font-mono text-xs break-all">{warning.ruleIdNormalized}</span>
                {/*
                 * `shortId` là lối vào Evidence Explorer, và nó vá luôn một lỗ hổng sẵn có: khi
                 * `filePath` NULL (không đối chiếu được với snapshot) thì cột Vị trí không render
                 * link, nên trước đây cảnh báo đó HOÀN TOÀN không bấm được. `shortId` thì luôn tồn
                 * tại.
                 *
                 * Cũng là chỗ dạy người dùng rằng Evidence ID bấm được — đúng affordance mà
                 * citation của chatbot sẽ dùng lại ở tuần 12-13.
                 */}
                <Link
                  to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${warning.shortId}`}
                  className="text-brand ml-2 font-mono text-xs"
                  title="Mở Evidence Explorer: ngữ cảnh mã, test liên quan và độ phủ"
                >
                  {warning.shortId}
                </Link>
              </td>
              <td className="py-2 pr-3">
                <WarningLocation warning={warning} snapshotId={snapshotId} />
              </td>
              <td className="py-2 pr-3">{warning.summary}</td>
              <td className="py-2">
                <DecisionBadge decision={reviewByShortId?.get(warning.shortId)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
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
  // splat route `files/*`. Hash để `useLineTarget` cuộn tới đúng dòng.
  //
  // Dựng hash qua `toLineHash` chứ không ghép chuỗi tay: từ tuần 10-11 còn ba chỗ khác cùng dựng
  // hash (thẻ ngữ cảnh mã, frame stack trace, citation của chatbot ở tuần 12-13), và mỗi chỗ tự
  // ghép là mỗi chỗ có thể sai một kiểu khác nhau.
  const hash = toLineHash(warning.startLine, warning.endLine);
  const suffix = warning.startLine === undefined ? '' : `:${String(warning.startLine)}`;

  // Tách thư mục khỏi tên file để dòng ngắt ở chỗ có nghĩa. Đường dẫn Java dài tới mức luôn phải
  // xuống dòng; nếu để nguyên một chuỗi, chỗ ngắt rơi vào giữa tên lớp — thứ người đọc quét mắt
  // tìm đầu tiên. Không cắt bớt ký tự nào: đường dẫn đầy đủ vẫn phải đọc được, kể cả khi nó xấu.
  const lastSlash = warning.filePath.lastIndexOf('/');
  const dir = lastSlash === -1 ? '' : warning.filePath.slice(0, lastSlash + 1);
  const fileName = warning.filePath.slice(lastSlash + 1);

  return (
    <Link
      to={`/snapshots/${snapshotId}/files/${toFilePathUrl(warning.filePath)}${hash}`}
      className="text-brand block font-mono text-xs"
    >
      {dir === '' ? null : <span className="text-ink-muted block break-all">{dir}</span>}
      <span className="block break-all">
        {fileName}
        {suffix}
      </span>
    </Link>
  );
}
