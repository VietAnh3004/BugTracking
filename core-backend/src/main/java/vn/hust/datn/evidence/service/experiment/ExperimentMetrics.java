package vn.hust.datn.evidence.service.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import vn.hust.datn.evidence.domain.ExperimentConfig;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse.ConfigMetrics;

/**
 * Chỉ số của thực nghiệm 3 cấu hình, tính từ các ô đã ghi. Thuần tính toán, không đụng DB — để test
 * được từng định nghĩa bằng dữ liệu dựng tay (định nghĩa: {@code docs/thuc-nghiem.md}).
 *
 * <p>Mẫu số bằng 0 cho {@code null}, không phải 0 — cùng quy ước với {@code RunMetricsService}.
 */
final class ExperimentMetrics {

    private static final String INSUFFICIENT = "INSUFFICIENT_EVIDENCE";

    private ExperimentMetrics() {}

    /**
     * Một ô đã ghi. {@code groundTruth} là nhãn MANUAL ({@code TRUE_POSITIVE}/{@code FALSE_POSITIVE}/
     * {@code UNCERTAIN}) hoặc {@code null} khi chưa gán.
     */
    record Row(
            UUID evidenceRecordId,
            ExperimentConfig config,
            int repeat,
            boolean succeeded,
            String verdict,
            String groundTruth,
            int claimCount,
            int unsupportedClaimCount,
            int citationCount,
            int citationResolvedCount,
            int llmCalls,
            int toolCalls,
            int inputTokens,
            int outputTokens,
            int latencyMs) {

        /** Có nhãn TP/FP để chấm. UNCERTAIN hay chưa gán thì không vào mẫu số độ chính xác. */
        boolean labeled() {
            return "TRUE_POSITIVE".equals(groundTruth) || "FALSE_POSITIVE".equals(groundTruth);
        }

        /** Khớp nhãn. INSUFFICIENT_EVIDENCE luôn sai — giống {@code agentAccuracy} của dashboard. */
        boolean correct() {
            return ("LIKELY_TRUE_POSITIVE".equals(verdict) && "TRUE_POSITIVE".equals(groundTruth))
                    || ("LIKELY_FALSE_POSITIVE".equals(verdict) && "FALSE_POSITIVE".equals(groundTruth));
        }
    }

    static List<ConfigMetrics> compute(List<Row> rows, int repeats) {
        List<ConfigMetrics> result = new ArrayList<>();
        for (ExperimentConfig config : ExperimentConfig.values()) {
            List<Row> ofConfig =
                    rows.stream().filter(r -> r.config() == config).toList();
            List<Row> ok = ofConfig.stream().filter(Row::succeeded).toList();
            List<Row> labeled = ok.stream().filter(Row::labeled).toList();
            List<Row> decided = labeled.stream()
                    .filter(r -> !INSUFFICIENT.equals(r.verdict()))
                    .toList();

            List<Double> byRepeat = new ArrayList<>();
            for (int repeat = 1; repeat <= repeats; repeat++) {
                int current = repeat;
                List<Row> inRepeat =
                        labeled.stream().filter(r -> r.repeat() == current).toList();
                byRepeat.add(ratio(countCorrect(inRepeat), inRepeat.size()));
            }

            result.add(new ConfigMetrics(
                    config,
                    ok.size(),
                    ofConfig.size() - ok.size(),
                    ratio(countCorrect(labeled), labeled.size()),
                    sampleStdDev(byRepeat),
                    byRepeat,
                    ratio(countCorrect(decided), decided.size()),
                    ratio(ok.stream().filter(r -> INSUFFICIENT.equals(r.verdict())).count(), ok.size()),
                    ratio(sum(ok, Row::unsupportedClaimCount), sum(ok, Row::claimCount)),
                    ratio(sum(ok, Row::citationResolvedCount), sum(ok, Row::citationCount)),
                    stability(ok, repeats),
                    average(ok, Row::llmCalls),
                    average(ok, Row::toolCalls),
                    average(ok, Row::inputTokens),
                    average(ok, Row::outputTokens),
                    average(ok, Row::latencyMs)));
        }
        return result;
    }

    /**
     * Tỉ lệ cảnh báo cho CÙNG một verdict ở mọi lần lặp. Chỉ tính cảnh báo đã đủ {@code repeats} ô
     * thành công — cảnh báo mới chạy một lần thì "ổn định" một cách tầm thường.
     */
    static Double stability(List<Row> ok, int repeats) {
        if (repeats < 2) {
            return null;
        }
        Map<UUID, List<Row>> byWarning = ok.stream().collect(Collectors.groupingBy(Row::evidenceRecordId));
        long complete = 0;
        long stable = 0;
        for (List<Row> cells : byWarning.values()) {
            if (cells.size() < repeats) {
                continue;
            }
            complete++;
            if (cells.stream().map(Row::verdict).distinct().count() == 1) {
                stable++;
            }
        }
        return ratio(stable, complete);
    }

    /** Độ lệch chuẩn mẫu (n − 1) qua các lần lặp CÓ dữ liệu; dưới 2 lần thì không tính được. */
    static Double sampleStdDev(List<Double> values) {
        List<Double> present = values.stream().filter(v -> v != null).toList();
        if (present.size() < 2) {
            return null;
        }
        double mean = present.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double squares = present.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(squares / (present.size() - 1));
    }

    private static long countCorrect(List<Row> rows) {
        return rows.stream().filter(Row::correct).count();
    }

    private static long sum(List<Row> rows, ToIntFunction<Row> field) {
        return rows.stream().mapToLong(field::applyAsInt).sum();
    }

    private static Double average(List<Row> rows, ToIntFunction<Row> field) {
        return rows.isEmpty() ? null : rows.stream().mapToInt(field).average().orElse(0);
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
