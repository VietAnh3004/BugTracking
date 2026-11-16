/**
 * Màn hình danh sách cảnh báo của một lần phân tích.
 *
 * `snapshotId` nằm trên URL bên cạnh `runId` dù backend không cần nó: nhờ vậy dựng được link tới
 * source viewer ngay mà không phải gọi thêm một request chỉ để hỏi "run này thuộc snapshot nào",
 * và deep-link vẫn mở được đầy đủ sau F5.
 *
 * Bốn trạng thái phải render đủ: đang tải / lỗi / rỗng / có dữ liệu. Nhánh lỗi tách riêng 409
 * ("run chưa xong") khỏi lỗi chung — backend cố tình trả 409 thay vì danh sách rỗng đúng để màn
 * hình này nói được sự khác nhau đó.
 */

import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useSnapshotRole } from '@/features/projects/hooks/useProjectMembers';
import { useReviews } from '@/features/review/hooks/useReview';
import { EmbeddingNotice } from '@/features/warnings/components/EmbeddingNotice';
import { RebuildLinksButton } from '@/features/warnings/components/RebuildLinksButton';
import { WarningFilters } from '@/features/warnings/components/WarningFilters';
import { WarningTable } from '@/features/warnings/components/WarningTable';
import { useAnalysisRun, useWarnings } from '@/features/warnings/hooks/useWarnings';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { IconChart, IconChat, IconCheck } from '@/shared/ui/icons';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { buttonClass, CARD } from '@/shared/ui/styles';

import type { WarningFilters as Filters } from '@/features/warnings/api/warningApi';

export function WarningsPage() {
  const params = useParams<{ snapshotId: string; runId: string }>();
  const snapshotId = params.snapshotId ?? '';
  const runId = params.runId ?? '';

  const [filters, setFilters] = useState<Filters>({});
  const run = useAnalysisRun(runId);
  const warnings = useWarnings(runId, filters);
  // Một request cho cả bảng thay vì hỏi từng dòng: bảng có tới 34 dòng, và 34 request chỉ để tô
  // trạng thái là cách chắc chắn làm màn hình giật.
  const reviews = useReviews(runId);
  const reviewByShortId = new Map((reviews.data ?? []).map((r) => [r.shortId, r.decision]));
  const isOwner = useSnapshotRole(snapshotId) === 'OWNER';

  if (run.isPending) {
    return <FullPageSpinner label="Đang tải kết quả phân tích…" />;
  }
  if (run.isError) {
    return <RunLoadError error={run.error} />;
  }

  return (
    <section className="flex flex-col gap-5">
      <PageHeader
        back={{ to: `/snapshots/${snapshotId}`, label: 'Về cây mã nguồn' }}
        title="Cảnh báo phân tích tĩnh"
        description="Mở một cảnh báo để xem ngữ cảnh mã, test liên quan và độ phủ trước khi đánh giá."
        actions={
          <>
            <Link
              to={`/snapshots/${snapshotId}/runs/${runId}/chat`}
              className={buttonClass('secondary')}
            >
              <IconChat className="size-4" />
              Hỏi Agent
            </Link>
            <Link
              to={`/snapshots/${snapshotId}/runs/${runId}/ground-truth`}
              className={buttonClass('secondary')}
            >
              <IconCheck className="size-4" />
              Gán nhãn
            </Link>
            {/*
             * Lối vào dashboard đặt ngay đây vì đó là chỗ người dùng đang đứng khi muốn biết
             * "mình đã review được bao nhiêu rồi" — không ai đi tìm một mục menu riêng cho việc đó.
             */}
            {/* Dashboard và báo cáo chỉ cho chủ dự án: file xuất chứa nhãn ground truth của chủ,
                đưa cho thành viên là phá tính "mù" của người gán thứ hai. */}
            {isOwner ? (
              <Link
                to={`/snapshots/${snapshotId}/runs/${runId}/dashboard`}
                className={buttonClass('primary')}
              >
                <IconChart className="size-4" />
                Xem chỉ số &amp; tải báo cáo →
              </Link>
            ) : null}
          </>
        }
      />

      <RunSummary
        findingCount={run.data.findingCount}
        locationCount={run.data.locationCount}
        unresolvedLocationCount={run.data.unresolvedLocationCount}
        linkCount={run.data.linkCount}
        unlinkedWarningCount={run.data.unlinkedWarningCount}
      />

      {/* Chỉ run đã xong mới có liên kết để dựng lại — backend trả 409 cho RUNNING/FAILED. */}
      {isOwner && (run.data.status === 'SUCCEEDED' || run.data.status === 'SUCCEEDED_CLEAN') ? (
        <div className="-mt-2 flex justify-end">
          <RebuildLinksButton runId={runId} />
        </div>
      ) : null}

      <EmbeddingNotice run={run.data} isOwner={isOwner} />

      {/* Run hỏng thì nói thẳng lý do — đây là chỗ duy nhất người dùng đọc được errorMessage. */}
      {run.data.status === 'FAILED' ? (
        <p className="m-0 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          Lần phân tích này đã hỏng: {run.data.errorMessage ?? 'không rõ lý do'}
        </p>
      ) : null}
      {run.data.status === 'SUCCEEDED_CLEAN' ? (
        <p className="text-ink-muted m-0 text-sm">{run.data.cleanAckNote}</p>
      ) : null}

      <div className={`${CARD} px-4 py-3`}>
        <WarningFilters value={filters} onChange={setFilters} />
      </div>

      {warnings.isPending ? <StatusBadge tone="neutral" label="Đang tải cảnh báo…" /> : null}
      {warnings.isError ? <WarningsLoadError error={warnings.error} /> : null}
      {warnings.isSuccess && warnings.data.items.length === 0 ? (
        <p className={`${CARD} text-ink-muted m-0 px-4 py-10 text-center text-sm`}>
          Không có cảnh báo nào khớp bộ lọc hiện tại.
        </p>
      ) : null}
      {warnings.isSuccess && warnings.data.items.length > 0 ? (
        <>
          <WarningTable
            warnings={warnings.data.items}
            snapshotId={snapshotId}
            runId={runId}
            reviewByShortId={reviewByShortId}
          />
          <p className="text-ink-muted m-0 text-sm">
            Hiển thị {warnings.data.items.length}/{warnings.data.total} cảnh báo.
          </p>
        </>
      ) : null}
    </section>
  );
}

