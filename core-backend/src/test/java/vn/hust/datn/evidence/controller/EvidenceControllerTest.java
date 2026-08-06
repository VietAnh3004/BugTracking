package vn.hust.datn.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.domain.Warning;
import vn.hust.datn.evidence.domain.WarningTool;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Các nhánh mã trạng thái của endpoint chi tiết evidence là HỢP ĐỒNG với giao diện, không phải chi
 * tiết cài đặt: mỗi nhánh ứng một câu khác nhau mà Evidence Explorer hiện cho người dùng.
 *
 * <p>Nhánh dễ làm sai nhất là "không có liên kết nào" — nó phải là 200 với mảng rỗng, KHÔNG phải
 * 404. Sự vắng mặt của bằng chứng chính là cơ sở của verdict {@code INSUFFICIENT_EVIDENCE}; trả
 * 404 ở đó là biến một kết quả thành một lỗi.
 */
class EvidenceControllerTest extends AbstractWebIntegrationTest {

    @Autowired
    private TestFixtures fixtures;

    private User owner;
    private AnalysisRun run;
    private Warning warning;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("ev-%d@test.local".formatted(System.nanoTime()));
        Project project = fixtures.project("ev-%d".formatted(System.nanoTime()), owner);
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 1);
        warning = fixtures.warning(run, "WEAK_MESSAGE_DIGEST_MD5", WarningTool.spotbugs, "PasswordHasher.java", 40);
    }

    private String shortId() {
        return warning.getEvidenceRecord().getShortId();
    }

    private String url(Object runId, String shortId) {
        return "/api/v1/analysis-runs/%s/evidence/%s".formatted(runId, shortId);
    }

    @Test
    void chiTiet_khongCoLienKetNao_traVe200VoiMangRong() throws Exception {
        getMockMvc()
                .perform(get(url(run.getId(), shortId())).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortId").value(shortId()))
                .andExpect(jsonPath("$.type").value(EvidenceType.STATIC_WARNING.name()))
                .andExpect(jsonPath("$.relatedTotal").value(0))
                .andExpect(jsonPath("$.codeContext").isArray())
                .andExpect(jsonPath("$.codeContext").isEmpty())
                .andExpect(jsonPath("$.tests").isEmpty())
                .andExpect(jsonPath("$.coverage").isEmpty())
                // snapshotId phải có mặt để FE dựng link tới mã nguồn mà không phải gọi thêm.
                .andExpect(jsonPath("$.snapshotId").isNotEmpty());
    }

    @Test
    void chiTiet_coLienKet_phanDungNhomVaGiuMethodWeightRelation() throws Exception {
        EvidenceRecord context =
                fixtures.evidenceRecord(run, EvidenceType.CODE_CONTEXT, EvidenceSource.parser, "PasswordHasher.java", 37);
        EvidenceRecord failedTest = fixtures.evidenceRecord(
                run, EvidenceType.FAILED_TEST, EvidenceSource.surefire, "PasswordHasherTest.java", 20);
        EvidenceRecord coverage = fixtures.evidenceRecord(
                run, EvidenceType.COVERAGE_INFORMATION, EvidenceSource.jacoco, "PasswordHasher.java", 1);

        fixtures.link(
                warning.getEvidenceRecord(), context, EvidenceLinkRelation.LOCATED_IN, EvidenceLinkMethod.LINE_EXACT, 1.0);
        fixtures.link(
                failedTest,
                warning.getEvidenceRecord(),
                EvidenceLinkRelation.SUPPORTS,
                EvidenceLinkMethod.NAMING_CONVENTION,
                0.3);
        fixtures.link(
                coverage, warning.getEvidenceRecord(), EvidenceLinkRelation.COVERS, EvidenceLinkMethod.LINE_EXACT, 1.0);

        getMockMvc()
                .perform(get(url(run.getId(), shortId())).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedTotal").value(3))
                .andExpect(jsonPath("$.codeContext[0].shortId").value(context.getShortId()))
                // Hướng có nghĩa với người đọc: "cảnh báo NẰM TRONG đoạn mã" khác "test ỦNG HỘ cảnh báo".
                .andExpect(jsonPath("$.codeContext[0].outgoing").value(true))
                .andExpect(jsonPath("$.tests[0].shortId").value(failedTest.getShortId()))
                .andExpect(jsonPath("$.tests[0].outgoing").value(false))
                // Ba trường này là yêu cầu cứng: thiếu chúng thì giao diện không phân biệt được
                // bằng chứng mạnh với yếu, và tuần 15 không đo được tỉ lệ liên kết yếu.
                .andExpect(jsonPath("$.tests[0].method").value(EvidenceLinkMethod.NAMING_CONVENTION.name()))
                .andExpect(jsonPath("$.tests[0].weight").value(0.3))
                .andExpect(jsonPath("$.tests[0].relation").value(EvidenceLinkRelation.SUPPORTS.name()))
                .andExpect(jsonPath("$.coverage[0].shortId").value(coverage.getShortId()));
    }

    /** Cảnh báo phải mang được DANH SÁCH vị trí — thứ {@code WarningView} của bảng cố ý không có. */
    @Test
    void chiTiet_neoLaCanhBao_kemMetadataRiengVaDanhSachViTri() throws Exception {
        getMockMvc()
                .perform(get(url(run.getId(), shortId())).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning.ruleId").value("WEAK_MESSAGE_DIGEST_MD5"))
                .andExpect(jsonPath("$.warning.tool").value(WarningTool.spotbugs.name()))
                .andExpect(jsonPath("$.warning.locations").isArray());
    }

    /** Evidence không phải cảnh báo thì khối {@code warning} VẮNG MẶT, chứ không phải rỗng. */
    @Test
    void chiTiet_neoKhongPhaiCanhBao_khongCoKhoiWarning() throws Exception {
        EvidenceRecord test =
                fixtures.evidenceRecord(run, EvidenceType.TEST_RESULT, EvidenceSource.surefire, "FooTest.java", 10);

        getMockMvc()
                .perform(get(url(run.getId(), test.getShortId())).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void chiTiet_shortIdKhongTonTai_tra404() throws Exception {
        getMockMvc()
                .perform(get(url(run.getId(), "EV-KHONGCO")).with(authenticatedAs(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/evidence-not-found"));
    }

    /**
     * Phân biệt UNKNOWN_ID với WRONG_RUN là hai CÂU KHÁC NHAU cho người dùng, và cũng chính là hai
     * giá trị {@code ClaimCitationResolution} mà CitationValidator tuần 12-13 dùng lại nguyên xi.
     */
    @Test
    void chiTiet_shortIdOChayKhac_tra404PhanLoaiRieng() throws Exception {
        Project project = fixtures.project("ev2-%d".formatted(System.nanoTime()), owner);
        AnalysisRun runKhac = fixtures.succeededRun(fixtures.snapshot(project), 1);

        getMockMvc()
                .perform(get(url(runKhac.getId(), shortId())).with(authenticatedAs(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/evidence-wrong-run"));
    }

    @Test
    void chiTiet_runDangChay_tra409() throws Exception {
        Project project = fixtures.project("ev3-%d".formatted(System.nanoTime()), owner);
        AnalysisRun dangChay = fixtures.runWithStatus(fixtures.snapshot(project), AnalysisRunStatus.RUNNING);

        getMockMvc()
                .perform(get(url(dangChay.getId(), "EV-0001A")).with(authenticatedAs(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://datn.hust.vn/problems/run-not-queryable"));
    }

    /** Không thuộc về bạn ⇒ 404, KHÔNG phải 403: không rò rỉ sự tồn tại của tài nguyên. */
    @Test
    void chiTiet_runCuaNguoiKhac_tra404ChuKhongPhai403() throws Exception {
        User nguoiKhac = fixtures.user("khac-%d@test.local".formatted(System.nanoTime()));

        getMockMvc()
                .perform(get(url(run.getId(), shortId())).with(authenticatedAs(nguoiKhac)))
                .andExpect(status().isNotFound());
    }

    @Test
    void chiTiet_khongCoToken_tra401() throws Exception {
        getMockMvc().perform(get(url(run.getId(), shortId()))).andExpect(status().isUnauthorized());
    }
}
