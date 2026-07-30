package vn.hust.datn.evidence.testsupport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningSeverity;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.repository.AnalysisRunRepository;
import vn.hust.datn.evidence.repository.EvidenceLinkRepository;
import vn.hust.datn.evidence.repository.EvidenceRecordRepository;
import vn.hust.datn.evidence.repository.ProjectRepository;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.WarningRepository;

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

    private final AtomicInteger seqCounter = new AtomicInteger(0);

    public Project project(String name) {
        Project project = new Project();
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
