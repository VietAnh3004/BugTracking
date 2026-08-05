package vn.hust.datn.evidence.testsupport;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceLink;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceStatus;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.domain.SnapshotStatus;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.UserRole;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.repository.EvidenceLinkRepository;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.repository.WarningRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;

/**
 * Builder dữ liệu test dùng chung: project → snapshot → run → evidence → warning → link.
 *
 * <p>Chỉ tồn tại trong {@code src/test} nên không lẫn vào mã production — không có endpoint
 * {@code /dev/seed} nào tương đương trong {@code src/main}, tránh dữ liệu giả sống sót tới lúc
 * deploy.
 *
 * <p>{@code seqInRun} tự tăng theo run (tuần 8-9 mới có service sinh số này thật khi ingest); dùng
 * {@code saveAndFlush} mọi nơi vì {@code short_id} do Postgres generated column tính — chỉ đọc
 * được sau khi đã flush xuống DB.
 */
@Component
public class TestFixtures {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositorySnapshotRepository snapshotRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private EvidenceRecordRepository evidenceRecordRepository;

    @Autowired
    private WarningRepository warningRepository;

    @Autowired
    private EvidenceLinkRepository evidenceLinkRepository;

    @Autowired
    private SnapshotFileRepository snapshotFileRepository;

    @Autowired
    private ObjectStorageClient storageClient;

    @Autowired
    private UserRepository userRepository;

    private final AtomicInteger seqCounter = new AtomicInteger(0);

