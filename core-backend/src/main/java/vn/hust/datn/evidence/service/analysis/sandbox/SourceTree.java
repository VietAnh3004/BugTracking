package vn.hust.datn.evidence.service.analysis.sandbox;

/**
 * Cây mã nguồn đã đóng gói, sẵn sàng đẩy vào container.
 *
 * @param tar        nội dung tar; mọi entry nằm dưới tiền tố {@code src/}
 * @param sourceRoot thư mục gốc dự án TƯƠNG ĐỐI gốc tar ({@code src} hoặc {@code src/<thư mục>}).
 *     Cố ý không phải đường dẫn tuyệt đối trong container: lớp này không được biết sandbox mount
 *     tar vào đâu, nếu không nó lại dính chặt vào một cách chạy cụ thể
 * @param fileCount  số file đã đóng gói — dùng cho log tổng kết và để phát hiện "gói được 0 file"
 */
public record SourceTree(byte[] tar, String sourceRoot, int fileCount, long totalBytes) {}
