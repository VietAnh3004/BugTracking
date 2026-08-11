package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;

import org.springframework.stereotype.Component;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Từ mã nguồn Java + một tập dòng quan tâm → các {@link CodeContextDraft} bao quanh chúng.
 *
 * <h2>Thuần hàm, không đụng DB và không đụng object storage</h2>
 *
 * Cùng ranh giới với bốn parser artifact (xem {@link EvidenceDraft}): người gọi đưa vào nội dung
 * file dạng chuỗi, lớp này không biết snapshot nào, run nào. Nhờ vậy unit test chạy trên chính mã
 * nguồn thật của {@code case-study/vulnshop} mà không cần Postgres hay MinIO.
 *
 * <h2>Không dùng symbol solver</h2>
 *
 * FQN của class ĐƯỢC KHAI BÁO trong file suy được thuần cú pháp: {@code package} + chuỗi tên type
 * lồng nhau. Symbol solver chỉ cần khi phải giải kiểu của một biểu thức — điều ta không làm — và
 * nó đòi classpath đầy đủ của dự án người dùng, thứ snapshot không giữ.
 *
 * <p>Hệ quả đã biết: không có {@code method_desc} (chữ ký JVM), nên EvidenceLinker tầng 2 khớp
 * overload chỉ theo TÊN method. Chấp nhận được vì tầng 2 vốn là tầng yếu nhất, và ghi lại trong
 * {@code content.desc_unavailable} để tuần 15 biết mà trừ hao.
 */
@Component
public class CodeContextExtractor {

    /**
     * Trần số dòng của một context. Một method 3000 dòng mà đưa nguyên vào evidence thì vừa vô
     * dụng cho LLM (ngốn sạch context budget) vừa làm phình bảng.
     */
    static final int MAX_CONTEXT_LINES = 400;

    /** Nửa cửa sổ cho nhánh {@code WINDOW} — vị trí không nằm trong khai báo nào. */
    static final int WINDOW_HALF_HEIGHT = 15;

    /**
     * Kết quả một lần trích: các draft dựng được, và số vị trí KHÔNG dựng được kèm lý do.
     *
     * <p>Đếm thất bại chứ không nuốt: nếu 100% vị trí không ra được context thì đó là hỏng thật
     * (đọc nhầm snapshot, sai encoding, JavaParser cấu hình sai phiên bản ngôn ngữ) — mà triệu
     * chứng bên ngoài lại y hệt "dự án này không có gì để liên kết".
     */
    public record Result(List<CodeContextDraft> drafts, List<String> failures) {}

    /**
     * Bắt buộc khai phiên bản ngôn ngữ TƯỜNG MINH.
     *
     * <p>Mặc định của JavaParser thấp hơn Java 21 sẽ parse hỏng {@code record}, {@code sealed} và
     * pattern matching trong {@code switch} — và hỏng theo kiểu trả về {@code ParseResult} không
     * thành công cho CẢ FILE, tức mất sạch context của file đó mà pipeline vẫn báo thành công.
     * Dự án case study là Spring Boot 3 / Java 21, repo người dùng cũng vậy.
     */
    private static final ParserConfiguration CONFIG =
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

    /**
     * @param filePath đường dẫn ĐÃ chuẩn hoá về {@code snapshot_file.path}
     * @param source toàn văn file, đã giải mã UTF-8
     * @param linesOfInterest các dòng có cảnh báo, tăng dần
     */
    public Result extract(String filePath, String source, SortedSet<Integer> linesOfInterest) {
        if (linesOfInterest.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        if (!filePath.endsWith(".java")) {
            // Semgrep quét cả file không phải Java. Không phải lỗi, và cũng không phải thất bại
            // đáng đếm — chỉ là ngoài phạm vi của một parser Java.
            return new Result(List.of(), List.of());
        }

        String[] lines = source.split("\n", -1);
        // JavaParser mới là thứ quyết định file có đọc được không, nên không tự đoán trước.
        ParseResult<CompilationUnit> parsed = new JavaParser(CONFIG).parse(source);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            String reason = parsed.getProblems().isEmpty()
                    ? "khong ro"
                    : parsed.getProblems().getFirst().getMessage();
            return new Result(
                    List.of(), List.of("%s: khong parse duoc (%s)".formatted(filePath, shorten(reason))));
        }
        CompilationUnit unit = parsed.getResult().get();
        String packageName = unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse("");

        // Gom theo (dòng đầu, dòng cuối) của khai báo bao quanh: nhiều cảnh báo trong CÙNG một
        // method chỉ được sinh MỘT context. Không gộp thì 28 cảnh báo SpotBugs của vulnshop cho ra
        // tới 28 bản sao gần trùng nhau, và mỗi bản là một Evidence ID riêng mà LLM phải đọc.
        Map<String, CodeContextDraft> byRange = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        for (int line : linesOfInterest) {
            Optional<CodeContextDraft> draft = contextFor(unit, packageName, filePath, lines, line);
            if (draft.isEmpty()) {
                failures.add("%s:%d: khong dung duoc ngu canh".formatted(filePath, line));
                continue;
            }
            CodeContextDraft value = draft.get();
            byRange.putIfAbsent(value.startLine() + "-" + value.endLine(), value);
        }
        return new Result(List.copyOf(byRange.values()), List.copyOf(failures));
    }

