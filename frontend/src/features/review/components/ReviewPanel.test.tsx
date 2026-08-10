/**
 * Chốt chặn của panel review: THÂN REQUEST gửi lên đúng những gì backend hiểu.
 *
 * Test này soi thẳng vào body chứ không chỉ kiểm DOM, vì hai trường ở đây không phải trang trí:
 *
 *   - `messageId` là **biến thực nghiệm** của user study (nhóm dùng Agent ↔ nhóm đối chứng). Gửi
 *     thừa hay gửi thiếu một lần là làm nhiễm dữ liệu tuần 16, và trên giao diện thì không nhìn ra.
 *   - `secondsSpent` bị backend ép `CHECK (... > 0)`. Bấm ngay khi vừa mở panel cho ra 0 giây; gửi
 *     số 0 lên là ăn 400 ngay ở thao tác thường gặp nhất.
 *
 * Cả hai đều phải VẮNG MẶT khỏi JSON chứ không phải mang giá trị `null`: OpenAPI khai chúng là
 * tuỳ chọn, và "không có" khác "có, giá trị rỗng".
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ReviewPanel } from '@/features/review/components/ReviewPanel';
import { createTestQueryClient } from '@/shared/lib/queryClient';
import { mockFetchByUrl } from '@/test/mockFetch';

const RUN_ID = '22222222-2222-2222-2222-222222222222';
const MESSAGE_ID = '33333333-3333-3333-3333-333333333333';

function savedReview(overrides: Record<string, unknown> = {}) {
  return {
    id: '44444444-4444-4444-4444-444444444444',
    analysisRunId: RUN_ID,
    shortId: 'EV-001A',
    decision: 'ACCEPT',
    createdAt: '2026-08-10T03:00:00Z',
    updatedAt: '2026-08-10T03:00:00Z',
    ...overrides,
  };
}

/**
 * `/reviews` khai TRƯỚC `/review`: `mockFetchByUrl` khớp theo đoạn con và lấy cái đầu tiên, nên
 * khai ngược lại thì lời gọi danh sách rơi vào nhánh PUT.
 */
function mockReviewApi(existing: unknown[] = []) {
  return mockFetchByUrl({
    '/reviews': { body: existing },
    '/review': { body: savedReview() },
  });
}

function renderPanel(props: { messageId?: string } = {}) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <ReviewPanel runId={RUN_ID} shortId="EV-001A" {...props} />
    </QueryClientProvider>,
  );
}

/** Thân JSON của lời gọi PUT review — chính thứ backend đọc được. */
function submittedBody(fetchSpy: ReturnType<typeof mockReviewApi>): Record<string, unknown> {
  const call = fetchSpy.mock.calls.find(
    ([url, init]) =>
      String(url).endsWith('/review') && (init as RequestInit | undefined)?.method === 'PUT',
  );
  if (call === undefined) {
    throw new Error('không có lời gọi PUT review nào');
  }
  return JSON.parse(String((call[1] as RequestInit).body)) as Record<string, unknown>;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ReviewPanel', () => {
  it('không mở chat thì body KHÔNG mang messageId — đó là nhóm đối chứng', async () => {
    const fetchSpy = mockReviewApi();
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'Đúng là lỗi' }));

    await waitFor(() => {
      const body = submittedBody(fetchSpy);
      expect(body).toEqual({ decision: 'ACCEPT' });
      // Khoá phải VẮNG hẳn, không phải mang giá trị null.
      expect('messageId' in body).toBe(false);
    });
  });

  it('đã đọc câu trả lời Agent thì body mang đúng messageId đó', async () => {
    const fetchSpy = mockReviewApi();
    renderPanel({ messageId: MESSAGE_ID });

    await userEvent.click(await screen.findByRole('button', { name: 'Báo nhầm' }));

    await waitFor(() => {
      expect(submittedBody(fetchSpy).messageId).toBe(MESSAGE_ID);
    });
  });

  /** Bấm ngay khi vừa mở panel: 0 giây là dữ liệu rác, và backend từ chối nó bằng CHECK. */
  it('bấm ngay lập tức thì không gửi secondsSpent', async () => {
    const fetchSpy = mockReviewApi();
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'Chưa quyết được' }));

    await waitFor(() => {
      expect('secondsSpent' in submittedBody(fetchSpy)).toBe(false);
    });
  });

  it('ghi chú chỉ toàn khoảng trắng thì không gửi note', async () => {
    const fetchSpy = mockReviewApi();
    renderPanel();

    await userEvent.type(await screen.findByLabelText('Ghi chú'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Thiếu bằng chứng' }));

    await waitFor(() => {
      expect('note' in submittedBody(fetchSpy)).toBe(false);
    });
  });

  it('ghi chú có nội dung thì gửi lên đã cắt khoảng trắng thừa', async () => {
    const fetchSpy = mockReviewApi();
    renderPanel();

    await userEvent.type(await screen.findByLabelText('Ghi chú'), '  Bien do nguoi dung kiem  ');
    await userEvent.click(screen.getByRole('button', { name: 'Đúng là lỗi' }));

    await waitFor(() => {
      expect(submittedBody(fetchSpy).note).toBe('Bien do nguoi dung kiem');
    });
  });

  /**
   * Quyết định cũ phải hiện lại sau khi tải lại trang, nếu không người dùng review lần hai và
   * tưởng lần đầu không ăn — đúng cái mà `UNIQUE (evidence_record_id, reviewer_id)` chống.
   */
  it('quyết định đã ghi hiện lại kèm mốc thời gian và cho biết có dùng Agent hay không', async () => {
    mockReviewApi([savedReview({ decision: 'REJECT', messageId: MESSAGE_ID, note: 'Da doc ky' })]);
    renderPanel();

    expect(await screen.findByText(/sau khi đọc Agent/)).toBeInTheDocument();
    expect(screen.queryByText('Chưa đánh giá')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByLabelText('Ghi chú')).toHaveValue('Da doc ky');
    });
  });

  it('chưa ai đánh giá thì nói rõ là chưa, không để trống', async () => {
    mockReviewApi();
    renderPanel();

    expect(await screen.findByText('Chưa đánh giá')).toBeInTheDocument();
  });
});
