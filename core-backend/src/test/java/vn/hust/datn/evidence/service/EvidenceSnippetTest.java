package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Mã nguồn trả cho model qua {@code get_code_context}. Thuần đơn vị — chỉ kiểm cách cắt, không cần DB.
 */
class EvidenceSnippetTest {

    private static EvidenceRecord record(EvidenceType type, Map<String, Object> content) {
        EvidenceRecord record = new EvidenceRecord();
        record.setType(type);
        record.setContent(content);
        return record;
    }

    @Test
    void maNganDuocGiuNguyenVan() {
        String code = "int a() {\n    return 1;\n}";

        assertThat(EvidenceQueryService.snippetOf(record(EvidenceType.CODE_CONTEXT, Map.of("snippet", code))))
                .isEqualTo(code);
    }

    @Test
    void maQuaDaiBiCatORanhGioiDongVaNoiRaLaDaCat() {
        String line = "    doSomething();\n";
        String code = line.repeat(400); // ~6000 ký tự, vượt trần

        String result = EvidenceQueryService.snippetOf(record(EvidenceType.CODE_CONTEXT, Map.of("snippet", code)));

        assertThat(result).isNotNull();
        assertThat(result.length()).isLessThan(EvidenceQueryService.MAX_SNIPPET_CHARS + 200);
        assertThat(result).contains("đã cắt bớt").contains(String.valueOf(code.length()));
        // Cắt ở ranh giới dòng: không để lại nửa câu lệnh.
        String kept = result.substring(0, result.indexOf("\n// …"));
        assertThat(kept).endsWith("doSomething();");
    }

    @Test
    void loaiKhacCodeContextHoacThieuMaThiKhongCoSnippet() {
        assertThat(EvidenceQueryService.snippetOf(record(EvidenceType.STATIC_WARNING, Map.of("snippet", "x"))))
                .isNull();
        assertThat(EvidenceQueryService.snippetOf(record(EvidenceType.CODE_CONTEXT, Map.of()))).isNull();
        assertThat(EvidenceQueryService.snippetOf(record(EvidenceType.CODE_CONTEXT, Map.of("snippet", "  "))))
                .isNull();
        assertThat(EvidenceQueryService.snippetOf(record(EvidenceType.CODE_CONTEXT, null))).isNull();
    }
}
