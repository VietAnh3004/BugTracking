/**
 * Phân tích tự động: backend chạy SpotBugs, Semgrep, test + JaCoCo trên chính snapshot này trong
 * Docker sandbox (ADR 0008). Nút nằm ở đầu trang, trạng thái là một khối riêng bên dưới — cả hai
 * đọc chung state từ `useSandboxRun` do trang dựng một lần.
 */

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { formatElapsed } from '@/features/warnings/lib/formatElapsed';
import { ApiError } from '@/shared/lib/apiClient';
import { IconPlay } from '@/shared/ui/icons';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { buttonClass, TEXT_LINK } from '@/shared/ui/styles';

import type { useSandboxRun } from '@/features/warnings/hooks/useWarnings';

type SandboxRunState = ReturnType<typeof useSandboxRun>;

export function SandboxRunButton({ sandbox }: { sandbox: SandboxRunState }) {
  return (
    <button
      type="button"
      onClick={sandbox.start}
      disabled={sandbox.running}
      className={buttonClass('primary')}
    >
      {sandbox.running ? (
        <span
          aria-hidden="true"
          className="inline-block size-3.5 animate-spin rounded-full border-2 border-white/40 border-t-white"
        />
      ) : (
        <IconPlay className="size-3.5" />
      )}
      {sandbox.running ? 'Đang phân tích…' : 'Phân tích tự động'}
    </button>
  );
}

export function SandboxRunStatus({
  sandbox,
  snapshotId,
}: {
  sandbox: SandboxRunState;
  snapshotId: string;
}) {
  const { run, running, startError, finishedHere } = sandbox;

  if (startError !== null) {
    return (
      <p
        className="m-0 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
        role="alert"
      >
        {describeStartError(startError)}
      </p>
    );
  }

  if (running) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="border-brand-ring bg-brand-subtle flex flex-col gap-1 rounded-xl border px-4 py-3"
      >
        <StatusBadge
          tone="neutral"
          label="Đang phân tích tự động — biên dịch, chạy test, SpotBugs, Semgrep"
        />
        <p className="text-ink-muted m-0 text-sm">
          {run === undefined ? null : (
            <>
              Đã chạy <Elapsed startedAt={run.startedAt} />.{' '}
            </>
          )}
          Thường mất vài phút. Có thể rời trang này, việc phân tích vẫn tiếp tục.
        </p>
      </div>
    );
  }

  if (!finishedHere || run === undefined) {
    return null;
  }

  if (run.status === 'FAILED') {
    return (
      <div
        role="alert"
        className="flex flex-col gap-1.5 rounded-xl border border-red-200 bg-red-50 px-4 py-3"
      >
        <StatusBadge tone="danger" label="Phân tích tự động thất bại" />
        <p className="m-0 text-sm text-red-700">{run.errorMessage ?? 'Backend không ghi lý do.'}</p>
      </div>
    );
  }

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3"
    >
      <StatusBadge
        tone="ok"
        label={
          run.status === 'SUCCEEDED_CLEAN'
            ? 'Phân tích xong: không có cảnh báo nào.'
            : `Phân tích xong: ${String(run.findingCount)} cảnh báo · ${String(run.codeContextCount)} ngữ cảnh mã nguồn · ${String(run.linkCount)} liên kết bằng chứng.`
        }
      />
      <Link
        to={`/snapshots/${snapshotId}/runs/${run.id}/warnings`}
        className={`${TEXT_LINK} text-sm`}
      >
        Xem danh sách cảnh báo →
      </Link>
    </div>
  );
}

/**
 * Đồng hồ "đã chạy" tự đếm mỗi giây. Không trông vào dữ liệu polling để render lại: TanStack
 * Query giữ nguyên tham chiếu khi response không đổi (structural sharing), nên đồng hồ sẽ đứng
 * yên suốt lúc run vẫn RUNNING.
 */
function Elapsed({ startedAt }: { startedAt: string }) {
  const [, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((tick) => tick + 1), 1000);
    return () => clearInterval(id);
  }, []);
  return <span className="font-mono">{formatElapsed(startedAt)}</span>;
}

function describeStartError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Không gọi được backend.';
  }
  if (error.problem.type?.endsWith('sandbox-unavailable') === true) {
    return 'Sandbox chưa bật hoặc Docker không phản hồi. Vẫn có thể dùng "Nạp kết quả phân tích".';
  }
  if (error.problem.type?.endsWith('unsupported-source-tree') === true) {
    // Lý do cụ thể (không có pom.xml, nhiều module…) nằm ở `detail` — đó mới là thứ cần sửa.
    return `Mã nguồn này chưa phân tích tự động được: ${error.problem.detail ?? error.message}`;
  }
  if (error.problem.type?.endsWith('snapshot-not-ready') === true) {
    return 'Snapshot chưa giải nén xong, thử lại sau ít phút.';
  }
  return error.problem.detail ?? error.message;
}
