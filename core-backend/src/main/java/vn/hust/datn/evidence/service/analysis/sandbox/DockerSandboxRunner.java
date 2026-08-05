package vn.hust.datn.evidence.service.analysis.sandbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;

import vn.hust.datn.evidence.config.properties.SandboxProperties;

/**
 * Chạy analyzer bằng cách điều khiển Docker daemon của host (ADR 0008).
 *
 * <h2>KHÔNG bind mount — đây là quyết định, không phải sở thích</h2>
 *
 * {@code docker run -v /workspace/abc:/work} phát ra từ <i>bên trong</i> core-backend được daemon
 * giải theo filesystem của <b>HOST</b>. Host không có đường dẫn đó ⇒ daemon <b>lặng lẽ tạo một
 * thư mục rỗng</b> ⇒ analyzer quét 0 class ⇒ 0 cảnh báo, exit 0. Rơi thẳng vào lớp hỏng-im-lặng mà
 * {@code ck_run_not_silently_empty} sinh ra để chặn.
 *
 * <p>Nguồn vào bằng {@code copyArchiveToContainer}, artifact ra bằng
 * {@code copyArchiveFromContainer}, vùng ghi dùng volume theo TÊN. Không có chuỗi đường dẫn nào để
 * hai bên hiểu khác nhau, và chạy giống hệt trên Windows / Linux / daemon từ xa.
 *
 * <h2>Ba lần chạy container, hai mức đặc quyền</h2>
 *
 * <pre>
 * resolve   CÓ mạng     chỉ phân giải phụ thuộc, không chạy test của người dùng
 * analyze   KHÔNG mạng  mvn test + SpotBugs — pha thực thi mã của người lạ
 * semgrep   KHÔNG mạng  quét mã nguồn
 * </pre>
 *
 * Cả ba dùng chung một volume. Ranh giới quan trọng: <b>pha nguy hiểm là pha không có mạng</b>.
 */
