package vn.hust.datn.evidence.service.analysis.sandbox;

/**
 * Cổng chạy analyzer trong môi trường cách ly.
 *
 * <h2>Vì sao là interface chứ không phải một lớp gọi thẳng Docker</h2>
 *
 * ADR 0008 chốt Docker-out-of-Docker qua socket của host, và ghi rõ rằng tách thành một service
 * {@code analyzer-runner} riêng là đúng về kiến trúc nhưng sai thời điểm. Interface này là chỗ giữ
 * để tách sau trong nửa ngày: mọi thứ phía trên chỉ biết {@link SandboxRequest} và
 * {@link SandboxResult}, không biết container tồn tại.
 *
 * <p>Nó cũng là chỗ cắm cho {@code CliSandboxRunner} — phương án lùi nếu {@code docker-java} lại
 * vỡ với Docker Desktop trên máy này (đã có sẹo: Testcontainers 1.21.0 nhận
 * {@code BadRequestException 400} với body rỗng, xem {@code pom.xml}).
 *
 * <h2>Mặc định là TẮT</h2>
 *
 * {@code app.analysis.runner} mặc định {@code disabled} và khi đó bean được nạp là
 * {@link DisabledSandboxRunner}. Deploy lên môi trường không có Docker daemon vẫn khởi động bình
 * thường và mọi thứ khác vẫn chạy — đường nạp artifact thủ công không phụ thuộc gì vào sandbox.
 */
public interface SandboxRunner {

    /**
     * Sandbox có dùng được ngay bây giờ không. Tách khỏi {@link #run} để endpoint trả 503 kèm
     * thông điệp rõ THAY VÌ tạo một {@code analysis_run} rồi để nó chết — không tạo bia mộ cho một
     * việc còn chưa bắt đầu được.
     */
    boolean isAvailable();

    /**
     * @throws SandboxException khi hạ tầng hỏng (không nói chuyện được với daemon, hết thời gian,
     *     không copy được artifact ra). Analyzer chạy xong nhưng cho kết quả xấu KHÔNG đi đường
     *     này — đó là chuyện của bốn tầng kiểm tính tỉnh táo, và nó cần artifact để phân tích.
     */
    SandboxResult run(SandboxRequest request);
}
