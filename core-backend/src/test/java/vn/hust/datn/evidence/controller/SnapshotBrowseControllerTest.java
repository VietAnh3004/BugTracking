package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.testsupport.InMemoryObjectStorageClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

/**
 * Test đường ĐỌC (C3). Dựng dữ liệu bằng cách gọi chính endpoint upload thật thay vì chèn tay
 * {@code snapshot_file} — như vậy test cũng canh luôn giao kèo giữa hai đường: nếu upload đổi cách
 * đặt {@code storage_key} hay đếm {@code line_count} mà đường đọc không đổi theo, test này đỏ.
 */
class SnapshotBrowseControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ObjectStorageClient objectStorageClient;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetStorage() {
        ((InMemoryObjectStorageClient) objectStorageClient).reset();
    }

    private User newUser() {
        return fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
    }

    /** Upload qua chính API thật rồi trả về id snapshot vừa tạo. */
    private UUID uploadSnapshot(User user, Project project, byte[] zipBytes) throws Exception {
        String body = getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(new MockMultipartFile("file", "repo.zip", "application/zip", zipBytes))
                        .with(authenticatedAs(user)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    @Test
    void danhSachFileTraVeDanhSachPhangGomCaThuMuc() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        // Cố tình dùng tên viết thường: so sánh chuỗi hoa/thường phụ thuộc collation của Postgres
        // (C so byte, en_US.UTF-8 gần như bỏ qua hoa thường), nên 'README.md' vs 'src' sẽ cho thứ
        // tự khác nhau tuỳ cấu hình DB - đỏ giả, không liên quan tới cái test này muốn chứng minh.
        byte[] zip = new ZipBuilder()
                .file("src/main/App.java", "class App {}\n")
                .file("readme.md", "# doc\n")
                .build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        String body = getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/files").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(body);
        var paths = IntStream.range(0, items.size())
                .mapToObj(i -> items.get(i).get("path").asText())
                .collect(Collectors.toList());
        // Thư mục cha (src, src/main) phải CÓ trong danh sách dù zip không hề chứa entry thư mục
        // nào - FE dựng cây từ danh sách phẳng này, thiếu node cha là cây gãy.
        org.assertj.core.api.Assertions.assertThat(paths)
                .containsExactly("readme.md", "src", "src/main", "src/main/App.java");
    }

    @Test
    void docFileKhongTruyenKhoangDongTraVeTuDong1() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder().file("a.txt", "dong1\ndong2\ndong3\n").build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "a.txt")
                        .with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLine").value(1))
                .andExpect(jsonPath("$.toLine").value(3))
                .andExpect(jsonPath("$.totalLines").value(3))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[0]").value("dong1"));
    }

    @Test
    void docFileTheoKhoangDongCatLatDungOServer() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        String content = IntStream.rangeClosed(1, 100).mapToObj("dong%d"::formatted).collect(Collectors.joining("\n"));
        byte[] zip = new ZipBuilder().file("big.txt", content).build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "big.txt")
                        .param("fromLine", "10")
                        .param("toLine", "12")
                        .with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLine").value(10))
                .andExpect(jsonPath("$.toLine").value(12))
                .andExpect(jsonPath("$.totalLines").value(100))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.lines.length()").value(3))
                // 1-based: dòng thứ 10 phải là "dong10", lệch một dòng ở đây làm source viewer
                // highlight sai mọi evidence.
                .andExpect(jsonPath("$.lines[0]").value("dong10"));
    }

    @Test
    void fileDaiHonNguongMacDinhBiCatVaDanhDauTruncated() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        String content = IntStream.rangeClosed(1, 2500).mapToObj("d%d"::formatted).collect(Collectors.joining("\n"));
        byte[] zip = new ZipBuilder().file("huge.txt", content).build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "huge.txt")
                        .with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2000))
                .andExpect(jsonPath("$.totalLines").value(2500))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void khoangDongRongHonNguongTraVe400() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder().file("a.txt", "x\n").build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "a.txt")
                        .param("fromLine", "1")
                        .param("toLine", "6000")
                        .with(authenticatedAs(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-line-range"));
    }

    @Test
    void docFileNhiPhanTraVe415() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder()
                .file("app.bin", new byte[] {0x01, 0x00, 0x02, 0x03})
                .build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "app.bin")
                        .with(authenticatedAs(user)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/binary-file"));
    }

    @Test
    void docThuMucTraVe409() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder().file("src/App.java", "class App {}\n").build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "src")
                        .with(authenticatedAs(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/file-content-not-stored"));
    }

    @Test
    void docFileKhongTonTaiTrongSnapshotTraVe404() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder().file("a.txt", "x\n").build();
        UUID snapshotId = uploadSnapshot(user, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "khong-ton-tai.txt")
                        .with(authenticatedAs(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    /** Chứng minh quy tắc "không thuộc về bạn ⇒ 404, không phải 403" áp dụng cả cho đường đọc. */
    @Test
    void duyetSnapshotCuaNguoiKhacTraVe404ChuKhongPhai403() throws Exception {
        User owner = newUser();
        User attacker = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), owner);
        byte[] zip = new ZipBuilder().file("secret.txt", "noi dung rieng tu\n").build();
        UUID snapshotId = uploadSnapshot(owner, project, zip);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/files").with(authenticatedAs(attacker)))
                .andExpect(status().isNotFound());

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + snapshotId + "/file")
                        .param("path", "secret.txt")
                        .with(authenticatedAs(attacker)))
                .andExpect(status().isNotFound());
    }

    @Test
    void duyetSnapshotChuaReadyTraVe409() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        // Snapshot dựng qua fixture (không qua upload) mặc định ở PENDING - đúng trạng thái "đang
        // giải nén" mà API duyệt file phải từ chối thay vì trả cây rỗng.
        RepositorySnapshot pending = fixtures.snapshot(project);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + pending.getId() + "/files").with(authenticatedAs(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/snapshot-not-ready"));
    }

    /** Metadata KHÔNG đòi READY: đây chính là endpoint để FE đọc lỗi của một snapshot hỏng. */
    @Test
    void layMetadataSnapshotChuaReadyVanTraVe200() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        RepositorySnapshot pending = fixtures.snapshot(project);

        getMockMvc()
                .perform(get("/api/v1/snapshots/" + pending.getId()).with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void danhSachSnapshotTheoDuAnTraVePageResponse() throws Exception {
        User user = newUser();
        Project project = fixtures.project("p-%d".formatted(System.nanoTime()), user);
        uploadSnapshot(user, project, new ZipBuilder().file("a.txt", "x\n").build());

        getMockMvc()
                .perform(get("/api/v1/projects/" + project.getId() + "/snapshots").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(1));
    }
}
