package vn.hust.datn.evidence.testsupport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
     * Artifact do CHÍNH SANDBOX sinh ra (khác {@link #spotbugsXml()} vốn do
     * {@code run-analysis.sh} chạy trên host): nó chứa thêm 6 finding của mã canary trong image.
     *
     * <p>Sinh lại bằng {@code ./infra/analyzer/build.sh} rồi copy từ {@code /work/.analysis/}.
     */
    public static byte[] sandboxSpotbugsXml() {
        return read(sandboxSamplesDir().resolve("spotbugs.xml"));
    }

    public static byte[] sandboxSemgrepSarif() {
        return read(sandboxSamplesDir().resolve("semgrep.sarif"));
    }

    private static Path sandboxSamplesDir() {
        return repoRoot().resolve("docs").resolve("samples").resolve("vulnshop-sandbox");
    }

    /** Đọc một file báo cáo trả về từ {@link #surefireReports()}. */
    public static byte[] bytesOf(Path path) {
        return read(path);
    }

    /**
     * Mọi đường dẫn file của vulnshop, tương đối gốc {@code case-study/vulnshop/} — chính là dạng
     * mà {@code snapshot_file.path} có sau khi người dùng nén thư mục đó và upload.
     *
     * <p>Dùng cây nguồn THẬT thay vì một danh sách bịa: bốn analyzer khai đường dẫn theo bốn định
     * dạng khác nhau và chỉ cây thật mới chứng minh được thuật toán khớp hậu tố đưa cả bốn về
     * cùng một chỉ mục. {@code target/} bị loại vì nó là sản phẩm biên dịch, không nằm trong repo
     * người dùng upload.
     */
    public static List<String> vulnshopSourcePaths() {
        Path root = repoRoot().resolve("case-study").resolve("vulnshop");
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> !path.startsWith("target/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Khong duyet duoc cay nguon " + root, e);
        }
    }

    /**
     * Toàn bộ cây nguồn vulnshop kèm NỘI DUNG, dùng cho fixture snapshot có object thật.
     *
     * <p>Từ tuần 10-11 pipeline phân tích đọc mã nguồn thật (JavaParser dựng {@code CODE_CONTEXT}),
     * nên một snapshot chỉ có chỉ mục đường dẫn không còn đủ để test đường ingest: nó sẽ cho ra 0
     * ngữ cảnh và run bị {@code decide()} đánh FAILED — đúng như thiết kế, nhưng không phải thứ
     * test muốn kiểm.
     */
    public static Map<String, String> vulnshopSourceContents() {
        Map<String, String> contents = new LinkedHashMap<>();
        for (String path : vulnshopSourcePaths()) {
            contents.put(path, vulnshopSourceText(path));
        }
        return contents;
    }

    /**
     * Nội dung một file trong cây nguồn vulnshop, đọc UTF-8.
     *
     * <p>Đi kèm {@link #vulnshopSourcePaths()}: cái kia cho danh sách đường dẫn để dựng chỉ mục
     * snapshot, cái này cho nội dung để {@code CodeContextExtractor} có thứ mà parse. Dùng mã
     * nguồn THẬT vì mã tự bịa luôn sạch hơn thực tế — không có comment tiếng Việt có dấu, không
     * có generic lồng nhau, không có annotation Spring.
     *
     * @param relativePath đường dẫn tương đối gốc {@code case-study/vulnshop}, dùng dấu {@code /}
     */
    public static String vulnshopSourceText(String relativePath) {
        Path path = repoRoot().resolve("case-study").resolve("vulnshop").resolve(relativePath.replace('/', File.separatorChar));
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc " + path, e);
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
