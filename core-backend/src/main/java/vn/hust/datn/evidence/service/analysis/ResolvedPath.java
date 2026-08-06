package vn.hust.datn.evidence.service.analysis;

/**
 * Kết quả đối chiếu một đường dẫn của analyzer với chỉ mục snapshot.
 *
 * @param rawPath  nguyên văn analyzer khai, luôn giữ lại để reparse được khi heuristic sai
 * @param filePath đã chuẩn hoá về {@code snapshot_file.path}; NULL khi không đối chiếu được —
 *                 NULL không bao giờ làm mất bằng chứng, chỉ làm mất khả năng bấm vào nó
 * @param kind     mức tin cậy, xem {@link PathMatchKind}
 */
public record ResolvedPath(String rawPath, String filePath, PathMatchKind kind) {

    public boolean resolved() {
        return filePath != null;
    }

    /**
     * Có được tính vào {@code analysis_run.location_count} hay không.
     *
     * <p>Mẫu số đó chỉ có nghĩa khi nó đếm những phép đối chiếu THẬT SỰ CÓ THỂ THẤT BẠI. Cộng vào
     * đó các đường dẫn không cần đối chiếu sẽ pha loãng tỉ lệ và làm hỏng chính chỉ số sức khoẻ mà
     * ADR 0004 dựng lên để canh rủi ro lớn nhất còn lại.
     */
    public boolean counted() {
        return kind != PathMatchKind.NOT_APPLICABLE && kind != PathMatchKind.SELF;
    }

    /** Đường dẫn không thuộc snapshot theo bản chất (frame JDK/thư viện) — không phải thất bại. */
    public static ResolvedPath notApplicable(String rawPath) {
        return new ResolvedPath(rawPath, null, PathMatchKind.NOT_APPLICABLE);
    }

    /** Đường dẫn lấy thẳng từ {@code snapshot_file.path}, không qua phép khớp nào — xem {@link PathMatchKind#SELF}. */
    public static ResolvedPath self(String path) {
        return new ResolvedPath(path, path, PathMatchKind.SELF);
    }
}
