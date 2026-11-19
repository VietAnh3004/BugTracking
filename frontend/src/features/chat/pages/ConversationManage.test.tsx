/**
 * Đổi tên và xoá phiên hỏi Agent ở trang chat. Chạy qua `routes` thật, chỉ giả `fetch`; mỗi test
 * kiểm cả cái HIỆN RA lẫn request ĐI RA — nút "Xoá" hiện đúng mà không gọi đúng endpoint là nút hỏng.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { fakeAuthTokens, mockFetchByUrl } from '@/test/mockFetch';
import { renderRoute } from '@/test/renderRoute';

const REFRESH_TOKEN_KEY = 'datn.refreshToken';
const CHAT_PATH = '/snapshots/s-1/runs/r-1/chat?conversation=c-1';

const CONVERSATION = {
  id: 'c-1',
  analysisRunId: 'r-1',
  focusShortId: null,
  title: 'Phiên về MD5',
  createdAt: '2026-11-19T00:00:00Z',
  updatedAt: '2026-11-19T00:00:00Z',
  projectTier: 'FREE',
};

function mockChat(extra: Parameters<typeof mockFetchByUrl>[0] = {}) {
  window.localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-gia');
  // `extra` đứng trước: mockFetchByUrl khớp theo `includes` và thứ tự khai báo, nên endpoint cụ thể
  // ('/conversations/c-1' của PATCH/DELETE) phải đứng trước '/analysis-runs/r-1/conversations'.
  // '/conversations/c-1/messages' đứng trước '/conversations/c-1' vì URL đó chứa URL kia.
  return mockFetchByUrl({
    '/api/v1/conversations/c-1/messages': { body: [] },
    ...extra,
    '/api/v1/auth/refresh': { body: fakeAuthTokens() },
    '/api/v1/analysis-runs/r-1/conversations': { body: [CONVERSATION] },
    '/actuator/health': { body: { status: 'UP' } },
  });
}

function callsWith(fetchSpy: ReturnType<typeof mockFetchByUrl>, method: string) {
  return fetchSpy.mock.calls.filter(
    ([input, init]) =>
      String(input).endsWith('/api/v1/conversations/c-1') && init?.method === method,
  );
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe('đổi tên phiên', () => {
  it('mở ô đổi tên với tên hiện tại, lưu thì gọi đúng PATCH với tên đã cắt khoảng trắng', async () => {
    const fetchSpy = mockChat({
      '/api/v1/conversations/c-1': { body: { ...CONVERSATION, title: 'Tên mới' } },
    });
    renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Đổi tên phiên' }));
    const input = screen.getByLabelText('Tên phiên');
    expect(input).toHaveValue('Phiên về MD5');

    await userEvent.clear(input);
    await userEvent.type(input, '  Tên mới  ');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(callsWith(fetchSpy, 'PATCH')).toHaveLength(1));
    const [, init] = callsWith(fetchSpy, 'PATCH')[0] ?? [];
    expect(JSON.parse(String(init?.body))).toEqual({ title: 'Tên mới' });
    // Khung đóng lại sau khi lưu.
    await waitFor(() => expect(screen.queryByLabelText('Tên phiên')).not.toBeInTheDocument());
  });

  it('nút Lưu tắt khi tên không đổi hoặc để trống — không gọi API vô ích', async () => {
    const fetchSpy = mockChat();
    renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Đổi tên phiên' }));
    const save = screen.getByRole('button', { name: 'Lưu' });
    expect(save).toBeDisabled();

    await userEvent.clear(screen.getByLabelText('Tên phiên'));
    await userEvent.type(screen.getByLabelText('Tên phiên'), '   ');
    expect(save).toBeDisabled();
    expect(callsWith(fetchSpy, 'PATCH')).toHaveLength(0);
  });

  it('backend từ chối thì hiện đúng lý do và giữ ô nhập', async () => {
    mockChat({
      '/api/v1/conversations/c-1': {
        status: 400,
        body: {
          title: 'Dữ liệu không hợp lệ',
          detail: 'title: kích thước phải nằm trong khoảng 0 đến 200',
        },
      },
    });
    renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Đổi tên phiên' }));
    await userEvent.type(screen.getByLabelText('Tên phiên'), ' thêm');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('kích thước phải nằm trong khoảng');
    expect(screen.getByLabelText('Tên phiên')).toBeInTheDocument();
  });
});

describe('xoá phiên', () => {
  it('bấm xoá chỉ hiện khung xác nhận, CHƯA gọi API', async () => {
    const fetchSpy = mockChat();
    renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Xoá phiên này' }));

    expect(screen.getByText(/không khôi phục được từ đây/)).toBeInTheDocument();
    expect(callsWith(fetchSpy, 'DELETE')).toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: 'Huỷ' }));
    expect(screen.queryByText(/không khôi phục được từ đây/)).not.toBeInTheDocument();
    expect(callsWith(fetchSpy, 'DELETE')).toHaveLength(0);
  });

  it('xác nhận thì gọi DELETE đúng phiên và bỏ phiên khỏi URL', async () => {
    const fetchSpy = mockChat({ '/api/v1/conversations/c-1': { status: 204 } });
    const { router } = renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Xoá phiên này' }));
    await userEvent.click(screen.getByRole('button', { name: 'Xoá phiên' }));

    await waitFor(() => expect(callsWith(fetchSpy, 'DELETE')).toHaveLength(1));
    await waitFor(() => expect(router.state.location.search).not.toContain('conversation='));
    expect(await screen.findByText('Chưa có phiên nào được chọn.')).toBeInTheDocument();
  });

  it('backend từ chối thì hiện lý do và giữ khung xác nhận', async () => {
    mockChat({
      '/api/v1/conversations/c-1': {
        status: 404,
        body: { title: 'Không tìm thấy', detail: 'Phiên không còn tồn tại' },
      },
    });
    renderRoute(CHAT_PATH);

    await userEvent.click(await screen.findByRole('button', { name: 'Xoá phiên này' }));
    await userEvent.click(screen.getByRole('button', { name: 'Xoá phiên' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Phiên không còn tồn tại');
    expect(screen.getByRole('button', { name: 'Xoá phiên' })).toBeInTheDocument();
  });

  it('chưa chọn phiên nào thì hai nút bị tắt', async () => {
    mockChat();
    renderRoute('/snapshots/s-1/runs/r-1/chat');

    expect(await screen.findByRole('button', { name: 'Đổi tên phiên' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Xoá phiên này' })).toBeDisabled();
  });
});
