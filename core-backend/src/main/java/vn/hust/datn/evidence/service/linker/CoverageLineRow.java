package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

/**
 * Một dòng độ phủ, kèm file của evidence chứa nó.
 *
 * <p>Giữ nguyên bốn bộ đếm {@code mi/ci/mb/cb} chứ không thu về boolean: "được phủ" theo JaCoCo là
 * {@code ci > 0}, tức COVERED **cộng** PARTIAL. Thu về boolean là mất đúng thông tin phân biệt
 * "chạy qua nhưng chưa hết nhánh" với "chạy hết" — mà LLM cần chính chỗ đó để cân nhắc.
 *
 * @param evidenceRecordId evidence {@code COVERAGE_INFORMATION} cấp sourcefile
 * @param filePath đường dẫn đã chuẩn hoá của evidence đó
 */
public record CoverageLineRow(
        UUID evidenceRecordId, String filePath, int lineNumber, int mi, int ci, int mb, int cb) {

    /** Dòng có được THỰC THI hay không — đúng định nghĩa của JaCoCo. */
    public boolean executed() {
        return ci > 0;
    }

    /** Nhãn ngắn cho {@code rationale}, khớp cột generated {@code coverage_line.status}. */
    public String statusLabel() {
        if (ci == 0) {
            return "MISSED";
        }
        return (mi > 0 || mb > 0) ? "PARTIAL" : "COVERED";
    }
}