@Service
@ConditionalOnProperty(name = "app.sandbox.runner", havingValue = "docker")
public class DockerSandboxRunner implements SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxRunner.class);

    /** Nhãn trên mọi container/volume để {@code SandboxReaper} dọn được đồ mồ côi. */
    private static final String LABEL_RUN_ID = "evidence.run-id";

    private static final String WORK_DIR = "/work";
    private static final String ARTIFACT_DIR = "/work/.analysis";
    private static final String ANALYZE_SCRIPT = "/opt/analyze.sh";

    /**
     * {@code --read-only} cần đúng hai tmpfs này, đã kiểm chứng bằng tay trước khi viết lớp này:
     * Maven ghi tạm vào {@code /tmp}, và image gốc đặt {@code HOME=/home/analyzer} nên nó cũng cố
     * tạo {@code ~/.m2}. Thiếu tmpfs thứ hai, container chết với
     * {@code mkdir: cannot create directory '/home/analyzer/.m2': Read-only file system}.
     */
    private static final String TMPFS_TMP = "/tmp";

    /**
     * HOME của cả hai image. Khai cả hai cho MỌI container thay vì chọn theo image: một tmpfs
     * thừa không tốn gì, còn chọn nhầm thì container chết ở một chỗ chẳng liên quan tới nguyên
     * nhân. Image analyzer chạy uid 10001 với {@code /home/analyzer}; image Semgrep chính chủ chạy
     * uid 1000 với {@code /home/semgrep}.
     */
    private static final List<String> TMPFS_HOMES = List.of("/home/analyzer", "/home/semgrep");

    private final DockerClient dockerClient;
    private final SandboxProperties properties;

    public DockerSandboxRunner(DockerClient dockerClient, SandboxProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (RuntimeException e) {
            // Không ném: đây là câu hỏi "dùng được không", và câu trả lời "không" phải rẻ. Tầng
            // trên dùng nó để trả 503 kèm thông điệp rõ thay vì tạo một run rồi để nó chết.
            log.warn("Khong ping duoc Docker daemon: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public SandboxResult run(SandboxRequest request) {
        long batDau = System.nanoTime();
        String volumeName = "evidence-run-" + request.runId();
        List<String> containerIds = new ArrayList<>(3);
        Map<String, String> logs = new LinkedHashMap<>();

        try {
            dockerClient
                    .createVolumeCmd()
                    .withName(volumeName)
                    .withLabels(Map.of(LABEL_RUN_ID, request.runId().toString()))
                    .exec();

            String javaImage = properties.javaImage();
            String sourceRootInContainer = WORK_DIR + "/" + request.sourceRoot();

            // Pha 1 — resolve. CÓ mạng, và là container DUY NHẤT nhận cây mã nguồn.
            String resolveId = createContainer(
                    request.runId(), volumeName, javaImage, true, ANALYZE_SCRIPT, "resolve", sourceRootInContainer);
            containerIds.add(resolveId);
            copySourceTree(resolveId, request.sourceTar());
            logs.put("resolve", runAndCollect(resolveId, "resolve"));

            // Pha 2 — analyze. KHÔNG mạng: đây mới là pha thực thi mã của người lạ.
            String analyzeId = createContainer(
                    request.runId(), volumeName, javaImage, false, ANALYZE_SCRIPT, "analyze", sourceRootInContainer);
            containerIds.add(analyzeId);
            logs.put("analyze", runAndCollect(analyzeId, "analyze"));

            // Pha 3 — semgrep. KHÔNG mạng.
            String semgrepId = createContainer(
                    request.runId(), volumeName, properties.semgrepImage(), false, sourceRootInContainer);
            containerIds.add(semgrepId);
            // Semgrep trả exit code khác 0 khi CÓ finding — đó là hành vi bình thường của nó, nên
            // exit code ở đây không phán xét được. Nhưng nới lỏng exit code thì phải bù bằng một
            // chốt khác, nếu không một lần Semgrep CHẾT sẽ đi qua im lặng: đã vấp thật khi bật
            // --read-only mà quên tmpfs cho HOME của image Semgrep — nó crash, và cả pipeline vẫn
            // "thành công" với đủ artifact của SpotBugs. Chốt đó là kiểm semgrep.sarif ở dưới.
            String semgrepLog = collectLogs(semgrepId, startAndWaitAllowingNonZero(semgrepId));
            logs.put("semgrep", semgrepLog);

            Map<String, byte[]> artifacts = copyArtifactsOut(semgrepId);
            byte[] sarif = artifacts.get("semgrep.sarif");
            if (sarif == null || sarif.length == 0) {
                throw new SandboxException(
                        "Semgrep khong sinh ra semgrep.sarif — no chay khong xong, khong phai 'khong co canh bao':%n%s"
                                .formatted(tail(semgrepLog)));
            }
            long duration = (System.nanoTime() - batDau) / 1_000_000L;
            log.info("Run {}: sandbox xong sau {} ms, thu duoc {} artifact", request.runId(), duration, artifacts.size());
            return new SandboxResult(artifacts, imageDigest(javaImage), duration, logs);
        } finally {
            // Dọn NGAY trong finally, không đợi reaper: reaper là lưới an toàn cho tiến trình chết
            // giữa chừng, không phải cơ chế dọn chính.
            cleanup(containerIds, volumeName);
        }
    }

    private String createContainer(
            UUID runId, String volumeName, String image, boolean withNetwork, String... command) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new com.github.dockerjava.api.model.Bind(
                        volumeName, new Volume(WORK_DIR)))
                // Mã của người lạ: cắt mạng ở pha nguy hiểm.
                .withNetworkMode(withNetwork ? "bridge" : "none")
                .withCapDrop(com.github.dockerjava.api.model.Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                // Fork bomb: timeout KHÔNG chặn được vì máy chết trước khi timeout kịp nổ.
                .withPidsLimit(Long.valueOf(properties.pidsLimit()))
                .withMemory(properties.memoryBytes())
                // BẰNG ĐÚNG memory: quên là swap không giới hạn và giới hạn bộ nhớ mất tác dụng.
                .withMemorySwap(properties.memoryBytes())
                .withNanoCPUs(properties.cpus() * 1_000_000_000L)
                .withReadonlyRootfs(true)
                // mode=1777 tường minh: mặc định tmpfs của Docker thuộc root với mode 0755, nên
                // container non-root không ghi được vào chính thư mục vừa được mount cho nó.
                .withTmpFs(tmpfsMounts())
                // AutoRemove(false) BẮT BUỘC: --rm làm container biến mất trước khi đọc được log
                // và exit code, tức là mất đúng thứ cần để chẩn đoán khi hỏng.
                .withAutoRemove(false);

        CreateContainerResponse response = dockerClient
                .createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withLabels(Map.of(LABEL_RUN_ID, runId.toString()))
                .withCmd(command)
                .exec();
        return response.getId();
    }

    private Map<String, String> tmpfsMounts() {
        Map<String, String> mounts = new LinkedHashMap<>();
        mounts.put(TMPFS_TMP, "rw,exec,size=512m,mode=1777");
        TMPFS_HOMES.forEach(home -> mounts.put(home, "rw,exec,size=64m,mode=1777"));
        return mounts;
    }

    /**
     * Tar do {@link SourceTreeBuilder} dựng đã mang sẵn tiền tố {@code repo/} và uid 10001, nên
     * đích ở đây là {@code /work} chứ không phải {@code /work/repo} — thư mục đích phải tồn tại
     * sẵn, mà volume mới chỉ có {@code /work}.
     */
    private void copySourceTree(String containerId, byte[] sourceTar) {
        dockerClient
                .copyArchiveToContainerCmd(containerId)
                .withRemotePath(WORK_DIR)
                .withTarInputStream(new ByteArrayInputStream(sourceTar))
                .exec();
    }

    private String runAndCollect(String containerId, String phase) {
        int exitCode = startAndWaitAllowingNonZero(containerId);
        String logs = collectLogs(containerId, exitCode);
        if (exitCode != 0) {
            // Ném với log đính kèm: `analyze.sh` đã in lý do đọc được ở dạng "PHAN TICH HONG: ...",
            // nuốt nó đi thì người vận hành chỉ còn một con số.
            throw new SandboxException("Pha '%s' hong voi exit code %d:%n%s".formatted(phase, exitCode, tail(logs)));
        }
        return logs;
    }

    private int startAndWaitAllowingNonZero(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        try {
            Integer exitCode = dockerClient
                    .waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return exitCode == null ? -1 : exitCode;
        } catch (RuntimeException e) {
            // Hàng rào thời gian tường. Giết container ngay thay vì để nó chạy tiếp ngốn tài
            // nguyên trong khi ta đã bỏ cuộc.
            killQuietly(containerId);
            throw new SandboxException(
                    "Container vuot qua han %s va da bi giet".formatted(properties.timeout()), e);
        }
    }

    private String collectLogs(String containerId, int exitCode) {
        StringBuilder buffer = new StringBuilder();
        try {
            dockerClient
                    .logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            buffer.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    })
                    .awaitCompletion(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Khong doc duoc log container {}: {}", containerId, e.getMessage());
        }
        return "exit=%d%n%s".formatted(exitCode, buffer);
    }

    /** Toàn bộ {@code /work/.analysis} về thành map tên → nội dung, đọc thẳng từ tar Docker trả về. */
    private Map<String, byte[]> copyArtifactsOut(String containerId) {
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        try (InputStream tarStream = dockerClient
                        .copyArchiveFromContainerCmd(containerId, ARTIFACT_DIR)
                        .exec();
                TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // Docker gói theo tên thư mục cuối: ".analysis/spotbugs.xml" -> "spotbugs.xml".
                String name = entry.getName().replaceFirst("^\\.analysis/", "");
                artifacts.put(name, tar.readAllBytes());
            }
        } catch (IOException e) {
            throw new SandboxException("Khong copy duoc artifact ra khoi container", e);
        }
        if (artifacts.isEmpty()) {
            // Rỗng ở đây KHÔNG được coi là "phân tích ra 0 kết quả" — nó là hỏng hạ tầng.
            throw new SandboxException("Container khong tra ve artifact nao trong " + ARTIFACT_DIR);
        }
        return artifacts;
    }

    /**
     * Digest chứ không phải tag: tag {@code :latest} trỏ tới image khác nhau sau mỗi lần dựng lại,
     * nên ghi tag thì tuần 15-16 chỉ nói được "cùng một cái tên".
     */
    private String imageDigest(String image) {
        try {
            List<String> digests = dockerClient.inspectImageCmd(image).exec().getRepoDigests();
            if (digests != null && !digests.isEmpty()) {
                return digests.get(0);
            }
            // Image dựng tại chỗ (chưa push) không có RepoDigest — dùng Id, vẫn là một digest thật.
            return dockerClient.inspectImageCmd(image).exec().getId();
        } catch (RuntimeException e) {
            log.warn("Khong doc duoc digest cua image {}: {}", image, e.getMessage());
            return null;
        }
    }

    private void cleanup(List<String> containerIds, String volumeName) {
        for (String id : containerIds) {
            try {
                dockerClient.removeContainerCmd(id).withForce(true).exec();
            } catch (RuntimeException e) {
                log.warn("Khong xoa duoc container {}: {}", id, e.getMessage());
            }
        }
        try {
            dockerClient.removeVolumeCmd(volumeName).exec();
        } catch (RuntimeException e) {
            log.warn("Khong xoa duoc volume {}: {}", volumeName, e.getMessage());
        }
    }

    private void killQuietly(String containerId) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (RuntimeException e) {
            log.warn("Khong giet duoc container {}: {}", containerId, e.getMessage());
        }
    }

    /** Chỉ giữ phần cuối của log cho thông điệp lỗi — {@code error_message} có trần 1000 ký tự. */
    private String tail(String logs) {
        int max = 800;
        return logs.length() <= max ? logs : "..." + logs.substring(logs.length() - max);
    }
}
