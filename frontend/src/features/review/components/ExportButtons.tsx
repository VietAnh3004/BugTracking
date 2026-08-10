import { useState } from 'react';

import { downloadFile } from '@/shared/lib/downloadClient';

/**
 * Hai nút tải báo cáo của một lần phân tích.
 *
 * CSV và JSON không thay thế nhau: CSV một dòng một cảnh báo để nạp thẳng vào công cụ thống kê ở
 * tuần 17-18; JSON mang đủ claim/citation/trace để truy ngược một con số về đúng bằng chứng sinh
 * ra nó. Bỏ một trong hai là mất một trong hai việc.
 */
export function ExportButtons({ runId }: { runId: string }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'csv' | 'json' | null>(null);

  async function download(format: 'csv' | 'json'): Promise<void> {
    setError(null);
    setBusy(format);
    try {
      await downloadFile(
        `/api/v1/analysis-runs/${runId}/export.${format}`,
        `bao-cao-${runId}.${format}`,
      );
    } catch (cause) {
      // Hỏng phải nói ra: một lần tải thất bại trong im lặng trông y hệt trình duyệt chặn popup,
      // và người dùng sẽ bấm lại mãi.
      setError(cause instanceof Error ? cause.message : 'Không tải được báo cáo.');
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="text-right">
      <div className="flex gap-2">
        <button
          type="button"
          disabled={busy !== null}
          onClick={() => void download('csv')}
          className="border-border rounded-md border px-3 py-1.5 text-xs font-medium disabled:opacity-50"
        >
          {busy === 'csv' ? 'Đang tải…' : 'Tải CSV'}
        </button>
        <button
          type="button"
          disabled={busy !== null}
          onClick={() => void download('json')}
          className="border-border rounded-md border px-3 py-1.5 text-xs font-medium disabled:opacity-50"
        >
          {busy === 'json' ? 'Đang tải…' : 'Tải JSON'}
        </button>
      </div>
      {error === null ? null : (
        <p role="alert" className="text-sev-high mt-1 mb-0 text-xs">
          {error}
        </p>
      )}
    </div>
  );
}
