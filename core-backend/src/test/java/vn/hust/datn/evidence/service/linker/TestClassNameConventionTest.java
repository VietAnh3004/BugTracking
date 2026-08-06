package vn.hust.datn.evidence.service.linker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Quy ước đặt tên là suy luận YẾU NHẤT trong ba tầng, nên nó phải sai theo hướng "không tìm thấy"
 * chứ không bao giờ theo hướng "tìm nhầm".
 *
 * <p>Thuần hàm tĩnh: không Spring, không DB.
 */
class TestClassNameConventionTest {

    @Test
    void boHauToTestThongThuong() {
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.FooTest")).contains("vn.hust.app.Foo");
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.FooTests")).contains("vn.hust.app.Foo");
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.FooIT")).contains("vn.hust.app.Foo");
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.FooTestCase")).contains("vn.hust.app.Foo");
    }

    /** Hậu tố xét DÀI TRƯỚC, nếu không {@code FooTests} ra {@code Foos}. */
    @Test
    void hauToDaiXetTruoc() {
        assertThat(TestClassNameConvention.productionClassOf("FooTests")).contains("Foo");
    }

    @Test
    void tienToTestCungNhanDien() {
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.TestFoo")).contains("vn.hust.app.Foo");
    }

    /** Lớp lồng nhau là một NHÓM test của lớp ngoài, không phải một lớp test riêng. */
    @Test
    void lopLongNhauQuyVeLopNgoai() {
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.FooTest$WhenEmpty"))
                .contains("vn.hust.app.Foo");
    }

    @Test
    void khongPhaiLopTest_traVeRong() {
        assertThat(TestClassNameConvention.productionClassOf("vn.hust.app.Foo")).isEmpty();
        assertThat(TestClassNameConvention.productionClassOf("Test")).isEmpty();
        assertThat(TestClassNameConvention.productionClassOf("")).isEmpty();
        assertThat(TestClassNameConvention.productionClassOf(null)).isEmpty();
    }

    /**
     * CA ĐÃ GẶP THẬT, và nó từng làm mất bằng chứng trong im lặng.
     *
     * <p>{@code UserRepositoryIntegrationTest} là lớp phủ đúng dòng SQL injection của
     * {@code findByUsername} — cạm bẫy trung tâm của cả case study. Bỏ hậu tố thôi thì ra
     * {@code UserRepositoryIntegration}, một lớp KHÔNG TỒN TẠI, và toàn bộ bằng chứng kiểm thử
     * của ca đó biến mất mà không có gì báo.
     *
     * <p>Cách chữa: sinh NHIỀU ứng viên và để dữ liệu quyết định. Tên lớp test không đủ để biết
     * ranh giới giữa tên lớp production và mô tả phạm vi.
     */
    @Test
    void tenLopTestCoMoTaPhamVi_sinhCaHaiUngVien() {
        assertThat(TestClassNameConvention.productionClassCandidates(
                        "vn.hust.datn.casestudy.vulnshop.user.UserRepositoryIntegrationTest"))
                .containsExactly(
                        "vn.hust.datn.casestudy.vulnshop.user.UserRepositoryIntegration",
                        "vn.hust.datn.casestudy.vulnshop.user.UserRepository");
    }

    /** Ứng viên xếp CỤ THỂ TRƯỚC: tên đầy đủ vẫn phải được thử trước khi cắt bớt. */
    @Test
    void ungVienXepCuTheTruoc() {
        assertThat(TestClassNameConvention.productionClassCandidates("app.OrderUnitTest"))
                .containsExactly("app.OrderUnit", "app.Order");
    }

    @Test
    void tenKhongCoMoTaPhamVi_chiMotUngVien() {
        assertThat(TestClassNameConvention.productionClassCandidates("app.FooTest")).containsExactly("app.Foo");
    }

    @Test
    void khongSuyDuoc_traVeDanhSachRong() {
        assertThat(TestClassNameConvention.productionClassCandidates("app.Foo")).isEmpty();
    }

    /**
     * Bốn nguồn khai tên lớp theo ba kiểu (JaCoCo dùng dấu {@code /}), nên phải quy về cùng dạng
     * trước khi so. So thẳng chuỗi là không bao giờ khớp — và không khớp thì im lặng.
     */
    @Test
    void canonicalQuyVeCungDangVaBoLopLongNhau() {
        assertThat(TestClassNameConvention.canonical("vn/hust/app/Foo")).isEqualTo("vn.hust.app.Foo");
        assertThat(TestClassNameConvention.canonical("vn.hust.app.Foo$Inner")).isEqualTo("vn.hust.app.Foo");
        assertThat(TestClassNameConvention.canonical(null)).isEmpty();
    }
}
