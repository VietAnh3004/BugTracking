package vn.hust.datn.evidence.service.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.ExperimentConfig;
import vn.hust.datn.evidence.dto.ExperimentStatusResponse.ConfigMetrics;

/** Từng định nghĩa chỉ số của thực nghiệm trên dữ liệu dựng tay (docs/thuc-nghiem.md). */
class ExperimentMetricsTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static ExperimentMetrics.Row row(UUID warning, int repeat, String verdict, String truth) {
        return new ExperimentMetrics.Row(
                warning, ExperimentConfig.LLM_CODE, repeat, true, verdict, truth, 2, 1, 4, 3, 1, 0, 100, 20, 500);
    }

    private static ConfigMetrics llmCode(List<ExperimentMetrics.Row> rows, int repeats) {
        return ExperimentMetrics.compute(rows, repeats).stream()
                .filter(m -> m.config() == ExperimentConfig.LLM_CODE)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void insufficientTinhLaSaiNhungBiBoRaKhoiDoChinhXacTrenOCoKetLuan() {
        ConfigMetrics m = llmCode(
                List.of(
                        row(A, 1, "LIKELY_TRUE_POSITIVE", "TRUE_POSITIVE"),
                        row(B, 1, "INSUFFICIENT_EVIDENCE", "FALSE_POSITIVE")),
                1);

        assertThat(m.accuracy()).isEqualTo(0.5);
        assertThat(m.decidedAccuracy()).isEqualTo(1.0);
        assertThat(m.insufficientRate()).isEqualTo(0.5);
    }

    @Test
    void oKhongCoNhanTpFpKhongVaoMauSoDoChinhXac() {
        ConfigMetrics m = llmCode(
                List.of(
                        row(A, 1, "LIKELY_FALSE_POSITIVE", "FALSE_POSITIVE"),
                        row(B, 1, "LIKELY_TRUE_POSITIVE", null),
                        row(B, 1, "LIKELY_TRUE_POSITIVE", "UNCERTAIN")),
                1);

        assertThat(m.accuracy()).isEqualTo(1.0);
        assertThat(m.succeededCells()).isEqualTo(3);
    }

    @Test
    void doLechChuanQuaCacLanLapVaDoOnDinh() {
        List<ExperimentMetrics.Row> rows = new ArrayList<>();
        // Lần 1: đúng cả hai; lần 2: đúng một; lần 3: chưa chạy.
        rows.add(row(A, 1, "LIKELY_TRUE_POSITIVE", "TRUE_POSITIVE"));
        rows.add(row(B, 1, "LIKELY_FALSE_POSITIVE", "FALSE_POSITIVE"));
        rows.add(row(A, 2, "LIKELY_TRUE_POSITIVE", "TRUE_POSITIVE"));
        rows.add(row(B, 2, "LIKELY_TRUE_POSITIVE", "FALSE_POSITIVE"));

        ConfigMetrics m = llmCode(rows, 3);

        assertThat(m.accuracyByRepeat()).containsExactly(1.0, 0.5, null);
        // Độ lệch chuẩn mẫu của {1.0, 0.5}: |1.0 − 0.5| / √2.
        assertThat(m.accuracyStdDev()).isCloseTo(0.5 / Math.sqrt(2), within(1e-9));
        // Chưa cảnh báo nào đủ 3 lần lặp: ổn định chưa tính được, KHÔNG phải 100%.
        assertThat(m.stability()).isNull();

        rows.add(row(A, 3, "LIKELY_TRUE_POSITIVE", "TRUE_POSITIVE"));
        rows.add(row(B, 3, "LIKELY_FALSE_POSITIVE", "FALSE_POSITIVE"));
        ConfigMetrics full = llmCode(rows, 3);

        assertThat(full.stability()).isEqualTo(0.5); // A ổn định, B đổi verdict giữa các lần
    }

    @Test
    void chuaCoOThiMoiTiLeLaNullKhongPhaiKhong() {
        ConfigMetrics m = llmCode(List.of(), 3);

        assertThat(m.accuracy()).isNull();
        assertThat(m.insufficientRate()).isNull();
        assertThat(m.unsupportedClaimRate()).isNull();
        assertThat(m.avgLlmCalls()).isNull();
        assertThat(m.accuracyStdDev()).isNull();
        assertThat(m.accuracyByRepeat()).containsExactly(null, null, null);
    }

    @Test
    void tiLeTrichDanVaTrungBinhChiPhi() {
        ConfigMetrics m = llmCode(List.of(row(A, 1, "LIKELY_TRUE_POSITIVE", "TRUE_POSITIVE")), 1);

        assertThat(m.unsupportedClaimRate()).isEqualTo(0.5);
        assertThat(m.citationResolvedRate()).isEqualTo(0.75);
        assertThat(m.avgInputTokens()).isEqualTo(100.0);
        assertThat(m.avgLatencyMs()).isEqualTo(500.0);
    }
}
