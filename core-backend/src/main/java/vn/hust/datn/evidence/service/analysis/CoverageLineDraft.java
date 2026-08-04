package vn.hust.datn.evidence.service.analysis;

import vn.hust.datn.evidence.domain.CoverageStatus;

/**
 * Độ phủ một dòng, giữ nguyên bốn bộ đếm gốc của JaCoCo.
 *
 * @param mi missed instructions
 * @param ci covered instructions — {@code ci > 0} CHÍNH LÀ định nghĩa "dòng được phủ" của JaCoCo
 * @param mb missed branches
 * @param cb covered branches
 */
public record CoverageLineDraft(int lineNumber, int mi, int ci, int mb, int cb) {

    /**
     * Cùng công thức với generated column {@code coverage_line.status}. Ở đây chỉ để parser thống
     * kê cho câu summary; giá trị lưu xuống DB do Postgres tự sinh, không ghi từ tầng ứng dụng.
     */
    public CoverageStatus status() {
        if (ci == 0) {
            return CoverageStatus.MISSED;
        }
        if (mi > 0 || (mb > 0 && cb > 0)) {
            return CoverageStatus.PARTIAL;
        }
        return CoverageStatus.COVERED;
    }

    /** Theo JaCoCo, "được phủ" là {@code ci > 0} — tức COVERED CỘNG PARTIAL, không phải chỉ COVERED. */
    public boolean executed() {
        return ci > 0;
    }
}
