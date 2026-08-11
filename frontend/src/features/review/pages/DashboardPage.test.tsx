/**
 * Ca quan trọng nhất của dashboard KHÔNG phải "hiện đủ số" mà là **trạng thái chưa có số liệu**:
 * một lần phân tích chưa ai review, chưa hỏi Agent lần nào, chưa gán nhãn ground truth. Đó chính
 * là trạng thái mà người chấm gặp đầu tiên khi mở demo.
 *
 * Nếu màn hình đó hiện `0%`, mọi kết luận đọc được từ nó đều sai theo hướng nguy hiểm nhất
 * ("Agent trích dẫn sai 100%") trong khi sự thật chỉ là "chưa có dữ liệu".
 */

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DashboardPage } from '@/features/review/pages/DashboardPage';
import { createTestQueryClient } from '@/shared/lib/queryClient';

const getRunMetricsMock = vi.hoisted(() => vi.fn());

vi.mock('@/features/review/api/reviewApi', () => ({
  getRunMetrics: getRunMetricsMock,
  listReviews: vi.fn(),
  submitReview: vi.fn(),
}));

/**
 * Các tỉ lệ để `null` TƯỜNG MINH chứ không bỏ khoá. Nay backend đã bỏ hẳn khoá null
 * (`jackson.default-property-inclusion: non_null`) nên dữ liệu thật là `undefined`, nhưng bộ test
 * cố ý giữ dạng CŨ: nó là chốt chặn nếu cấu hình đó bị gỡ. Ép kiểu ở đây là chủ ý — mục đích là
 * chạy trên dữ liệu mà runtime có thể gửi, không phải dữ liệu mà trình biên dịch tin là có thể.
 */
function emptyMetrics() {
  return {
    analysisRunId: 'run-1',
    citationQuality: {
      answerCount: 0,
      claimCount: 0,
      unsupportedClaimCount: 0,
      unsupportedClaimRate: null,
      citationCount: 0,
      citationsByResolution: {},
      citationAccuracy: null,
      downgradedAnswerCount: 0,
    },
    reviewProgress: {
      warningCount: 34,
      reviewedCount: 0,
      decisionCounts: {},
      withAgentCount: 0,
      withoutAgentCount: 0,
      agreementWithAgent: null,
    },
    groundTruth: { labelledCount: 0, agentAccuracy: null, humanAccuracy: null },
    agentCost: {
      modelCallCount: 0,
      inputTokens: 0,
      outputTokens: 0,
      toolCallCount: 0,
      averageLatencyMs: null,
      averageIterationsPerAnswer: null,
      failedTurnCount: 0,
    },
  };
}

function renderDashboard() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/snapshots/s1/runs/run-1/dashboard']}>
        <Routes>
          <Route path="/snapshots/:snapshotId/runs/:runId/dashboard" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('DashboardPage', () => {
  it('chưa có dữ liệu thì mọi tỉ lệ hiện dấu gạch, tuyệt đối không có 0%', async () => {
    getRunMetricsMock.mockResolvedValue(emptyMetrics());
    renderDashboard();

    expect(await screen.findByText('Unsupported Claim Rate')).toBeInTheDocument();

    // Sáu tỉ lệ: unsupported, accuracy, agreement, agentAccuracy, humanAccuracy + 2 số thực
    // (latency, iterations) — tất cả đều phải là dấu gạch.
    expect(screen.getAllByText('—')).toHaveLength(7);
    expect(screen.queryByText(/0\.0%/)).not.toBeInTheDocument();
  });

  /** Số đếm KHÁC tỉ lệ: `0 / 34` là một phép đo thật và phải hiện thành số. */
  it('số đếm vẫn hiện là 0, không bị gộp chung vào "chưa có"', async () => {
    getRunMetricsMock.mockResolvedValue(emptyMetrics());
    renderDashboard();

    expect(await screen.findByText('0 / 34')).toBeInTheDocument();
  });

  /**
   * `citationsByResolution` chỉ chứa các kết cục THỰC SỰ xuất hiện (SQL nhóm theo giá trị có
   * thật). Bốn kết cục vẫn phải hiện đủ, kết cục vắng mặt là `0` — thiếu một cột thì không ai
   * nhận ra `MALFORMED` chưa bao giờ xảy ra hay đơn giản là bị quên hiển thị.
   */
  it('hiện đủ bốn kết cục trích dẫn kể cả kết cục chưa xảy ra lần nào', async () => {
    getRunMetricsMock.mockResolvedValue({
      ...emptyMetrics(),
      citationQuality: {
        ...emptyMetrics().citationQuality,
        citationCount: 5,
        citationsByResolution: { RESOLVED: 4, UNKNOWN_ID: 1 },
        citationAccuracy: 0.8,
      },
    });
    renderDashboard();

    expect(await screen.findByText('RESOLVED')).toBeInTheDocument();
    expect(screen.getByText('WRONG_RUN')).toBeInTheDocument();
    expect(screen.getByText('UNKNOWN_ID')).toBeInTheDocument();
    expect(screen.getByText('MALFORMED')).toBeInTheDocument();
    expect(screen.getByText('80.0%')).toBeInTheDocument();
  });

  /**
   * Nhóm dùng Agent và nhóm đối chứng phải đọc được TÁCH BIỆT trên màn hình: đó là biến thực
   * nghiệm của user study, và gộp chúng là mất luôn khả năng so sánh hai nhóm.
   */
  it('tách riêng nhóm có dùng Agent và nhóm đối chứng', async () => {
    getRunMetricsMock.mockResolvedValue({
      ...emptyMetrics(),
      reviewProgress: {
        ...emptyMetrics().reviewProgress,
        reviewedCount: 7,
        decisionCounts: { ACCEPT: 5, REJECT: 2 },
        withAgentCount: 4,
        withoutAgentCount: 3,
        agreementWithAgent: 0.75,
      },
    });
    renderDashboard();

    expect(await screen.findByText('Có dùng Agent')).toBeInTheDocument();
    expect(screen.getByText('Nhóm đối chứng')).toBeInTheDocument();
    expect(screen.getByText('75.0%')).toBeInTheDocument();
  });
});
