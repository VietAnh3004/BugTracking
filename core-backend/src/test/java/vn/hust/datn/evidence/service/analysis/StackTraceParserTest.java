package vn.hust.datn.evidence.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.StackFrameKind;

/** Unit test thuần trên text thô — không XML, không DB. */
class StackTraceParserTest {

    private static final String TEST_CLASS = "vn.hust.datn.casestudy.vulnshop.cart.CartServiceTest";

    /**
     * Frame mang tiền tố module JPMS. Regex tham chiếu ở {@code case-study/summarize.py:25} dùng
     * {@code [\w.$]+} nên KHÔNG có {@code /} và bỏ sót hoàn toàn loại frame này — mà mọi trace của
     * JUnit đều có nó.
     */
    @Test
    void parse_tienToModuleJavaBase_vanRaFrame() {
        List<StackFrameDraft> frames =
                StackTraceParser.parse("at java.base/java.lang.reflect.Method.invoke(Method.java:580)", TEST_CLASS);

        assertThat(frames).hasSize(1);
        StackFrameDraft frame = frames.get(0);
        assertThat(frame.modulePrefix()).isEqualTo("java.base");
        assertThat(frame.declaringClass()).isEqualTo("java.lang.reflect.Method");
        assertThat(frame.methodName()).isEqualTo("invoke");
        assertThat(frame.lineNumber()).isEqualTo(580);
        assertThat(frame.frameKind()).isEqualTo(StackFrameKind.JDK);
    }

    @Test
    void parse_tienToClassloaderAppHaiGachCheo_vanRaFrame() {
        List<StackFrameDraft> frames =
                StackTraceParser.parse("at app//org.junit.jupiter.api.Assertions.fail(Assertions.java:12)", TEST_CLASS);

        assertThat(frames.get(0).modulePrefix()).isEqualTo("app");
        assertThat(frames.get(0).frameKind()).isEqualTo(StackFrameKind.TEST_FRAMEWORK);
    }

    /**
     * {@code (Native Method)} KHÔNG có tên file. Regex tham chiếu đòi {@code :(\d+)} nên bỏ luôn
     * frame; ở đây frame vẫn tồn tại với {@code sourceFileName}/{@code lineNumber} NULL — đúng lý
     * do cột {@code stack_frame.line_number} phải nullable.
     */
    @Test
    void parse_nativeMethodVaUnknownSource_lineNumberNullNhungVanCoFrame() {
        List<StackFrameDraft> frames = StackTraceParser.parse(
                """
                at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                at com.example.Foo.bar(Unknown Source)
                """,
                TEST_CLASS);

        assertThat(frames).hasSize(2);
        assertThat(frames).allSatisfy(frame -> {
            assertThat(frame.sourceFileName()).isNull();
            assertThat(frame.lineNumber()).isNull();
            assertThat(frame.declaringClass()).isNotNull();
        });
    }

    @Test
    void parse_lambdaVaInnerClassVaKhoiKhoiTao_vanRaFrame() {
        List<StackFrameDraft> frames = StackTraceParser.parse(
                """
                at vn.hust.datn.casestudy.vulnshop.cart.Outer$Inner.lambda$compute$0(Outer.java:12)
                at vn.hust.datn.casestudy.vulnshop.cart.SortField.<clinit>(SortField.java:9)
                at vn.hust.datn.casestudy.vulnshop.cart.Cart.<init>(Cart.java:5)
                """,
                TEST_CLASS);

        assertThat(frames).hasSize(3);
        assertThat(frames).extracting(StackFrameDraft::methodName).containsExactly("lambda$compute$0", "<clinit>", "<init>");
        assertThat(frames).extracting(StackFrameDraft::frameKind).containsOnly(StackFrameKind.PRODUCTION);
    }

    /** Inner class {@code Outer$Inner} nằm trong file {@code Outer.java}, không phải Inner.java. */
    @Test
    void rawFilePath_innerClass_dungTenFileCuaLopNgoai() {
        StackFrameDraft frame = StackTraceParser.parse(
                        "at vn.hust.datn.casestudy.vulnshop.cart.Outer$Inner.run(Outer.java:12)", TEST_CLASS)
                .get(0);

        assertThat(frame.rawFilePath()).isEqualTo("vn/hust/datn/casestudy/vulnshop/cart/Outer.java");
    }

    /** Chỉ dòng {@code at } thành frame — {@code Caused by:} và {@code ... 27 more} thì không. */
    @Test
    void parse_causedByVaConNMore_khongThanhFrame() {
        List<StackFrameDraft> frames = StackTraceParser.parse(
                """
                java.lang.RuntimeException: boom
                	at vn.hust.datn.casestudy.vulnshop.cart.CartService.total(CartService.java:9)
                Caused by: java.lang.ArithmeticException: / by zero
                	at vn.hust.datn.casestudy.vulnshop.cart.CartService.avg(CartService.java:25)
                	... 27 more
                """,
                TEST_CLASS);

        assertThat(frames).hasSize(2);
        assertThat(frames).extracting(StackFrameDraft::ordinal).containsExactly(0, 1);
    }

    /** Dòng {@code at } không parse được vẫn phải có hàng — bỏ qua im lặng là thứ dự án này chống. */
    @Test
    void parse_dongAtKhongParseDuoc_raUnknownChuKhongBiBoQua() {
        List<StackFrameDraft> frames = StackTraceParser.parse("at [dinh dang la hoan toan]", TEST_CLASS);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).frameKind()).isEqualTo(StackFrameKind.UNKNOWN);
        assertThat(frames.get(0).rawLine()).isEqualTo("at [dinh dang la hoan toan]");
    }

    @Test
    void parse_phanBietDuNamLoaiFrame() {
        List<StackFrameDraft> frames = StackTraceParser.parse(
                """
                at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
                at vn.hust.datn.casestudy.vulnshop.cart.CartServiceTest.avg(CartServiceTest.java:42)
                at vn.hust.datn.casestudy.vulnshop.cart.CartService.averagePrice(CartService.java:25)
                at java.base/java.lang.reflect.Method.invoke(Method.java:580)
                at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:100)
                """,
                TEST_CLASS);

        assertThat(frames)
                .extracting(StackFrameDraft::frameKind)
                .containsExactly(
                        StackFrameKind.TEST_FRAMEWORK,
                        StackFrameKind.TEST,
                        StackFrameKind.PRODUCTION,
                        StackFrameKind.JDK,
                        StackFrameKind.LIBRARY);
    }

    @Test
    void parse_traceRong_traVeDanhSachRong() {
        assertThat(StackTraceParser.parse(null, TEST_CLASS)).isEmpty();
        assertThat(StackTraceParser.parse("   ", TEST_CLASS)).isEmpty();
    }
}
