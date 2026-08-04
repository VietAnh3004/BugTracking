package vn.hust.datn.evidence.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Đọc artifact THẬT của analyzer trong {@code docs/samples/vulnshop/} làm fixture cho test parser.
 *
 * <p>Cố ý KHÔNG chép sang {@code src/test/resources}: bản chép sẽ trôi khỏi bản thật mà không ai
 * biết, và khi đó test parser xanh trong khi parser không còn đọc nổi artifact hiện hành. Đây
 * chính là lý do {@code OpenApiSnapshotTest} đọc thẳng {@code ../docs/api/openapi.json}.
 *
 * <p>Đi ngược cây thư mục để tìm gốc repo thay vì dùng đường dẫn tương đối cứng: working
 * directory của {@code mvn} là {@code core-backend/}, nhưng của IDE có thể là gốc repo.
 */
public final class SampleArtifacts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SampleArtifacts() {}

    public static byte[] spotbugsXml() {
        return read(samplesDir().resolve("spotbugs.xml"));
    }

    public static byte[] semgrepSarif() {
        return read(samplesDir().resolve("semgrep.sarif"));
    }

    public static byte[] jacocoXml() {
        return read(samplesDir().resolve("jacoco.xml"));
    }

    public static List<Path> surefireReports() {
        Path dir = samplesDir().resolve("surefire-reports");
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc " + dir, e);
        }
    }

    /**
     * Con số kỳ vọng của vulnshop. Đọc từ {@code expected-analysis.json} thay vì hard-code trong
     * test: file đó là nguồn sự thật duy nhất và đã được CI đối chiếu qua
     * {@code run-analysis.sh --check}, nên test tự đỏ khi artifact đổi mà không ai cập nhật.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> expectedAnalysis() {
        Path path = repoRoot().resolve("case-study").resolve("vulnshop").resolve("expected-analysis.json");
        try {
            return MAPPER.readValue(Files.readAllBytes(path), Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc " + path, e);
        }
    }

    public static int expectedInt(String group, String key) {
        Object value = expectedAnalysis().get(group).get(key);
        return ((Number) value).intValue();
    }

    private static Path samplesDir() {
        return repoRoot().resolve("docs").resolve("samples").resolve("vulnshop");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path dir = current; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("docs").resolve("samples").resolve("vulnshop"))) {
                return dir;
            }
        }
        throw new IllegalStateException(
                "Khong tim thay docs/samples/vulnshop khi di nguoc tu " + current
                        + ". Chay lai analyzer bang ./case-study/run-analysis.sh vulnshop neu thu muc do trong.");
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc artifact mau " + path, e);
        }
    }
}
