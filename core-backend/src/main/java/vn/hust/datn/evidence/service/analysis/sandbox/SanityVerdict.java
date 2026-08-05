package vn.hust.datn.evidence.service.analysis.sandbox;

/**
 * Kết luận của bốn tầng kiểm tính tỉnh táo (ADR 0008 mục 4).
 *
 * @param ok                  artifact đáng tin để dựng bằng chứng hay không
 * @param reason              lý do đọc được khi {@code ok = false}; null khi đạt
 * @param canaryOk            canary ra đủ finding của CẢ HAI bộ rule
 * @param canaryFindingCount  số finding của mã canary — bị LOẠI khỏi {@code finding_count}, giữ
 *                            lại để khi canary đỏ thì đọc được "ra mấy cái" chứ không chỉ true/false
 */
public record SanityVerdict(boolean ok, String reason, boolean canaryOk, int canaryFindingCount) {

    public static SanityVerdict passed(int canaryFindingCount) {
        return new SanityVerdict(true, null, true, canaryFindingCount);
    }

    public static SanityVerdict failed(String reason, boolean canaryOk, int canaryFindingCount) {
        return new SanityVerdict(false, reason, canaryOk, canaryFindingCount);
    }
}
