import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  type ChatMessage,
  type Conversation,
  createConversation,
  deleteConversation,
  getMessages,
  listConversations,
  renameConversation,
} from '@/features/chat/api/chatApi';

export const chatKeys = {
  conversations: (runId: string) => ['conversations', runId] as const,

  messages: (conversationId: string) => ['conversation-messages', conversationId] as const,
};

export function useConversations(runId: string) {
  return useQuery<Conversation[]>({
    queryKey: chatKeys.conversations(runId),

    queryFn: () => listConversations(runId),

    enabled: runId !== '',
  });
}

/**

 * Lịch sử một phiên.

 *

 * KHÔNG `staleTime: Infinity` như bằng chứng: lịch sử hội thoại thay đổi mỗi lần hỏi. Nhưng cũng

 * không refetch tự động — luồng SSE đã đẩy câu trả lời vào cache rồi, gọi lại chỉ để nhận đúng

 * thứ vừa có là một vòng mạng thừa.

 */

export function useMessages(conversationId: string) {
  return useQuery<ChatMessage[]>({
    queryKey: chatKeys.messages(conversationId),

    queryFn: () => getMessages(conversationId),

    enabled: conversationId !== '',

    refetchOnWindowFocus: false,
  });
}

export function useCreateConversation(runId: string) {
  const queryClient = useQueryClient();

  return useMutation<Conversation, Error, string | null>({
    mutationFn: (focusShortId) => createConversation(runId, focusShortId),

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations(runId) });
    },
  });
}

export function useRenameConversation(runId: string) {
  const queryClient = useQueryClient();
  return useMutation<Conversation, Error, { conversationId: string; title: string }>({
    mutationFn: ({ conversationId, title }) => renameConversation(conversationId, title),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations(runId) });
    },
  });
}

export function useDeleteConversation(runId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (conversationId) => deleteConversation(conversationId),
    onSuccess: (_data, conversationId) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations(runId) });
      // Lịch sử của phiên đã xoá không còn truy cập được (404): bỏ khỏi cache thay vì để nó gọi lại.
      queryClient.removeQueries({ queryKey: chatKeys.messages(conversationId) });
    },
  });
}