/**
 * Tỉ lệ vị trí không đối chiếu được hiện NGAY trên đầu màn hình, không giấu trong một tab phụ:
 * ADR 0004 gọi chuẩn hoá đường dẫn là rủi ro lớn nhất còn lại của mô hình bằng chứng, và cách duy
 * nhất để nó không hỏng trong im lặng là để người vận hành nhìn thấy con số.
 */
function RunSummary({
  findingCount,
  locationCount,
  unresolvedLocationCount,
  linkCount,
  unlinkedWarningCount,
}: {
  findingCount: number;
  locationCount: number;
  unresolvedLocationCount: number;
  linkCount: number;
  unlinkedWarningCount: number;
}) {
  const resolved = locationCount - unresolvedLocationCount;
  const linked = findingCount - unlinkedWarningCount;
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <StatTile value={String(findingCount)} caption="cảnh báo tĩnh" />
      <StatTile
        value={percent(resolved, locationCount)}
        caption={`${String(resolved)}/${String(locationCount)} vị trí đối chiếu được với mã nguồn`}
        warn={unresolvedLocationCount > 0}
      />
      {/*
       * Bộ đếm liên kết cùng một lý do tồn tại với ô bên cạnh: một liên kết KHÔNG sinh ra được
       * trông y hệt "dự án này không có bằng chứng liên quan". Không hiện ra thì muốn biết
       * 30/34 cảnh báo trơ trọi phải mở DB.
       */}
      <StatTile
        value={percent(linked, findingCount)}
        caption={`${String(linked)}/${String(findingCount)} cảnh báo có bằng chứng liên quan (${String(linkCount)} liên kết)`}
        warn={unlinkedWarningCount > 0}
      />
    </div>
  );
}

/** Ô số liệu: con số lớn để quét mắt, câu đầy đủ bên dưới để đọc chính xác. */
function StatTile({
  value,
  caption,
  warn = false,
}: {
  value: string;
  caption: string;
  warn?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border px-4 py-3 shadow-xs ${warn ? 'border-orange-200 bg-orange-50' : 'border-border bg-surface'}`}
    >
      <p
        className={`m-0 text-2xl font-semibold tracking-tight tabular-nums ${warn ? 'text-sev-medium' : 'text-ink'}`}
      >
        {value}
      </p>
      <p
        className={`mt-0.5 mb-0 text-sm ${warn ? 'text-sev-medium font-medium' : 'text-ink-muted'}`}
      >
        {caption}
      </p>
    </div>
  );
}

/** Mẫu số 0 thì hiện gạch — "0/0" không phải 0% cũng không phải 100%. */
function percent(part: number, whole: number): string {
  return whole === 0 ? '—' : `${String(Math.round((part / whole) * 100))}%`;
}

function RunLoadError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.status === 404) {
    return <StatusBadge tone="danger" label="Không tìm thấy lần phân tích này." />;
  }
  return <StatusBadge tone="danger" label="Không tải được lần phân tích." />;
}

function WarningsLoadError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.status === 409) {
    // 409 chứ không phải danh sách rỗng: "chưa chạy xong" khác hẳn "đúng là không có cảnh báo".
    return (
      <StatusBadge
        tone="warning"
        label="Lần phân tích này chưa hoàn tất nên chưa có cảnh báo để xem."
      />
    );
  }
  return <StatusBadge tone="danger" label="Không tải được danh sách cảnh báo." />;
}
