package vn.hust.datn.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotStatus;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.testsupport.InMemoryObjectStorageClient;
import vn.hust.datn.evidence.testsupport.TestFixtures;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

class SnapshotUploadControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private RepositorySnapshotRepository snapshotRepository;

    @Autowired
    private SnapshotFileRepository snapshotFileRepository;

    @Autowired
    private ObjectStorageClient objectStorageClient;

    private InMemoryObjectStorageClient storage() {
        return (InMemoryObjectStorageClient) objectStorageClient;
    }

    @AfterEach
    void resetStorage() {
        storage().reset();
    }

    private MockMultipartFile zipFile(byte[] zipBytes) {
        return new MockMultipartFile("file", "repo.zip", "application/zip", zipBytes);
    }

    @Test
    void uploadThanhCongTraVeSnapshotReadyVaLapChiMucDungFile() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("vulnshop-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder()
                .file("src/main/App.java", "class App {}\n")
                .file("README.md", "# vulnshop")
                .build();

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(zipFile(zip))
                        .with(authenticatedAs(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.totalSizeBytes").isNumber());

        assertThat(snapshotFileRepository.findAll()).anyMatch(f -> f.getPath().equals("src/main/App.java"));
        assertThat(snapshotFileRepository.findAll())
                .anyMatch(f -> f.getPath().equals("src") && f.getEntryType() == SnapshotEntryType.DIRECTORY);
        assertThat(storage().size()).isEqualTo(2);
    }

    @Test
    void uploadVaoDuAnKhongThuocVeMinhTraVe404() throws Exception {
        User owner = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        User attacker = fixtures.user("attacker-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("private-project-%d".formatted(System.nanoTime()), owner);
        byte[] zip = new ZipBuilder().file("a.txt", "x").build();

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(zipFile(zip))
                        .with(authenticatedAs(attacker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/not-found"));
    }

    @Test
    void uploadFileKhongPhaiZipTraVe400VaKhongTaoHangSnapshotNao() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("vulnshop-%d".formatted(System.nanoTime()), user);
        MockMultipartFile notAZip = new MockMultipartFile("file", "photo.png", "image/png", "khong phai zip".getBytes());

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(notAZip)
                        .with(authenticatedAs(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/invalid-archive"));

        assertThat(snapshotRepository.findAll()).noneMatch(s -> s.getProjectId().equals(project.getId()));
    }

    /**
     * Chứng minh hướng bất nhất AN TOÀN đã thiết kế: lỗi object storage ở file thứ hai giữa vòng
     * lặp upload ⇒ snapshot chuyển FAILED, {@code snapshot_file} RỖNG (không có index trỏ tới object
     * chưa từng được ghi) — không phải trạng thái nửa vời trộn lẫn file cũ và file mới.
     */
    @Test
    void loiObjectStorageGiuaChungUploadChuyenSnapshotSangFailedVaKhongLuuHangFileNao() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("vulnshop-%d".formatted(System.nanoTime()), user);
        byte[] zip = new ZipBuilder()
                .file("a.txt", "noi dung a")
                .file("b-loi.txt", "noi dung b se loi khi ghi")
                .file("c.txt", "noi dung c")
                .build();
        storage().failOnKeyContaining("b-loi.txt");

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(zipFile(zip))
                        .with(authenticatedAs(user)))
                .andExpect(status().isInternalServerError());

        RepositorySnapshot snapshot = snapshotRepository.findAll().stream()
                .filter(s -> s.getProjectId().equals(project.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(snapshot.getErrorMessage()).isNotBlank();
        assertThat(snapshotFileRepository.findBySnapshotIdOrderByEntryTypeDescNameAsc(snapshot.getId()))
                .isEmpty();
    }

    @Test
    void uploadVuotSoFileToiDaTraVe413() throws Exception {
        User user = fixtures.user("owner-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("vulnshop-%d".formatted(System.nanoTime()), user);
        ZipBuilder builder = new ZipBuilder();
        // Ngưỡng thật (UPLOAD_MAX_FILES=20000) quá tốn thời gian để dựng trong test - dùng override
        // riêng qua @TestPropertySource sẽ tách context Spring tốn kém hơn tạo 20001 entry rỗng.
        for (int i = 0; i < 20_001; i++) {
            builder.file("f" + i + ".txt", "x");
        }
        byte[] zip = builder.build();

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + project.getId() + "/snapshots")
                        .file(zipFile(zip))
                        .with(authenticatedAs(user)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/archive-limit-exceeded"));
    }

    @Test
    void uploadKhongCoTokenTraVe401() throws Exception {
        byte[] zip = new ZipBuilder().file("a.txt", "x").build();

        getMockMvc()
                .perform(multipart("/api/v1/projects/" + UUID.randomUUID() + "/snapshots").file(zipFile(zip)))
                .andExpect(status().isUnauthorized());
    }
}