    private Optional<CodeContextDraft> contextFor(
            CompilationUnit unit, String packageName, String filePath, String[] lines, int line) {
        Optional<BodyDeclaration<?>> enclosing = unit.findAll(BodyDeclaration.class).stream()
                .map(node -> (BodyDeclaration<?>) node)
                .filter(node -> contains(node, line))
                // HẸP NHẤT thắng: một dòng trong method của một inner class nằm trong range của cả
                // inner class, outer class và method. Chỉ method mới là "ngữ cảnh" đúng nghĩa.
                .min(Comparator.comparingInt(CodeContextExtractor::heightOf))
                .map(node -> node);

        if (enclosing.isEmpty()) {
            // Dòng nằm ngoài mọi khai báo (import, package, comment đầu file). VẪN sinh context
            // bằng một cửa sổ quanh nó: mọi cảnh báo có vị trí phải có ĐÚNG MỘT context, nếu không
            // thì "linker sinh 0 link" và "cảnh báo nằm ở chỗ lạ" trông giống hệt nhau.
            int from = Math.max(1, line - WINDOW_HALF_HEIGHT);
            int to = Math.min(lines.length, line + WINDOW_HALF_HEIGHT);
            return Optional.of(draft(filePath, lines, from, to, null, "WINDOW", null, packageName));
        }

        BodyDeclaration<?> node = enclosing.get();
        Range range = node.getRange().orElseThrow();
        int from = range.begin.line;
        int to = Math.min(range.end.line, from + MAX_CONTEXT_LINES - 1);
        String typeFqn = enclosingTypeFqn(node, packageName);

        return Optional.of(switch (node) {
            case MethodDeclaration method ->
                draft(filePath, lines, from, to, typeFqn + "#" + method.getNameAsString(), "METHOD", method.getNameAsString(), packageName);
            case ConstructorDeclaration constructor ->
                draft(filePath, lines, from, to, typeFqn + "#" + constructor.getNameAsString(), "CONSTRUCTOR", "<init>", packageName);
            case InitializerDeclaration initializer -> {
                String jacocoName = initializer.isStatic() ? "<clinit>" : "<init>";
                yield draft(filePath, lines, from, to, typeFqn + "#" + jacocoName, "STATIC_INITIALIZER", jacocoName, packageName);
            }
            case FieldDeclaration field -> {
                String fieldName = field.getVariables().stream()
                        .findFirst()
                        .map(VariableDeclarator::getNameAsString)
                        .orElse("?");
                // JaCoCo tính lệnh khởi tạo field vào <init> (hoặc <clinit> nếu static) — không
                // phải một "method" riêng. Ghi đúng cách JaCoCo gọi để tầng 2 khớp được.
                String jacocoName = field.isStatic() ? "<clinit>" : "<init>";
                yield draft(filePath, lines, from, to, typeFqn + "#field:" + fieldName, "FIELD", jacocoName, packageName);
            }
            case TypeDeclaration<?> type -> draft(filePath, lines, from, to, typeFqn, "TYPE", null, packageName);
            default -> draft(filePath, lines, from, to, typeFqn, "TYPE", null, packageName);
        });
    }

    private CodeContextDraft draft(
            String filePath,
            String[] lines,
            int fromLine,
            int toLine,
            String symbol,
            String kind,
            String jacocoMethodName,
            String packageName) {
        String snippet = sliceLines(lines, fromLine, toLine);
        String label = symbol != null ? symbol : filePath + ":" + fromLine + "-" + toLine;

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("kind", kind);
        content.put("package_name", packageName);
        content.put("snippet", snippet);
        content.put("context_start_line", fromLine);
        content.put("context_end_line", toLine);
        if (jacocoMethodName != null) {
            content.put("jacoco_method_name", jacocoMethodName);
        }
        // Ghi ra để tuần 15 biết mà trừ hao khi đo độ chính xác của liên kết theo method: không có
        // symbol solver nên khớp overload chỉ theo TÊN.
        content.put("desc_unavailable", true);

        return new CodeContextDraft(
                EvidenceType.CODE_CONTEXT,
                EvidenceSource.parser,
                filePath,
                fromLine,
                toLine,
                symbol,
                "Ngữ cảnh mã nguồn %s (%s), dòng %d-%d".formatted(label, kind, fromLine, toLine),
                Map.copyOf(content),
                kind,
                jacocoMethodName);
    }

    /** FQN của type khai báo gần nhất bao quanh {@code node}, gồm cả chuỗi class lồng nhau. */
    private static String enclosingTypeFqn(Node node, String packageName) {
        List<String> names = new ArrayList<>();
        Optional<Node> current = Optional.of(node);
        while (current.isPresent()) {
            if (current.get() instanceof TypeDeclaration<?> type) {
                names.addFirst(type.getNameAsString());
            }
            current = current.get().getParentNode();
        }
        String nested = String.join(".", names);
        if (nested.isEmpty()) {
            return packageName;
        }
        return packageName.isEmpty() ? nested : packageName + "." + nested;
    }

    private static boolean contains(BodyDeclaration<?> node, int line) {
        return node.getRange().map(range -> range.begin.line <= line && line <= range.end.line).orElse(false);
    }

    private static int heightOf(BodyDeclaration<?> node) {
        return node.getRange().map(range -> range.end.line - range.begin.line).orElse(Integer.MAX_VALUE);
    }

    /** 1-based, end inclusive — cùng quy ước với {@code SnapshotQueryService.readFile}. */
    private static String sliceLines(String[] lines, int fromLine, int toLine) {
        StringBuilder out = new StringBuilder();
        for (int i = fromLine; i <= Math.min(toLine, lines.length); i++) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(stripCarriageReturn(lines[i - 1]));
        }
        return out.toString();
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static String shorten(String message) {
        String single = message.replace('\n', ' ').replace('\r', ' ');
        return single.length() <= 200 ? single : single.substring(0, 200) + "...";
    }
}
