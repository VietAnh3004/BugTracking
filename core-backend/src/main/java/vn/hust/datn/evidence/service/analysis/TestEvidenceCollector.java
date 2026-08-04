package vn.hust.datn.evidence.service.analysis;

import java.util.List;

import vn.hust.datn.evidence.domain.EvidenceSource;

/**
 * Thu bằng chứng từ artifact kiểm thử. Điểm mở rộng {@code TestEvidenceCollector} mà kế hoạch
 * tuần 8-9 gọi tên.
 *
 * <p>Kiểu trả về là {@code List<EvidenceDraft>} chứ không phải một record chung vì hai
 * implementation trả về hai LOẠI draft khác nhau: Surefire sinh {@link TestCaseDraft} +
 * {@link StackTraceDraft}, JaCoCo sinh {@code CoverageDraft}. Sealed interface giữ được kiểm tra
 * vét cạn ở {@code EvidenceMaterializer}.
 *
 * <p>Thuần hàm như {@link StaticAnalysisAdapter}: không DB, không biết {@code analysis_run}, không
 * chuẩn hoá đường dẫn.
 */
public interface TestEvidenceCollector {

    /** {@code surefire} hoặc {@code jacoco}. */
    EvidenceSource supportedSource();

    boolean looksLikeSupportedArtifact(byte[] rawArtifact);

    /** Phiên bản công cụ đọc từ chính artifact; null khi artifact không khai (JaCoCo). */
    String toolVersion(byte[] rawArtifact);

    List<EvidenceDraft> collect(byte[] rawArtifact);
}
