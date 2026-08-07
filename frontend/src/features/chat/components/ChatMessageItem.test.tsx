/**
 * Hiển thị một lượt trả lời của Agent.
 *
 * Ba điều đang được canh, và cả ba đều là luận điểm của đồ án chứ không phải chi tiết giao diện:
 * trích dẫn hợp lệ phải BẤM ĐƯỢC sang đúng bằng chứng, trích dẫn hỏng vẫn phải HIỆN RA, và việc
 * verdict bị hạ cấp phải nhìn thấy được.
 */

import { describe, expect, it } from 'vitest';

import { MemoryRouter } from 'react-router-dom';

import type { ChatMessage } from '@/features/chat/api/chatApi';
import { ChatMessageItem } from '@/features/chat/components/ChatMessageItem';

import { render, screen } from '@testing-library/react';

const SNAPSHOT_ID = 'snap-1';
const RUN_ID = 'run-1';

function reply(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'msg-1',
    ordinal: 1,
    role: 'ASSISTANT',
    content: 'Tóm tắt của Agent',
    verdict: 'LIKELY_TRUE_POSITIVE',
    suggestedNextChecks: [],
    contradictingShortIds: [],
    claims: [],
    createdAt: '2026-10-20T10:00:00Z',
    ...overrides,
  };
}

function show(message: ChatMessage) {
  return render(
    <MemoryRouter>
      <ul>
        <ChatMessageItem message={message} snapshotId={SNAPSHOT_ID} runId={RUN_ID} />
      </ul>
    </MemoryRouter>,
  );
}

describe('ChatMessageItem', () => {
  it('trích dẫn hợp lệ là liên kết mở đúng bằng chứng', () => {
    show(
      reply({
        claims: [
          {
            ordinal: 0,
            text: 'Dòng 66 giải tham chiếu giá trị có thể null',
            verdict: 'LIKELY_TRUE_POSITIVE',
            citations: [{ citedShortIdRaw: 'EV-001A', resolution: 'RESOLVED', shortId: 'EV-001A' }],
          },
        ],
      }),
    );

    expect(screen.getByRole('link', { name: 'EV-001A' })).toHaveAttribute(
      'href',
      `/snapshots/${SNAPSHOT_ID}/runs/${RUN_ID}/evidence/EV-001A`,
    );
  });

  /**
   * Trích dẫn bịa KHÔNG được biến mất, và cũng không được thành liên kết.
   *
   * Giấu đi thì giao diện sạch sẽ trong khi Evidence Citation Accuracy mất mẫu số; còn tạo liên
   * kết tới một ID không tồn tại thì đưa người dùng tới một trang 404 và làm họ tưởng hệ thống hỏng.
   */
  it('trích dẫn bịa vẫn hiện ra nhưng không bấm được', () => {
    show(
      reply({
        verdict: 'INSUFFICIENT_EVIDENCE',
        claims: [
          {
            ordinal: 0,
            text: 'Một nhận định không có căn cứ',
            verdict: 'INSUFFICIENT_EVIDENCE',
            downgradedFromVerdict: 'LIKELY_FALSE_POSITIVE',
            citations: [{ citedShortIdRaw: 'EV-FFFF', resolution: 'UNKNOWN_ID' }],
          },
        ],
      }),
    );

    expect(screen.getByText('EV-FFFF')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /EV-FFFF/ })).not.toBeInTheDocument();
    expect(screen.getByText(/không tồn tại/)).toBeInTheDocument();
  });

  it('việc hạ cấp verdict hiện ra chứ không bị giấu', () => {
    show(
      reply({ verdict: 'INSUFFICIENT_EVIDENCE', downgradedFromVerdict: 'LIKELY_TRUE_POSITIVE' }),
    );

    expect(screen.getByText('Chưa đủ bằng chứng')).toBeInTheDocument();
    expect(screen.getByText(/đã hạ cấp từ/)).toBeInTheDocument();
  });

  it('nhận định không có trích dẫn nào vẫn được giữ lại và đánh dấu', () => {
    show(
      reply({
        claims: [
          {
            ordinal: 0,
            text: 'Nói mà không dẫn nguồn',
            verdict: 'INSUFFICIENT_EVIDENCE',
            downgradedFromVerdict: 'LIKELY_TRUE_POSITIVE',
            citations: [],
          },
        ],
      }),
    );

    expect(screen.getByText('Nói mà không dẫn nguồn')).toBeInTheDocument();
    expect(screen.getByText('Không có trích dẫn nào')).toBeInTheDocument();
  });

  it('bằng chứng phản bác hiện thành liên kết riêng', () => {
    show(reply({ contradictingShortIds: ['EV-00B2'] }));

    expect(
      screen.getByRole('heading', { name: 'Bằng chứng đi ngược lại kết luận' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'EV-00B2' })).toBeInTheDocument();
  });

  it('lượt của người dùng không có verdict hay nhận định', () => {
    show(reply({ role: 'USER', content: 'Câu hỏi của tôi', verdict: undefined }));

    expect(screen.getByText('Câu hỏi của tôi')).toBeInTheDocument();
    expect(screen.queryByText('Chưa đủ bằng chứng')).not.toBeInTheDocument();
  });
});
