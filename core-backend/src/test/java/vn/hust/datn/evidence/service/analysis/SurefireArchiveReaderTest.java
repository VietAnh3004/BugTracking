package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

class SurefireArchiveReaderTest {

    private static final String BAO_CAO = "<testsuite name=\"a.B\"><testcase classname=\"a.B\" name=\"c\"/></testsuite>";

    private final SurefireArchiveReader reader = new SurefireArchiveReader(new UploadProperties(1000, 10_000_000L, 100, 1_000_000L));

    /**
     * Hồi quy cho đúng lý do lớp này tồn tại: {@code ZipExtractor} lọc bỏ mọi thứ dưới thư mục
     * {@code target}, mà báo cáo Surefire nằm chính ở đó. Dùng lại nó sẽ cho ra "0 test" không kèm
     * lỗi nào.
     */
    @Test
    void read_baoCaoNamDuoiThuMucTarget_vanDocDuoc() {
        byte[] zip = new ZipBuilder()
                .file("target/surefire-reports/TEST-a.B.xml", BAO_CAO)
                .build();

        List<NamedArtifact> reports = reader.read(zip);

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).name()).isEqualTo("target/surefire-reports/TEST-a.B.xml");
        assertThat(new String(reports.get(0).content(), StandardCharsets.UTF_8)).isEqualTo(BAO_CAO);
    }

    /** Surefire sinh cả {@code *.txt} tóm tắt cạnh {@code TEST-*.xml} — chỉ cái sau có cấu trúc. */
    @Test
    void read_boQuaFileKhongPhaiTestXml() {
        byte[] zip = new ZipBuilder()
                .directory("surefire-reports")
                .file("surefire-reports/TEST-a.B.xml", BAO_CAO)
                .file("surefire-reports/a.B.txt", "Tests run: 1")
                .file("surefire-reports/a.B-output.txt", "log")
                .build();

        assertThat(reader.read(zip)).extracting(NamedArtifact::name).containsExactly("surefire-reports/TEST-a.B.xml");
    }

    /** Thứ tự ổn định là điều kiện để cùng một zip cho cùng một dãy seq_in_run ở mọi lần nạp. */
    @Test
    void read_sapXepTheoDuongDanBatKeThuTuTrongZip() {
        byte[] zip = new ZipBuilder()
                .file("r/TEST-z.Z.xml", BAO_CAO)
                .file("r/TEST-a.A.xml", BAO_CAO)
                .file("r/TEST-m.M.xml", BAO_CAO)
                .build();

        assertThat(reader.read(zip))
                .extracting(NamedArtifact::name)
                .containsExactly("r/TEST-a.A.xml", "r/TEST-m.M.xml", "r/TEST-z.Z.xml");
    }

    /** Nén nhầm thư mục là lỗi thường gặp nhất — thông điệp phải nói ra đã tìm thấy gì. */
    @Test
    void read_khongCoBaoCaoNao_nemKemDanhSachDaTimThay() {
        byte[] zip = new ZipBuilder()
                .file("src/main/java/A.java", "class A {}")
                .file("pom.xml", "<project/>")
                .build();

        assertThatThrownBy(() -> reader.read(zip))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("TEST-*.xml")
                .hasMessageContaining("pom.xml");
    }

    @Test
    void read_vuotSoFileToiDa_nem() {
        SurefireArchiveReader chatChe = new SurefireArchiveReader(new UploadProperties(2, 10_000_000L, 100, 1_000_000L));
        byte[] zip = new ZipBuilder()
                .file("TEST-a.xml", BAO_CAO)
                .file("TEST-b.xml", BAO_CAO)
                .file("TEST-c.xml", BAO_CAO)
                .build();

        assertThatThrownBy(() -> chatChe.read(zip))
                .isInstanceOf(InvalidArtifactException.class)
                .hasMessageContaining("so file toi da");
    }

    @Test
    void read_khongPhaiZip_nem() {
        assertThatThrownBy(() -> reader.read("khong phai zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void looksLikeArchive_theoMagicNumberChuKhongPhaiDuoiFile() {
        assertThat(reader.looksLikeArchive(new ZipBuilder().file("TEST-a.xml", BAO_CAO).build()))
                .isTrue();
        assertThat(reader.looksLikeArchive(BAO_CAO.getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(reader.looksLikeArchive(new byte[] {0x50, 0x4B})).isFalse();
        assertThat(reader.looksLikeArchive(null)).isFalse();
    }
}
