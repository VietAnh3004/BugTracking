package vn.hust.datn.evidence.service.analysis;

import java.util.Map;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Một bằng chứng đã parse xong nhưng CHƯA có {@code id}, CHƯA có {@code seq_in_run}, và CHƯA
 * chuẩn hoá đường dẫn.
 *
 * <p>Ranh giới này là điều kiện để parser unit-test được bằng đúng file trong
 * {@code docs/samples/}: parser không đụng DB, không biết {@code analysis_run} tồn tại, không
 * biết snapshot nào. Việc cấp số thứ tự và ghi xuống DB là của {@code EvidenceMaterializer}.
 *
 * <p>Sealed để {@code EvidenceMaterializer} switch vét cạn được — thêm một loại bằng chứng mới mà
 * quên xử lý sẽ là lỗi biên dịch, không phải một nhánh bị bỏ quên trong im lặng. Danh sách
 * {@code permits} mở rộng dần qua từng commit của đợt 8-9.
 */
public sealed interface EvidenceDraft permits WarningDraft {

    EvidenceType type();

    EvidenceSource source();

    /**
     * Đường dẫn NGUYÊN VĂN analyzer khai; null khi analyzer không cung cấp (Surefire). Việc đối
     * chiếu về {@code snapshot_file.path} do {@link SnapshotPathResolver} làm ở tầng trên.
     */
    String rawFilePath();

    Integer startLine();

    Integer endLine();

    String symbol();

    /**
     * Câu tóm tắt một dòng cho prompt LLM và UI. Luôn dựng được kể cả khi vị trí NULL.
     *
     * <p>Dùng đường dẫn NGUYÊN VĂN chứ không phải đường dẫn đã chuẩn hoá: giữ parser thuần hàm
     * quan trọng hơn việc câu tóm tắt trùng khít với {@code file_path}. Trạng thái đối chiếu nằm ở
     * {@code content.path_match_kind} và được UI hiển thị thành nhãn riêng.
     */
    String summary();

    Map<String, Object> content();
}
