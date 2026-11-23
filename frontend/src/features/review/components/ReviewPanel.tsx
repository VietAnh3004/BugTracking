import { useEffect, useRef, useState } from 'react';

import type { ReviewDecision } from '@/features/review/api/reviewApi';
import {
  useLatestAgentAnswer,
  useReviews,
  useSubmitReview,
} from '@/features/review/hooks/useReview';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';

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
    className: 'border-danger/40 bg-danger/10 text-danger ring-2 ring-danger/30',
  },
  {
    value: 'REJECT',
    label: 'Báo nhầm',
    hint: 'Cảnh báo sai — nêu lý do trong mã ở phần ghi chú.',
    className: 'border-success/40 bg-success/10 text-success ring-2 ring-success/30',
  },
  {
    value: 'UNCERTAIN',
    label: 'Chưa quyết được',
    hint: 'Đã xem đủ bằng chứng nhưng vẫn không chắc.',
    className: 'border-warning/40 bg-warning/10 text-warning ring-2 ring-warning/30',
  },
  {
    value: 'INSUFFICIENT_EVIDENCE',
    label: 'Thiếu bằng chứng',
    hint: 'Hệ thống chưa đưa ra đủ bằng chứng để phán xét.',
    className: 'border-border-strong bg-surface-sunken text-ink-muted ring-2 ring-border-strong',
  },
];

interface ReviewPanelProps {
  runId: string;
  shortId: string;
  /**
   * Câu trả lời của Agent người dùng vừa đọc, nếu nơi gọi biết chắc. Vắng mặt thì panel tự tra câu
   * trả lời MỚI NHẤT mà người dùng đã nhận về cảnh báo này.
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

  // Lỗi gặp thật 23/11/2026: panel chỉ nằm ở trang cảnh báo, nơi không ai truyền `messageId`, nên
  // MỌI quyết định ghi "không dùng Agent" dù người dùng vừa hỏi Agent ở trang chat. Nay đã có câu
  // trả lời cho đúng cảnh báo này thì coi là đã đọc, và nói rõ điều đó trước khi người dùng bấm.
  const latestAnswer = useLatestAgentAnswer(runId, shortId);
  const agentMessageId = messageId ?? latestAnswer.data?.messageId ?? null;
  // Chưa tra xong thì chưa cho bấm: bấm lúc này sẽ xếp nhầm người đã đọc Agent vào nhóm đối chứng.
  const waitingForAgentLookup = messageId == null && latestAnswer.isPending;

  const [note, setNote] = useState('');
  const openedAt = useRef(Date.now());

  useEffect(() => {
    setNote(existing?.note ?? '');
  }, [existing?.note]);

  // Ghi chú đang gõ khác với bản đã lưu. So theo bản đã cắt khoảng trắng vì đó là thứ được gửi đi.
  const savedNote = existing?.note ?? '';
  const noteDirty = note.trim() !== savedNote.trim();

  /**
   * Lưu RIÊNG ghi chú cho một quyết định đã có.
   *
   * Backend ghi đè cả hàng (PUT), nên phải gửi lại ĐÚNG các giá trị đã lưu chứ không phải giá trị
   * hiện tại của panel: `messageId` là biến thực nghiệm (có đọc Agent hay không) và `secondsSpent` là
   * thời gian ra QUYẾT ĐỊNH. Viết thêm một câu ghi chú không được xoá dấu "đã đọc Agent", cũng không
   * được kéo dài thời gian quyết định.
   */
  function saveNote(): void {
    if (existing === undefined) {
      return;
    }
    submit.mutate({
      shortId,
      body: {
        decision: existing.decision,
        ...(existing.messageId != null ? { messageId: existing.messageId } : {}),
        // Rỗng = xoá ghi chú: bỏ trường đi thì backend ghi null.
        ...(note.trim() === '' ? {} : { note: note.trim() }),
        ...(existing.secondsSpent != null ? { secondsSpent: existing.secondsSpent } : {}),
      },
    });
  }

