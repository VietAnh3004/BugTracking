package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.ArtifactSource;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.exception.SandboxUnavailableException;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.service.SnapshotQueryService;
import vn.hust.datn.evidence.service.analysis.sandbox.AnalyzerSanityChecker;
import vn.hust.datn.evidence.service.analysis.sandbox.SandboxRequest;
import vn.hust.datn.evidence.service.analysis.sandbox.SandboxResult;
import vn.hust.datn.evidence.service.analysis.sandbox.SandboxRunner;
import vn.hust.datn.evidence.service.analysis.sandbox.SanityVerdict;
import vn.hust.datn.evidence.service.analysis.sandbox.SourceTree;
import vn.hust.datn.evidence.service.analysis.sandbox.SourceTreeBuilder;

/**
 * Điều phối một lần phân tích: nhận dạng artifact → parse → materialize → quyết định trạng thái.
 *
 * <h2>Class này KHÔNG mang {@code @Transactional}</h2>
 *
 * Cùng khuôn {@code SnapshotService}. Mọi thao tác DB đi qua {@link AnalysisRunPersistenceService}
 * (bia mộ {@code RUNNING} và kết cục, mỗi cái một transaction {@code REQUIRES_NEW}) hoặc
 * {@link EvidenceMaterializer} (một transaction cho toàn bộ evidence). Nhờ vậy nhánh ghi FAILED
 * không bao giờ nằm trong transaction vừa hỏng.
 *
 * <h2>Hai loại thất bại, hai cách trả lời khác nhau</h2>
 *
 * <ul>
 *   <li><b>Lỗi của yêu cầu</b> — snapshot không thuộc về người gọi, snapshot chưa READY, artifact
 *       không nhận dạng được: NÉM, và ném <i>trước khi</i> tạo hàng {@code RUNNING}. Không để lại
 *       bia mộ cho mọi lần gửi sai (cùng lý do {@code SnapshotService} gọi
 *       {@code ingestor.validateFormat} trước {@code createPending}).
 *   <li><b>Lỗi của dữ liệu phân tích</b> — artifact hỏng giữa chừng, 100% đường dẫn không đối
 *       chiếu được, không có analyzer tĩnh nào: KHÔNG ném, mà ghi run {@code FAILED} kèm lý do đọc
 *       được rồi trả về run đó. Đây chính là hình dạng mà giai đoạn D cần (chạy bất đồng bộ, FE
 *       đọc {@code status}/{@code errorMessage} qua {@code GET /analysis-runs/{id}}), nên đường
 *       này không phải viết lại.
 * </ul>
 */
