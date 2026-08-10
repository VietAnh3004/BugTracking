package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ba bộ lọc của màn hình cảnh báo.
 *
 * <p>Bộ test này sinh ra sau một lỗi thật: ô <b>"Đường dẫn file"</b> trên giao diện không bao giờ
 * ra kết quả. Truy vấn dùng {@code e.filePath = :filePath} — so khớp BẰNG CHÍNH XÁC — trong khi ô
 * đó là một textbox tự do có placeholder {@code src/main/java/…}. Đường dẫn Java thật dài 60+ ký tự
 * nên không ai gõ đủ, và gõ thiếu một ký tự thì bảng rỗng, trông y hệt "file này không có cảnh báo
 * nào".
 *
 * <p>Vì sao lọt: {@code AnalysisRunQueryControllerTest} CÓ gọi bộ lọc file, nhưng chỉ với **một
 * đường dẫn đầy đủ** — đúng cái giá trị duy nhất mà so khớp bằng chính xác vẫn chạy đúng. Test
 * xanh, tính năng hỏng. Bộ test này thêm các giá trị mà người dùng thật sự gõ: tên lớp, một thư
 * mục ở giữa, phần đuôi file, chữ thường.
 *
 * <p>Bài học đi kèm: một tham số lọc cần test cho <b>hình dạng đầu vào</b> chứ không chỉ cho
 * "có truyền hay không truyền". Một ca duy nhất dùng giá trị hoàn hảo nhất chứng minh được rất ít.
 */
class WarningFilterTest extends AbstractIntegrationTest {

    @Autowired
    private AnalysisRunQueryService service;

    @Autowired
    private TestFixtures fixtures;

    private AnalysisRun run;
    private java.util.UUID ownerId;

    @BeforeEach
    void seed() {
        Project project = fixtures.project("loc-" + System.nanoTime());
        ownerId = project.getOwnerId();
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 3);

        fixtures.warning(
                run,
                "NP_NULL_ON_SOME_PATH",
                WarningTool.spotbugs,
                "src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java",
                66);
        fixtures.warning(
                run,
                "EI_EXPOSE_REP2",
                WarningTool.spotbugs,
                "src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java",
                14);
        fixtures.warning(
                run,
                "unvalidated-redirect",
                WarningTool.semgrep,
                "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java",
                49);
    }

    private List<WarningView> loc(WarningTool tool, WarningSeverity severity, String filePath) {
        return service.listWarnings(run.getId(), ownerId, tool, severity, filePath, PageRequest.of(0, 50))
                .getContent();
    }

    /** Chính là thao tác người dùng làm: gõ tên lớp, không gõ cả đường dẫn. */
    @Test
    void locTheoMotPhanDuongDan_raDungCacFileKhop() {
        assertThat(loc(null, null, "UserService")).hasSize(2);
    }

    @Test
    void locTheoTenFileKemDuoi_cungRa() {
        assertThat(loc(null, null, "UserService.java")).hasSize(2);
    }

    @Test
    void locTheoMotThuMucOGiuaDuongDan_cungRa() {
        assertThat(loc(null, null, "vulnshop/user")).hasSize(2);
    }

    /** Không phân biệt hoa thường: người dùng gõ vội, và không có lý do gì bắt họ gõ đúng kiểu. */
    @Test
    void khongPhanBietHoaThuong() {
        assertThat(loc(null, null, "userservice")).hasSize(2);
        assertThat(loc(null, null, "USERSERVICE")).hasSize(2);
    }

    /** Đường dẫn đầy đủ vẫn phải chạy — đây là hành vi cũ, không được làm hỏng. */
    @Test
    void duongDanDayDuVanRa() {
        assertThat(loc(null, null, "src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserService.java"))
                .hasSize(2);
    }

    /** Không khớp gì thì rỗng — phân biệt với "lọc hỏng nên lúc nào cũng rỗng". */
    @Test
    void chuoiKhongCoTrongDuongDanNao_raRong() {
        assertThat(loc(null, null, "KhongCoLopNaoTenNhuVay")).isEmpty();
    }

    /**
     * KHÔNG lọc gì cũng phải chạy — nghe hiển nhiên tới mức không đáng viết, nhưng chính ca này
     * từng cho HTTP 500: {@code null} không suy được kiểu nên Hibernate bind dạng {@code bytea} và
     * Postgres đổ {@code function lower(bytea) does not exist}. Xem ghi chú ở
     * {@code WarningRepository.findForUi}.
     */
    @Test
    void locRongHoacTrangThiKhongLoc() {
        assertThat(loc(null, null, null)).hasSize(3);
        assertThat(loc(null, null, "   ")).hasSize(3);
    }

    /**
     * Lọc theo tool mà KHÔNG lọc theo file — đúng tổ hợp làm hỏng ở lần chạy trước.
     *
     * <p>Tách thành ca riêng vì lỗi kiểu {@code null} phụ thuộc THỨ TỰ CHẠY: nếu một lời gọi có
     * {@code filePath} thật đi trước thì prepared statement đã mang kiểu text và ca này ăn theo,
     * xanh giả. Giữ nó ở đây để nếu ép kiểu trong truy vấn bị gỡ đi thì có ít nhất hai ca cùng đỏ.
     */
    @Test
    void locTheoToolMaKhongLocTheoFile() {
        assertThat(loc(WarningTool.spotbugs, null, null)).hasSize(2);
        assertThat(loc(null, WarningSeverity.MEDIUM, null)).hasSize(3);
    }

    @Test
    void locTheoCongCu() {
        assertThat(loc(WarningTool.semgrep, null, null)).hasSize(1);
        assertThat(loc(WarningTool.spotbugs, null, null)).hasSize(2);
    }

    /** Ba bộ lọc phải chồng nhau bằng AND, không phải OR. */
    @Test
    void baBoLocChongNhauBangAND() {
        assertThat(loc(WarningTool.spotbugs, null, "UserService")).hasSize(2);
        assertThat(loc(WarningTool.semgrep, null, "UserService")).isEmpty();
    }

    /**
     * Bộ đếm tổng phải khớp với số dòng trả về.
     *
     * <p>Query chính và {@code countQuery} là HAI câu SQL viết tay tách rời nhau — sửa điều kiện ở
     * câu này mà quên câu kia thì bảng hiện 2 dòng kèm dòng chữ "Hiển thị 2/34 cảnh báo", và không
     * có lỗi nào.
     */
    @Test
    void tongSoKhopVoiSoDongTraVe() {
        var page = service.listWarnings(
                run.getId(), ownerId, null, null, "UserService", PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }
}
