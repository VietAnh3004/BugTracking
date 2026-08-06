package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.testsupport.SampleArtifacts;

/**
 * Thuần hàm nên không cần Spring, không cần Postgres, không cần MinIO.
 *
 * <p>Ca "hạnh phúc" chạy trên mã nguồn THẬT của {@code case-study/vulnshop} — cùng lý do bốn parser
 * artifact test bằng file thật trong {@code docs/samples/}: mã tự bịa luôn sạch hơn thực tế. Các ca
 * biên phải tự chế vì vulnshop không chắc có đủ (nó là Spring Boot bình thường, không có record hay
 * sealed).
 */
class CodeContextExtractorTest {

    private final CodeContextExtractor extractor = new CodeContextExtractor();

    private static SortedSet<Integer> lines(int... values) {
        SortedSet<Integer> set = new TreeSet<>();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }

    private static String vulnshopPasswordHasher() {
        return SampleArtifacts.vulnshopSourceText("src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java");
    }

    /**
     * Ca đối chứng của cả tài liệu lẫn test: {@code PasswordHasher.java:40} là ví dụ chạy suốt
     * {@code docs/evidence-model.md} (mục 2 và mục 8), nằm trong
     * {@code checksumForDeduplication}. Nếu extractor trỏ sai method thì mọi liên kết tầng 1 sai.
     */
    @Test
    void mangNguonThat_traVeDungMethodBaoQuanh() {
        String source = vulnshopPasswordHasher();

        CodeContextExtractor.Result result = extractor.extract(
                "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java", source, lines(40));

        assertThat(result.failures()).isEmpty();
        assertThat(result.drafts()).hasSize(1);
        CodeContextDraft draft = result.drafts().getFirst();
        assertThat(draft.type()).isEqualTo(EvidenceType.CODE_CONTEXT);
        assertThat(draft.symbol())
                .isEqualTo("vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#checksumForDeduplication");
        assertThat(draft.kind()).isEqualTo("METHOD");
        assertThat(draft.jacocoMethodName()).isEqualTo("checksumForDeduplication");
        assertThat(draft.startLine()).isLessThan(40);
        assertThat(draft.endLine()).isGreaterThan(40);
        assertThat(draft.content().get("snippet").toString()).contains("MessageDigest.getInstance(\"MD5\")");
    }

    /**
     * Hai cảnh báo trong CÙNG một method chỉ được sinh MỘT context. Không gộp thì 28 cảnh báo
     * SpotBugs của vulnshop cho ra tới 28 Evidence ID gần trùng nhau, mỗi cái ngốn context budget
     * của LLM.
     */
    @Test
    void haiViTriCungMotMethod_chiSinhMotContext() {
        String source = vulnshopPasswordHasher();

        CodeContextExtractor.Result result = extractor.extract(
                "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java", source, lines(40, 41));

        assertThat(result.drafts()).hasSize(1);
    }

    @Test
    void haiViTriKhacMethod_sinhHaiContext() {
        String source = vulnshopPasswordHasher();

        CodeContextExtractor.Result result = extractor.extract(
                "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java", source, lines(26, 40));

        assertThat(result.drafts()).hasSize(2);
        assertThat(result.drafts()).extracting(CodeContextDraft::symbol)
                .containsExactlyInAnyOrder(
                        "vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#hashPassword",
                        "vn.hust.datn.casestudy.vulnshop.auth.PasswordHasher#checksumForDeduplication");
    }

    @Test
    void constructor_dungTenJacocoLaInit() {
        String source =
                """
                package p;
                class Foo {
                    Foo(int x) {
                        System.out.println(x);
                    }
                }
                """;

        CodeContextExtractor.Result result = extractor.extract("Foo.java", source, lines(4));

        assertThat(result.drafts().getFirst().kind()).isEqualTo("CONSTRUCTOR");
        assertThat(result.drafts().getFirst().jacocoMethodName()).isEqualTo("<init>");
        assertThat(result.drafts().getFirst().symbol()).isEqualTo("p.Foo#Foo");
    }

    @Test
    void khoiStatic_dungTenJacocoLaClinit() {
        String source =
                """
                package p;
                class Foo {
                    static int x;
                    static {
                        x = 1;
                    }
                }
                """;

        CodeContextExtractor.Result result = extractor.extract("Foo.java", source, lines(5));

        assertThat(result.drafts().getFirst().kind()).isEqualTo("STATIC_INITIALIZER");
        assertThat(result.drafts().getFirst().jacocoMethodName()).isEqualTo("<clinit>");
    }

    /** JaCoCo tính lệnh khởi tạo field vào {@code <init>}/{@code <clinit>}, không phải method riêng. */
    @Test
    void khoiTaoField_anhXaVeInitHoacClinit() {
        String source =
                """
                package p;
                class Foo {
                    private final String a = compute();
                    private static final String B = compute();
                    static String compute() { return "x"; }
                }
                """;

        assertThat(extractor.extract("Foo.java", source, lines(3)).drafts().getFirst().jacocoMethodName())
                .isEqualTo("<init>");
        assertThat(extractor.extract("Foo.java", source, lines(4)).drafts().getFirst().jacocoMethodName())
                .isEqualTo("<clinit>");
    }

