package vn.hust.datn.evidence.service.analysis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/**
 * Chạy THẬT hai image analyzer trên cây nguồn THẬT của vulnshop, qua đúng đường mà production đi.
 *
 * <p>Đây là test đắt nhất của cả dự án (~3 phút) và cũng là test chứng minh nhiều nhất: nó kiểm
 * một lượt cơ chế cp-vào/cp-ra không bind mount, hai pha hai mức đặc quyền, {@code --read-only} +
 * tmpfs, dọn container/volume, và cuối cùng là <b>tiêu chí nghiệm thu của giai đoạn D</b> —
 * sandbox phải cho ra ĐÚNG cùng số liệu như {@code run-analysis.sh} chạy trên host.
 *
 * <p><b>Tự bỏ qua</b> khi không có Docker daemon hoặc chưa dựng image, kèm lý do in ra rõ ràng.
 * Không để nó đỏ trên máy chưa chạy {@code ./infra/analyzer/build.sh}: một test đỏ vì thiếu điều
 * kiện môi trường sẽ nhanh chóng bị người ta bỏ qua theo thói quen, và rồi bỏ qua luôn cả khi nó
 * đỏ thật.
 */
@TestPropertySource(
        properties = {
            "app.sandbox.runner=docker",
            // Ngắn hơn mặc định để test không treo 15 phút nếu daemon nghẽn.
            "app.sandbox.timeout=PT8M"
        })
class DockerSandboxRunnerTest extends AbstractIntegrationTest {

    @Autowired
    private SandboxRunner sandboxRunner;

    private final AnalyzerSanityChecker checker = new AnalyzerSanityChecker(new ObjectMapper());

    /**
     * Điều kiện chạy, đánh giá TRƯỚC khi Spring dựng context. Ping daemon và kiểm cả hai image —
     * thiếu image thì thông điệp phải nói ra lệnh cần chạy, không phải một lỗi "no such image" ở
     * giữa bài test.
     */
    static boolean sandboxSanSang() {
        try (var httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .build()
                        .getDockerHost())
                .build()) {
            var client = DockerClientImpl.getInstance(
                    DefaultDockerClientConfig.createDefaultConfigBuilder().build(), httpClient);
            client.pingCmd().exec();
            for (String image : new String[] {"evidence/analyzer-java:latest", "evidence/analyzer-semgrep:latest"}) {
                client.inspectImageCmd(image).exec();
            }
            return true;
        } catch (Exception e) {
            System.out.println("Bo qua DockerSandboxRunnerTest: " + e.getMessage()
                    + ". Chay './infra/analyzer/build.sh --no-check' de dung image, va xem CLAUDE.md ve DOCKER_HOST.");
            return false;
        }
    }

    /** Cây nguồn vulnshop, KHÔNG có {@code target/} — sandbox phải tự biên dịch được. */
    private byte[] vulnshopTar() {
        Path root = repoRoot().resolve("case-study").resolve("vulnshop");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(buffer);
                var walk = Files.walk(root)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            java.util.Set<String> dirs = new java.util.HashSet<>();
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.startsWith("target/")) {
                    continue;
                }
                // Entry THƯ MỤC là bắt buộc: thiếu chúng, Docker tự tạo thư mục trung gian và tạo
                // chúng thuộc về ROOT — container non-root không ghi được target/ (xem
                // SourceTreeBuilder.writeParentDirs). Test này dựng tar theo đúng khuôn đó.
                String entryName = "repo/" + relative;
                for (int slash = entryName.indexOf('/'); slash >= 0; slash = entryName.indexOf('/', slash + 1)) {
                    String dir = entryName.substring(0, slash);
                    if (dirs.add(dir)) {
                        TarArchiveEntry dirEntry = new TarArchiveEntry(dir + "/");
                        dirEntry.setMode(0_755);
                        dirEntry.setUserId(10001);
                        dirEntry.setGroupId(10001);
                        tar.putArchiveEntry(dirEntry);
                        tar.closeArchiveEntry();
                    }
                }
                byte[] content = Files.readAllBytes(path);
                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(content.length);
                entry.setMode(0_644);
                entry.setUserId(10001);
                entry.setGroupId(10001);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("case-study").resolve("vulnshop"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Khong tim thay case-study/vulnshop");
    }

    @Test
    @EnabledIf("sandboxSanSang")
    void run_vulnshopThat_choDungCungSoLieuNhuChayTrenHost() {
        SandboxRequest request = new SandboxRequest(UUID.randomUUID(), vulnshopTar(), "repo");

        SandboxResult result = sandboxRunner.run(request);

        // Bốn artifact, đúng tên mà AnalyzerSanityChecker và bốn parser mong đợi.
        assertThat(result.artifacts()).containsKeys("spotbugs.xml", "semgrep.sarif", "jacoco.xml");
        assertThat(result.artifacts().keySet()).anyMatch(name -> name.startsWith("surefire-reports/"));
        assertThat(result.imageDigest()).isNotBlank();
        assertThat(result.durationMs()).isPositive();
        assertThat(result.logs()).containsKeys("resolve", "analyze", "semgrep");

        // Bốn tầng kiểm tính tỉnh táo chạy trên artifact THẬT vừa sinh — kể cả canary.
        SanityVerdict verdict = checker.check(result.artifacts());
        assertThat(verdict.reason()).isNull();
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.canaryOk()).isTrue();
        assertThat(verdict.canaryFindingCount()).isPositive();

        // TIÊU CHÍ NGHIỆM THU của giai đoạn D: khớp expected-analysis.json.
        assertThat(demCanhBaoNguoiDung(result.artifacts()))
                .isEqualTo(SampleArtifacts.expectedInt("spotbugs", "findings"));
    }

    private int demCanhBaoNguoiDung(Map<String, byte[]> artifacts) {
        return (int) new vn.hust.datn.evidence.service.analysis.SpotbugsXmlAdapter()
                .parse(artifacts.get("spotbugs.xml")).stream()
                        .filter(draft -> !AnalyzerSanityChecker.isCanaryEvidence(draft))
                        .count();
    }

    /** Sandbox tắt thì phải nói rõ là tắt, không phải trả về "0 artifact". */
    @Test
    void isAvailable_traLoiDuocMaKhongNem() {
        assertThat(sandboxRunner).isInstanceOf(DockerSandboxRunner.class);
        // Không assert true/false: máy CI có thể không có daemon. Điều cần kiểm là nó KHÔNG NÉM.
        sandboxRunner.isAvailable();
    }
}
