package vn.hust.datn.evidence.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.GroundTruthSource;
import vn.hust.datn.evidence.domain.GroundTruthVerdict;
import vn.hust.datn.evidence.exception.InvalidGroundTruthException;

/**
 * File nhãn SEEDED của vulnshop ({@code case-study/ground-truth/vulnshop-seeded.yaml}) phải nạp
 * được, và không được lặp lại lỗi đã gặp: TP-05 và FP-02 từng ghi ĐẢO số dòng trong SEEDED.md suốt
 * từ tuần 2 tới tuần 16, vì không gì đối chiếu tài liệu đó với mã.
 */
class SeededLabelsFileTest {

    private static final Path SEEDED_FILE =
            Path.of("..", "case-study", "ground-truth", "vulnshop-seeded.yaml").normalize();

    private static final String PASSWORD_HASHER =
            "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java";

    @Test
    void fileSeededNapDuoc_vaMoiMucDeuLaSeeded() throws Exception {
        List<GroundTruthService.ImportEntry> entries = GroundTruthService.parse(Files.readAllBytes(SEEDED_FILE));

        // 11 TP có cảnh báo (TP-01..10, TP-16) + 6 FP. TP-11..15 không công cụ nào bắt ⇒ không có mục.
        assertThat(entries).hasSize(17);
        assertThat(entries).allMatch(entry -> entry.source() == GroundTruthSource.SEEDED);
        assertThat(entries).allMatch(entry -> entry.file().startsWith("src/main/java/"));
    }

    /**
     * Cặp TP-05/FP-02: cùng luật, cùng file, nhãn ngược nhau — chính vì vậy đảo dòng là đảo đáp án.
     * Dòng 53 nằm trong {@code md5Hex} (gọi từ {@code hashPassword}), dòng 40 trong
     * {@code checksumForDeduplication}.
     */
    @Test
    void tp05ODong53_fp02ODong40() throws Exception {
        List<GroundTruthService.ImportEntry> entries = GroundTruthService.parse(Files.readAllBytes(SEEDED_FILE));

        assertThat(entries)
                .filteredOn(entry -> entry.file().equals(PASSWORD_HASHER) && entry.line() == 53)
                .singleElement()
                .extracting(GroundTruthService.ImportEntry::label)
                .isEqualTo(GroundTruthVerdict.TRUE_POSITIVE);
        assertThat(entries)
                .filteredOn(entry -> entry.file().equals(PASSWORD_HASHER) && entry.line() == 40)
                .singleElement()
                .extracting(GroundTruthService.ImportEntry::label)
                .isEqualTo(GroundTruthVerdict.FALSE_POSITIVE);
    }

    /** YAML có alias lồng nhau ("billion laughs") bị từ chối thay vì làm nổ bộ nhớ. */
    @Test
    void yamlNhieuAlias_biTuChoi() {
        StringBuilder yaml = new StringBuilder("a: &a [x, x]\n");
        for (int i = 1; i <= 12; i++) {
            yaml.append("a").append(i).append(": &a").append(i).append(" [*a, *a]\n");
        }
        yaml.append("labels: []\n");

        assertThatThrownBy(() -> GroundTruthService.parse(yaml.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidGroundTruthException.class);
    }
}
