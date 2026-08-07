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
  const { ask, steps, isStreaming, error } = useAskAgent(selectedId);

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
    <div className="mx-auto flex h-[calc(100vh-8rem)] w-full max-w-4xl flex-col gap-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-slate-900">Hỏi Agent</h1>
          <p className="text-sm text-slate-600">
            Mọi nhận định đều kèm Evidence ID đã được đối chiếu với kho bằng chứng.{' '}
            <Link
              to={`/snapshots/${snapshotId}/runs/${runId}/warnings`}
              className="text-sky-700 hover:underline"
            >
              Về bảng cảnh báo
            </Link>
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
            className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
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
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            Phiên mới{focus != null ? ` về ${focus}` : ''}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto rounded-lg border border-slate-200 bg-slate-50 p-4">
        {selectedId === '' ? (
          <p className="text-sm text-slate-600">
            Chưa có phiên nào được chọn. Bấm <strong>Phiên mới</strong> để bắt đầu.
          </p>
        ) : messages.isPending ? (
          <p className="text-sm text-slate-600">Đang tải lịch sử…</p>
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

        {error != null && (
          <p role="alert" className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800">
            {error}
          </p>
        )}

        <div ref={bottom} />
      </div>

      <form onSubmit={(event) => void submit(event)} className="flex items-end gap-2">
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
          className="flex-1 resize-none rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100"
        />
        <button
          type="submit"
          disabled={selectedId === '' || isStreaming || draft.trim() === ''}
          className="rounded-md bg-sky-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {isStreaming ? 'Đang hỏi…' : 'Gửi'}
        </button>
      </form>
    </div>
  );
}
