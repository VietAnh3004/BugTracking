import { useCallback, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { type ChatMessage, askAgent } from '@/features/chat/api/chatApi';
import { chatKeys } from '@/features/chat/hooks/useConversation';

/** Một dòng tiến trình hiện ra trong lúc chờ. */
export interface ProgressStep {
  id: number;
  label: string;
  detail: string | null;
  failed: boolean;
}

/**
 * Trạng thái của một lượt hỏi đang chạy.
 *
 * Tiến trình được giữ ở state cục bộ chứ không nhét vào TanStack Query: nó KHÔNG phải dữ liệu
 * máy chủ, nó là những gì đang xảy ra ngay lúc này và biến mất khi câu trả lời tới. Đưa vào cache
 * sẽ khiến nó sống lại khi người dùng quay lại trang, hiển thị một tiến trình đã kết thúc từ lâu.
 */
export function useAskAgent(conversationId: string) {
  const queryClient = useQueryClient();
  const [steps, setSteps] = useState<ProgressStep[]>([]);
  const [isStreaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextId = useRef(0);

  const addStep = useCallback((label: string, detail: string | null, failed = false) => {
    setSteps((current) => [...current, { id: nextId.current++, label, detail, failed }]);
  }, []);

  const appendMessage = useCallback(
    (message: ChatMessage) => {
      queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(conversationId), (current) => [
        ...(current ?? []),
        message,
      ]);
    },
    [conversationId, queryClient],
  );

  const ask = useCallback(
    async (question: string): Promise<void> => {
      setSteps([]);
      setError(null);
      setStreaming(true);

      // Câu hỏi hiện ra NGAY, không đợi máy chủ: chờ tới lúc có câu trả lời mới thấy câu mình vừa
      // gõ thì giao diện như bị treo. Id tạm được thay bằng bản thật khi tải lại lịch sử.
      appendMessage({
        id: `tam-${Date.now()}`,
        ordinal: -1,
        role: 'USER',
        content: question,
        suggestedNextChecks: [],
        contradictingShortIds: [],
        claims: [],
        createdAt: new Date().toISOString(),
      });

      try {
        await askAgent(conversationId, question, {
          onStatus: (message) => addStep(message, null),
          onToolCall: (call) =>
            addStep(`Truy xuất ${call.name}`, describeArguments(call.arguments)),
          onToolResult: (result) =>
            addStep(
              `Xong ${result.name}`,
              result.error ?? describeResult(result.itemCount, result.durationMs),
              result.error !== null,
            ),
          onAnswer: (message) => {
            appendMessage(message);
            setSteps([]);
          },
          onError: (message) => setError(message),
        });
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Không gọi được Agent');
      } finally {
        setStreaming(false);
        // Đồng bộ lại với máy chủ: lượt hỏi tạm ở trên chưa có id thật, và một lượt hỏng có thể
        // đã ghi câu hỏi mà chưa ghi câu trả lời.
        void queryClient.invalidateQueries({ queryKey: chatKeys.messages(conversationId) });
      }
    },
    [addStep, appendMessage, conversationId, queryClient],
  );

  return { ask, steps, isStreaming, error };
}

function describeArguments(args: Record<string, unknown>): string | null {
  const evidenceId = args['evidence_id'];
  if (typeof evidenceId === 'string') {
    return evidenceId;
  }
  const query = args['query'];
  return typeof query === 'string' ? `“${query}”` : null;
}

function describeResult(itemCount: number | null, durationMs: number): string {
  // `null` nghĩa là tool không trả danh sách (get_warning). Hiện "0 mục" ở đó là nói dối.
  const count = itemCount === null ? '' : `${itemCount} mục · `;
  return `${count}${durationMs} ms`;
}
