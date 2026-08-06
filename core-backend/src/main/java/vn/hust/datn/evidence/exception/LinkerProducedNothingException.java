package vn.hust.datn.evidence.exception;

import java.util.UUID;

/**
 * Chạy lại EvidenceLinker mà không sinh được liên kết nào, trong khi run CÓ ngữ cảnh mã nguồn.
 *
 * <p>Đây là bất biến cấu trúc, không phải phán đoán heuristic: một {@code CODE_CONTEXT} chỉ tồn
 * tại VÌ có một vị trí cảnh báo trỏ vào nó, nên hễ có context mà 0 liên kết thì tầng 1 của linker
 * đã hỏng. Cùng ràng buộc mà {@code ck_run_links_not_silently_empty} ép ở tầng DB.
 *
 * <p>Vì sao phải NÉM chứ không trả 200 kèm số 0: lệnh rebuild XOÁ hết liên kết cũ trước khi ghi
 * lại. Một lần rebuild xoá sạch rồi báo thành công với 0 liên kết là mất dữ liệu trong im lặng —
 * ném ở đây khiến transaction rollback và liên kết cũ còn nguyên.
 */
public class LinkerProducedNothingException extends RuntimeException {

    private final transient UUID runId;

    public LinkerProducedNothingException(UUID runId, int codeContextCount) {
        super("Chay lai lien ket cho run %s ra 0 lien ket trong khi co %d ngu canh ma nguon. Lien ket cu duoc giu nguyen."
                .formatted(runId, codeContextCount));
        this.runId = runId;
    }

    public UUID getRunId() {
        return runId;
    }
}
