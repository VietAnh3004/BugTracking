/**
 * Một thẻ bằng chứng liên quan: nó là gì, liên kết bằng cách nào, và vì sao.
 *
 * Presentational thuần — không gọi API, không biết route nào ngoài hai đường dẫn nhận qua props.
 */

import { Link } from 'react-router-dom';

import type { RelatedEvidence } from '@/features/evidence/api/evidenceApi';
import { FailureKindBadge } from '@/features/evidence/components/FailureKindBadge';
import { LinkStrengthBadge } from '@/features/evidence/components/LinkStrengthBadge';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';
import { IconFile } from '@/shared/ui/icons';
import { CARD } from '@/shared/ui/styles';

interface RelatedEvidenceCardProps {
  item: RelatedEvidence;
  snapshotId: string;
  runId: string;
  /**
   * Đường dẫn và dòng của bằng chứng ĐANG XEM (cái neo).
   *
   * Dùng cho bằng chứng cấp FILE, điển hình là độ phủ: nó có `filePath` nhưng không có
   * `startLine`, nên link của nó mở file ở đầu trong khi câu lý do nói "Dòng 66: COVERED".
   * Khi thẻ trỏ vào ĐÚNG file của cái neo thì dòng của neo chính là dòng đang bàn tới.
   *
   * Điều kiện "cùng file" là bắt buộc, không phải cho chặt chẽ: thẻ test trỏ tới
   * `UserServiceTest.java` mà gắn dòng 66 của `UserService.java` vào là nhảy tới một dòng
   * hoàn toàn không liên quan — sai theo kiểu trông vẫn hợp lý.
   */
  anchorFilePath?: string | null;
  anchorStartLine?: number | null;
}

export function RelatedEvidenceCard({
  item,
  snapshotId,
  runId,
  anchorFilePath,
  anchorStartLine,
}: RelatedEvidenceCardProps) {
  const cungFileVoiNeo = anchorFilePath != null && item.filePath === anchorFilePath;
  const dongDeNhay = item.startLine ?? (cungFileVoiNeo ? anchorStartLine : null);

  return (
    <li className={`${CARD} min-w-0 p-3`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Link
          to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${item.shortId}`}
          className="bg-brand-subtle text-brand hover:bg-brand-solid rounded-sm px-1.5 py-0.5 font-mono text-xs font-semibold transition-colors hover:text-white"
        >
          {item.shortId}
        </Link>
        <span className="text-ink-subtle text-2xs font-mono tracking-wide">{item.type}</span>
      </div>

      {/*
        `summary` được viết cho LLM: một câu TỰ CHỨA, đọc được khi đứng một mình giữa prompt, nên
        nó nhồi cả kiểu hỏng, tên test, lớp ngoại lệ và thông điệp vào một dòng. Khối `dl` dưới đây
        được viết cho NGƯỜI: trường có nhãn, quét mắt nhanh. Hiện cả hai là nói mọi thứ hai lần và
        làm thẻ dài gấp đôi mà không thêm thông tin nào.
        Bằng chứng có `testCase` thì khối có cấu trúc phủ hết nội dung câu tóm tắt, nên bỏ câu đó
        đi TRÊN MÀN HÌNH. Dữ liệu trong DB không đổi — Agent vẫn đọc `summary` như cũ.
      */}
      {item.testCase != null ? null : (
        <p className="text-ink mt-2 mb-2.5 text-sm [overflow-wrap:anywhere]">{item.summary}</p>
      )}

      {/*
        `== null` chứ không `=== undefined`: xem ghi chú ở `application.yml` của core-backend.
        Backend nay bỏ hẳn trường null, nhưng chỗ này DEREFERENCE giá trị nên vẫn kiểm cả hai —
        một `"testCase": null` lọt qua đây là trắng nguyên trang (`null.className`), chứ không
        phải thiếu mất một dòng.
      */}
      {item.testCase == null ? null : (
        <dl className="bg-surface-sunken text-ink-muted mt-2 mb-3 flex flex-col gap-1 rounded-md px-2.5 py-2 text-xs">
          <div className="flex gap-1">
            <dt className="font-semibold">Test:</dt>
            <dd className="font-mono break-all">
              {item.testCase.className}#{item.testCase.methodName}
            </dd>
          </div>
          <div className="flex flex-wrap items-center gap-1">
            <dt className="font-semibold">Trạng thái:</dt>
            <dd className="flex items-center gap-1">
              {item.testCase.status}
              {/*
                `status` chuẩn hoá chỉ có PASSED/FAILED/SKIPPED, nên nó GỘP hai kiểu hỏng khác hẳn
                nhau. Chip này trả lại phân biệt đó — xem `FailureKindBadge`.
              */}
              {item.testCase.failureKind == null ? null : (
                <FailureKindBadge kind={item.testCase.failureKind} />
              )}
            </dd>
          </div>
          {/*
            Thời gian chạy CHỈ nằm trong câu tóm tắt của test xanh ("Test xanh: X.y (12 ms)"), nên
            bỏ câu đó đi mà không đưa nó vào đây là làm mất dữ liệu chứ không phải bớt lặp.
          */}
          {item.testCase.durationMs == null ? null : (
            <div className="flex gap-1">
              <dt className="font-semibold">Thời gian:</dt>
              <dd>{item.testCase.durationMs} ms</dd>
            </div>
          )}
          {item.testCase.failureType == null ? null : (
            <div className="flex gap-1">
              <dt className="font-semibold">Ngoại lệ:</dt>
              <dd className="font-mono break-all">{item.testCase.failureType}</dd>
            </div>
          )}
          {item.testCase.failureMessage == null ? null : (
            <div className="flex gap-1">
              <dt className="font-semibold">Lỗi:</dt>
              {/* Text node: React tự escape. Thông điệp lỗi đến từ mã người dùng (ADR 0006). */}
              <dd className="font-mono break-all">{item.testCase.failureMessage}</dd>
            </div>
          )}
          {/*
            Stack trace là một bằng chứng RIÊNG có Evidence ID riêng. Không dựng link tới nó thì
            câu lý do "stack trace đi qua dòng 66" thành một lời khẳng định không kiểm chứng được
            — đúng thứ mà cả màn hình này sinh ra để chống.
          */}
          {item.testCase.stackTraceShortId == null ? null : (
            <div className="flex gap-1">
              <dt className="font-semibold">Stack trace:</dt>
              <dd>
                <Link
                  to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${item.testCase.stackTraceShortId}`}
                  className="text-brand font-mono"
                >
                  {item.testCase.stackTraceShortId}
                </Link>
              </dd>
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
        <p className="border-border-strong text-ink-muted mt-2.5 mb-0 border-l-2 pl-2.5 text-xs leading-relaxed [overflow-wrap:anywhere]">
          {item.rationale}
        </p>
      )}

      {item.filePath == null ? null : (
        <Link
          to={`/snapshots/${snapshotId}/files/${toFilePathUrl(item.filePath)}${toLineHash(dongDeNhay, item.endLine)}`}
          className="text-brand mt-2.5 flex items-center gap-1.5 font-mono text-xs font-medium break-all hover:underline"
        >
          <IconFile className="size-3.5" />
          {item.filePath}
          {dongDeNhay == null ? '' : `:${String(dongDeNhay)}`}
        </Link>
      )}
    </li>
  );
}
