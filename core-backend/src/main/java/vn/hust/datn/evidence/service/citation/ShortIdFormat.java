package vn.hust.datn.evidence.service.citation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nhận dạng và chuẩn hoá Evidence ID dạng ngắn ({@code EV-XXXX}).
 *
 * <p>Thuần hàm, không đụng DB — tách ra để phân biệt được hai câu hỏi khác hẳn nhau: "chuỗi này
 * có phải một Evidence ID không" (ở đây) và "Evidence ID này có tồn tại không"
 * ({@link CitationValidator}). Gộp lại thì {@code MALFORMED} và {@code UNKNOWN_ID} lẫn vào nhau,
 * mà hai thứ đó nói về hai lỗi hoàn toàn khác của mô hình.
 *
 * <p>Khuôn dạng đến từ cột sinh tự động trong V2: {@code 'EV-' || upper(lpad(to_hex(seq_in_run),
 * 3, '0')) || upper(substr(md5(id), 1, 1))} — luôn đúng bốn ký tự hex viết hoa.
 */
public final class ShortIdFormat {

    private static final Pattern WELL_FORMED = Pattern.compile("^EV-[0-9A-F]{4}$");

    private ShortIdFormat() {}

    /**
     * Chuẩn hoá để TRA CỨU: bỏ khoảng trắng thừa và viết hoa.
     *
     * <p>Chỉ chuẩn hoá đúng hai thứ này, có chủ đích. Gỡ thêm ngoặc vuông, backtick hay dấu chấm
     * câu quanh ID nghe có vẻ tử tế nhưng sẽ biến Evidence Citation Accuracy thành phép đo lòng
     * khoan dung của chính hàm này thay vì phép đo mức bám bằng chứng của mô hình.
     *
     * <p>Giá trị trả về KHÔNG BAO GIỜ thay thế {@code claim_citation.cited_short_id_raw} — cột đó
     * giữ nguyên văn LLM viết, kể cả khi sai (V2 ghi rõ đó là mẫu số thật).
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(java.util.Locale.ROOT);
    }

    /** {@code true} khi chuỗi có hình dạng của một Evidence ID, bất kể nó có tồn tại hay không. */
    public static boolean isWellFormed(String raw) {
        return WELL_FORMED.matcher(normalize(raw)).matches();
    }

    /** Dạng chuẩn để tra DB, hoặc rỗng nếu chuỗi không phải Evidence ID. */
    public static Optional<String> canonical(String raw) {
        String normalized = normalize(raw);
        return WELL_FORMED.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }
}
