/**
 * Thẻ "Thực nghiệm 3 cấu hình" trên Dashboard (chỉ chủ dự án — cả trang Dashboard đã vậy).
 *
 * So cùng một model trên cùng các cảnh báo, chỉ khác lượng thông tin được thấy: LLM-Code (chỉ mã),
 * LLM-Static (mã + cảnh báo), Evidence-Agent (agent thật, tự gọi tool). Đây là phép so sánh trung
 * tâm của đồ án — giao thức ở docs/thuc-nghiem.md.
 *
 * Chạy rải nhiều ngày trên Gemini free: hết hạn mức thì máy chủ tự dừng và ghi lý do, hôm sau bấm
 * "Chạy tiếp" là đi tiếp từ ô dở. Tỉ lệ chưa tính được hiện "—", không phải 0% (xem formatMetric).
 */

import { useState } from 'react';

import {
  type ExperimentConfig,
  type ExperimentConfigMetrics,
  type ExperimentStatus,
  experimentCsvPath,
} from '@/features/review/api/experimentApi';
import {
  useExperiment,
  useStartExperiment,
  useStopExperiment,
} from '@/features/review/hooks/useExperiment';
import { formatCount, formatDecimal, formatRatio } from '@/features/review/lib/formatMetric';
import { ApiError } from '@/shared/lib/apiClient';
import { downloadFile } from '@/shared/lib/downloadClient';
import { IconClose, IconDownload, IconPlay } from '@/shared/ui/icons';
import { InfoTip } from '@/shared/ui/InfoTip';
import { ErrorState, LoadingState } from '@/shared/ui/states';
import { buttonClass, CARD } from '@/shared/ui/styles';

const CONFIG_LABELS: Record<ExperimentConfig, { title: string; detail: string }> = {
  LLM_CODE: { title: 'LLM-Code', detail: 'Chỉ đoạn mã, không biết có cảnh báo' },
  LLM_STATIC: { title: 'LLM-Static', detail: 'Đoạn mã + nội dung cảnh báo' },
  EVIDENCE_AGENT: { title: 'Evidence-Agent', detail: 'Agent thật, tự truy xuất bằng chứng' },
};

const CONFIG_ORDER: ExperimentConfig[] = ['LLM_CODE', 'LLM_STATIC', 'EVIDENCE_AGENT'];

