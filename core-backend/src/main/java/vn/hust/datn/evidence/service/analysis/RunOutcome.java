package vn.hust.datn.evidence.service.analysis;

import java.util.Map;

import vn.hust.datn.evidence.domain.AnalysisRunStatus;

/**
 * Kết cục đã QUYẾT ĐỊNH XONG của một lần phân tích, trước khi chạm vào DB.
 *
 * <p>Tồn tại như một kiểu riêng để ép đúng một thứ tự: <b>quyết định trạng thái trước, UPDATE
 * sau</b>. Nếu để tầng điều phối vừa suy luận vừa gán từng cột trong một transaction đang mở, thì
 * {@code ck_run_not_silently_empty} và {@code ck_run_paths_not_all_unresolved} sẽ nổ ra <i>lúc
 * flush</i> — tức bên trong chính transaction đang cố ghi trạng thái, và cả nhánh xử lý lỗi cũng
 * chết theo. Đây là cùng họ bài học với {@code RefreshTokenService}: ghi-rồi-ném trong một
 * transaction luôn phải xét lại.
 *
 * @param cleanAckNote bắt buộc khi {@code SUCCEEDED_CLEAN}; phải SINH TỪ SỐ ĐO THẬT, không phải
 *     một chuỗi hằng — một câu cố định thì "dự án sạch" và "analyzer im lặng hỏng" đọc lên giống
 *     hệt nhau
 * @param result       null khi hỏng trước lúc materialize kịp chạy
 */
public record RunOutcome(
        AnalysisRunStatus status,
        String cleanAckNote,
        String errorMessage,
        MaterializationResult result,
        Map<String, String> toolVersions,
        SandboxInfo sandbox) {

    public static RunOutcome succeeded(MaterializationResult result, Map<String, String> toolVersions) {
        return new RunOutcome(AnalysisRunStatus.SUCCEEDED, null, null, result, toolVersions, null);
    }

    public static RunOutcome clean(String cleanAckNote, MaterializationResult result, Map<String, String> toolVersions) {
        return new RunOutcome(AnalysisRunStatus.SUCCEEDED_CLEAN, cleanAckNote, null, result, toolVersions, null);
    }

    public static RunOutcome failed(String errorMessage, MaterializationResult result, Map<String, String> toolVersions) {
        return new RunOutcome(AnalysisRunStatus.FAILED, null, errorMessage, result, toolVersions, null);
    }

    /**
     * Gắn dấu vết sandbox vào một kết cục ĐÃ quyết định xong, giữ nguyên mọi thứ khác.
     *
     * <p>Tách khỏi các factory ở trên để giữ nguyên thứ tự "quyết định trạng thái trước, ghi sau":
     * dấu vết sandbox là dữ liệu đi kèm, không được tham gia vào việc chọn trạng thái.
     */
    public RunOutcome withSandbox(SandboxInfo info) {
        return new RunOutcome(status, cleanAckNote, errorMessage, result, toolVersions, info);
    }
}
