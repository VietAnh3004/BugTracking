/**
 * Vùng tải lên repo dạng zip.
 *
 * Có CẢ kéo-thả VÀ `<input type="file">` (ẩn trong `<label>`, không phải `display:none` trên input
 * rời): kéo-thả một mình là lỗi tiếp cận — không thao tác được bằng bàn phím, và trình đọc màn
 * hình không có gì để kích hoạt. `<label htmlFor>` cho phép bấm/Enter mở hộp thoại chọn file như
 * một nút bình thường.
 */

import { useRef, useState } from 'react';

import { useUploadSnapshot } from '@/features/snapshots/hooks/useSnapshots';
import { ApiError } from '@/shared/lib/apiClient';

import type { DragEvent } from 'react';

export function UploadZone({ projectId }: { projectId: string }) {
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadSnapshot(projectId);

  function submit(file: File | undefined) {
    if (file !== undefined) {
      upload.mutate(file);
    }
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    submit(event.dataTransfer.files[0]);
  }

  return (
    <div
      onDragOver={(event) => {
        // Không chặn mặc định thì trình duyệt MỞ file trong tab thay vì báo cho ta.
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      className={`rounded border-2 border-dashed p-6 text-center ${
        dragging ? 'border-brand bg-brand-subtle' : 'border-border'
      }`}
    >
      <p className="text-ink-muted mt-0 mb-3 text-sm">Kéo thả file .zip của repo vào đây, hoặc</p>

      <label
        htmlFor="snapshot-file"
        className="bg-brand hover:bg-brand-hover inline-block cursor-pointer rounded px-4 py-2 font-semibold text-white"
      >
        Chọn file zip
      </label>
      <input
        id="snapshot-file"
        ref={inputRef}
        type="file"
        accept=".zip,application/zip"
        className="sr-only"
        onChange={(event) => {
          submit(event.target.files?.[0]);
          // Reset để chọn LẠI CÙNG một file vẫn kích hoạt onChange (trình duyệt không bắn sự kiện
          // khi giá trị không đổi) — cần thiết khi lần tải trước thất bại và người dùng thử lại.
          event.target.value = '';
        }}
      />

      {upload.isPending ? (
        <div className="mt-4" role="status" aria-live="polite">
          <div className="bg-border h-2 w-full overflow-hidden rounded">
            <div
              className="bg-brand h-full transition-[width]"
              style={{ width: `${String(upload.progress)}%` }}
            />
          </div>
          <p className="text-ink-muted mt-1 text-sm">Đang tải lên… {upload.progress}%</p>
        </div>
      ) : null}

      {upload.isError ? (
        <p className="mt-3 text-sm text-red-700" role="alert">
          {describeUploadError(upload.error)}
        </p>
      ) : null}
    </div>
  );
}

function describeUploadError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Tải lên thất bại.';
  }
  // Ánh xạ đúng các `type` mà GlobalExceptionHandler của backend trả về, thay vì hiện thông điệp
  // kỹ thuật thô cho người dùng.
  if (error.problem.type?.endsWith('archive-limit-exceeded') === true) {
    return 'File zip vượt giới hạn cho phép (số file, dung lượng sau giải nén, hoặc tỷ lệ nén).';
  }
  if (error.problem.type?.endsWith('invalid-archive') === true) {
    return 'File tải lên không phải zip hợp lệ, hoặc chứa đường dẫn không an toàn.';
  }
  if (error.problem.type?.endsWith('upload-too-large') === true) {
    return 'File tải lên quá lớn.';
  }
  return error.problem.detail ?? error.message;
}
