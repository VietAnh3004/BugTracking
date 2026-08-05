package vn.hust.datn.evidence.service.analysis.sandbox;

import java.util.UUID;

/**
 * Đầu vào một lần chạy sandbox.
 *
 * @param runId      dùng làm nhãn {@code evidence.run-id} trên mọi container/volume, để
 *                   {@code SandboxReaper} dọn được đồ mồ côi khi tiến trình chết giữa chừng
 * @param sourceTar  cây mã nguồn đã đóng gói, uid/gid đã đặt đúng user trong image — xem
 *                   {@link SourceTreeBuilder} để biết vì sao uid quan trọng
 * @param sourceRoot đường dẫn thư mục gốc dự án BÊN TRONG tar (thư mục chứa {@code pom.xml} nông
 *                   nhất), tương đối gốc cây
 */
public record SandboxRequest(UUID runId, byte[] sourceTar, String sourceRoot) {}
