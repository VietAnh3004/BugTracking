/**
 * Nạp artifact analyzer (SpotBugs, Semgrep, Surefire, JaCoCo) cho một snapshot — đường UI cho
 * `POST /snapshots/{id}/analysis-runs`, trước đây chỉ gọi được bằng `curl`.
 *
 * File được GOM DẦN rồi mới gửi, không gửi ngay khi chọn như `UploadZone`: một lần phân tích cần
 * cùng lúc nhiều file nằm ở nhiều thư mục (`TEST-*.xml` thường ở `surefire-reports/`), mà hộp
 * thoại chọn file của trình duyệt chỉ chọn được trong một thư mục mỗi lần.
 *
 * Có cả kéo-thả lẫn `<input type="file">` trong `<label>` — cùng lý do tiếp cận với `UploadZone`.
 */

import { useState } from 'react';
import { Link } from 'react-router-dom';

import { useIngestArtifacts } from '@/features/warnings/hooks/useWarnings';
import { ApiError } from '@/shared/lib/apiClient';
import { StatusBadge } from '@/shared/ui/StatusBadge';

import type { AnalysisRun } from '@/features/warnings/api/warningApi';
import type { DragEvent } from 'react';

interface ArtifactUploadPanelProps {
  snapshotId: string;
  onClose: () => void;
}

