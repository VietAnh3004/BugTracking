package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import vn.hust.datn.evidence.AbstractWebIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.AnalysisRunStatus;
import vn.hust.datn.evidence.domain.EvidenceLinkMethod;
import vn.hust.datn.evidence.domain.EvidenceLinkRelation;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Ngày 5 của tuần 4: 5 tool RPC qua request thật (không gọi service trực tiếp như
 * {@code EvidenceQueryServiceTest}) — xác nhận cả tầng JSON snake_case, path routing, và 3 nhánh
 * lỗi ánh xạ đúng problem type đã chốt trong ADR 0005.
 */
class EvidenceToolControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    @Test
    void getWarning_traVeJsonSnakeCaseDungGiaTri() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "WEAK_MESSAGE_DIGEST_MD5", WarningTool.spotbugs, "PasswordHasher.java", 40);

        String body =
                """
                {"analysis_run_id": "%s", "evidence_id": "%s"}
                """
                        .formatted(run.getId(), warning.getEvidenceRecord().getShortId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_warning").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule_id").value("WEAK_MESSAGE_DIGEST_MD5"))
                .andExpect(jsonPath("$.file_path").value("PasswordHasher.java"))
                .andExpect(jsonPath("$.severity_normalized").exists());
    }

    @Test
    void getWarning_evidenceKhongTonTaiTraVe404EvidenceNotFound() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);

        String body = """
                {"analysis_run_id": "%s", "evidence_id": "EV-KHONG-TON-TAI"}
                """.formatted(run.getId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_warning").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/evidence-not-found"));
    }

    @Test
    void getWarning_shortIdOChayKhacTraVe404EvidenceWrongRun() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshotA = fixtures.snapshot(project);
        AnalysisRun runA = fixtures.succeededRun(snapshotA, 1);
        Warning warning = fixtures.warning(runA, "SQL_INJECTION_SPRING_JDBC", WarningTool.spotbugs, "UserRepository.java", 35);

        RepositorySnapshot snapshotB = fixtures.snapshot(project);
        AnalysisRun runB = fixtures.succeededRun(snapshotB, 1);

        String body =
                """
                {"analysis_run_id": "%s", "evidence_id": "%s"}
                """
                        .formatted(runB.getId(), warning.getEvidenceRecord().getShortId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_warning").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/evidence-wrong-run"));
    }

    @Test
    void getWarning_runDangChayTraVe409RunNotQueryable() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.runWithStatus(snapshot, AnalysisRunStatus.RUNNING);

        String body = """
                {"analysis_run_id": "%s", "evidence_id": "EV-BAT-KY"}
                """.formatted(run.getId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_warning").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/run-not-queryable"));
    }

    @Test
    void getRelatedTests_itemCoDayDuMethodVaWeight() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 2);
        Warning warning = fixtures.warning(run, "ES_COMPARING_PARAMETER_STRING_WITH_EQ", WarningTool.spotbugs, "OrderStatusChecker.java", 18);
        EvidenceRecord failedTest = fixtures.evidenceRecord(run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "OrderStatusCheckerTest.java", 42);
        fixtures.link(
                failedTest, warning.getEvidenceRecord(), EvidenceLinkRelation.SUPPORTS, EvidenceLinkMethod.NAMING_CONVENTION, 0.4);

        String body =
                """
                {"analysis_run_id": "%s", "evidence_id": "%s"}
                """
                        .formatted(run.getId(), warning.getEvidenceRecord().getShortId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_related_tests").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].method").value("NAMING_CONVENTION"))
                .andExpect(jsonPath("$.items[0].weight").value(0.4))
                .andExpect(jsonPath("$.items[0].short_id").exists());
    }

    @Test
    void searchEvidence_traVeItemsVaTotal() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "PasswordHasher.java", 40);

        String body =
                """
                {"analysis_run_id": "%s", "query": "PasswordHasher"}
                """
                        .formatted(run.getId());

        getMockMvc()
                .perform(post("/api/v1/tools/search_evidence").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void getCoverage_traVeRong() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "DM_CONVERT_CASE", WarningTool.spotbugs, "TextNormalizer.java", 10);

        String body =
                """
                {"analysis_run_id": "%s", "evidence_id": "%s"}
                """
                        .formatted(run.getId(), warning.getEvidenceRecord().getShortId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_coverage").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getCodeContext_traVeRongVaKhongLoi() throws Exception {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        Warning warning = fixtures.warning(run, "DM_CONVERT_CASE", WarningTool.spotbugs, "TextNormalizer.java", 10);

        String body =
                """
                {"analysis_run_id": "%s", "evidence_id": "%s"}
                """
                        .formatted(run.getId(), warning.getEvidenceRecord().getShortId());

        getMockMvc()
                .perform(post("/api/v1/tools/get_code_context").contentType(MediaType.APPLICATION_JSON).content(body).with(serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
