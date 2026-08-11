/**
 * Evidence Explorer — deliverable của tuần 10-11: mở 1 cảnh báo → thấy ngữ cảnh mã, test liên quan
 * và độ phủ.
 *
 * Container DUY NHẤT ở cấp trang: mọi thứ dưới nó là presentational. Kế hoạch gốc cảnh báo màn
 * hình này "chắc chắn sẽ phình — tách sớm, đừng đợi", nên nó được tách ngay từ đầu.
 *
 * Ba trạng thái async phải phân biệt được BẰNG MẮT, không được rơi chung một nhánh:
 *   - đang tải     → spinner có chữ
 *   - lỗi          → câu PHÂN LOẠI theo `problem.type`, mực đỏ
 *   - thật sự rỗng → `EmptyEvidenceNotice`, viền đứt nét, mực thường
 *
 * Gộp trạng thái 3 vào trạng thái 2 là phá đúng thứ mà backend đã cố tình trả 409 (thay vì `[]`)
 * để bảo toàn: "rỗng vì chưa build" khác "rỗng vì đúng là rỗng".
 */

import { Link, useParams } from 'react-router-dom';

import { EmptyEvidenceNotice } from '@/features/evidence/components/EmptyEvidenceNotice';
import { EvidenceHeader } from '@/features/evidence/components/EvidenceHeader';
import { ReviewPanel } from '@/features/review/components/ReviewPanel';
import { RelatedEvidenceGroup } from '@/features/evidence/components/RelatedEvidenceGroup';
import { useEvidenceDetail } from '@/features/evidence/hooks/useEvidenceDetail';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';

export function EvidenceExplorerPage() {
  const params = useParams();
  const snapshotId = params.snapshotId ?? '';
  const runId = params.runId ?? '';
  const shortId = params.shortId ?? '';

  const { data, isPending, isError, error } = useEvidenceDetail(runId, shortId);

  return (
    <article>
      <Link
        to={`/snapshots/${snapshotId}/runs/${runId}/warnings`}
        className="text-brand mb-4 inline-block text-sm"
      >
        ‹ Về danh sách cảnh báo
      </Link>

      {isPending ? <FullPageSpinner label="Đang tải bằng chứng…" /> : null}
      {isError ? <EvidenceError error={error} /> : null}

      {data === undefined ? null : (
        <>
          <EvidenceHeader evidence={data} runId={runId} />

          {/* Panel đánh giá ngay dưới phần đầu: người dùng đọc bằng chứng xong là quyết được
              luôn, không phải quay lại bảng cảnh báo. */}
          <div className="mb-6">
            <ReviewPanel runId={runId} shortId={shortId} />
          </div>

          {data.relatedTotal === 0 ? (
            <EmptyEvidenceNotice locationResolved={data.filePath != null} />
          ) : (
            <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
              <div>
                <RelatedEvidenceGroup
                  title="Ngữ cảnh mã nguồn"
                  items={data.codeContext}
                  emptyMessage="Không dựng được ngữ cảnh mã nguồn cho vị trí này."
                  snapshotId={snapshotId}
                  runId={runId}
                />
              </div>
              <div>
                <RelatedEvidenceGroup
                  title="Test liên quan"
                  items={data.tests}
                  emptyMessage="Không có test nào liên quan tới bằng chứng này."
                  snapshotId={snapshotId}
                  runId={runId}
                />
                <RelatedEvidenceGroup
                  title="Độ phủ"
                  items={data.coverage}
                  emptyMessage="Không có dữ liệu độ phủ cho vị trí này."
                  snapshotId={snapshotId}
                  runId={runId}
                />
                {data.other.length === 0 ? null : (
                  <RelatedEvidenceGroup
                    title="Liên kết khác"
                    items={data.other}
                    emptyMessage=""
                    snapshotId={snapshotId}
                    runId={runId}
                  />
                )}
              </div>
            </div>
          )}
        </>
      )}
    </article>
  );
}

/**
 * Phân loại lỗi theo `problem.type` chứ không gộp một câu chung.
 *
 * Backend cố tình phân biệt `evidence-not-found` với `evidence-wrong-run` — cùng hai giá trị
 * `ClaimCitationResolution` mà CitationValidator dùng ở tuần 12-13. Gộp chúng lại ở đây là vứt đi
 * đúng thông tin mà việc phân loại sinh ra để cung cấp.
 */
function EvidenceError({ error }: { error: unknown }) {
  const type = error instanceof ApiError ? error.problem.type : undefined;

  const message =
    type === 'https://datn.hust.vn/problems/run-not-queryable'
      ? 'Lần phân tích chưa hoàn tất hoặc đã hỏng, nên chưa có bằng chứng để xem.'
      : type === 'https://datn.hust.vn/problems/evidence-wrong-run'
        ? 'Evidence ID này có tồn tại, nhưng thuộc một lần phân tích khác.'
        : type === 'https://datn.hust.vn/problems/evidence-not-found'
          ? 'Không tìm thấy Evidence ID này.'
          : 'Không tải được bằng chứng.';

  return (
    <p className="text-sev-high border-sev-high rounded border p-3 text-sm" role="alert">
      {message}
    </p>
  );
}