  function choose(decision: ReviewDecision): void {
    // Bỏ hẳn trường thay vì gửi `null`: OpenAPI khai chúng là tuỳ chọn, và `undefined` không
    // được serialize nên backend nhận đúng "không có" thay vì "có, giá trị null".
    const seconds = elapsedSeconds(openedAt.current);
    submit.mutate({
      shortId,
      body: {
        decision,
        ...(agentMessageId != null ? { messageId: agentMessageId } : {}),
        ...(note.trim() === '' ? {} : { note: note.trim() }),
        ...(seconds === null ? {} : { secondsSpent: seconds }),
      },
    });
  }

  return (
    <section className={`${CARD} p-4`}>
      <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
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

      <p className="text-ink-muted mt-0 mb-1 text-sm">
        Sau khi xem bằng chứng ở trên, cảnh báo này là gì?
      </p>
      <p className="text-ink-subtle mt-0 mb-3 text-xs" data-testid="agent-usage-note">
        {agentUsageNote(messageId, latestAnswer)}
      </p>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {CHOICES.map((choice) => {
          const active = existing?.decision === choice.value;
          return (
            <button
              key={choice.value}
              type="button"
              title={choice.hint}
              disabled={submit.isPending || waitingForAgentLookup}
              onClick={() => choose(choice.value)}
              aria-pressed={active}
              className={`flex cursor-pointer flex-col items-start gap-0.5 rounded-lg border px-3 py-2.5 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
                active
                  ? choice.className
                  : 'border-border bg-surface text-ink hover:border-border-strong hover:bg-surface-muted'
              }`}
            >
              <span className="text-sm font-semibold">{choice.label}</span>
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
        className={`${INPUT} mt-3 resize-none`}
      />

      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <p className="text-ink-subtle m-0 text-xs" role="status">
          {existing === undefined
            ? 'Ghi chú được lưu cùng lúc bạn chọn một đánh giá ở trên.'
            : noteDirty
              ? 'Ghi chú chưa lưu.'
              : savedNote.trim() !== ''
                ? 'Đã lưu ghi chú.'
                : null}
        </p>
        {existing === undefined ? null : (
          <button
            type="button"
            onClick={saveNote}
            disabled={!noteDirty || submit.isPending}
            className={buttonClass('secondary', 'sm')}
          >
            {submit.isPending ? 'Đang lưu…' : 'Lưu ghi chú'}
          </button>
        )}
      </div>

      {submit.isError ? (
        <p role="alert" className="text-danger mt-2 text-xs">
          Không ghi được đánh giá: {submit.error.message}
        </p>
      ) : null}
    </section>
  );
}

/** Câu cho người dùng biết đánh giá sắp ghi sẽ được xếp vào nhóm nào. */
function agentUsageNote(
  messageId: string | null,
  latestAnswer: ReturnType<typeof useLatestAgentAnswer>,
): string {
  if (messageId != null) {
    return 'Đánh giá sẽ được ghi là đã đọc câu trả lời Agent vừa xem.';
  }
  if (latestAnswer.isPending) {
    return 'Đang kiểm tra bạn đã hỏi Agent về cảnh báo này chưa…';
  }
  if (latestAnswer.isError) {
    return 'Không kiểm tra được câu trả lời Agent; đánh giá sẽ được ghi là không dùng Agent.';
  }
  const answer = latestAnswer.data;
  if (answer?.messageId == null) {
    return 'Bạn chưa hỏi Agent về cảnh báo này, nên đánh giá sẽ được ghi là không dùng Agent.';
  }
  const when =
    answer.answeredAt == null ? '' : ` lúc ${new Date(answer.answeredAt).toLocaleString('vi-VN')}`;
  const model = answer.model == null ? '' : ` · ${answer.model}`;
  return `Bạn đã hỏi Agent về cảnh báo này (câu trả lời${when}${model}), nên đánh giá sẽ được ghi là đã đọc Agent.`;
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
