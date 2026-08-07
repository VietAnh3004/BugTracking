import { Link } from 'react-router-dom';

import type { Citation, CitationResolution } from '@/features/chat/api/chatApi';

/**
 * Bốn kết cục của một lượt trích dẫn — mỗi cái nói về một lỗi khác của mô hình, nên hiển thị
 * khác nhau chứ không gộp thành "hợp lệ / không hợp lệ".
 *
 * Trích dẫn HỎNG vẫn hiện ra, có chủ đích: chúng là mẫu số của Evidence Citation Accuracy. Giấu
 * đi thì giao diện trông sạch sẽ trong khi người chấm không thấy được tỉ lệ thật.
 */
const EXPLANATIONS: Record<CitationResolution, string> = {
  RESOLVED: 'Bằng chứng có thật trong lần phân tích này. Bấm để mở.',
  WRONG_RUN:
    'Evidence ID này có thật nhưng thuộc một lần phân tích khác — Agent nhớ nhầm ngữ cảnh.',
  UNKNOWN_ID: 'Không có bằng chứng nào mang Evidence ID này. Agent đã bịa ra nó.',
  MALFORMED: 'Chuỗi này không phải một Evidence ID.',
};

const LABELS: Record<CitationResolution, string> = {
  RESOLVED: '',
  WRONG_RUN: 'sai lần phân tích',
  UNKNOWN_ID: 'không tồn tại',
  MALFORMED: 'không phải ID',
};

interface CitationChipProps {
  citation: Citation;
  snapshotId: string;
  runId: string;
}

export function CitationChip({ citation, snapshotId, runId }: CitationChipProps) {
  const explanation = EXPLANATIONS[citation.resolution];

  if (citation.resolution === 'RESOLVED' && citation.shortId != null) {
    return (
      <Link
        to={`/snapshots/${snapshotId}/runs/${runId}/evidence/${citation.shortId}`}
        title={explanation}
        className="inline-flex items-center rounded-md bg-sky-50 px-2 py-0.5 font-mono text-xs font-medium text-sky-800 ring-1 ring-sky-600/20 ring-inset hover:bg-sky-100"
      >
        {citation.shortId}
      </Link>
    );
  }

  return (
    <span
      title={explanation}
      className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2 py-0.5 text-xs text-amber-800 ring-1 ring-amber-600/20 ring-inset"
    >
      <span className="font-mono">{citation.citedShortIdRaw}</span>
      <span className="text-amber-700">· {LABELS[citation.resolution]}</span>
    </span>
  );
}
