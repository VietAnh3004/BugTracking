package vn.hust.datn.evidence.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.service.EvidenceQueryService;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Embedding trên pgvector THẬT, với agent-service giả: sinh vector sau phân tích, tìm kết hợp so
 * chữ + theo nghĩa, và hỏng thì lùi về so chữ kèm lý do — không bao giờ làm hỏng run.
 *
 * <p>Vector 3 chiều dựng tay để biết trước đáp án: bằng chứng "alpha" = trục x, "beta" = trục y,
 * "gamma" = trục z; câu hỏi gần trục y thì "beta" phải đứng đầu.
 */
class EmbeddingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EvidenceQueryService evidenceQueryService;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    private AnalysisRun run;
    private EvidenceRecord alpha;
    private EvidenceRecord beta;
    private EvidenceRecord gamma;

    @BeforeEach
    void setUp() {
        User owner = fixtures.user("emb-" + System.nanoTime() + "@x.test");
        Project project = fixtures.project("emb-" + System.nanoTime(), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        alpha = record(run, EvidenceType.STATIC_WARNING, "alpha MD5 bam mat khau");
        beta = record(run, EvidenceType.TEST_RESULT, "beta kiem thu dang nhap");
        gamma = record(run, EvidenceType.CODE_CONTEXT, "gamma ngu canh ma");

        when(embeddingClient.embed(anyList(), eq("document"))).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return new EmbeddingClient.Result("model-gia", 3, texts.stream().map(EmbeddingServiceTest::axisOf).toList());
        });
    }

    private EvidenceRecord record(AnalysisRun target, EvidenceType type, String summary) {
        EvidenceRecord record = fixtures.evidenceRecord(target, type, EvidenceSource.parser, "src/A.java", 1);
        jdbcTemplate.update("UPDATE evidence_record SET summary = ? WHERE id = ?", summary, record.getId());
        record.setSummary(summary);
        return record;
    }

    private static List<Double> axisOf(String text) {
        if (text.contains("alpha")) {
            return List.of(1.0, 0.0, 0.0);
        }
        if (text.contains("beta")) {
            return List.of(0.0, 1.0, 0.0);
        }
        return List.of(0.0, 0.0, 1.0);
    }

    private void queryVector(double x, double y, double z) {
        when(embeddingClient.embed(anyList(), eq("query")))
                .thenReturn(new EmbeddingClient.Result("model-gia", 3, List.of(List.of(x, y, z))));
    }

    @Test
    void sinhVectorChoMoiBangChung_vaGhiSoLuongLenRun() {
        embeddingService.embedRun(run.getId());
        // Chạy lại phải là upsert, không nhân đôi hàng (PK chunk_id + model).
        embeddingService.embedRun(run.getId());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence_embedding v JOIN evidence_record e ON e.id = v.evidence_record_id"
                        + " WHERE e.analysis_run_id = ?",
                Integer.class,
                run.getId());
        assertThat(rows).isEqualTo(3);
        AnalysisRun reloaded = fixtures.reloadRun(run.getId());
        assertThat(reloaded.getEmbeddingCount()).isEqualTo(3);
        assertThat(reloaded.getEmbeddingModel()).isEqualTo("model-gia");
        assertThat(reloaded.getEmbeddingError()).isNull();
    }

    @Test
    void agentServiceLoi_runVanThanhCong_vaLyDoDuocGhiLai() {
        when(embeddingClient.embed(anyList(), eq("document")))
                .thenThrow(new EmbeddingUnavailableException("agent-service tra ve 503: Embedding chua bat"));

        embeddingService.embedRunQuietly(run.getId());

        AnalysisRun reloaded = fixtures.reloadRun(run.getId());
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(reloaded.getEmbeddingCount()).isZero();
        assertThat(reloaded.getEmbeddingError()).contains("Embedding chua bat");
    }

    /** So chữ đứng trước (chính xác), tìm theo nghĩa bù vào sau, mỗi kết quả nói rõ cách tìm ra. */
    @Test
    void timKetHop_soChuTruoc_roiBuBangKetQuaGanNghia() {
        embeddingService.embedRun(run.getId());
        queryVector(0.1, 1.0, 0.0);

        List<EvidenceQueryService.SearchHit> hits =
                evidenceQueryService.searchHybrid(run.getId(), "gamma", null, null, 3);

        assertThat(hits).extracting(h -> h.record().getShortId())
                .containsExactly(gamma.getShortId(), beta.getShortId(), alpha.getShortId());
        assertThat(hits).extracting(EvidenceQueryService.SearchHit::matchedBy)
                .containsExactly("keyword", "semantic", "semantic");
    }

    /** Câu hỏi tự do không khớp chữ nào: so chữ trả rỗng, tìm theo nghĩa vẫn ra đúng bằng chứng. */
    @Test
    void cauHoiKhongKhopChu_vanTimRaNhoTheoNghia() {
        embeddingService.embedRun(run.getId());
        queryVector(0.0, 1.0, 0.0);

        List<EvidenceQueryService.SearchHit> hits =
                evidenceQueryService.searchHybrid(run.getId(), "test nao lien quan toi login", null, null, 1);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.record().getShortId()).isEqualTo(beta.getShortId());
            assertThat(hit.matchedBy()).isEqualTo("semantic");
        });
    }

    @Test
    void timTheoNghia_tonTrongBoLocLoai_vaKhongBaoGioRaNgoaiRun() {
        embeddingService.embedRun(run.getId());
        AnalysisRun other = fixtures.succeededRun(fixtures.snapshot(fixtures.project("khac-" + System.nanoTime())), 1);
        record(other, EvidenceType.TEST_RESULT, "beta cua run khac");
        embeddingService.embedRun(other.getId());
        queryVector(0.0, 1.0, 0.0);

        List<EvidenceQueryService.SearchHit> hits = evidenceQueryService.searchHybrid(
                run.getId(), "khong khop chu nao", Set.of(EvidenceType.STATIC_WARNING), null, 5);

        assertThat(hits).extracting(h -> h.record().getShortId()).containsExactly(alpha.getShortId());
        assertThat(hits).allMatch(h -> h.record().getAnalysisRunId().equals(run.getId()));
    }

    @Test
    void runChuaCoVector_chiTimBangSoChu() {
        queryVector(0.0, 1.0, 0.0);

        assertThat(evidenceQueryService.searchHybrid(run.getId(), "khong khop chu nao", null, null, 5))
                .isEmpty();
    }
}
