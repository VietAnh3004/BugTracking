package vn.hust.datn.evidence.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Công thức Cohen's kappa, kiểm trên ví dụ có đáp án tính tay — con số này đi thẳng vào chương 4
 * của báo cáo, nên sai một dấu là sai một kết luận.
 */
class InterRaterTest {

    private static List<String[]> pairs(String a, String b, int times, List<String[]> into) {
        for (int i = 0; i < times; i++) {
            into.add(new String[] {a, b});
        }
        return into;
    }

    /**
     * Ví dụ kinh điển 50 mục: cùng TP 20, A=TP/B=FP 5, A=FP/B=TP 10, cùng FP 15.
     * p_o = 35/50 = 0.7; p_e = 0.5·0.6 + 0.5·0.4 = 0.5; kappa = (0.7 − 0.5)/(1 − 0.5) = 0.4.
     */
    @Test
    void viDuTinhTay_kappaBang0_4() {
        List<String[]> data = new ArrayList<>();
        pairs("TRUE_POSITIVE", "TRUE_POSITIVE", 20, data);
        pairs("TRUE_POSITIVE", "FALSE_POSITIVE", 5, data);
        pairs("FALSE_POSITIVE", "TRUE_POSITIVE", 10, data);
        pairs("FALSE_POSITIVE", "FALSE_POSITIVE", 15, data);

        RunMetricsService.InterRater result = RunMetricsService.InterRater.of(data);

        assertThat(result.pairs()).isEqualTo(50);
        assertThat(result.agreement()).isCloseTo(0.7, within(1e-9));
        assertThat(result.kappa()).isCloseTo(0.4, within(1e-9));
    }

    /** Nhãn UNCERTAIN là một loại riêng — người này "không chắc", người kia "TP" là KHÔNG đồng ý. */
    @Test
    void uncertainLaMotLoaiRieng() {
        List<String[]> data = new ArrayList<>();
        pairs("UNCERTAIN", "TRUE_POSITIVE", 1, data);
        pairs("TRUE_POSITIVE", "TRUE_POSITIVE", 1, data);

        assertThat(RunMetricsService.InterRater.of(data).agreement()).isEqualTo(0.5);
    }

    @Test
    void khongCoCapNao_tatCaDeTrong() {
        RunMetricsService.InterRater result = RunMetricsService.InterRater.of(List.of());

        assertThat(result.pairs()).isZero();
        assertThat(result.agreement()).isNull();
        assertThat(result.kappa()).isNull();
    }
}
