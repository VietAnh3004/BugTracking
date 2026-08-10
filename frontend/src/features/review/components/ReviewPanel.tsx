import { useEffect, useRef, useState } from 'react';

import type { ReviewDecision } from '@/features/review/api/reviewApi';
import { useReviews, useSubmitReview } from '@/features/review/hooks/useReview';

/**
 * Bốn lựa chọn của người dùng về một cảnh báo.
 *
 * `UNCERTAIN` và `INSUFFICIENT_EVIDENCE` là HAI thứ khác nhau, cố ý không gộp: cái đầu là "tôi đã
 * xem đủ bằng chứng và vẫn không quyết được", cái sau là "hệ thống chưa cho tôi đủ bằng chứng để
 * xem". Gộp lại thì mất đúng thông tin cho biết nên cải thiện Agent hay cải thiện pipeline bằng
 * chứng — và đó là một trong những câu hỏi mà đồ án này đặt ra.
 */
const CHOICES: { value: ReviewDecision; label: string; hint: string; className: string }[] = [
  {
    value: 'ACCEPT',
    label: 'Đúng là lỗi',
    hint: 'Cảnh báo có căn cứ, nên sửa mã.',
    className: 'border-red-300 bg-red-50 text-red-800',
  },
  {
    value: 'REJECT',
    label: 'Báo nhầm',
    hint: 'Cảnh báo sai — nêu lý do trong mã ở phần ghi chú.',
    className: 'border-emerald-300 bg-emerald-50 text-emerald-800',
  },
  {
    value: 'UNCERTAIN',
    label: 'Chưa quyết được',
    hint: 'Đã xem đủ bằng chứng nhưng vẫn không chắc.',
    className: 'border-amber-300 bg-amber-50 text-amber-800',
  },
  {
    value: 'INSUFFICIENT_EVIDENCE',
    label: 'Thiếu bằng chứng',
    hint: 'Hệ thống chưa đưa ra đủ bằng chứng để phán xét.',
    className: 'border-slate-300 bg-slate-50 text-slate-700',
  },
];

interface ReviewPanelProps {
  runId: string;
  shortId: string;
  /**
   * Câu trả lời của Agent người dùng vừa đọc, nếu có.
   *
   * Đây là **biến thực nghiệm** của user study, không phải metadata: nó phân biệt nhóm dùng Agent
   * với nhóm đối chứng. Backend kiểm lại giá trị này chứ không tin — gửi một id bừa sẽ ra 400.
   */
  messageId?: string | null;
}

export function ReviewPanel({ runId, shortId, messageId = null }: ReviewPanelProps) {
  const reviews = useReviews(runId);
  const submit = useSubmitReview(runId);
  const existing = (reviews.data ?? []).find((r) => r.shortId === shortId);

  const [note, setNote] = useState('');
  const openedAt = useRef(Date.now());

  useEffect(() => {
    setNote(existing?.note ?? '');
  }, [existing?.note]);

  function choose(decision: ReviewDecision): void {
    // Bỏ hẳn trường thay vì gửi `null`: OpenAPI khai chúng là tuỳ chọn, và `undefined` không
    // được serialize nên backend nhận đúng "không có" thay vì "có, giá trị null".
    const seconds = elapsedSeconds(openedAt.current);
    submit.mutate({
      shortId,
      body: {
        decision,
        ...(messageId != null ? { messageId } : {}),
        ...(note.trim() === '' ? {} : { note: note.trim() }),
        ...(seconds === null ? {} : { secondsSpent: seconds }),
      },
    });
  }

  return (
    <section className="border-border rounded-md border p-3">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-ink m-0 text-sm font-semibold">Đánh giá của bạn</h3>
        {existing === undefined ? (
          <span className="text-ink-muted text-xs">Chưa đánh giá</span>
        ) : (
          <span className="text-ink-muted text-xs">
            Đã ghi lúc {new Date(existing.updatedAt).toLocaleString('vi-VN')}
            {existing.messageId != null ? ' · sau khi đọc Agent' : ' · không dùng Agent'}
          </span>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        {CHOICES.map((choice) => {
          const active = existing?.decision === choice.value;
          return (
            <button
              key={choice.value}
              type="button"
              title={choice.hint}
              disabled={submit.isPending}
              onClick={() => choose(choice.value)}
              className={`rounded-md border px-3 py-1.5 text-xs font-medium disabled:opacity-50 ${
                active ? choice.className : 'border-border text-ink-muted hover:bg-slate-50'
              }`}
            >
              {choice.label}
            </button>
          );
        })}
      </div>

      <label htmlFor={`note-${shortId}`} className="sr-only">
        Ghi chú
      </label>
      <textarea
        id={`note-${shortId}`}
        value={note}
        onChange={(event) => setNote(event.target.value)}
        rows={2}
        maxLength={2000}
        placeholder="Vì sao? (ghi chú này đi vào báo cáo)"
        className="border-border mt-2 w-full resize-none rounded-md border px-2 py-1.5 text-xs"
      />

      {submit.isError ? (
        <p role="alert" className="text-sev-high mt-2 text-xs">
          Không ghi được đánh giá: {submit.error.message}
        </p>
      ) : null}
    </section>
  );
}

/**
 * Thời gian từ lúc mở panel, tính bằng giây.
 *
 * Trả `null` khi chưa đủ một giây: backend từ chối giá trị 0 vì đó là dữ liệu rác, không phải
 * "rất nhanh". Con số này client tự đo nên chỉ tham khảo — nó KHÔNG phải "Time to understand"
 * của kế hoạch §6, cái đó đo trong phiên có người quan sát.
 */
function elapsedSeconds(startedAt: number): number | null {
  const seconds = Math.round((Date.now() - startedAt) / 1000);
  return seconds > 0 ? seconds : null;
}