export function ArtifactUploadPanel({ snapshotId, onClose }: ArtifactUploadPanelProps) {
  const [files, setFiles] = useState<File[]>([]);
  const [dragging, setDragging] = useState(false);
  const ingest = useIngestArtifacts(snapshotId);
  const inputId = `artifact-files-${snapshotId}`;

  function addFiles(list: FileList | null) {
    if (list === null || list.length === 0) {
      return;
    }
    // Kết quả lần nạp trước không còn khớp với danh sách mới.
    ingest.reset();
    setFiles((current) => {
      const seen = new Set(current.map(fileKey));
      const added = Array.from(list).filter((file) => !seen.has(fileKey(file)));
      return [...current, ...added];
    });
  }

  function removeFile(target: File) {
    ingest.reset();
    setFiles((current) => current.filter((file) => fileKey(file) !== fileKey(target)));
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    addFiles(event.dataTransfer.files);
  }

  function submit() {
    ingest.mutate(files, {
      onSuccess: (run) => {
        // Run FAILED: giữ nguyên danh sách để người dùng bỏ/thêm file rồi thử lại.
        if (run.status !== 'FAILED') {
          setFiles([]);
        }
      },
    });
  }

  return (
    <section
      aria-label="Nạp kết quả phân tích"
      className="border-border flex flex-col gap-3 rounded border p-4"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="m-0 text-base font-semibold">Nạp kết quả phân tích</h2>
        <button
          type="button"
          onClick={onClose}
          disabled={ingest.isPending}
          className="text-ink-muted cursor-pointer text-sm hover:underline disabled:cursor-not-allowed"
        >
          Đóng
        </button>
      </div>

      <p className="text-ink-muted m-0 text-sm">
        Nhận <strong>SpotBugs</strong> (<code>spotbugs.xml</code>), <strong>Semgrep</strong> (
        <code>.sarif</code>), <strong>JaCoCo</strong> (<code>jacoco.xml</code>) và{' '}
        <strong>Surefire</strong> (các file <code>TEST-*.xml</code>, hoặc zip của thư mục{' '}
        <code>surefire-reports</code>). Hệ thống nhận dạng theo nội dung file, không theo tên. Có
        thể chọn nhiều lần để gom file từ nhiều thư mục.
      </p>

      <div
        onDragOver={(event) => {
          // Không chặn mặc định thì trình duyệt MỞ file trong tab thay vì báo cho ta.
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={`rounded border-2 border-dashed p-4 text-center ${
          dragging ? 'border-brand bg-brand-subtle' : 'border-border'
        }`}
      >
        <p className="text-ink-muted mt-0 mb-2 text-sm">Kéo thả file vào đây, hoặc</p>
        <label
          htmlFor={inputId}
          className="border-brand text-brand hover:bg-brand-subtle inline-block cursor-pointer rounded border px-4 py-1.5 font-semibold"
        >
          Chọn file
        </label>
        <input
          id={inputId}
          type="file"
          multiple
          accept=".xml,.sarif,.json,.zip"
          className="sr-only"
          onChange={(event) => {
            addFiles(event.target.files);
            // Reset để chọn LẠI cùng file (sau khi đã bỏ nó khỏi danh sách) vẫn kích hoạt onChange.
            event.target.value = '';
          }}
        />
      </div>

      {files.length > 0 ? (
        <ul aria-label="File đã chọn" className="m-0 flex list-none flex-col gap-1 p-0 text-sm">
          {files.map((file) => (
            <li
              key={fileKey(file)}
              className="bg-surface-muted flex items-center justify-between gap-3 rounded px-3 py-1"
            >
              <span className="min-w-0 truncate font-mono">{file.name}</span>
              <span className="text-ink-muted flex shrink-0 items-center gap-3">
                {formatSize(file.size)}
                <button
                  type="button"
                  onClick={() => removeFile(file)}
                  disabled={ingest.isPending}
                  aria-label={`Bỏ ${file.name}`}
                  className="cursor-pointer text-red-700 hover:underline disabled:cursor-not-allowed"
                >
                  Bỏ
                </button>
              </span>
            </li>
          ))}
        </ul>
      ) : null}

      <button
        type="button"
        onClick={submit}
        disabled={files.length === 0 || ingest.isPending}
        className="bg-brand hover:bg-brand-hover cursor-pointer self-start rounded px-4 py-2 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
      >
        {ingest.isPending ? 'Đang nạp…' : `Nạp ${String(files.length)} file`}
      </button>

      {ingest.isPending ? (
        <div role="status" aria-live="polite">
          <div className="bg-border h-2 w-full overflow-hidden rounded">
            <div
              className="bg-brand h-full transition-[width]"
              style={{ width: `${String(ingest.progress)}%` }}
            />
          </div>
          <p className="text-ink-muted mt-1 mb-0 text-sm">
            {ingest.progress < 100
              ? `Đang tải lên… ${String(ingest.progress)}%`
              : 'Đã tải lên, đang phân tích và liên kết bằng chứng…'}
          </p>
        </div>
      ) : null}

      {ingest.isError ? (
        <p className="m-0 text-sm text-red-700" role="alert">
          {describeIngestError(ingest.error)}
        </p>
      ) : null}

      {ingest.isSuccess ? <IngestResult run={ingest.data} snapshotId={snapshotId} /> : null}
    </section>
  );
}

function IngestResult({ run, snapshotId }: { run: AnalysisRun; snapshotId: string }) {
  if (run.status === 'FAILED') {
    return (
      <div role="alert" className="flex flex-col gap-1">
        <StatusBadge tone="danger" label="Phân tích thất bại" />
        <p className="m-0 text-sm text-red-700">{run.errorMessage ?? 'Backend không ghi lý do.'}</p>
      </div>
    );
  }

  const summary =
    run.status === 'SUCCEEDED_CLEAN'
      ? 'Đã nạp xong: không có cảnh báo nào.'
      : `Đã nạp xong: ${String(run.findingCount)} cảnh báo · ${String(run.codeContextCount)} ngữ cảnh mã nguồn · ${String(run.linkCount)} liên kết bằng chứng.`;

  return (
    <div role="status" className="flex flex-col gap-2">
      <StatusBadge tone="ok" label={summary} />
      {run.unresolvedLocationCount > 0 ? (
        // Số này khác 0 thường nghĩa là artifact được sinh từ một phiên bản mã khác với snapshot —
        // cảnh báo vẫn hiện, nhưng không mở được đúng dòng mã và thiếu liên kết.
        <StatusBadge
          tone="warning"
          label={`${String(run.unresolvedLocationCount)}/${String(run.locationCount)} vị trí không khớp với file nào trong snapshot — artifact có thể được sinh từ phiên bản mã khác.`}
        />
      ) : null}
      <Link
        to={`/snapshots/${snapshotId}/runs/${run.id}/warnings`}
        className="text-brand self-start font-semibold"
      >
        Xem danh sách cảnh báo →
      </Link>
    </div>
  );
}

/** Tên + kích thước + thời điểm sửa: đủ phân biệt hai `TEST-*.xml` trùng tên ở hai thư mục. */
function fileKey(file: File): string {
  return `${file.name}:${String(file.size)}:${String(file.lastModified)}`;
}

function formatSize(bytes: number): string {
  return bytes < 1024 ? `${String(bytes)} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

function describeIngestError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Nạp thất bại.';
  }
  // Ánh xạ đúng các `type` mà GlobalExceptionHandler trả về cho endpoint này.
  if (error.problem.type?.endsWith('invalid-artifact') === true) {
    return `Có file không phải kết quả của analyzer được hỗ trợ: ${error.problem.detail ?? error.message}`;
  }
  if (error.problem.type?.endsWith('snapshot-not-ready') === true) {
    return 'Snapshot chưa giải nén xong, thử lại sau ít phút.';
  }
  if (error.problem.type?.endsWith('upload-too-large') === true) {
    return 'Tổng dung lượng file quá lớn.';
  }
  return error.problem.detail ?? error.message;
}
