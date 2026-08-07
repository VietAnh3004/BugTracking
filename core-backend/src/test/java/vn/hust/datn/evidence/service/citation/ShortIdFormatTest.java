package vn.hust.datn.evidence.service.citation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Thuần hàm, không Spring — ranh giới giữa "có hình dạng Evidence ID không" và "có tồn tại không".
 *
 * <p>Test này canh một quyết định dễ bị xói mòn: hàm chuẩn hoá chỉ được bỏ khoảng trắng và viết
 * hoa. Mỗi lần ai đó thêm một phép "dọn dẹp" tử tế (gỡ ngoặc, gỡ dấu chấm câu, chấp nhận thiếu
 * tiền tố) là một lần Evidence Citation Accuracy đo lòng khoan dung của hàm này thay vì đo mức bám
 * bằng chứng của mô hình.
 */
class ShortIdFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"EV-0001", "EV-ABCD", "EV-FFFF", "EV-01A2"})
    void dungKhuonDang(String raw) {
        assertThat(ShortIdFormat.isWellFormed(raw)).isTrue();
    }

    /** Khoảng trắng thừa quanh ID là lỗi gõ, không phải lỗi bám bằng chứng. */
    @Test
    void boKhoangTrangThua() {
        assertThat(ShortIdFormat.canonical("  EV-01A2 ")).contains("EV-01A2");
    }

    /** Viết thường vẫn trỏ đúng một bằng chứng duy nhất — hạ cấp vì phím Shift là đo nhầm thứ. */
    @Test
    void chapNhanVietThuong() {
        assertThat(ShortIdFormat.canonical("ev-01a2")).contains("EV-01A2");
    }

    /**
     * Bốn ca dưới đây phải là {@code MALFORMED}, KHÔNG phải "gần đúng rồi sửa hộ".
     *
     * <ul>
     *   <li>{@code EV-ZZZZ} — Z không phải hex, tức mô hình không lấy ID từ prompt mà tự chế.
     *   <li>{@code EV-123} — thiếu ký tự băm, đúng thứ mà ký tự đó sinh ra để chặn.
     *   <li>{@code [EV-01A2]} — gỡ ngoặc hộ thì hàm này bắt đầu đoán ý mô hình.
     *   <li>{@code 01A2} — thiếu tiền tố.
     * </ul>
     */
    @ParameterizedTest
    @ValueSource(strings = {"EV-ZZZZ", "EV-123", "EV-012345", "[EV-01A2]", "`EV-01A2`", "01A2", "bang chung so 3", ""})
    void saiKhuonDang(String raw) {
        assertThat(ShortIdFormat.isWellFormed(raw)).isFalse();
        assertThat(ShortIdFormat.canonical(raw)).isEmpty();
    }

    @Test
    void chuoiNullKhongNemLoi() {
        assertThat(ShortIdFormat.isWellFormed(null)).isFalse();
        assertThat(ShortIdFormat.normalize(null)).isEmpty();
    }
}
