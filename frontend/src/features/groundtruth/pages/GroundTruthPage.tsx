/**
 * Trang gán nhãn ground truth của một lần phân tích.
 *
 * Hai chế độ, do BACKEND quyết theo vai trò (giao diện chỉ hiển thị theo):
 *   - Chủ dự án — người gán CHÍNH: thấy gợi ý SEEDED, xuất/nạp được file nhãn.
 *   - Thành viên — người gán THỨ HAI, MÙ: không thấy nhãn của chủ dự án lẫn gợi ý nào. Server
 *     không gửi những thứ đó xuống, nên "mù" không phụ thuộc vào việc giao diện có giấu hay không.
 *
 * Ground truth là đáp án dùng để chấm Agent và tính Cohen's kappa — xem dashboard.
 */

import { useRef } from 'react';
import { useParams } from 'react-router-dom';

import * as api from '@/features/groundtruth/api/groundTruthApi';
import { LabelRow } from '@/features/groundtruth/components/LabelRow';
import {
  useDeleteLabel,
  useGroundTruthSheet,
  useImportLabels,
  useSaveLabel,
} from '@/features/groundtruth/hooks/useGroundTruth';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { IconDownload, IconUpload } from '@/shared/ui/icons';
import { PageHeader } from '@/shared/ui/PageHeader';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { buttonClass } from '@/shared/ui/styles';

export function GroundTruthPage() {
  const params = useParams<{ snapshotId: string; runId: string }>();
  const snapshotId = params.snapshotId ?? '';
  const runId = params.runId ?? '';

  const sheet = useGroundTruthSheet(runId);
  const save = useSaveLabel(runId);
  const remove = useDeleteLabel(runId);
  const importLabels = useImportLabels(runId);
  const fileInput = useRef<HTMLInputElement>(null);

  if (sheet.isPending) {
    return <FullPageSpinner label="Đang tải danh sách cảnh báo…" />;
  }
  if (sheet.isError) {
    return <StatusBadge tone="danger" label="Không tải được bảng gán nhãn." />;
  }

  const { rows, role } = sheet.data;
  const labelled = rows.filter((row) => row.label != null).length;
  const isOwner = role === 'OWNER';

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        back={{
          to: `/snapshots/${snapshotId}/runs/${runId}/warnings`,
          label: 'Về danh sách cảnh báo',
        }}
        title="Gán nhãn ground truth"
        description={
          isOwner
            ? 'Bạn là người gán CHÍNH. Đọc mã rồi quyết: lỗi thật, báo nhầm, hay không chắc. Gợi ý SEEDED (nếu có) chỉ để đối chiếu, không phải đáp án.'
            : 'Bạn là người gán THỨ HAI và gán MÙ: bạn không thấy nhãn của chủ dự án hay gợi ý nào. Hãy tự đọc mã và quyết định độc lập — hai nhãn được so với nhau để tính Cohen’s kappa.'
        }
        meta={
          <StatusBadge
            tone={labelled === rows.length && rows.length > 0 ? 'ok' : 'neutral'}
            label={`Đã gán ${String(labelled)}/${String(rows.length)} cảnh báo`}
          />
        }
        actions={
          isOwner ? (
            <>
              <button
                type="button"
                onClick={() => void api.exportLabels(runId)}
                className={buttonClass('secondary')}
              >
                <IconDownload className="size-4" />
                Xuất file nhãn
              </button>
              <label htmlFor="import-labels" className={buttonClass('secondary')}>
                <IconUpload className="size-4" />
                Nạp file nhãn
              </label>
              <input
                id="import-labels"
                ref={fileInput}
                type="file"
                accept=".yaml,.yml,application/yaml,text/yaml"
                className="sr-only"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file !== undefined) {
                    importLabels.mutate(file);
                  }
                  event.target.value = '';
                }}
              />
            </>
          ) : undefined
        }
      />

      {importLabels.isSuccess ? (
        <div
          role="status"
          className="rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900"
        >
          Đã nạp {importLabels.data.labelsWritten} nhãn từ {importLabels.data.entriesMatched} mục
          khớp.
          {importLabels.data.unmatched.length === 0 ? null : (
            <>
              {' '}
              {importLabels.data.unmatched.length} mục KHÔNG khớp cảnh báo nào (đường dẫn hoặc dòng
              lệch?):
              <ul className="mt-1 mb-0 list-inside list-disc font-mono text-xs">
                {importLabels.data.unmatched.map((entry) => (
                  <li key={entry}>{entry}</li>
                ))}
              </ul>
            </>
          )}
        </div>
      ) : null}
      {importLabels.isError ? (
        <p
          role="alert"
          className="m-0 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {importLabels.error instanceof ApiError
            ? (importLabels.error.problem.detail ?? importLabels.error.message)
            : 'Không nạp được file.'}
        </p>
      ) : null}
      {save.isError ? (
        <p role="alert" className="m-0 text-sm text-red-700">
          Không lưu được nhãn: {save.error.message}
        </p>
      ) : null}

      <ul className="m-0 grid list-none grid-cols-1 gap-3 p-0">
        {rows.map((row) => (
          <LabelRow
            key={row.shortId}
            row={row}
            snapshotId={snapshotId}
            runId={runId}
            saving={save.isPending || remove.isPending}
            onSave={(label, rationale) => save.mutate({ shortId: row.shortId, label, rationale })}
            onDelete={() => remove.mutate(row.shortId)}
          />
        ))}
      </ul>
    </div>
  );
}
