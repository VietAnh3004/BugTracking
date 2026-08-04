package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit test thuần, không Spring, không DB — resolver là hàm thuần trên một chỉ mục trong bộ nhớ.
 *
 * <p>Bộ chỉ mục mẫu mô phỏng đúng cấu trúc vulnshop sau khi upload: đường dẫn tương đối gốc repo,
 * có {@code src/main/java} và {@code src/test/java}.
 */
class SnapshotPathResolverTest {

    private static final List<String> VULNSHOP_PATHS = List.of(
            "pom.xml",
            "README.md",
            "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java",
            "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java",
            "src/main/java/vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker.java",
            "src/main/java/vn/hust/datn/casestudy/vulnshop/user/UserRepository.java",
            "src/test/java/vn/hust/datn/casestudy/vulnshop/order/OrderStatusCheckerTest.java");

    private final SnapshotPathResolver resolver = SnapshotPathResolver.of(VULNSHOP_PATHS);

    /**
     * Bài kiểm quan trọng nhất: bốn công cụ khai bốn định dạng khác nhau cho CÙNG một file, cả
     * bốn phải về đúng một {@code snapshot_file.path}.
     */
    @Test
    void resolve_bonDinhDangCuaBonCongCu_veCungMotPath() {
        String expected = "src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java";

        // SpotBugs: package-relative
        assertThat(resolver.resolve("vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java")
                        .filePath())
                .isEqualTo(expected);

        // Semgrep: tuyệt đối trong container Docker
        assertThat(resolver.resolve(
                                "/work/vulnshop/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java")
                        .filePath())
                .isEqualTo(expected);

        // JaCoCo: đã ghép package/@name + '/' + sourcefile/@name
        assertThat(resolver.resolve("vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java")
                        .filePath())
                .isEqualTo(expected);

        // Surefire: suy từ FQCN của frame stack trace
        assertThat(resolver.resolve("vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java")
                        .filePath())
                .isEqualTo(expected);
    }

    /**
     * Chốt chặn cho quyết định "không strip tiền tố": tiền tố container KHÔNG được hard-code ở
     * đâu cả, nên một tiền tố hoàn toàn khác vẫn phải khớp.
     */
    @Test
    void resolve_tienToContainerKhacHan_vanKhop() {
        assertThat(resolver.resolve(
                                "/builds/ci-runner-7/checkout/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java")
                        .filePath())
                .isEqualTo("src/main/java/vn/hust/datn/casestudy/vulnshop/auth/AuthController.java");
    }

    @Test
    void resolve_khopChinhXac_traVeExact() {
        ResolvedPath result = resolver.resolve("pom.xml");

        assertThat(result.kind()).isEqualTo(PathMatchKind.EXACT);
        assertThat(result.filePath()).isEqualTo("pom.xml");
    }

    /** Zip có một thư mục gốc phụ (kiểu GitHub tải về) — chỉ mục đã mang tiền tố đó. */
    @Test
    void resolve_snapshotCoThuMucGocPhu_vanKhop() {
        SnapshotPathResolver nested = SnapshotPathResolver.of(
                List.of("vulnshop-main/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java"));

        ResolvedPath result = nested.resolve("vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java");

        assertThat(result.kind()).isEqualTo(PathMatchKind.SUFFIX);
        assertThat(result.filePath())
                .isEqualTo("vulnshop-main/src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java");
    }

    /**
     * Hai file trùng tên ở hai package. Có package thì phân biệt được; chỉ có tên trần thì
     * KHÔNG ĐƯỢC ĐOÁN — đoán sai làm evidence trỏ vào file khác, tệ hơn nhiều so với không trỏ
     * vào đâu.
     */
    @Test
    void resolve_hangTenTrungOHaiGoi_coPackageThiPhanBietDuoc() {
        SnapshotPathResolver duplicated = SnapshotPathResolver.of(List.of(
                "src/main/java/vn/hust/app/user/User.java", "src/main/java/vn/hust/app/admin/User.java"));

        assertThat(duplicated.resolve("vn/hust/app/admin/User.java").filePath())
                .isEqualTo("src/main/java/vn/hust/app/admin/User.java");

        ResolvedPath bare = duplicated.resolve("User.java");
        assertThat(bare.kind()).isEqualTo(PathMatchKind.AMBIGUOUS);
        assertThat(bare.filePath()).isNull();
    }

    /** Tên file duy nhất trong repo là mức tin cậy thấp nhất — phải phân biệt được với SUFFIX. */
    @Test
    void resolve_chiCoTenFileVaDuyNhat_traVeFilenameUnique() {
        ResolvedPath result = resolver.resolve("OrderStatusChecker.java");

        assertThat(result.kind()).isEqualTo(PathMatchKind.FILENAME_UNIQUE);
        assertThat(result.filePath())
                .isEqualTo("src/main/java/vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker.java");
    }

    @Test
    void resolve_dauGachNguocVaGachDoi_duocChuanHoa() {
        assertThat(resolver
                        .resolve("\\work\\\\vulnshop\\src\\main\\java\\vn\\hust\\datn\\casestudy\\vulnshop\\auth\\PasswordHasher.java")
                        .filePath())
                .isEqualTo("src/main/java/vn/hust/datn/casestudy/vulnshop/auth/PasswordHasher.java");

        assertThat(resolver.resolve("./pom.xml").kind()).isEqualTo(PathMatchKind.EXACT);
    }

    /**
     * Không khớp thì KHÔNG được coi là lỗi làm hỏng bằng chứng: hàm chỉ trả về NULL cho filePath,
     * caller vẫn tạo evidence và cộng vào unresolved_location_count.
     */
    @Test
    void resolve_khongTimThay_traVeNullChuKhongNemLoi() {
        ResolvedPath result = resolver.resolve("com/example/khong/ton/tai/Foo.java");

        assertThat(result.kind()).isEqualTo(PathMatchKind.NOT_FOUND);
        assertThat(result.filePath()).isNull();
        assertThat(result.rawPath()).isEqualTo("com/example/khong/ton/tai/Foo.java");
        assertThat(result.resolved()).isFalse();
        assertThat(result.counted()).isTrue();
    }

    @Test
    void resolve_chuoiRong_traVeNotFound() {
        assertThat(resolver.resolve("").kind()).isEqualTo(PathMatchKind.NOT_FOUND);
        assertThat(resolver.resolve("   ").kind()).isEqualTo(PathMatchKind.NOT_FOUND);
        assertThat(resolver.resolve(null).kind()).isEqualTo(PathMatchKind.NOT_FOUND);
    }

    /**
     * Frame JDK/thư viện KHÔNG thuộc snapshot theo bản chất nên không được tính vào mẫu số. Nếu
     * tính, tỉ lệ thất bại của một run hoàn toàn đúng vẫn là 37/44 ~ 84% trên vulnshop và bộ đếm
     * mất sạch giá trị cảnh báo.
     */
    @Test
    void notApplicable_frameNgoaiSnapshot_khongTinhVaoBoDem() {
        ResolvedPath result = ResolvedPath.notApplicable("java/base/java/lang/reflect/Method.java");

        assertThat(result.kind()).isEqualTo(PathMatchKind.NOT_APPLICABLE);
        assertThat(result.counted()).isFalse();
        assertThat(result.resolved()).isFalse();
    }

    /** Đường dẫn chứa {@code ..} tự nhiên không khớp gì — snapshot_file.path bị CHECK cấm chứa nó. */
    @Test
    void resolve_duongDanChuaDauChamKep_khongKhopGi() {
        assertThat(resolver.resolve("src/main/java/../../../etc/passwd").filePath())
                .isNull();
    }
}
