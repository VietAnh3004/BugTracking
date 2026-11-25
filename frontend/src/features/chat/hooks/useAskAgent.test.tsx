/**
 * Huỷ lượt hỏi: bấm "Dừng" hay rời trang đều phải báo SERVER huỷ, không chỉ huỷ `fetch`.
 *
 * Chỉ huỷ `fetch` thì giao diện trông như đã dừng, trong khi agent-service vẫn gọi LLM tới hết
 * lượt — đúng loại hỏng không ai thấy, chỉ lộ ra ở hoá đơn quota.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';

import { useAskAgent } from '@/features/chat/hooks/useAskAgent';
import { createTestQueryClient } from '@/shared/lib/queryClient';

import type { ReactNode } from 'react';

const askAgentMock = vi.hoisted(() => vi.fn());
const cancelTurnMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/chat/api/chatApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/chat/api/chatApi')>();
  return { ...actual, askAgent: askAgentMock, cancelTurn: cancelTurnMock };
});

/** Lượt hỏi "đang chạy" cho tới khi bị huỷ — giống stream SSE thật khi `signal` bị abort. */
function hangingUntilAborted(
  _conversationId: string,
  _question: string,
  _callbacks: unknown,
  signal: AbortSignal,
): Promise<void> {
  return new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
  });
}

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>;
}

afterEach(() => {
  askAgentMock.mockReset();
  cancelTurnMock.mockReset();
});

describe('useAskAgent — huỷ lượt hỏi', () => {
  it('nút Dừng huỷ ở trình duyệt, báo server huỷ, và không hiện lỗi đỏ', async () => {
    askAgentMock.mockImplementation(hangingUntilAborted);
    cancelTurnMock.mockResolvedValue(undefined);
    const { result } = renderHook(() => useAskAgent('conv-1'), { wrapper });

    act(() => {
      void result.current.ask('Cau hoi');
    });
    await waitFor(() => expect(result.current.isStreaming).toBe(true));

    await act(async () => {
      await result.current.stop();
    });

    expect(cancelTurnMock).toHaveBeenCalledWith('conv-1');
    await waitFor(() => expect(result.current.isStreaming).toBe(false));
    expect(result.current.stopped).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it('rời trang giữa lượt hỏi cũng báo server huỷ', async () => {
    askAgentMock.mockImplementation(hangingUntilAborted);
    cancelTurnMock.mockResolvedValue(undefined);
    const { result, unmount } = renderHook(() => useAskAgent('conv-2'), { wrapper });

    act(() => {
      void result.current.ask('Cau hoi');
    });
    await waitFor(() => expect(result.current.isStreaming).toBe(true));

    unmount();

    expect(cancelTurnMock).toHaveBeenCalledWith('conv-2');
  });

  it('rời trang khi KHÔNG có lượt nào đang chạy thì không gọi server', () => {
    const { unmount } = renderHook(() => useAskAgent('conv-3'), { wrapper });

    unmount();

    expect(cancelTurnMock).not.toHaveBeenCalled();
  });
});

describe('useAskAgent — báo thử lại khi máy chủ model lỗi tạm thời', () => {
  /** Gặp thật 24/11/2026: Gemini trả 503 liên tục, lượt hỏi im lặng nhiều phút như bị treo. */
  it('mỗi lần thử lại thành một dòng tiến trình có lý do và thời gian chờ', async () => {
    askAgentMock.mockImplementation(
      (
        _conversationId: string,
        _question: string,
        callbacks: { onRetry?: (retry: unknown) => void },
        signal: AbortSignal,
      ) => {
        callbacks.onRetry?.({
          attempt: 2,
          maxAttempts: 6,
          reason: 'Máy chủ model quá tải hoặc lỗi tạm thời (HTTP 503)',
          delaySeconds: 1.8,
        });
        return hangingUntilAborted(_conversationId, _question, callbacks, signal);
      },
    );
    cancelTurnMock.mockResolvedValue(undefined);
    const { result } = renderHook(() => useAskAgent('conv-3'), { wrapper });

    act(() => {
      void result.current.ask('Cau hoi');
    });

    await waitFor(() => expect(result.current.steps).toHaveLength(1));
    expect(result.current.steps[0]).toMatchObject({
      label: 'Thử lại lần 2/6',
      detail: 'Máy chủ model quá tải hoặc lỗi tạm thời (HTTP 503) · chờ 2 giây',
      failed: true,
    });

    // Dừng trước khi test kết thúc: afterEach reset mock TRƯỚC khi gỡ component, nên một lượt còn
    // đang chạy lúc gỡ sẽ gọi `cancelTurn` đã mất giá trị trả về.
    await act(async () => {
      await result.current.stop();
    });
  });
});
