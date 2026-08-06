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

import { WarningFilters } from '@/features/warnings/components/WarningFilters';
import { WarningTable } from '@/features/warnings/components/WarningTable';
import { useAnalysisRun, useWarnings } from '@/features/warnings/hooks/useWarnings';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { StatusBadge } from '@/shared/ui/StatusBadge';

import type { WarningFilters as Filters } from '@/features/warnings/api/warningApi';

export function WarningsPage() {
  const params = useParams<{ snapshotId: string; runId: string }>();
  const snapshotId = params.snapshotId ?? '';
  const runId = params.runId ?? '';

  const [filters, setFilters] = useState<Filters>({});
  const run = useAnalysisRun(runId);
  const warnings = useWarnings(runId, filters);

  if (run.isPending) {
    return <FullPageSpinner label="Đang tải kết quả phân tích…" />;
  }
  if (run.isError) {
    return <RunLoadError error={run.error} />;
  }

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to={`/snapshots/${snapshotId}`} className="text-brand text-sm">
            ← Về cây mã nguồn
          </Link>
          <h1 className="mt-1 mb-0 text-xl font-semibold">Cảnh báo phân tích tĩnh</h1>
        </div>
        <RunSummary
          findingCount={run.data.findingCount}
          locationCount={run.data.locationCount}
          unresolvedLocationCount={run.data.unresolvedLocationCount}
        />
      </header>

      {/* Run hỏng thì nói thẳng lý do — đây là chỗ duy nhất người dùng đọc được errorMessage. */}
      {run.data.status === 'FAILED' ? (
        <p className="m-0 text-sm text-red-700">
          Lần phân tích này đã hỏng: {run.data.errorMessage ?? 'không rõ lý do'}
        </p>
      ) : null}
      {run.data.status === 'SUCCEEDED_CLEAN' ? (
        <p className="text-ink-muted m-0 text-sm">{run.data.cleanAckNote}</p>
      ) : null}

      <WarningFilters value={filters} onChange={setFilters} />

      {warnings.isPending ? <StatusBadge tone="neutral" label="Đang tải cảnh báo…" /> : null}
      {warnings.isError ? <WarningsLoadError error={warnings.error} /> : null}
      {warnings.isSuccess && warnings.data.items.length === 0 ? (
        <p className="text-ink-muted m-0 text-sm">Không có cảnh báo nào khớp bộ lọc hiện tại.</p>
      ) : null}
      {warnings.isSuccess && warnings.data.items.length > 0 ? (
        <>
          <WarningTable warnings={warnings.data.items} snapshotId={snapshotId} runId={runId} />
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
}: {
  findingCount: number;
  locationCount: number;
  unresolvedLocationCount: number;
}) {
  const resolved = locationCount - unresolvedLocationCount;
  return (
    <div className="text-ink-muted text-right text-sm">
      <p className="m-0">{findingCount} cảnh báo tĩnh</p>
      <p className={`m-0 ${unresolvedLocationCount > 0 ? 'text-sev-medium font-medium' : ''}`}>
        {resolved}/{locationCount} vị trí đối chiếu được với mã nguồn
      </p>
    </div>
  );
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
