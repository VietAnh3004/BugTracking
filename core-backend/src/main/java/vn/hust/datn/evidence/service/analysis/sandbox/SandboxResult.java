package vn.hust.datn.evidence.service.analysis.sandbox;

import java.util.Map;

/**
 * Artifact thô mà sandbox thu được, CHƯA diễn giải.
 *
 * <p>Cố ý không parse gì ở đây: {@code SandboxRunner} chỉ chịu trách nhiệm "chạy được và lấy được
 * file ra". Việc đọc file là của bốn parser đã có từ giai đoạn A, và việc phán xét kết quả là của
 * bốn tầng kiểm tính tỉnh táo. Trộn ba việc đó vào một lớp thì không test riêng được cái nào.
 *
 * @param artifacts    tên file → nội dung, lấy nguyên từ {@code /work/.analysis/}
 * @param imageDigest  digest của image ĐÃ CHẠY, không phải tag — tag {@code :latest} trỏ tới image
 *                     khác nhau sau mỗi lần dựng lại
 * @param durationMs   thời gian tường của cả hai pha
 * @param logs         log của từng pha, để đưa vào {@code error_message} khi hỏng
 */
public record SandboxResult(
        Map<String, byte[]> artifacts, String imageDigest, long durationMs, Map<String, String> logs) {}