/** Một hàng của bảng so sánh: nhãn, giải thích, và cách lấy giá trị đã định dạng của một cấu hình. */
const ROWS: { label: string; hint: string; value: (m: ExperimentConfigMetrics) => string }[] = [
  {
    label: 'Độ chính xác',
    hint: 'Tỉ lệ ô có verdict khớp nhãn ground truth (MANUAL), gộp mọi lần lặp. "Thiếu bằng chứng" tính là sai.',
    value: (m) => formatRatio(m.accuracy),
  },
  {
    label: 'Theo từng lần lặp',
    hint: 'Độ chính xác của lần 1 · lần 2 · lần 3.',
    value: (m) =>
      (m.accuracyByRepeat as (number | null)[]).map((value) => formatRatio(value)).join(' · '),
  },
  {
    label: 'Độ lệch giữa các lần',
    hint: 'Độ lệch chuẩn của độ chính xác giữa các lần lặp (tính khi có ít nhất 2 lần). Càng nhỏ càng ổn định.',
    value: (m) => formatRatio(m.accuracyStdDev),
  },
  {
    label: 'Chính xác khi có kết luận',
    hint: 'Độ chính xác chỉ trên các ô không trả "Thiếu bằng chứng".',
    value: (m) => formatRatio(m.decidedAccuracy),
  },
  {
    label: 'Tỉ lệ "Thiếu bằng chứng"',
    hint: 'Tỉ lệ ô trả INSUFFICIENT_EVIDENCE, kể cả do bị hạ cấp vì trích dẫn hỏng.',
    value: (m) => formatRatio(m.insufficientRate),
  },
  {
    label: 'Unsupported Claim Rate',
    hint: 'Tỉ lệ nhận định không có trích dẫn hợp lệ nào. Càng thấp càng tốt.',
    value: (m) => formatRatio(m.unsupportedClaimRate),
  },
  {
    label: 'Trích dẫn hợp lệ',
    hint: 'Tỉ lệ Evidence ID tra được về bằng chứng thật của lần phân tích này.',
    value: (m) => formatRatio(m.citationResolvedRate),
  },
  {
    label: 'Độ ổn định',
    hint: 'Tỉ lệ cảnh báo cho cùng verdict ở mọi lần lặp. Chỉ tính cảnh báo đã chạy đủ số lần lặp.',
    value: (m) => formatRatio(m.stability),
  },
  {
    label: 'Lượt gọi model / ô',
    hint: 'Trung bình số lần gọi model để có một câu trả lời.',
    value: (m) => formatDecimal(m.avgLlmCalls),
  },
  {
    label: 'Tool call / ô',
    hint: 'Trung bình số lần model tự gọi tool. Hai cấu hình đối chứng luôn bằng 0.',
    value: (m) => formatDecimal(m.avgToolCalls),
  },
  {
    label: 'Token vào / ra mỗi ô',
    hint: 'Trung bình token gửi lên và nhận về mỗi ô.',
    value: (m) =>
      `${formatDecimal(m.avgInputTokens, '', 0)} / ${formatDecimal(m.avgOutputTokens, '', 0)}`,
  },
  {
    label: 'Độ trễ / ô',
    hint: 'Trung bình thời gian một ô, kể cả thời gian chờ thử lại khi máy chủ model quá tải.',
    value: (m) => formatDecimal(m.avgLatencyMs == null ? null : m.avgLatencyMs / 1000, ' giây'),
  },
  {
    label: 'Ô xong / hỏng',
    hint: 'Ô hỏng được chạy lại khi bấm "Chạy tiếp".',
    value: (m) => `${formatCount(m.succeededCells)} / ${formatCount(m.failedCells)}`,
  },
];

export function ExperimentCard({ runId }: { runId: string }) {
  const { data, isPending, isError, error } = useExperiment(runId);

  if (isPending) {
    return <LoadingState label="Đang tải thực nghiệm…" shape="lines" rows={4} />;
  }
  if (isError) {
    return (
      <ErrorState
        title="Không tải được thực nghiệm 3 cấu hình."
        message={error instanceof ApiError ? error.message : undefined}
      />
    );
  }
  return <ExperimentView runId={runId} status={data} />;
}