    /** Class lồng nhau: phải chọn phạm vi HẸP NHẤT, và FQN phải mang cả chuỗi class. */
    @Test
    void classLongNhau_chonMethodTrongCung() {
        String source =
                """
                package p;
                class Outer {
                    static class Inner {
                        void doThing() {
                            int x = 1;
                        }
                    }
                }
                """;

        CodeContextDraft draft = extractor.extract("Outer.java", source, lines(5)).drafts().getFirst();

        assertThat(draft.symbol()).isEqualTo("p.Outer.Inner#doThing");
        assertThat(draft.kind()).isEqualTo("METHOD");
    }

    /**
     * Cấu trúc Java 21. Đây là ca canh cạm bẫy nguy hiểm nhất của lớp này: JavaParser cấu hình
     * thiếu {@code LanguageLevel} sẽ parse hỏng CẢ FILE và trả về 0 context — mà pipeline vẫn báo
     * thành công.
     */
    @Test
    void cauTrucJava21_parseDuoc() {
        String source =
                """
                package p;
                sealed interface Shape permits Circle {}
                record Circle(double r) implements Shape {}
                class Use {
                    String describe(Shape s) {
                        return switch (s) {
                            case Circle c -> "tron " + c.r();
                        };
                    }
                }
                """;

        CodeContextExtractor.Result result = extractor.extract("Use.java", source, lines(7));

        assertThat(result.failures()).isEmpty();
        assertThat(result.drafts().getFirst().symbol()).isEqualTo("p.Use#describe");
    }

    /** Mã nguồn người dùng có thể không biên dịch được. Không ném, nhưng phải ĐẾM. */
    @Test
    void fileKhongParseDuoc_khongNemNhungDemThatBai() {
        CodeContextExtractor.Result result = extractor.extract("Hong.java", "class { ((( khong phai java", lines(1));

        assertThat(result.drafts()).isEmpty();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().getFirst()).contains("Hong.java").contains("khong parse duoc");
    }

    /** Semgrep quét cả file không phải Java — ngoài phạm vi, và KHÔNG phải thất bại đáng đếm. */
    @Test
    void fileKhongPhaiJava_bhoQuaVaKhongTinhLaThatBai() {
        CodeContextExtractor.Result result = extractor.extract("app.yml", "key: value", lines(1));

        assertThat(result.drafts()).isEmpty();
        assertThat(result.failures()).isEmpty();
    }

    /**
     * Dòng nằm ngoài mọi khai báo (import, package). Vẫn phải có context: mọi cảnh báo có vị trí
     * cần ĐÚNG MỘT context, nếu không thì "linker sinh 0 link" và "cảnh báo nằm ở chỗ lạ" trông
     * giống hệt nhau.
     */
    @Test
    void dongNgoaiMoiKhaiBao_vanSinhContextDangCuaSo() {
        String source =
                """
                package p;
                import java.util.List;
                class Foo {
                    void x() {}
                }
                """;

        CodeContextDraft draft = extractor.extract("Foo.java", source, lines(2)).drafts().getFirst();

        assertThat(draft.kind()).isEqualTo("WINDOW");
        assertThat(draft.symbol()).isNull();
        assertThat(draft.jacocoMethodName()).isNull();
        assertThat(draft.startLine()).isEqualTo(1);
    }

    /** Method khổng lồ không được biến thành một evidence khổng lồ. */
    @Test
    void methodQuaDai_biCatTheoTran() {
        StringBuilder source = new StringBuilder("package p;\nclass Foo {\n    void big() {\n");
        for (int i = 0; i < CodeContextExtractor.MAX_CONTEXT_LINES + 100; i++) {
            source.append("        int v").append(i).append(" = ").append(i).append(";\n");
        }
        source.append("    }\n}\n");

        CodeContextDraft draft =
                extractor.extract("Foo.java", source.toString(), lines(10)).drafts().getFirst();

        assertThat(draft.endLine() - draft.startLine() + 1).isEqualTo(CodeContextExtractor.MAX_CONTEXT_LINES);
    }

    @Test
    void khongCoViTriNao_khongLamGi() {
        CodeContextExtractor.Result result = extractor.extract("Foo.java", "package p;", new TreeSet<>());

        assertThat(result.drafts()).isEmpty();
        assertThat(result.failures()).isEmpty();
    }

    /** Toàn bộ cây nguồn vulnshop phải parse được — canh chống cấu hình JavaParser trôi. */
    @Test
    void toanBoCayNguonVulnshop_parseDuoc() {
        List<String> javaFiles = SampleArtifacts.vulnshopSourcePaths().stream()
                .filter(path -> path.endsWith(".java"))
                .toList();

        assertThat(javaFiles).isNotEmpty();
        for (String path : javaFiles) {
            CodeContextExtractor.Result result =
                    extractor.extract(path, SampleArtifacts.vulnshopSourceText(path), lines(1));
            assertThat(result.failures()).as("parse %s", path).isEmpty();
        }
    }
}
