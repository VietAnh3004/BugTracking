import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import { AgentProgress } from '@/features/chat/components/AgentProgress';
import { ChatMessageItem } from '@/features/chat/components/ChatMessageItem';
import { useAskAgent } from '@/features/chat/hooks/useAskAgent';
import {
  useConversations,
  useCreateConversation,
  useMessages,
} from '@/features/chat/hooks/useConversation';
import { IconArrowLeft, IconChat } from '@/shared/ui/icons';
import { buttonClass, CARD, INPUT } from '@/shared/ui/styles';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states';

/**
 * Hỏi đáp với Agent về một lần phân tích.
 *
 * Trạng thái phiên nằm trên URL (`?conversation=`) chứ không trong state: người dùng chia sẻ
 * được đường dẫn tới đúng cuộc trao đổi, và nút Back của trình duyệt hoạt động như mong đợi.
 *
 * `?focus=EV-XXXX` là lối vào từ Evidence Explorer: nó quyết định phiên MỚI sẽ neo vào cảnh báo
 * nào, và không ảnh hưởng tới phiên đã mở.
 */
export function ChatPage() {
  const { snapshotId = '', runId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const focus = searchParams.get('focus');
  const selectedId = searchParams.get('conversation') ?? '';

  const conversations = useConversations(runId);
  const messages = useMessages(selectedId);
  const createConversation = useCreateConversation(runId);
  const { ask, stop, steps, isStreaming, stopped, error } = useAskAgent(selectedId);

  const [draft, setDraft] = useState('');
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.data, steps]);

  function selectConversation(id: string): void {
    const next = new URLSearchParams(searchParams);
    next.set('conversation', id);
    setSearchParams(next, { replace: true });
  }

  async function startConversation(): Promise<void> {
    const created = await createConversation.mutateAsync(focus);
    selectConversation(created.id);
  }

  async function submit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const question = draft.trim();
    if (question === '' || isStreaming || selectedId === '') {
      return;
    }
    setDraft('');
    await ask(question);
  }

  return (
    <div className="mx-auto flex h-[calc(100vh-10rem)] min-h-[32rem] w-full max-w-4xl flex-col gap-3">
      <header className="flex flex-col gap-2">
        <Link
          to={`/snapshots/${snapshotId}/runs/${runId}/warnings`}
          className="text-ink-subtle hover:text-brand inline-flex items-center gap-1 self-start text-xs font-medium"
        >
          <IconArrowLeft className="size-3.5" />
          Về bảng cảnh báo
        </Link>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-ink m-0 text-xl font-semibold tracking-tight">Hỏi Agent</h1>
            <p className="text-ink-muted mt-0.5 mb-0 text-sm">
              Mọi nhận định đều kèm Evidence ID đã được đối chiếu với kho bằng chứng.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="conversation-picker" className="sr-only">
              Chọn phiên hỏi đáp
            </label>
            <select
              id="conversation-picker"
              value={selectedId}
              onChange={(event) => selectConversation(event.target.value)}
              className={`${INPUT} w-56`}
            >
              <option value="">— chọn phiên —</option>
              {(conversations.data ?? []).map((conversation) => (
                <option key={conversation.id} value={conversation.id}>
                  {conversation.title}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => void startConversation()}
              disabled={createConversation.isPending}
              className={buttonClass('primary')}
            >
              Phiên mới{focus != null ? ` về ${focus}` : ''}
            </button>
          </div>
        </div>
      </header>

      <div className={`${CARD} bg-surface-muted flex-1 overflow-y-auto p-4 sm:p-4`}>
        {selectedId === '' ? (
          <div className="flex h-full items-center justify-center">
            <EmptyState
              icon={<IconChat className="size-5" />}
              title="Chưa có phiên nào được chọn."
              description={
                <>
                  Chọn một phiên cũ ở ô phía trên, hoặc bấm <strong>Phiên mới</strong> để bắt đầu.
                </>
              }
            />
          </div>
        ) : messages.isPending ? (
          <LoadingState label="Đang tải lịch sử…" shape="lines" rows={4} />
        ) : (
          <ul className="space-y-3">
            {(messages.data ?? []).map((message) => (
              <ChatMessageItem
                key={message.id}
                message={message}
                snapshotId={snapshotId}
                runId={runId}
              />
            ))}
          </ul>
        )}

        <div className="mt-3">
          <AgentProgress steps={steps} isStreaming={isStreaming} />
        </div>

        {stopped && (
          <p role="status" className="text-ink-muted mt-3 text-sm">
            Đã dừng lượt hỏi. Câu hỏi vẫn nằm trong lịch sử, có thể hỏi lại bất cứ lúc nào.
          </p>
        )}

        {error != null && (
          <div className="mt-3">
            <ErrorState
              title="Lượt hỏi không hoàn tất."
              message={error}
              hint="Câu hỏi vẫn nằm trong lịch sử — gửi lại để hỏi tiếp."
            />
          </div>
        )}

        <div ref={bottom} />
      </div>

      <form onSubmit={(event) => void submit(event)} className={`${CARD} flex items-end gap-2 p-2`}>
        <label htmlFor="question" className="sr-only">
          Câu hỏi
        </label>
        <textarea
          id="question"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          rows={2}
          disabled={selectedId === '' || isStreaming}
          placeholder="Ví dụ: có test nào liên quan tới cảnh báo này không?"
          className="text-ink placeholder:text-ink-subtle flex-1 resize-none rounded-md border-0 bg-transparent px-2.5 py-1.5 text-sm outline-none disabled:opacity-60"
        />
        {isStreaming ? (
          // Đang chạy thì nút chính là "Dừng", không phải một nút "Đang hỏi…" bị khoá: người dùng
          // nhận ra mình hỏi nhầm thì phải có cách thôi chờ và thôi tốn quota.
          <button
            type="button"
            onClick={() => void stop()}
            className={buttonClass('dangerOutline')}
          >
            Dừng
          </button>
        ) : (
          <button
            type="submit"
            disabled={selectedId === '' || draft.trim() === ''}
            className={buttonClass('primary')}
          >
            Gửi
          </button>
        )}
      </form>
    </div>
  );
}
