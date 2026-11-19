/**
 * Chọn model ở trang chat. Quyền dùng model Premium gắn với DỰ ÁN (hạng nằm trong phiên hội thoại
 * trả về từ backend), nên ô chọn chỉ hiện ở dự án Premium. Test đi qua `routes` thật và chỉ giả
 * `fetch` + `askAgent`; điều cần khẳng định là cái ĐI RA khỏi trình duyệt, không chỉ cái hiện lên.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const askAgentMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/chat/api/chatApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/chat/api/chatApi')>();
  return { ...actual, askAgent: askAgentMock };
});

const REFRESH_TOKEN_KEY = 'datn.refreshToken';
const CHAT_PATH = '/snapshots/s-1/runs/r-1/chat?conversation=c-1';

function conversation(projectTier: 'FREE' | 'PREMIUM') {
  return {
    id: 'c-1',
    analysisRunId: 'r-1',
    focusShortId: null,
    title: 'Phiên thử',
    createdAt: '2026-11-19T00:00:00Z',
    updatedAt: '2026-11-19T00:00:00Z',
    projectTier,
  };
}

function answer(model: string | null) {
  return {
    id: 'm-1',
    ordinal: 1,
    role: 'ASSISTANT',
    content: 'Tóm tắt của agent',
    verdict: 'INSUFFICIENT_EVIDENCE',
    suggestedNextChecks: [],
    contradictingShortIds: [],
    claims: [],
    createdAt: '2026-11-19T00:00:01Z',
    ...(model === null ? {} : { model }),
  };
}

function mockChat(projectTier: 'FREE' | 'PREMIUM', messages: unknown[] = []) {
  window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
  return mockFetchByUrl({
    '/api/v1/auth/refresh': { body: fakeAuthTokens() },
    '/api/v1/analysis-runs/r-1/conversations': { body: [conversation(projectTier)] },
    '/api/v1/conversations/c-1/messages': { body: messages },
    '/actuator/health': { body: { status: 'UP' } },
  });
}

async function send(question: string) {
  await userEvent.type(await screen.findByLabelText('Câu hỏi'), question);
  await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
}

beforeEach(() => {
  window.localStorage.clear();
  askAgentMock.mockResolvedValue(undefined);
});

afterEach(() => {
  vi.restoreAllMocks();
  askAgentMock.mockReset();
  window.localStorage.clear();
});

describe('ô chọn model ở trang chat', () => {
  it('dự án Premium: có ô chọn, mặc định Gemini, đổi sang Sonnet thì gửi PREMIUM', async () => {
    mockChat('PREMIUM');
    renderRoute(CHAT_PATH);

    const select = await screen.findByLabelText('Model');
    expect(select).toHaveValue('FREE');
    expect(screen.getByRole('option', { name: 'Gemini · miễn phí' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Claude Sonnet 5 · Premium' })).toBeInTheDocument();

    await userEvent.selectOptions(select, 'PREMIUM');
    await send('Cảnh báo này có thật không?');

    await waitFor(() => expect(askAgentMock).toHaveBeenCalledTimes(1));
    const [conversationId, question, , , model] = askAgentMock.mock.calls[0] as unknown[];
    expect(conversationId).toBe('c-1');
    expect(question).toBe('Cảnh báo này có thật không?');
    expect(model).toBe('PREMIUM');
  });

  it('dự án Premium: không đổi gì thì câu hỏi đi bằng model miễn phí (không tự tốn tiền)', async () => {
    mockChat('PREMIUM');
    renderRoute(CHAT_PATH);

    await send('Hỏi thường');

    await waitFor(() => expect(askAgentMock).toHaveBeenCalledTimes(1));
    expect((askAgentMock.mock.calls[0] as unknown[])[4]).toBe('FREE');
  });

  it('dự án Free: KHÔNG có ô chọn model và không gửi trường model', async () => {
    mockChat('FREE');
    renderRoute(CHAT_PATH);

    await screen.findByLabelText('Câu hỏi');
    expect(screen.queryByLabelText('Model')).not.toBeInTheDocument();

    await send('Hỏi ở dự án free');

    await waitFor(() => expect(askAgentMock).toHaveBeenCalledTimes(1));
    expect((askAgentMock.mock.calls[0] as unknown[])[4]).toBeUndefined();
  });

  it('tin trả lời của Agent ghi rõ model đã trả lời', async () => {
    mockChat('PREMIUM', [answer('claude-sonnet-5')]);
    renderRoute(CHAT_PATH);

    expect(await screen.findByText('claude-sonnet-5')).toBeInTheDocument();
  });

  it('tin cũ không có model thì không hiện nhãn', async () => {
    mockChat('FREE', [answer(null)]);
    renderRoute(CHAT_PATH);

    await screen.findByText('Tóm tắt của agent');
    expect(screen.queryByTitle('Model đã trả lời')).not.toBeInTheDocument();
  });
});
