package evidence.pertest;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.tools.ExecFileLoader;

/**
 * Chuyển các file {@code <n>.exec} của {@link PerTestListener} thành MỘT file JSON:
 * test nào chạy qua dòng nào.
 *
 * <pre>
 * {"format":"evidence-per-test-coverage","version":1,
 *  "tests":[{"class":"...Test","method":"m","status":"PASSED",
 *            "lines":{"vn/hust/.../CartService.java":[12,13]}}]}
 * </pre>
 *
 * <p>Đường dẫn dạng {@code <package>/<SourceFile>.java} — ĐÚNG dạng mà jacoco.xml dùng, để
 * backend chuẩn hoá về đường dẫn snapshot bằng cùng một {@code SnapshotPathResolver}.
 *
 * <p>"Dòng được chạy" = FULLY hoặc PARTLY covered — cùng định nghĩa "ci > 0" của JaCoCo mà bảng
 * coverage_line đang dùng. Hai định nghĩa lệch nhau thì "dòng này được phủ" có hai nghĩa trong
 * cùng một hệ thống.
 *
 * <p>Dùng: {@code java PerTestCoverage <thư mục pertest> <target/classes> <file JSON ra>}.
 */
public final class PerTestCoverage {

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("merge")) {
            merge(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length != 3) {
            System.err.println("Dung: PerTestCoverage <pertestDir> <classesDir> <outJson>");
            System.exit(2);
        }
        Path pertestDir = Path.of(args[0]);
        Path classesDir = Path.of(args[1]);
        Path out = Path.of(args[2]);

        Path manifest = pertestDir.resolve("manifest.tsv");
        if (!Files.exists(manifest)) {
            System.err.println("[evidence-pertest] khong co manifest.tsv — khong co du lieu tung test");
            System.exit(3);
        }

        List<String> entries = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        int written = 0;
        try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("{\"format\":\"evidence-per-test-coverage\",\"version\":1,\"tests\":[");
            for (String entry : entries) {
                String[] parts = entry.split("\t");
                if (parts.length != 4) {
                    continue;
                }
                Map<String, TreeSet<Integer>> lines = coveredLines(pertestDir.resolve(parts[0] + ".exec"), classesDir);
                if (written > 0) {
                    w.write(',');
                }
                w.write("{\"class\":" + json(parts[1]) + ",\"method\":" + json(parts[2]) + ",\"status\":"
                        + json(parts[3]) + ",\"lines\":{");
                int fileIndex = 0;
                for (Map.Entry<String, TreeSet<Integer>> file : lines.entrySet()) {
                    if (fileIndex++ > 0) {
                        w.write(',');
                    }
                    w.write(json(file.getKey()) + ":" + file.getValue().toString().replace(" ", ""));
                }
                w.write("}}");
                written++;
            }
            w.write("]}");
        }
        System.out.println("[evidence-pertest] " + written + " test -> " + out);
    }

    /**
     * Gộp MỌI file .exec trong {@code pertestDir} (từng test + phần giữa các test) với phần còn lại
     * agent ghi khi JVM thoát ({@code targetExec}), rồi ghi đè {@code targetExec}.
     *
     * <p>Bắt buộc chạy TRƯỚC {@code jacoco:report}: listener reset agent sau mỗi test, nên tự
     * {@code targetExec} chỉ còn phần chạy sau test cuối — sinh báo cáo từ nó là ra 0 dòng được phủ.
     */
    private static void merge(Path pertestDir, Path targetExec) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        if (Files.exists(targetExec)) {
            loader.load(targetExec.toFile());
        }
        int files = 0;
        if (Files.isDirectory(pertestDir)) {
            try (var stream = Files.list(pertestDir)) {
                for (Path exec : stream.filter(p -> p.toString().endsWith(".exec")).sorted().toList()) {
                    loader.load(exec.toFile());
                    files++;
                }
            }
        }
        loader.save(targetExec.toFile(), false);
        System.out.println("[evidence-pertest] gop " + files + " file .exec vao " + targetExec);
    }

    private static Map<String, TreeSet<Integer>> coveredLines(Path execFile, Path classesDir) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execFile.toFile());
        CoverageBuilder builder = new CoverageBuilder();
        new Analyzer(loader.getExecutionDataStore(), builder).analyzeAll(classesDir.toFile());

        Map<String, TreeSet<Integer>> result = new TreeMap<>();
        for (ISourceFileCoverage source : builder.getSourceFiles()) {
            if (source.getLineCounter().getCoveredCount() == 0) {
                continue;
            }
            TreeSet<Integer> covered = new TreeSet<>();
            for (int line = source.getFirstLine(); line <= source.getLastLine(); line++) {
                int status = source.getLine(line).getStatus();
                if (status == ICounter.FULLY_COVERED || status == ICounter.PARTLY_COVERED) {
                    covered.add(line);
                }
            }
            String path = source.getPackageName().isEmpty()
                    ? source.getName()
                    : source.getPackageName() + "/" + source.getName();
            result.put(path, covered);
        }
        return result;
    }

    private static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
