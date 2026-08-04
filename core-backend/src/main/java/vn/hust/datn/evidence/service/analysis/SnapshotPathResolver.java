package vn.hust.datn.evidence.service.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đối chiếu đường dẫn do analyzer khai với chỉ mục {@code snapshot_file.path} của một snapshot.
 *
 * <h2>Vì sao cần</h2>
 *
 * Bốn công cụ khai đường dẫn theo bốn định dạng không join trực tiếp được (phát hiện 4,
 * {@code docs/evidence-model.md} mục 6):
 *
 * <pre>
 * SpotBugs  vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java   (package-relative)
 * Semgrep   /work/vulnshop/src/main/java/vn/hust/.../AuthController.java  (tuyệt đối trong container)
 * JaCoCo    ghép package/@name + '/' + sourcefile/@name
 * Surefire  KHÔNG có đường dẫn nào — chỉ FQCN và tên file trần trong text thô
 * </pre>
 *
 * Đích cần về là {@code snapshot_file.path} (tương đối gốc repo đã upload), vì đó là thứ duy nhất
 * {@code SnapshotQueryService.readFile} và source viewer tra cứu được.
 *
 * <h2>Vì sao KHÔNG strip tiền tố</h2>
 *
 * Cách hiển nhiên là cắt bỏ {@code /work/vulnshop/src/main/java/} khỏi đường dẫn Semgrep. Sai về
 * nguyên tắc: tiền tố đó chỉ là hệ quả của cách {@code case-study/run-analysis.sh} mount thư mục
 * vào container. Đổi cách mount, đổi máy, hay chạy qua sandbox của core-backend là tiền tố khác
 * ngay — và khi đó MỌI vị trí âm thầm không khớp được, đúng loại hỏng im lặng mà ADR 0004 gọi tên
 * là rủi ro lớn nhất còn lại của mô hình bằng chứng.
 *
 * <h2>Thuật toán</h2>
 *
 * Khớp <b>hậu tố dài nhất và duy nhất</b>: rút dần segment đầu cho tới khi còn đúng một ứng viên.
 * Rút ngắn chỉ có thể làm TĂNG số ứng viên, nên gặp nhiều hơn một ứng viên là dừng ngay với
 * {@link PathMatchKind#AMBIGUOUS} — rút thêm cũng không bao giờ khá hơn.
 *
 * <p><b>Không bao giờ đoán bừa.</b> Không đối chiếu được thì {@code filePath} NULL và bằng chứng
 * vẫn được tạo; cái mất là khả năng bấm vào nó, không phải bản thân bằng chứng. Số lần thất bại
 * được đếm vào {@code analysis_run.unresolved_location_count} để nó hiện thành số thay vì biến
 * mất.
 *
 * <p>Đối tượng này bất biến và dựng MỘT LẦN cho cả run — không phải bean Spring vì nó mang trạng
 * thái riêng của từng snapshot.
 */
public final class SnapshotPathResolver {

    private final Set<String> allPaths;

    /** Tên file trần -> mọi path chứa nó. Cắt không gian tìm kiếm trước khi so hậu tố. */
    private final Map<String, List<String>> byFileName;

    private SnapshotPathResolver(Set<String> allPaths, Map<String, List<String>> byFileName) {
        this.allPaths = allPaths;
        this.byFileName = byFileName;
    }

    public static SnapshotPathResolver of(Collection<String> snapshotFilePaths) {
        Set<String> paths = new HashSet<>(snapshotFilePaths.size() * 2);
        Map<String, List<String>> index = new HashMap<>();
        for (String path : snapshotFilePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            paths.add(path);
            index.computeIfAbsent(fileNameOf(path), key -> new java.util.ArrayList<>())
                    .add(path);
        }
        return new SnapshotPathResolver(paths, index);
    }

    public ResolvedPath resolve(String rawPath) {
        String canonical = canonicalize(rawPath);
        if (canonical.isEmpty()) {
            return new ResolvedPath(rawPath, null, PathMatchKind.NOT_FOUND);
        }
        if (allPaths.contains(canonical)) {
            return new ResolvedPath(rawPath, canonical, PathMatchKind.EXACT);
        }

        List<String> candidates = byFileName.get(fileNameOf(canonical));
        if (candidates == null) {
            return new ResolvedPath(rawPath, null, PathMatchKind.NOT_FOUND);
        }

        // Từ hậu tố DÀI NHẤT (cả chuỗi) rút dần về tên file trần.
        String remaining = canonical;
        while (true) {
            final String suffix = remaining;
            List<String> matches = candidates.stream()
                    .filter(path -> path.equals(suffix) || path.endsWith("/" + suffix))
                    .toList();
            if (matches.size() == 1) {
                // Chỉ khớp được bằng tên file trần là mức tin cậy thấp nhất — tách riêng để tuần
                // 15 đo được tỉ lệ đó thay vì gộp chung vào SUFFIX rồi tưởng mọi thứ đều chắc.
                PathMatchKind kind =
                        suffix.indexOf('/') < 0 ? PathMatchKind.FILENAME_UNIQUE : PathMatchKind.SUFFIX;
                return new ResolvedPath(rawPath, matches.get(0), kind);
            }
            if (matches.size() > 1) {
                // Rút ngắn hậu tố chỉ có thể làm TĂNG số ứng viên, nên không cần thử tiếp.
                return new ResolvedPath(rawPath, null, PathMatchKind.AMBIGUOUS);
            }
            int slash = suffix.indexOf('/');
            if (slash < 0) {
                return new ResolvedPath(rawPath, null, PathMatchKind.NOT_FOUND);
            }
            remaining = suffix.substring(slash + 1);
        }
    }

    /**
     * Chuẩn hoá thô, chưa đụng tới chỉ mục: đổi {@code \} thành {@code /}, gộp {@code //}, bỏ
     * {@code ./} và {@code /} ở đầu, bỏ khoảng trắng thừa.
     *
     * <p>Không xử lý {@code ..}: {@code snapshot_file.path} đã được CHECK ở tầng DB cấm chứa
     * {@code ..} (chống zip-slip từ tuần 5-7), nên một đường dẫn analyzer chứa {@code ..} sẽ tự
     * nhiên không khớp gì — đúng hành vi mong muốn, không cần nhánh riêng.
     */
    private static String canonicalize(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.trim().replace('\\', '/');
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