@Service
public class AnalysisRunService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunService.class);

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final SnapshotQueryService snapshotQueryService;
    private final SnapshotFileRepository snapshotFileRepository;
    private final AnalyzerRegistry registry;
    private final SurefireArchiveReader surefireArchiveReader;
    private final EvidenceMaterializer materializer;
    private final AnalysisRunPersistenceService persistence;
    private final SandboxRunner sandboxRunner;
    private final SourceTreeBuilder sourceTreeBuilder;
    private final AnalyzerSanityChecker sanityChecker;
    private final Executor sandboxExecutor;

    public AnalysisRunService(
            SnapshotQueryService snapshotQueryService,
            SnapshotFileRepository snapshotFileRepository,
            AnalyzerRegistry registry,
            SurefireArchiveReader surefireArchiveReader,
            EvidenceMaterializer materializer,
            AnalysisRunPersistenceService persistence,
            SandboxRunner sandboxRunner,
            SourceTreeBuilder sourceTreeBuilder,
            AnalyzerSanityChecker sanityChecker,
            @Qualifier("sandboxExecutor") Executor sandboxExecutor) {
        this.snapshotQueryService = snapshotQueryService;
        this.snapshotFileRepository = snapshotFileRepository;
        this.registry = registry;
        this.surefireArchiveReader = surefireArchiveReader;
        this.materializer = materializer;
        this.persistence = persistence;
        this.sandboxRunner = sandboxRunner;
        this.sourceTreeBuilder = sourceTreeBuilder;
        this.sanityChecker = sanityChecker;
        this.sandboxExecutor = sandboxExecutor;
    }

    /**
     * Chạy analyzer trong sandbox — BẤT ĐỒNG BỘ.
     *
     * <p>Trả về ngay một run {@code RUNNING}; FE theo dõi bằng {@code GET /analysis-runs/{id}}.
     * Một lần phân tích thật mất vài phút, giữ HTTP connection suốt thời gian đó là cách chắc chắn
     * để gặp timeout ở proxy/ingress mà không ai biết run vẫn đang chạy.
     *
     * <p>Ba phép kiểm chạy ĐỒNG BỘ, TRƯỚC khi tạo hàng nào — cùng nguyên tắc với đường nạp tay:
     * không để lại bia mộ cho một việc còn chưa bắt đầu được.
     *
     * <p>Đẩy việc bằng {@code Executor} tường minh chứ KHÔNG dùng {@code @Async}: {@code @Async}
     * chỉ có tác dụng khi lời gọi đi qua proxy Spring, nên gọi từ chính lớp này (self-invocation)
     * sẽ chạy ĐỒNG BỘ mà không báo gì — cùng họ cạm bẫy với {@code REQUIRES_NEW} đã ghi ở
     * {@link AnalysisRunPersistenceService}.
     */
    public AnalysisRun startSandboxRun(UUID snapshotId, UUID ownerId) {
        snapshotQueryService.getReady(snapshotId, ownerId);
        if (!sandboxRunner.isAvailable()) {
            throw new SandboxUnavailableException(
                    "Sandbox khong dung duoc luc nay (app.sandbox.runner hoac Docker daemon). "
                            + "Van co the nap artifact thu cong qua POST /api/v1/snapshots/{id}/analysis-runs.");
        }
        // Dựng cây nguồn ĐỒNG BỘ: bốn phép từ chối của nó (không pom, không java, nhiều module,
        // sha256 lệch) là lỗi của YÊU CẦU, phải trả 409 ngay chứ không thành một run FAILED mà
        // người dùng phải đợi vài phút mới biết.
        SourceTree sourceTree = sourceTreeBuilder.build(snapshotId);

        AnalysisRun run = persistence.createRunning(snapshotId, ArtifactSource.DOCKER_SANDBOX);
        UUID runId = run.getId();
        sandboxExecutor.execute(() -> runSandboxSafely(runId, snapshotId, sourceTree));
        return run;
    }

    /**
     * Chạy trên luồng nền, nên KHÔNG được để ngoại lệ nào thoát ra: không ai bắt nó, và run sẽ
     * treo mãi ở {@code RUNNING}.
     */
    private void runSandboxSafely(UUID runId, UUID snapshotId, SourceTree sourceTree) {
        try {
            SandboxResult sandboxResult =
                    sandboxRunner.run(new SandboxRequest(runId, sourceTree.tar(), sourceTree.sourceRoot()));

            SanityVerdict verdict = sanityChecker.check(sandboxResult.artifacts());
            SandboxInfo info = new SandboxInfo(
                    sandboxResult.imageDigest(),
                    sourceTree.sourceRoot(),
                    verdict.canaryOk(),
                    verdict.canaryFindingCount(),
                    sandboxResult.durationMs());

            if (!verdict.ok()) {
                // Bốn tầng kiểm nói KHÔNG: dừng ở đây, KHÔNG materialize. Ghi bằng chứng từ một
                // lần chạy không đáng tin còn tệ hơn không ghi gì — nó trông y hệt dữ liệu thật.
                persistence.finalizeRun(
                        runId, RunOutcome.failed(shorten(verdict.reason()), null, null).withSandbox(info));
                return;
            }

            List<DetectedArtifact> detected = detectAll(toNamedArtifacts(sandboxResult.artifacts()));
            persistence.finalizeRun(runId, analyze(snapshotId, runId, detected).withSandbox(info));
        } catch (RuntimeException e) {
            log.error("Sandbox run {} hong", runId, e);
            persistence.finalizeRun(runId, RunOutcome.failed(shorten(messageOf(e)), null, null));
        }
    }

    private List<NamedArtifact> toNamedArtifacts(Map<String, byte[]> artifacts) {
        List<NamedArtifact> named = new ArrayList<>();
        artifacts.forEach((name, content) -> {
            // Bỏ log và file phụ trợ mà analyze.sh ghi cạnh artifact: chúng không phải đầu vào của
            // parser nào, và AnalyzerRegistry sẽ từ chối chúng (đúng, nhưng làm hỏng cả run).
            if (name.endsWith(".log") || name.endsWith(".txt") || name.equals("effective-pom.xml")) {
                return;
            }
            named.add(new NamedArtifact(name, content));
        });
        return named;
    }

    public AnalysisRun ingest(UUID snapshotId, UUID ownerId, List<NamedArtifact> artifacts) {
        // Không thuộc về bạn ⇒ 404, và chưa READY ⇒ 409 — cả hai trước khi tạo hàng nào.
        snapshotQueryService.getReady(snapshotId, ownerId);
        if (artifacts == null || artifacts.isEmpty()) {
            throw new InvalidArtifactException("Khong co artifact nao duoc gui len");
        }

        List<DetectedArtifact> detected = detectAll(artifacts);

        AnalysisRun run = persistence.createRunning(snapshotId);
        UUID runId = run.getId();
        try {
            return persistence.finalizeRun(runId, analyze(snapshotId, runId, detected));
        } catch (RuntimeException e) {
            // Nhánh này chỉ còn lỗi ngoài dự kiến (DB hỏng, hết bộ nhớ...): mọi thất bại đã lường
            // trước đều đi qua RunOutcome.failed(...) bên trong analyze().
            log.error("Phan tich run {} hong ngoai du kien", runId, e);
            persistence.finalizeRun(runId, RunOutcome.failed(shorten(messageOf(e)), null, null));
            throw e;
        }
    }

    /**
     * Nhận dạng TOÀN BỘ artifact trước, rồi mới tạo run. Zip báo cáo Surefire được bung ra ở đây
     * chứ không ở controller: "một artifact Surefire là cả một thư mục" là sự thật của miền, không
     * phải chi tiết của giao thức HTTP.
     */
    private List<DetectedArtifact> detectAll(List<NamedArtifact> artifacts) {
        List<NamedArtifact> phang = new ArrayList<>();
        for (NamedArtifact artifact : artifacts) {
            if (surefireArchiveReader.looksLikeArchive(artifact.content())) {
                phang.addAll(surefireArchiveReader.read(artifact.content()));
            } else {
                phang.add(artifact);
            }
        }

        List<DetectedArtifact> detected = new ArrayList<>(phang.size());
        for (NamedArtifact artifact : phang) {
            detected.add(new DetectedArtifact(artifact, registry.detect(artifact)));
        }
        // Sắp ỔN ĐỊNH theo thứ tự nạp chuẩn: thứ tự này trở thành seq_in_run, tức trở thành
        // Evidence ID. Nhiều file TEST-*.xml giữ nguyên thứ tự đã sắp theo tên từ trước.
        detected.sort((a, b) -> registry.canonicalOrder().compare(a.analyzer(), b.analyzer()));
        return detected;
    }

    private RunOutcome analyze(UUID snapshotId, UUID runId, List<DetectedArtifact> detected) {
        Map<String, String> toolVersions = new LinkedHashMap<>();
        List<EvidenceDraft> drafts = new ArrayList<>();

        for (DetectedArtifact item : detected) {
            byte[] content = item.artifact().content();
            try {
                // Loại bằng chứng của mã canary NGAY tại đây. Không chỉ vì finding_count sẽ sai:
                // đường dẫn evidence/canary/Canary.java không có trong snapshot người dùng, nên
                // mỗi finding canary còn cộng một điểm vào unresolved_location_count và làm bẩn
                // chính chỉ số đo sức khoẻ chuẩn hoá đường dẫn. Với run nạp tay bộ lọc này không
                // loại gì — ở đó không có canary.
                item.analyzer().parse(content).stream()
                        .filter(draft -> !AnalyzerSanityChecker.isCanaryEvidence(draft))
                        .forEach(drafts::add);
                String version = item.analyzer().toolVersion(content);
                if (version != null) {
                    // JaCoCo không khai phiên bản — khoá vắng mặt là bình thường, không phải thiếu
                    // dữ liệu. putIfAbsent: 9 file Surefire cùng một phiên bản.
                    toolVersions.putIfAbsent(item.analyzer().toolKey(), version);
                }
            } catch (InvalidArtifactException e) {
                // Artifact là dữ liệu KHÔNG TIN CẬY. Một file hỏng phải cho run FAILED kèm lý do
                // người vận hành đọc được, không phải 500 với một stack trace vô nghĩa.
                return RunOutcome.failed(
                        shorten("Artifact '%s' (%s) khong doc duoc: %s"
                                .formatted(item.artifact().name(), item.analyzer().toolKey(), e.getMessage())),
                        null,
                        toolVersions);
            }
        }

        SnapshotPathResolver pathResolver =
                SnapshotPathResolver.of(snapshotFileRepository.findFilePathsBySnapshotId(snapshotId));
        MaterializationResult result = materializer.materialize(runId, drafts, pathResolver);

        log.info(
                "Run {}: {} evidence ({} canh bao tinh), {}/{} vi tri doi chieu duoc, tool {}",
                runId,
                result.evidenceCount(),
                result.findingCount(),
                result.locationCount() - result.unresolvedLocationCount(),
                result.locationCount(),
                toolVersions);

        return decide(detected, result, toolVersions);
    }

    /**
     * Quyết định trạng thái — trước khi chạm vào DB, xem Javadoc {@link RunOutcome}.
     *
     * <p>Ba nhánh FAILED ở đây đều là hiện thân của nguyên tắc "analyzer trả về 0 phải bị coi là
     * lỗi", và đều KHÔNG thể suy ra từ nhau.
     */
    private RunOutcome decide(
            List<DetectedArtifact> detected, MaterializationResult result, Map<String, String> toolVersions) {
        if (result.evidenceCount() == 0) {
            return RunOutcome.failed(
                    "Khong thu duoc bang chung nao tu artifact da nap. Mot Evidence Store rong nguy hiem "
                            + "hon nhieu so voi mot loi bao thang.",
                    result,
                    toolVersions);
        }
        if (result.allLocationsUnresolved()) {
            // ADR 0004 gọi đích danh chuẩn hoá đường dẫn là rủi ro lớn nhất còn lại: hỏng 100% cho
            // ra một Evidence Store trông đầy đủ nhưng không bằng chứng nào bấm được, và
            // EvidenceLinker ở tuần 10-11 sinh 0 liên kết. ck_run_paths_not_all_unresolved cũng
            // chặn ở tầng DB — ở đây chặn sớm để có thông điệp đọc được thay vì một lỗi ràng buộc.
            return RunOutcome.failed(
                    "Khong doi chieu duoc vi tri nao (%d/%d) voi cay file cua snapshot. Nhieu kha nang artifact "
                                    .formatted(result.unresolvedLocationCount(), result.locationCount())
                            + "duoc sinh tu mot ma nguon KHAC voi snapshot nay.",
                    result,
                    toolVersions);
        }

        List<String> analyzerTinh = detected.stream()
                .map(item -> item.analyzer().toolKey())
                .filter(AnalysisRunService::laAnalyzerTinh)
                .distinct()
                .toList();
        if (analyzerTinh.isEmpty()) {
            // Không có artifact phân tích tĩnh mà finding_count = 0 thì SUCCEEDED_CLEAN là một lời
            // nói dối: ta chưa hề chạy công cụ nào có thể sinh cảnh báo.
            return RunOutcome.failed(
                    "Khong co artifact phan tich tinh nao (spotbugs/semgrep) nen khong the ket luan du an sach.",
                    result,
                    toolVersions);
        }

        if (result.findingCount() > 0) {
            return RunOutcome.succeeded(result, toolVersions);
        }
        return RunOutcome.clean(cleanAckNote(analyzerTinh, result, toolVersions), result, toolVersions);
    }

    private static boolean laAnalyzerTinh(String toolKey) {
        for (WarningTool tool : WarningTool.values()) {
            if (tool.name().equals(toolKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sinh {@code clean_ack_note} TỪ SỐ ĐO THẬT. Một chuỗi hằng thì "dự án thật sự sạch" và
     * "analyzer im lặng hỏng" đọc lên giống hệt nhau — mà phân biệt được hai thứ đó chính là lý do
     * {@code SUCCEEDED_CLEAN} tồn tại như một trạng thái riêng (ADR 0003).
     */
    private String cleanAckNote(
            List<String> analyzerTinh, MaterializationResult result, Map<String, String> toolVersions) {
        return "%s da chay va khong bao canh bao nao. Bang chung khac van thu duoc: %d evidence, %d/%d vi tri doi chieu "
                        .formatted(
                                analyzerTinh,
                                result.evidenceCount(),
                                result.locationCount() - result.unresolvedLocationCount(),
                                result.locationCount())
                + "duoc voi snapshot. Phien ban cong cu: %s.".formatted(toolVersions.isEmpty() ? "khong khai" : toolVersions);
    }

    private String messageOf(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String shorten(String message) {
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
    }

    /** Một artifact đã biết parser nào đọc nó. */
    private record DetectedArtifact(NamedArtifact artifact, DetectedAnalyzer analyzer) {}
}
