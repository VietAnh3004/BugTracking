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

    /** Có được tính vào {@code analysis_run.location_count} hay không. */
    public boolean counted() {
        return kind != PathMatchKind.NOT_APPLICABLE;
    }

    /** Đường dẫn không thuộc snapshot theo bản chất (frame JDK/thư viện) — không phải thất bại. */
    public static ResolvedPath notApplicable(String rawPath) {
        return new ResolvedPath(rawPath, null, PathMatchKind.NOT_APPLICABLE);
    }
}
