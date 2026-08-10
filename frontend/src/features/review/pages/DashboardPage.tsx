/**
 * Dashboard chỉ số của một lần phân tích — chỗ duy nhất đọc được cả bốn nhóm số của đồ án cạnh
 * nhau: chất lượng trích dẫn, đối chiếu người ↔ Agent, ground truth, và chi phí.
 *
 * Nguyên tắc xuyên suốt màn hình này: **tỉ lệ chưa tính được hiện dấu gạch, không phải `0%`**.
 * Backend cố tình trả `null` thay vì `0` cho mọi tỉ lệ có mẫu số 0 (xem `RunMetricsResponse`), và
 * nếu giao diện quy nó về `0%` thì công sức đó đổ sông — "chưa ai review" sẽ đọc thành "review hết
 * mà không khớp gì". Cùng một triết lý với "rỗng là một kết luận" của tuần 10-11.
 *
 * Container duy nhất: gọi API rồi truyền số đã định dạng xuống, mọi component con thuần trình bày.
 */

import { Link, useParams } from 'react-router-dom';

import { ExportButtons } from '@/features/review/components/ExportButtons';
import { Metric, MetricCard } from '@/features/review/components/MetricCard';
import { useRunMetrics } from '@/features/review/hooks/useReview';
import { formatCount, formatDecimal, formatRatio } from '@/features/review/lib/formatMetric';
import { ApiError } from '@/shared/lib/apiClient';
import { FullPageSpinner } from '@/shared/ui/FullPageSpinner';
import { StatusBadge } from '@/shared/ui/StatusBadge';

/** Bốn kết cục của CitationValidator, giữ nguyên thứ tự "tốt → xấu" để đọc thành một dãy. */
const RESOLUTIONS = ['RESOLVED', 'WRONG_RUN', 'UNKNOWN_ID', 'MALFORMED'] as const;

const DECISIONS = [
  { key: 'ACCEPT', label: 'Đúng là lỗi' },
  { key: 'REJECT', label: 'Báo nhầm' },
  { key: 'UNCERTAIN', label: 'Chưa quyết' },
  { key: 'INSUFFICIENT_EVIDENCE', label: 'Thiếu bằng chứng' },
] as const;

