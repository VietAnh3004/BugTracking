/**
 * Một cảnh báo trên trang gán nhãn: thông tin cảnh báo, gợi ý SEEDED (chỉ chủ dự án), và ô gán
 * nhãn kèm lý do.
 *
 * Lý do là BẮT BUỘC (backend từ chối chuỗi rỗng): kế hoạch §5 dùng nó để chống circularity — viết
 * được vì sao nghĩa là đã đọc mã, không phải chép kết luận của Agent hay của gợi ý.
 */

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { SeverityBadge } from '@/features/warnings/components/SeverityBadge';
import { displaySummary } from '@/features/warnings/lib/displaySummary';
import { toFilePathUrl, toLineHash } from '@/features/snapshots/lib/filePathUrl';
import { IconCheck } from '@/shared/ui/icons';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';

import type {
  GroundTruthLabel,
  GroundTruthRow,
  GroundTruthVerdict,
} from '@/features/groundtruth/api/groundTruthApi';

const VERDICT_LABELS: Record<GroundTruthVerdict, string> = {
  TRUE_POSITIVE: 'Lỗi thật',
  FALSE_POSITIVE: 'Báo nhầm',
  UNCERTAIN: 'Không chắc',
};

const VERDICT_ACTIVE: Record<GroundTruthVerdict, string> = {
  TRUE_POSITIVE: 'border-danger/40 bg-danger/10 text-danger ring-2 ring-danger/30',
  FALSE_POSITIVE: 'border-success/40 bg-success/10 text-success ring-2 ring-success/30',
  UNCERTAIN: 'border-warning/40 bg-warning/10 text-warning ring-2 ring-warning/30',
};

const VERDICTS: GroundTruthVerdict[] = ['TRUE_POSITIVE', 'FALSE_POSITIVE', 'UNCERTAIN'];

interface LabelRowProps {
  row: GroundTruthRow;
  snapshotId: string;
  runId: string;
  saving: boolean;
  onSave: (label: GroundTruthVerdict, rationale: string) => void;
  onDelete: () => void;
}

export function LabelRow({ row, snapshotId, runId, saving, onSave, onDelete }: LabelRowProps) {
  const [verdict, setVerdict] = useState<GroundTruthVerdict | null>(row.label?.label ?? null);
  const [rationale, setRationale] = useState(row.label?.rationale ?? '');

  // Đồng bộ lại khi bảng tải lại từ server (sau khi lưu, hoặc sau khi nạp file).
  useEffect(() => {
    setVerdict(row.label?.label ?? null);
    setRationale(row.label?.rationale ?? '');
  }, [row.label?.label, row.label?.rationale]);

  const dirty =
    verdict !== (row.label?.label ?? null) || rationale !== (row.label?.rationale ?? '');
  const canSave = verdict !== null && rationale.trim() !== '' && dirty && !saving;

  return (
    <li className={`${CARD} flex flex-col gap-3 p-4`}>
      <div className="flex flex-wrap items-center gap-2">
        <SeverityBadge severity={row.severity} />
        <span className="text-ink font-mono text-xs font-medium">{row.ruleId}</span>
        <span className="text-ink-subtle text-xs">{row.tool}</span>
        <Link
          to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${row.shortId}`}
          className="bg-brand-subtle text-brand text-2xs rounded-sm px-1.5 py-0.5 font-mono font-semibold"
        >
          {row.shortId}
        </Link>
        {row.filePath == null ? null : (
          <Link
            to={`/snapshots/${snapshotId}/files/${toFilePathUrl(row.filePath)}${toLineHash(row.startLine, undefined)}`}
            className="text-brand font-mono text-xs break-all hover:underline"
          >
            {row.filePath}
            {row.startLine == null ? '' : `:${String(row.startLine)}`}
          </Link>
        )}
        {row.label == null ? null : (
          <span className="text-success ml-auto inline-flex items-center gap-1 text-xs">
            <IconCheck className="size-3.5" />
            Đã gán
          </span>
        )}
      </div>

      <p className="text-ink m-0 text-sm" title={row.summary}>
        {displaySummary(row.summary)}
      </p>

      {row.seededHint == null ? null : <SeededHint hint={row.seededHint} />}

      <div className="flex flex-wrap gap-2" role="group" aria-label={`Nhãn cho ${row.shortId}`}>
        {VERDICTS.map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={verdict === value}
            onClick={() => setVerdict(value)}
            className={`cursor-pointer rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
              verdict === value
                ? VERDICT_ACTIVE[value]
                : 'border-border bg-surface text-ink hover:bg-surface-muted'
            }`}
          >
            {VERDICT_LABELS[value]}
          </button>
        ))}
      </div>

      <label className="sr-only" htmlFor={`rationale-${row.shortId}`}>
        Lý do cho {row.shortId}
      </label>
      <textarea
        id={`rationale-${row.shortId}`}
        value={rationale}
        onChange={(event) => setRationale(event.target.value)}
        rows={2}
        maxLength={2000}
        placeholder="Lý do (bắt buộc), viết SAU KHI đọc mã: vì sao đây là lỗi thật / báo nhầm?"
        className={`${INPUT} resize-none`}
      />

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={!canSave}
          onClick={() => {
            if (verdict !== null) {
              onSave(verdict, rationale.trim());
            }
          }}
          className={buttonClass('primary', 'sm')}
        >
          Lưu nhãn
        </button>
        {row.label == null ? null : (
          <>
            <button
              type="button"
              disabled={saving}
              onClick={onDelete}
              className={buttonClass('ghost', 'sm')}
            >
              Xoá nhãn
            </button>
            <span className="text-ink-subtle text-xs">
              {row.label.labeler} · {new Date(row.label.labeledAt).toLocaleString('vi-VN')}
            </span>
          </>
        )}
      </div>
    </li>
  );
}

/**
 * Nhãn gieo có chủ đích — CHỈ là gợi ý. Đặt trong khung viền đứt nét, chữ nhạt: nó nằm đó để đối
 * chiếu sau khi đọc mã, không phải đáp án để bấm theo.
 */
function SeededHint({ hint }: { hint: GroundTruthLabel }) {
  return (
    <p className="border-border-strong text-ink-muted m-0 rounded-lg border border-dashed px-3 py-2 text-xs">
      <span className="font-semibold">Gợi ý SEEDED:</span> {VERDICT_LABELS[hint.label]} —{' '}
      {hint.rationale}
    </p>
  );
}
