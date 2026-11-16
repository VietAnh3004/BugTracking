import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { type ChatMessage, askAgent, cancelTurn } from '@/features/chat/api/chatApi';
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
  const [stopped, setStopped] = useState(false);
  const nextId = useRef(0);
  const inFlight = useRef<AbortController | null>(null);
  // Hội thoại của lượt đang bay — cleanup khi unmount chạy SAU render cuối, lúc đó
  // `conversationId` trong closure có thể đã là hội thoại khác.
  const inFlightConversation = useRef<string | null>(null);

  // Rời trang = huỷ lượt đang bay ở CẢ server (đã chốt với người dùng: không tốn quota cho câu
  // trả lời không ai đọc). Chỉ huỷ `fetch` thì core-backend chỉ biết khi nó thử ghi khung kế tiếp,
  // có thể là sau cả một lượt gọi LLM. Gọi cancel là fire-and-forget: trang đã đi rồi, không còn
  // chỗ nào hiện lỗi.
  useEffect(
    () => () => {
      if (inFlight.current !== null && inFlightConversation.current !== null) {
        inFlight.current.abort();
        void cancelTurn(inFlightConversation.current).catch(() => undefined);
      }
    },
    [],
  );

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
      // Một lượt mới huỷ lượt cũ: hai stream cùng ghi vào một cache sẽ trộn hai câu trả lời.
      // Phía server, câu hỏi mới tự huỷ lượt cũ của cùng hội thoại (ConversationService.ask).
      inFlight.current?.abort();
      const controller = new AbortController();
      inFlight.current = controller;
      inFlightConversation.current = conversationId;

      setSteps([]);
      setError(null);
      setStopped(false);
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
        await askAgent(
          conversationId,
          question,
          {
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
          },
          controller.signal,
        );
      } catch (cause) {
        // Huỷ có chủ đích không phải lỗi: đừng hiện thông báo đỏ cho một thao tác người dùng
        // vừa tự làm (hỏi câu mới, hoặc rời trang).
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : 'Không gọi được Agent');
        }
      } finally {
        if (inFlight.current === controller) {
          inFlight.current = null;
          inFlightConversation.current = null;
          setStreaming(false);
        }
        // Đồng bộ lại với máy chủ: lượt hỏi tạm ở trên chưa có id thật, và một lượt hỏng có thể
        // đã ghi câu hỏi mà chưa ghi câu trả lời.
        void queryClient.invalidateQueries({ queryKey: chatKeys.messages(conversationId) });
      }
    },
    [addStep, appendMessage, conversationId, queryClient],
  );

  /** Nút "Dừng": huỷ ở trình duyệt NGAY (giao diện thôi chờ), rồi báo server huỷ phía nó. */
  const stop = useCallback(async (): Promise<void> => {
    const controller = inFlight.current;
    if (controller === null) {
      return;
    }
    controller.abort();
    setSteps([]);
    setStopped(true);
    try {
      await cancelTurn(conversationId);
    } catch {
      // Huỷ phía server hỏng thì lượt đó chạy nốt rồi tự kết thúc — không đáng một thông báo đỏ
      // đè lên câu "Đã dừng" người dùng vừa thấy.
    }
  }, [conversationId]);

  return { ask, stop, steps, isStreaming, stopped, error };
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
