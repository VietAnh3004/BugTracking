/**
 * Khung đổi tên / xoá MỘT phiên hỏi Agent, hiện ngay dưới thanh chọn phiên.
 *
 * Xoá không đòi gõ lại tên như xoá dự án: phiên chỉ là một cuộc trao đổi, người dùng mở phiên mới
 * được ngay. Nhưng vẫn phải xác nhận một bước vì từ giao diện thì không lấy lại được.
 */

import { useState } from 'react';

import {
  useDeleteConversation,
  useRenameConversation,
} from '@/features/chat/hooks/useConversation';
import { ApiError } from '@/shared/lib/apiClient';
import { buttonClass } from '@/shared/ui/styles';
import { TextField } from '@/shared/ui/TextField';

import type { Conversation } from '@/features/chat/api/chatApi';
import type { FormEvent } from 'react';

interface ConversationPanelProps {
  mode: 'rename' | 'delete';
  conversation: Conversation;
  runId: string;
  onClose: () => void;
  /** Gọi sau khi xoá xong, để trang bỏ phiên đó khỏi URL. */
  onDeleted: () => void;
}

function describe(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Không gọi được máy chủ';
}

export function ConversationPanel({
  mode,
  conversation,
  runId,
  onClose,
  onDeleted,
}: ConversationPanelProps) {
  return mode === 'rename' ? (
    <RenameForm conversation={conversation} runId={runId} onClose={onClose} />
  ) : (
    <DeleteConfirm
      conversation={conversation}
      runId={runId}
      onClose={onClose}
      onDeleted={onDeleted}
    />
  );
}

function RenameForm({
  conversation,
  runId,
  onClose,
}: Pick<ConversationPanelProps, 'conversation' | 'runId' | 'onClose'>) {
  const [title, setTitle] = useState(conversation.title);
  const rename = useRenameConversation(runId);
  const trimmed = title.trim();
  const unchanged = trimmed === conversation.title;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (trimmed === '' || unchanged) {
      return;
    }
    rename.mutate({ conversationId: conversation.id, title: trimmed }, { onSuccess: onClose });
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3 sm:flex-row sm:items-end"
    >
      <div className="flex-1">
        <TextField
          id={`rename-conversation-${conversation.id}`}
          label="Tên phiên"
          value={title}
          onChange={setTitle}
          autoComplete="off"
          maxLength={200}
        />
      </div>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={trimmed === '' || unchanged || rename.isPending}
          className={buttonClass('primary')}
        >
          {rename.isPending ? 'Đang lưu…' : 'Lưu'}
        </button>
        <button
          type="button"
          onClick={onClose}
          disabled={rename.isPending}
          className={buttonClass('secondary')}
        >
          Huỷ
        </button>
      </div>
      {rename.isError ? (
        <p role="alert" className="text-danger m-0 text-sm sm:basis-full">
          {describe(rename.error)}
        </p>
      ) : null}
    </form>
  );
}

function DeleteConfirm({
  conversation,
  runId,
  onClose,
  onDeleted,
}: Omit<ConversationPanelProps, 'mode'>) {
  const remove = useDeleteConversation(runId);

  return (
    <div className="border-danger/30 bg-danger/5 flex flex-col gap-3 rounded-lg border p-3">
      <p className="m-0 text-sm">
        Xoá phiên <strong>{conversation.title}</strong>? Các câu hỏi và câu trả lời trong phiên sẽ
        biến mất khỏi giao diện và không khôi phục được từ đây.
      </p>
      {remove.isError ? (
        <p role="alert" className="text-danger m-0 text-sm">
          {describe(remove.error)}
        </p>
      ) : null}
      <div className="flex gap-2">
        <button
          type="button"
          disabled={remove.isPending}
          onClick={() => remove.mutate(conversation.id, { onSuccess: onDeleted })}
          className={buttonClass('danger')}
        >
          {remove.isPending ? 'Đang xoá…' : 'Xoá phiên'}
        </button>
        <button
          type="button"
          onClick={onClose}
          disabled={remove.isPending}
          className={buttonClass('secondary')}
        >
          Huỷ
        </button>
      </div>
    </div>
  );
}
