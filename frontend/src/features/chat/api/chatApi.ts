import { apiClient } from '@/shared/lib/apiClient';
import type { components } from '@/shared/lib/api-types';
import { type SseFrame, streamSse } from '@/shared/lib/sseClient';

/** Kiểu lấy từ OpenAPI, không khai lại bằng tay — CI đối chiếu bằng `git diff --exit-code`. */
export type Conversation = components['schemas']['ConversationResponse'];
export type ChatMessage = components['schemas']['ChatMessageResponse'];
export type Claim = components['schemas']['ClaimView'];
export type Citation = components['schemas']['CitationView'];
export type Verdict = NonNullable<ChatMessage['verdict']>;
export type CitationResolution = Citation['resolution'];

export function listConversations(runId: string): Promise<Conversation[]> {
  return apiClient.get<Conversation[]>(`/api/v1/analysis-runs/${runId}/conversations`);
}

export function createConversation(
  runId: string,
  focusShortId: string | null,
): Promise<Conversation> {
  return apiClient.post<Conversation>(`/api/v1/analysis-runs/${runId}/conversations`, {
    focusShortId,
    title: null,
  });
}

export function getMessages(conversationId: string): Promise<ChatMessage[]> {
  return apiClient.get<ChatMessage[]>(`/api/v1/conversations/${conversationId}/messages`);
}

/**
 * Sự kiện tiến trình mà backend forward nguyên văn từ agent-service.
 *
 * Chỉ khai những trường giao diện thật sự dùng: khai đủ mọi trường ở đây nghĩa là mỗi lần
 * agent-service thêm một số đo là frontend phải sửa theo, dù không hiển thị gì thêm.
 */
export interface ToolCallFrame {
  name: string;
  arguments: Record<string, unknown>;
}

export interface ToolResultFrame {
  name: string;
  durationMs: number;
  itemCount: number | null;
  error: string | null;
}

export interface AskCallbacks {
  onStatus: (message: string) => void;
  onToolCall: (call: ToolCallFrame) => void;
  onToolResult: (result: ToolResultFrame) => void;
  /** Câu trả lời ĐÃ qua hậu kiểm trích dẫn và đã ghi xuống DB. */
  onAnswer: (message: ChatMessage) => void;
  onError: (message: string) => void;
}

/**
 * Hỏi một câu và nhận tiến trình qua SSE.
 *
 * Gọi core-backend chứ KHÔNG gọi agent-service: agent-service không có xác thực người dùng, không
 * bật CORS, và câu trả lời của nó chưa qua hậu kiểm trích dẫn (ADR 0010).
 */
export function askAgent(
  conversationId: string,
  question: string,
  callbacks: AskCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  return streamSse(
    { path: `/api/v1/conversations/${conversationId}/messages`, body: { question }, signal },
    (frame) => dispatch(frame, callbacks),
  );
}

function dispatch(frame: SseFrame, callbacks: AskCallbacks): void {
  const payload = safeParse(frame.data);
  if (payload === null) {
    return;
  }

  switch (frame.name) {
    case 'status':
      callbacks.onStatus(readString(payload, 'message') ?? 'Đang xử lý');
      break;
    case 'tool_call':
      callbacks.onToolCall({
        name: readString(payload, 'name') ?? 'không rõ',
        arguments: (payload['arguments'] as Record<string, unknown>) ?? {},
      });
      break;
    case 'tool_result':
      callbacks.onToolResult({
        name: readString(payload, 'name') ?? 'không rõ',
        durationMs: readNumber(payload, 'duration_ms') ?? 0,
        itemCount: readNumber(payload, 'item_count'),
        error: readString(payload, 'error'),
      });
      break;
    case 'answer':
      callbacks.onAnswer(payload as unknown as ChatMessage);
      break;
    case 'error':
      callbacks.onError(readString(payload, 'message') ?? 'Agent gặp lỗi không xác định');
      break;
    default:
      // Sự kiện lạ từ một backend mới hơn: bỏ qua thay vì làm hỏng cả lượt hỏi đáp.
      break;
  }
}

function safeParse(data: string): Record<string, unknown> | null {
  try {
    return JSON.parse(data) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function readString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(payload: Record<string, unknown>, key: string): number | null {
  const value = payload[key];
  return typeof value === 'number' ? value : null;
}