    public User user(String email) {
        User user = new User();
        user.setEmail(email);
        // Không phải mật khẩu thật nào cần đăng nhập được qua fixture này - test auth thật đi
        // qua AuthService.register() (BCrypt thật), fixture này chỉ cần một hàng app_user hợp lệ
        // để project.owner_id có FK trỏ tới.
        user.setPasswordHash("!not-a-real-bcrypt-hash!");
        user.setDisplayName("Test User");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    /** Project vô chủ - chỉ dùng cho test không đụng tới authorization (evidence tool, mapper...). */
    public Project project(String name) {
        return project(name, user(name + "-owner@test.local"));
    }

    public Project project(String name, User owner) {
        Project project = new Project();
        project.setOwnerId(owner.getId());
        project.setName(name);
        project.setDescription(null);
        project.setCreatedAt(OffsetDateTime.now());
        project.setUpdatedAt(OffsetDateTime.now());
        return projectRepository.saveAndFlush(project);
    }

    public RepositorySnapshot snapshot(Project project) {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setSourceRef("main");
        snapshot.setStorageKey("test/" + UUID.randomUUID());
        snapshot.setCreatedAt(OffsetDateTime.now());
        return snapshotRepository.saveAndFlush(snapshot);
    }

    /** Run đã SUCCEEDED với `findingCount` cảnh báo — trạng thái duy nhất mà tool API cho query. */
    public AnalysisRun succeededRun(RepositorySnapshot snapshot, int findingCount) {
        AnalysisRun run = new AnalysisRun();
        run.setSnapshotId(snapshot.getId());
        run.setStatus(AnalysisRunStatus.SUCCEEDED);
        run.setFindingCount(findingCount);
        run.setStartedAt(OffsetDateTime.now());
        run.setFinishedAt(OffsetDateTime.now());
        return analysisRunRepository.saveAndFlush(run);
    }

    public AnalysisRun runWithStatus(RepositorySnapshot snapshot, AnalysisRunStatus status) {
        AnalysisRun run = new AnalysisRun();
        run.setSnapshotId(snapshot.getId());
        run.setStatus(status);
        run.setFindingCount(status == AnalysisRunStatus.RUNNING || status == AnalysisRunStatus.FAILED ? 0 : 1);
        run.setStartedAt(OffsetDateTime.now());
        if (status != AnalysisRunStatus.RUNNING) {
            run.setFinishedAt(OffsetDateTime.now());
        }
        return analysisRunRepository.saveAndFlush(run);
    }

    /**
     * Snapshot {@code READY} kèm chỉ mục {@code snapshot_file} — điều kiện để
     * {@code SnapshotPathResolver} có gì mà đối chiếu.
     *
     * <p>Chỉ tạo hàng {@code FILE}, không tạo hàng thư mục: truy vấn
     * {@code findFilePathsBySnapshotId} lọc theo {@code entryType = 'FILE'}, nên thư mục không ảnh
     * hưởng gì tới việc đối chiếu đường dẫn.
     */
    public RepositorySnapshot readySnapshotWithFiles(Project project, List<String> filePaths) {
        RepositorySnapshot snapshot = snapshot(project);
        snapshot.setStatus(SnapshotStatus.READY);
        snapshot.setFileCount(filePaths.size());
        snapshotRepository.saveAndFlush(snapshot);

        OffsetDateTime now = OffsetDateTime.now();
        List<SnapshotFile> rows = new ArrayList<>(filePaths.size());
        for (String path : filePaths) {
            int lastSlash = path.lastIndexOf('/');
            SnapshotFile row = new SnapshotFile();
            row.setSnapshotId(snapshot.getId());
            row.setPath(path);
            row.setParentPath(lastSlash < 0 ? "" : path.substring(0, lastSlash));
            row.setName(path.substring(lastSlash + 1));
            row.setEntryType(SnapshotEntryType.FILE);
            row.setSizeBytes(1);
            row.setBinary(false);
            row.setStored(true);
            row.setStorageKey(snapshot.getStorageKey() + path);
            // ck_snapshot_file_stored_has_object doi CA storage_key LAN sha256 khi stored=true.
            row.setSha256("0".repeat(64));
            row.setCreatedAt(now);
            rows.add(row);
        }
        snapshotFileRepository.saveAll(rows);
        snapshotFileRepository.flush();
        return snapshot;
    }

    /**
     * Như {@link #readySnapshotWithFiles} nhưng có NỘI DUNG THẬT trong object storage và sha256
     * tính từ chính nội dung đó.
     *
     * <p>Cần thiết cho {@code SourceTreeBuilder}: nó đọc object rồi đối chiếu sha256, nên fixture
     * chỉ có metadata sẽ đỏ ở bước đối chiếu chứ không kiểm được thứ cần kiểm.
     */
    public RepositorySnapshot readySnapshotWithContent(Project project, Map<String, String> pathToContent) {
        RepositorySnapshot snapshot = snapshot(project);
        snapshot.setStatus(SnapshotStatus.READY);
        snapshot.setFileCount(pathToContent.size());
        snapshotRepository.saveAndFlush(snapshot);

        OffsetDateTime now = OffsetDateTime.now();
        List<SnapshotFile> rows = new ArrayList<>(pathToContent.size());
        pathToContent.forEach((path, content) -> {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            String storageKey = snapshot.getStorageKey() + path;
            storageClient.putObject(storageKey, bytes, "text/plain; charset=utf-8");

            int lastSlash = path.lastIndexOf('/');
            SnapshotFile row = new SnapshotFile();
            row.setSnapshotId(snapshot.getId());
            row.setPath(path);
            row.setParentPath(lastSlash < 0 ? "" : path.substring(0, lastSlash));
            row.setName(path.substring(lastSlash + 1));
            row.setEntryType(SnapshotEntryType.FILE);
            row.setSizeBytes(bytes.length);
            row.setBinary(false);
            row.setStored(true);
            row.setStorageKey(storageKey);
            row.setSha256(sha256Hex(bytes));
            row.setCreatedAt(now);
            rows.add(row);
        });
        snapshotFileRepository.saveAll(rows);
        snapshotFileRepository.flush();
        return snapshot;
    }

    /** Đánh dấu một file là không có nội dung trên object storage (quá lớn lúc upload). */
    public void markNotStored(RepositorySnapshot snapshot, String path) {
        SnapshotFile file = snapshotFileRepository
                .findBySnapshotIdAndPath(snapshot.getId(), path)
                .orElseThrow();
        file.setStored(false);
        file.setStorageKey(null);
        file.setSha256(null);
        snapshotFileRepository.saveAndFlush(file);
    }

    /** Làm sha256 trong chỉ mục lệch khỏi nội dung thật — mô phỏng DB và object storage trôi khỏi nhau. */
    public void corruptSha256(RepositorySnapshot snapshot, String path) {
        SnapshotFile file = snapshotFileRepository
                .findBySnapshotIdAndPath(snapshot.getId(), path)
                .orElseThrow();
        file.setSha256("0".repeat(64));
        snapshotFileRepository.saveAndFlush(file);
    }

    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Đọc lại một run TỪ DB, không trả về entity đã cache.
     *
     * <p>Cần thiết khi phần được kiểm ghi bằng {@code JdbcTemplate} (materializer) chứ không qua
     * Hibernate: entity trong persistence context sẽ không thấy thay đổi đó, và một assert dựa
     * vào nó xanh giả.
     */
    public AnalysisRun reloadRun(UUID runId) {
        analysisRunRepository.flush();
        return analysisRunRepository.findById(runId).orElseThrow();
    }

    public EvidenceRecord evidenceRecord(AnalysisRun run, EvidenceType type, EvidenceSource source, String filePath, Integer startLine) {
        EvidenceRecord record = new EvidenceRecord();
        record.setSeqInRun(seqCounter.incrementAndGet());
        record.setAnalysisRunId(run.getId());
        record.setType(type);
        record.setSource(source);
        record.setFilePath(filePath);
        record.setStartLine(startLine);
        record.setEndLine(startLine);
        record.setSymbol(null);
        record.setSummary(type + " tại " + filePath + ":" + startLine);
        record.setContent(Map.of());
        record.setStatus(EvidenceStatus.ACTIVE);
        record.setCreatedAt(OffsetDateTime.now());
        return evidenceRecordRepository.saveAndFlush(record);
    }

    /**
     * Tạo evidence_record + warning mở rộng 1:1 trong một lời gọi — trường hợp dùng phổ biến nhất.
     *
     * <p>{@code @Transactional} bắt buộc: gọi {@code this.evidenceRecord(...)} là self-invocation
     * nên KHÔNG qua proxy Spring, nó tham gia (không mở transaction mới) vào transaction do
     * annotation này khởi tạo — nhờ vậy {@code record} vẫn managed khi {@code warning} được lưu
     * ngay sau. Thiếu annotation này, {@code evidenceRecord(...)} tự mở/đóng transaction riêng
     * (qua {@code saveAndFlush} của repository), entity trả về bị detach, và Hibernate từ chối
     * gán một entity detached vào quan hệ {@code @MapsId} (lỗi "detached entity passed to persist").
     */
    @Transactional
    public Warning warning(AnalysisRun run, String ruleId, WarningTool tool, String filePath, int startLine) {
        EvidenceRecord record = evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.valueOf(tool.name()), filePath, startLine);

        Warning warning = new Warning();
        warning.setEvidenceRecord(record); // @MapsId: PHẢI set entity, set riêng evidenceRecordId không đủ tin cậy.
        warning.setTool(tool);
        warning.setRuleId(ruleId);
        warning.setRuleIdNormalized(ruleId);
        warning.setSeverityNormalized(WarningSeverity.MEDIUM);
        warning.setHasPrimaryLocation(true);
        warning.setSourceIndex(0);
        return warningRepository.saveAndFlush(warning);
    }

    public EvidenceLink link(
            EvidenceRecord from, EvidenceRecord to, EvidenceLinkRelation relation, EvidenceLinkMethod method, double weight) {
        EvidenceLink link = new EvidenceLink();
        link.setFromEvidenceId(from.getId());
        link.setToEvidenceId(to.getId());
        link.setRelation(relation);
        link.setMethod(method);
        link.setWeight(BigDecimal.valueOf(weight));
        link.setCreatedAt(OffsetDateTime.now());
        return evidenceLinkRepository.saveAndFlush(link);
    }
}
