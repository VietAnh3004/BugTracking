package vn.hust.datn.evidence.service.citation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.ClaimCitationResolution;
import vn.hust.datn.evidence.domain.ClaimVerdict;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.service.citation.AnswerDraft.ClaimDraft;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Hậu kiểm trích dẫn trên Evidence Store thật.
 *
 * <p>Dùng {@code short_id} do chính Postgres sinh ra chứ không tự bịa chuỗi: cột đó là GENERATED
 * (V2), nên một test tự đặt "EV-0001" sẽ xanh ngay cả khi công thức sinh ID đổi — mà đổi công thức
 * thì mọi trích dẫn LLM đã ghi trong DB trỏ vào hư không.
 */
class CitationValidatorTest extends AbstractIntegrationTest {

    @Autowired
    private CitationValidator validator;

    @Autowired
    private TestFixtures fixtures;

    private AnalysisRun run;
    private AnalysisRun runKhac;
    private EvidenceRecord canhBao;
    private EvidenceRecord nguCanhMa;

    @BeforeEach
    void setUp() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        run = fixtures.succeededRun(snapshot, 2);
        runKhac = fixtures.succeededRun(snapshot, 1);

        canhBao = fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "UserService.java", 66);
        nguCanhMa = fixtures.evidenceRecord(run, EvidenceType.CODE_CONTEXT, EvidenceSource.parser, "UserService.java", 60);
    }

    private AnswerDraft answer(ClaimVerdict verdict, ClaimDraft... claims) {
        return new AnswerDraft(verdict, "tom tat", List.of(claims), List.of(), List.of());
    }

    private ClaimDraft claim(String text, String... citedIds) {
        return new ClaimDraft(text, List.of(citedIds), new BigDecimal("0.800"));
    }

    @Test
    void trichDanDungTrongRun_giuNguyenVerdict() {
        ValidatedAnswer result = validator.validate(
                run.getId(),
                answer(
                        ClaimVerdict.LIKELY_TRUE_POSITIVE,
                        claim("Dong 66 giai tham chieu user co the null", canhBao.getShortId())));

        assertThat(result.verdict()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
        assertThat(result.downgradedFrom()).isNull();
        assertThat(result.claims()).singleElement().satisfies(c -> {
            assertThat(c.supported()).isTrue();
            assertThat(c.downgradedFrom()).isNull();
            assertThat(c.citations()).singleElement().satisfies(citation -> {
                assertThat(citation.resolution()).isEqualTo(ClaimCitationResolution.RESOLVED);
                assertThat(citation.evidenceRecordId()).isEqualTo(canhBao.getId());
            });
        });
    }

    /**
     * Trọng tâm của cả cơ chế: nhận định không có căn cứ bị HẠ CẤP chứ không bị XOÁ. Xoá thì
     * Unsupported Claim Rate luôn bằng 0 và luôn vô nghĩa (kế hoạch §4).
     */
    @Test
    void nhanDinhKhongCoTrichDan_biHaCapChuKhongBiXoa() {
        ValidatedAnswer result =
                validator.validate(run.getId(), answer(ClaimVerdict.LIKELY_FALSE_POSITIVE, claim("Toi tin la vay")));

        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().getFirst().verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(result.claims().getFirst().downgradedFrom()).isEqualTo(ClaimVerdict.LIKELY_FALSE_POSITIVE);
        assertThat(result.unsupportedClaimCount()).isEqualTo(1);
    }

    /** ID bịa nhưng đúng khuôn dạng: đây là hallucination, phải phân biệt với lỗi gõ sai định dạng. */
    @Test
    void idKhongTonTaiODau_laUnknownId() {
        ValidatedAnswer result =
                validator.validate(run.getId(), answer(ClaimVerdict.LIKELY_TRUE_POSITIVE, claim("bia", "EV-FFFF")));

        assertThat(result.claims().getFirst().citations().getFirst().resolution())
                .isEqualTo(ClaimCitationResolution.UNKNOWN_ID);
        assertThat(result.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
    }

    /**
     * ID có thật nhưng ở run khác — lỗi NGỮ CẢNH, không phải bịa. Gộp nó vào UNKNOWN_ID sẽ giấu
     * mất một loại lỗi hoàn toàn khác: mô hình nhớ ID từ hội thoại trước.
     */
    @Test
    void idThuocRunKhac_laWrongRun() {
        EvidenceRecord oRunKhac =
                fixtures.evidenceRecord(runKhac, EvidenceType.STATIC_WARNING, EvidenceSource.semgrep, "CartService.java", 25);

        ValidatedAnswer result = validator.validate(
                run.getId(), answer(ClaimVerdict.LIKELY_TRUE_POSITIVE, claim("nham run", oRunKhac.getShortId())));

        assertThat(result.claims().getFirst().citations().getFirst().resolution())
                .isEqualTo(ClaimCitationResolution.WRONG_RUN);
        assertThat(result.claims().getFirst().citations().getFirst().evidenceRecordId())
                .as("WRONG_RUN khong duoc tro ve evidence nao — CHECK trong V2 cam dieu do")
                .isNull();
    }

    @Test
    void chuoiKhongPhaiEvidenceId_laMalformed() {
        ValidatedAnswer result = validator.validate(
                run.getId(), answer(ClaimVerdict.LIKELY_TRUE_POSITIVE, claim("mo ta thay vi id", "bang chung ve null")));

        assertThat(result.claims().getFirst().citations().getFirst().resolution())
                .isEqualTo(ClaimCitationResolution.MALFORMED);
    }

    /** Nguyên văn LLM viết phải đi xuống DB không suy suyển — đó là mẫu số thật (V2 ghi rõ). */
    @Test
    void giuNguyenVanChuoiLlmViet() {
        ValidatedAnswer result = validator.validate(
                run.getId(), answer(ClaimVerdict.LIKELY_TRUE_POSITIVE, claim("x", "  ev-ffff  ", "[EV-0001]")));

        assertThat(result.claims().getFirst().citations())
                .extracting(ValidatedAnswer.ResolvedCitation::citedShortIdRaw)
                .containsExactly("  ev-ffff  ", "[EV-0001]");
    }

    /** Một trích dẫn hợp lệ là đủ để nhận định đứng vững; những cái hỏng vẫn được ghi lại. */
    @Test
    void motTrichDanHopLeGiuaNhieuCaiHong_vanDuocChongLung() {
        ValidatedAnswer result = validator.validate(
                run.getId(),
                answer(
                        ClaimVerdict.LIKELY_TRUE_POSITIVE,
                        claim("hon hop", "EV-FFFF", canhBao.getShortId(), "khong phai id")));

        assertThat(result.claims().getFirst().supported()).isTrue();
        assertThat(result.claims().getFirst().verdict()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
        assertThat(result.citationCount()).isEqualTo(3);
        assertThat(result.resolvedCitationCount()).isEqualTo(1);
    }

    /**
     * Verdict tổng thể chỉ trụ được khi có ÍT NHẤT một nhận định đứng vững. Không có điều kiện này
     * thì một câu trả lời gồm toàn nhận định vô căn cứ vẫn ra {@code LIKELY_FALSE_POSITIVE} — đúng
     * cái bẫy mà vulnshop dựng lên.
     */
    @Test
    void moiNhanDinhDeuVoCanCu_haCapCaCauTraLoi() {
        ValidatedAnswer result = validator.validate(
                run.getId(),
                answer(ClaimVerdict.LIKELY_FALSE_POSITIVE, claim("a", "EV-FFFF"), claim("b", "khong phai id")));

        assertThat(result.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(result.downgradedFrom()).isEqualTo(ClaimVerdict.LIKELY_FALSE_POSITIVE);
        assertThat(result.unsupportedClaimCount()).isEqualTo(2);
    }

    /** Một nhận định vững là đủ cho cả câu trả lời, dù nhận định khác vẫn bị hạ cấp riêng. */
    @Test
    void motNhanDinhVung_cauTraLoiGiuNguyen_nhungNhanDinhKiaVanBiHaCap() {
        ValidatedAnswer result = validator.validate(
                run.getId(),
                answer(
                        ClaimVerdict.LIKELY_TRUE_POSITIVE,
                        claim("co can cu", nguCanhMa.getShortId()),
                        claim("khong can cu")));

        assertThat(result.verdict()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
        assertThat(result.downgradedFrom()).isNull();
        assertThat(result.claims().get(1).verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
    }

    /**
     * Câu trả lời KHÔNG có nhận định nào cũng bị hạ cấp: không có gì để bám vào bằng chứng thì
     * không có gì để tin. Nếu bỏ qua ca này, một agent hỏng trả về mảng rỗng sẽ đi qua hậu kiểm
     * mà vẫn giữ nguyên verdict.
     */
    @Test
    void khongCoNhanDinhNao_haCap() {
        ValidatedAnswer result = validator.validate(run.getId(), answer(ClaimVerdict.LIKELY_TRUE_POSITIVE));

        assertThat(result.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(result.downgradedFrom()).isEqualTo(ClaimVerdict.LIKELY_TRUE_POSITIVE);
    }

    /**
     * LLM vốn đã kết luận {@code INSUFFICIENT_EVIDENCE} thì KHÔNG được ghi là "đã hạ cấp" — ghi
     * vào sẽ thổi phồng số lần hạ cấp trong báo cáo bằng những lần chẳng có gì xảy ra.
     */
    @Test
    void vonDaInsufficient_khongGhiLaHaCap() {
        ValidatedAnswer result =
                validator.validate(run.getId(), answer(ClaimVerdict.INSUFFICIENT_EVIDENCE, claim("khong chac")));

        assertThat(result.verdict()).isEqualTo(ClaimVerdict.INSUFFICIENT_EVIDENCE);
        assertThat(result.downgradedFrom()).isNull();
        assertThat(result.claims().getFirst().downgradedFrom()).isNull();
    }
}