function ExperimentView({ runId, status }: { runId: string; status: ExperimentStatus }) {
  const start = useStartExperiment(runId);
  const stop = useStopExperiment(runId);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const done = status.succeededCells;
  const recorded = status.succeededCells + status.failedCells;
  const finished = done >= status.totalCells && status.totalCells > 0;
  const percent = status.totalCells === 0 ? 0 : Math.round((done / status.totalCells) * 100);
  const actionError = start.error ?? stop.error;

  async function downloadCsv(): Promise<void> {
    setDownloadError(null);
    try {
      await downloadFile(experimentCsvPath(runId), `thuc-nghiem-${runId}.csv`);
    } catch (cause) {
      setDownloadError(cause instanceof Error ? cause.message : 'Không tải được CSV.');
    }
  }

  return (
    <section className={`${CARD} p-4`} data-testid="experiment-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5">
            <h2 className="text-ink m-0 text-sm font-semibold">Thực nghiệm 3 cấu hình</h2>
            <InfoTip label="Giải thích: Thực nghiệm 3 cấu hình">
              Cùng model, cùng câu hỏi, cùng các cảnh báo; chỉ khác lượng thông tin model được thấy.
              Mỗi ô là một cảnh báo × một cấu hình × một lần lặp. LLM-Code và LLM-Static tốn 1 lượt
              gọi model mỗi ô, Evidence-Agent tốn khoảng 5-9 lượt. Hết hạn mức thì máy chủ tự dừng;
              bấm Chạy tiếp để đi tiếp từ ô dở.
            </InfoTip>
          </div>
          <p className="text-ink-muted m-0 mt-1 text-xs">
            {formatCount(done)} / {formatCount(status.totalCells)} ô xong
            {status.failedCells > 0 ? ` · ${formatCount(status.failedCells)} ô hỏng` : ''}
            {` · ${String(status.repeats)} lần lặp mỗi ô`}
          </p>
        </div>

        <div className="flex gap-2">
          {status.running ? (
            <button
              type="button"
              className={buttonClass('dangerOutline')}
              disabled={stop.isPending}
              onClick={() => stop.mutate()}
            >
              <IconClose className="size-4" />
              {stop.isPending ? 'Đang dừng…' : 'Dừng'}
            </button>
          ) : (
            <button
              type="button"
              className={buttonClass('primary')}
              disabled={start.isPending || (finished && status.failedCells === 0)}
              onClick={() => start.mutate()}
            >
              <IconPlay className="size-4" />
              {finished && status.failedCells === 0
                ? 'Đã chạy xong'
                : recorded === 0
                  ? 'Chạy'
                  : 'Chạy tiếp'}
            </button>
          )}
          <button
            type="button"
            className={buttonClass('secondary')}
            disabled={recorded === 0}
            onClick={() => void downloadCsv()}
          >
            <IconDownload className="size-4" />
            Tải CSV
          </button>
        </div>
      </div>

      <div
        className="bg-surface-sunken mt-3 h-1.5 overflow-hidden rounded-full"
        role="progressbar"
        aria-label="Tiến độ thực nghiệm"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <div className="bg-brand-solid h-full" style={{ width: `${String(percent)}%` }} />
      </div>

      {status.running ? (
        <p className="text-info m-0 mt-2 text-xs" data-testid="experiment-current">
          Đang chạy{status.currentCell == null ? '…' : `: ${status.currentCell}`}
        </p>
      ) : status.lastStopReason == null ? null : (
        <p
          className={`m-0 mt-2 text-xs ${finished ? 'text-success' : 'text-warning'}`}
          data-testid="experiment-stop-reason"
        >
          {status.lastStopReason}
        </p>
      )}
      {[actionError?.message, downloadError].map((message) =>
        message == null ? null : (
          <p key={message} role="alert" className="text-danger m-0 mt-2 text-xs">
            {message}
          </p>
        ),
      )}

      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[560px] border-collapse text-sm">
          <thead>
            <tr className="border-border border-b">
              <th className="text-ink-muted py-2 pr-3 text-left text-xs font-medium">Chỉ số</th>
              {CONFIG_ORDER.map((config) => (
                <th key={config} className="py-2 pr-3 text-right align-bottom">
                  <span className="text-ink block text-xs font-semibold">
                    {CONFIG_LABELS[config].title}
                  </span>
                  <span className="text-ink-subtle block text-[11px] font-normal">
                    {CONFIG_LABELS[config].detail}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROWS.map((row) => (
              <tr key={row.label} className="border-border/60 border-b last:border-b-0">
                <th
                  scope="row"
                  className="text-ink-muted py-1.5 pr-3 text-left text-xs font-normal"
                >
                  <span className="inline-flex items-center gap-1">
                    {row.label}
                    <InfoTip label={`Giải thích: ${row.label}`}>{row.hint}</InfoTip>
                  </span>
                </th>
                {CONFIG_ORDER.map((config) => {
                  const metrics = status.configs.find((m) => m.config === config);
                  return (
                    <td key={config} className="text-ink py-1.5 pr-3 text-right tabular-nums">
                      {metrics === undefined ? '—' : row.value(metrics)}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
