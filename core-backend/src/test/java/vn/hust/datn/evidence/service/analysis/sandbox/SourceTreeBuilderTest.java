package vn.hust.datn.evidence.service.analysis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.exception.UnsupportedSourceTreeException;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Bốn kiểm tra bắt buộc trước khi gửi mã nguồn vào sandbox, cộng cái bẫy uid đã vấp thật lúc dựng
 * image.
 *
 * <p>Điểm chung của mọi phép từ chối ở đây: thứ chúng chặn đều dẫn tới một lần phân tích "thành
 * công" với 0 cảnh báo. Một repo Gradle chạy qua sandbox Maven cho ra 0 class, 0 cảnh báo, exit 0
 * — trông y hệt một dự án sạch.
 */
class SourceTreeBuilderTest extends AbstractIntegrationTest {

    @Autowired
    private SourceTreeBuilder builder;

    @Autowired
    private TestFixtures fixtures;

    private RepositorySnapshot snapshotOf(Map<String, String> files) {
        Project project = fixtures.project("tree-" + UUID.randomUUID());
        return fixtures.readySnapshotWithContent(project, files);
    }

    private static Map<String, String> duAnMauMotModule() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project><artifactId>demo</artifactId></project>");
        files.put("README.md", "# demo");
        files.put("src/main/java/vn/hust/App.java", "class App {}");
        files.put("src/test/java/vn/hust/AppTest.java", "class AppTest {}");
        return files;
    }

    private List<TarArchiveEntry> entriesOf(byte[] tar) throws Exception {
        List<TarArchiveEntry> entries = new ArrayList<>();
        try (TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(tar))) {
            TarArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @Test
    void build_duAnMotModule_dongGoiDuFileVaSourceRootLaGocTar() throws Exception {
        SourceTree tree = builder.build(snapshotOf(duAnMauMotModule()).getId());

        assertThat(tree.sourceRoot()).isEqualTo("repo");
        assertThat(tree.fileCount()).isEqualTo(4);
        assertThat(entriesOf(tree.tar()).stream()
                        .filter(entry -> !entry.isDirectory())
                        .map(TarArchiveEntry::getName))
                .containsExactlyInAnyOrder(
                        "repo/pom.xml",
                        "repo/README.md",
                        "repo/src/main/java/vn/hust/App.java",
                        "repo/src/test/java/vn/hust/AppTest.java");
    }

    /**
     * Hồi quy cho một lỗi mà CHỈ test chạy container thật bắt được ({@code DockerSandboxRunnerTest}).
     *
     * <p>Tar chỉ có entry file thì {@code copyArchiveToContainer} để Docker tự tạo các thư mục
     * trung gian, và Docker tạo chúng thuộc về ROOT. Container non-root sau đó không tạo được
     * {@code target/} và Maven chết với {@code Cannot create resource output directory}.
     *
     * <p>Đáng ghi nhớ: phép kiểm "tar có đủ file không" vẫn XANH trong suốt thời gian lỗi này tồn
     * tại — danh sách file đúng, chỉ thiếu thứ không ai nghĩ tới.
     */
    @Test
    void build_coEntryThuMucChoMoiCapCha_voiCungUid() throws Exception {
        List<TarArchiveEntry> entries = entriesOf(builder.build(snapshotOf(duAnMauMotModule()).getId()).tar());

        assertThat(entries.stream().filter(TarArchiveEntry::isDirectory).map(TarArchiveEntry::getName))
                .contains("repo/", "repo/src/", "repo/src/main/", "repo/src/main/java/", "repo/src/main/java/vn/");
        assertThat(entries).allSatisfy(entry -> assertThat(entry.getLongUserId())
                .as(entry.getName())
                .isEqualTo(10001L));
    }

    /**
     * Cạm bẫy đã vấp thật lúc dựng image: {@code docker cp} gán quyền root, container non-root
     * không ghi được {@code target/} và Maven chết với "Cannot create resource output directory" —
     * một lỗi VỀ TRÌNH BIÊN DỊCH, không hề nhắc tới quyền.
     */
    @Test
    void build_moiEntryMangUidCuaUserTrongImageChuKhongPhaiRoot() throws Exception {
        SourceTree tree = builder.build(snapshotOf(duAnMauMotModule()).getId());

        assertThat(entriesOf(tree.tar())).allSatisfy(entry -> {
            assertThat(entry.getLongUserId()).isEqualTo(10001L);
            assertThat(entry.getLongGroupId()).isEqualTo(10001L);
        });
    }

    /**
     * ustar cắt tên ở 100 ký tự và cắt ÂM THẦM. Đường dẫn Java thật dễ vượt mốc đó, và một file
     * bị cắt tên thì analyzer quét nhầm đường dẫn mà không có gì báo.
     */
    @Test
    void build_duongDanDaiHon100KyTu_khongBiCatAmTham() throws Exception {
        String duongDanDai = "src/main/java/vn/hust/datn/casestudy/vulnshop/mot/hai/ba/bon/nam/sau/bay/"
                + "MotCaiTenLopRatDaiDeVuotQuaGioiHanUstar.java";
        assertThat(duongDanDai.length()).isGreaterThan(100);

        Map<String, String> files = duAnMauMotModule();
        files.put(duongDanDai, "class X {}");

        SourceTree tree = builder.build(snapshotOf(files).getId());

        assertThat(entriesOf(tree.tar())).extracting(TarArchiveEntry::getName).contains("repo/" + duongDanDai);
    }

    @Test
    void build_pomNamTrongThuMucCon_sourceRootTroToiThuMucDo() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", "# repo");
        files.put("backend/pom.xml", "<project/>");
        files.put("backend/src/main/java/A.java", "class A {}");

        SourceTree tree = builder.build(snapshotOf(files).getId());

        assertThat(tree.sourceRoot()).isEqualTo("repo/backend");
    }

    /** Repo Gradle: sandbox chạy Maven nên nó sẽ quét 0 class rồi "thành công". Từ chối thẳng. */
    @Test
    void build_khongCoPomXml_tuChoiKemGoiYGradle() {
        Map<String, String> files = Map.of("build.gradle", "plugins {}", "src/main/java/A.java", "class A {}");
        UUID snapshotId = snapshotOf(files).getId();

        assertThatThrownBy(() -> builder.build(snapshotId))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining("Gradle");
    }

    @Test
    void build_khongCoFileJavaNao_tuChoi() {
        Map<String, String> files = Map.of("pom.xml", "<project/>", "README.md", "# trong");
        UUID snapshotId = snapshotOf(files).getId();

        assertThatThrownBy(() -> builder.build(snapshotId))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining(".java");
    }

    /** Không có căn cứ nào để chọn dự án nào — đoán bừa thì quét nhầm chỗ mà vẫn "thành công". */
    @Test
    void build_nhieuPomCungDoSauNongNhat_tuChoiThayViDoan() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("api/pom.xml", "<project/>");
        files.put("api/src/main/java/A.java", "class A {}");
        files.put("web/pom.xml", "<project/>");
        files.put("web/src/main/java/B.java", "class B {}");
        UUID snapshotId = snapshotOf(files).getId();

        assertThatThrownBy(() -> builder.build(snapshotId))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining("cung o do sau nong nhat");
    }

    /** Sandbox chỉ chạy một lần {@code mvn} ở một thư mục ⇒ bỏ sót mọi module con mà vẫn báo xong. */
    @Test
    void build_pomKhaiModules_tuChoi() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project><modules><module>api</module></modules></project>");
        files.put("api/src/main/java/A.java", "class A {}");
        UUID snapshotId = snapshotOf(files).getId();

        assertThatThrownBy(() -> builder.build(snapshotId))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining("multi-module");
    }

    /** File nguồn quá lớn nên không được lưu lúc upload: bỏ qua im lặng là quét thiếu đúng file đó. */
    @Test
    void build_fileJavaKhongCoNoiDung_tuChoiChuKhongBoQua() {
        RepositorySnapshot snapshot = snapshotOf(duAnMauMotModule());
        fixtures.markNotStored(snapshot, "src/main/java/vn/hust/App.java");

        assertThatThrownBy(() -> builder.build(snapshot.getId()))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining("App.java");
    }

    /**
     * Chỉ mục DB và object storage KHÔNG nguyên tử với nhau (xem {@code SnapshotService}). Phân
     * tích một cây mã nguồn khác với cây người dùng đã upload là hỏng im lặng ở dạng tệ nhất: mọi
     * Evidence ID vẫn hợp lệ, chỉ là chúng nói về một mã nguồn khác.
     */
    @Test
    void build_noiDungKhongKhopSha256_tuChoi() {
        RepositorySnapshot snapshot = snapshotOf(duAnMauMotModule());
        fixtures.corruptSha256(snapshot, "src/main/java/vn/hust/App.java");

        assertThatThrownBy(() -> builder.build(snapshot.getId()))
                .isInstanceOf(UnsupportedSourceTreeException.class)
                .hasMessageContaining("sha256");
    }

    /** File không phải mã nguồn mà quá lớn thì bỏ qua được — không chặn cả run vì một cái ảnh. */
    @Test
    void build_fileKhongPhaiJavaKhongCoNoiDung_chiBoQua() throws Exception {
        Map<String, String> files = duAnMauMotModule();
        files.put("docs/anh-to.png", "gia-lam-anh");
        RepositorySnapshot snapshot = snapshotOf(files);
        fixtures.markNotStored(snapshot, "docs/anh-to.png");

        SourceTree tree = builder.build(snapshot.getId());

        assertThat(tree.fileCount()).isEqualTo(4);
        assertThat(entriesOf(tree.tar()))
                .extracting(TarArchiveEntry::getName)
                .doesNotContain("repo/docs/anh-to.png");
    }
}