export function DashboardPage() {
  const params = useParams<{ snapshotId: string; runId: string }>();
  const snapshotId = params.snapshotId ?? '';
  const runId = params.runId ?? '';

  const { data, isPending, isError, error } = useRunMetrics(runId);

  if (isPending) {
    return <FullPageSpinner label="Đang tính chỉ số…" />;
  }
  if (isError) {
    return <MetricsLoadError error={error} />;
  }

  const { citationQuality: citation, reviewProgress: review, groundTruth, agentCost } = data;

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link
            to={`/snapshots/${snapshotId}/runs/${runId}/warnings`}
            className="text-brand text-sm"
          >
            ← Về danh sách cảnh báo
          </Link>
          <h1 className="mt-1 mb-0 text-xl font-semibold">Chỉ số của lần phân tích</h1>
        </div>
        <ExportButtons runId={runId} />
      </header>

      <div className="grid gap-4 lg:grid-cols-2">
        <MetricCard
          title="Chất lượng trích dẫn"
          note="Mỗi nhận định của Agent phải gắn Evidence ID kiểm chứng được. Hai tỉ lệ dưới đây là thước đo trung tâm của đồ án."
        >
          <Metric
            label="Unsupported Claim Rate"
            value={formatRatio(citation.unsupportedClaimRate)}
            hint="Tỉ lệ nhận định không gắn được trích dẫn nào hợp lệ. Càng thấp càng tốt."
          />
          <Metric
            label="Evidence Citation Accuracy"
            value={formatRatio(citation.citationAccuracy)}
            hint="Tỉ lệ lượt trích dẫn giải quyết được về đúng bằng chứng của chính lần phân tích này."
          />
          <Metric label="Số câu trả lời" value={formatCount(citation.answerCount)} />
          <Metric
            label="Nhận định / không căn cứ"
            value={`${formatCount(citation.claimCount)} / ${formatCount(citation.unsupportedClaimCount)}`}
          />
          <Metric
            label="Bị hạ cấp verdict"
            value={formatCount(citation.downgradedAnswerCount)}
            hint="Số câu trả lời bị CitationValidator hạ xuống INSUFFICIENT_EVIDENCE."
          />
          <Metric label="Tổng lượt trích dẫn" value={formatCount(citation.citationCount)} />
        </MetricCard>

        <MetricCard
          title="Phân bố kết cục trích dẫn"
          note="Bốn kết cục của CitationValidator. Ba loại sau đều là trích dẫn hỏng, nhưng hỏng theo ba cách khác nhau nên không gộp."
        >
          {RESOLUTIONS.map((resolution) => (
            <Metric
              key={resolution}
              label={resolution}
              // Kết cục không xuất hiện lần nào là `0` THẬT, không phải "chưa biết" — khoá vắng
              // mặt chỉ vì SQL nhóm theo giá trị có thật.
              value={formatCount(citation.citationsByResolution[resolution] ?? 0)}
            />
          ))}
        </MetricCard>

        <MetricCard
          title="Đối chiếu người ↔ Agent"
          note="Nhóm có và không dùng Agent để RIÊNG: đó là biến thực nghiệm của user study, gộp lại là mất luôn nhóm đối chứng."
        >
          <Metric
            label="Đã review"
            value={`${formatCount(review.reviewedCount)} / ${formatCount(review.warningCount)}`}
          />
          <Metric
            label="Human acceptance rate"
            value={formatRatio(review.agreementWithAgent)}
            hint="Tỉ lệ quyết định của người khớp verdict của Agent, tính trên các review có đọc Agent."
          />
          <Metric
            label="Có dùng Agent"
            value={formatCount(review.withAgentCount)}
            hint="Người dùng đọc câu trả lời của Agent trước khi quyết."
          />
          <Metric
            label="Nhóm đối chứng"
            value={formatCount(review.withoutAgentCount)}
            hint="Quyết định mà không mở hội thoại với Agent."
          />
          {DECISIONS.map((decision) => (
            <Metric
              key={decision.key}
              label={decision.label}
              value={formatCount(review.decisionCounts[decision.key] ?? 0)}
            />
          ))}
        </MetricCard>

        <MetricCard
          title="Ground truth"
          note="Nhãn chuẩn do người gán sau khi đọc mã. Chưa nạp ở tuần 14 — đây là món nợ có chủ đích của tuần 15."
        >
          <Metric label="Cảnh báo đã gán nhãn" value={formatCount(groundTruth.labelledCount)} />
          <Metric
            label="Agent đúng"
            value={formatRatio(groundTruth.agentAccuracy)}
            hint="Tỉ lệ verdict của Agent khớp nhãn chuẩn."
          />
          <Metric
            label="Người đúng"
            value={formatRatio(groundTruth.humanAccuracy)}
            hint="Tỉ lệ quyết định của người khớp nhãn chuẩn."
          />
        </MetricCard>

        <MetricCard
          title="Chi phí Agent"
          note="Đọc từ llm_trace. Số lượt hỏng đứng cạnh chi phí có chủ đích: một Agent rẻ vì hay chết không phải là một Agent rẻ."
        >
          <Metric label="Lượt gọi model" value={formatCount(agentCost.modelCallCount)} />
          <Metric label="Lượt gọi tool" value={formatCount(agentCost.toolCallCount)} />
          <Metric label="Token vào" value={formatCount(agentCost.inputTokens)} />
          <Metric label="Token ra" value={formatCount(agentCost.outputTokens)} />
          <Metric
            label="Độ trễ trung bình"
            value={formatDecimal(agentCost.averageLatencyMs, ' ms')}
          />
          <Metric
            label="Số vòng lặp / lượt hỏi"
            value={formatDecimal(agentCost.averageIterationsPerAnswer)}
            hint="Số vòng gọi tool trung bình để trả lời một câu hỏi."
          />
          <Metric
            label="Lượt hỏi hỏng"
            value={formatCount(agentCost.failedTurnCount)}
            hint="Gọi model xong nhưng không sinh ra câu trả lời nào."
          />
        </MetricCard>
      </div>
    </section>
  );
}

function MetricsLoadError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.status === 409) {
    // Cùng lý do với màn hình cảnh báo: 409 nghĩa là "chưa chạy xong", khác hẳn "không có số liệu".
    return (
      <StatusBadge
        tone="warning"
        label="Lần phân tích này chưa hoàn tất nên chưa có chỉ số để xem."
      />
    );
  }
  return <StatusBadge tone="danger" label="Không tải được chỉ số của lần phân tích." />;
}
