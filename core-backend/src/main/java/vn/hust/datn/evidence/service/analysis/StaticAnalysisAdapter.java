package vn.hust.datn.evidence.service.analysis;

import java.util.List;

import vn.hust.datn.evidence.domain.WarningTool;

/**
 * Adapter cho một công cụ phân tích tĩnh. Điểm mở rộng mà §1bis của kế hoạch gọi tên
 * ({@code StaticAnalysisAdapter}): thêm công cụ mới không phải sửa code cũ.
 *
 * <p><b>Khác {@code SnapshotSourceIngestor} một cách có ý thức.</b> Ingestor có Javadoc ghi rõ
 * "không làm factory/registry vì chỉ một implementation không cần cơ chế chọn lựa". Ở đây có HAI
 * implementation thật ngay từ đầu (SpotBugs, Semgrep) nên buộc phải chọn theo {@link WarningTool}.
 * Đó là thay đổi có lý do, không phải quên quy ước.
 *
 * <p><b>Thuần hàm.</b> Không đụng DB, không biết {@code analysis_run}, không chuẩn hoá đường dẫn.
 * Nhờ vậy unit-test được bằng đúng file trong {@code docs/samples/vulnshop/} thay vì phải dựng
 * Spring context.
 */
public interface StaticAnalysisAdapter {

    WarningTool supportedTool();

    /**
     * Kiểm rẻ trước khi parse — cùng vai trò {@code validateFormat()} của
     * {@code SnapshotSourceIngestor}: từ chối sớm để không tạo bia mộ trong DB cho mọi lần thử
     * sai định dạng.
     */
    boolean looksLikeSupportedArtifact(byte[] rawArtifact);

    /**
     * Phiên bản công cụ đọc từ CHÍNH artifact; null khi artifact không khai (JaCoCo). Ghi vào
     * {@code analysis_run.tool_versions} để tuần 15-16 nói được "kết quả này sinh bởi phiên bản
     * nào" thay vì "chúng tôi có chạy analyzer".
     */
    String toolVersion(byte[] rawArtifact);

    /**
     * @throws InvalidArtifactException khi artifact không đọc được, hoặc không có cảnh báo nào —
     *     0 cảnh báo phải là lỗi ồn ào, không phải một danh sách rỗng lặng lẽ
     */
    List<WarningDraft> parse(byte[] rawArtifact);
}
