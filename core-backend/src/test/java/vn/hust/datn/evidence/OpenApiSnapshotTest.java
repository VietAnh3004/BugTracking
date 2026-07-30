package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Đối chiếu {@code docs/api/openapi.json} (commit trong repo, FE sinh client TypeScript từ đây)
 * với spec thật sinh bởi springdoc.
 *
 * <p>KHÔNG so byte — springdoc không deterministic mặc định (thứ tự key map, tags cấp cao có thể
 * đổi giữa các lần chạy JVM). Cả hai phía đọc qua {@link ObjectMapper} với
 * {@code ORDER_MAP_ENTRIES_BY_KEYS} rồi so chuỗi đã canonical hoá — cách này còn miễn nhiễm CRLF
 * (Git trên Windows biến {@code \n} thành {@code \r\n} khi checkout).
 *
 * <p>Khi lệch, KHÔNG tự sửa file committed — ghi ra {@code target/openapi-actual.json} kèm lệnh
 * cần chạy. Chỉ ghi đè khi chạy với {@code -Dopenapi.snapshot.write=true}; CI không bao giờ
 * truyền cờ đó. Cùng triết lý với {@code run-analysis.sh --check} / {@code check-evidence-model.py}.
 */
class OpenApiSnapshotTest extends AbstractWebIntegrationTest {

    private static final Path COMMITTED_SPEC =
            Path.of("..", "docs", "api", "openapi.json").normalize();
    private static final Path ACTUAL_SPEC_DUMP = Path.of("target", "openapi-actual.json");

    private final ObjectMapper canonicalMapper =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    @Test
    void openApiSpecKhopVoiFileCommit() throws Exception {
        String liveSpecJson = getMockMvc()
                .perform(get("/api/v1/openapi"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String liveCanonical = canonicalize(liveSpecJson);

        boolean writeMode = Boolean.getBoolean("openapi.snapshot.write");
        if (writeMode) {
            Files.createDirectories(COMMITTED_SPEC.getParent());
            Files.writeString(COMMITTED_SPEC, prettyPrint(liveSpecJson), StandardCharsets.UTF_8);
            return;
        }

        if (Files.notExists(COMMITTED_SPEC)) {
            fail("Chưa có %s. Chạy: mvn test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.write=true".formatted(COMMITTED_SPEC));
        }

        String committedCanonical = canonicalize(Files.readString(COMMITTED_SPEC, StandardCharsets.UTF_8));

        if (!committedCanonical.equals(liveCanonical)) {
            dumpActualForDiff(liveSpecJson);
            fail(
                    "Spec OpenAPI đã đổi nhưng docs/api/openapi.json chưa cập nhật. "
                            + "Xem %s để so sánh, rồi chạy: "
                            + "mvn test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.write=true"
                                    .formatted(ACTUAL_SPEC_DUMP.toAbsolutePath()));
        }

        assertThat(committedCanonical).isEqualTo(liveCanonical);
    }

    private String canonicalize(String json) throws IOException {
        JsonNode tree = canonicalMapper.readTree(json);
        return canonicalMapper.writeValueAsString(tree);
    }

    private String prettyPrint(String json) throws IOException {
        JsonNode tree = canonicalMapper.readTree(json);
        return canonicalMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    private void dumpActualForDiff(String liveSpecJson) {
        try {
            Files.createDirectories(ACTUAL_SPEC_DUMP.getParent());
            Files.writeString(ACTUAL_SPEC_DUMP, prettyPrint(liveSpecJson), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Chỉ để tiện debug — không che lỗi thật của assertion phía trên nếu ghi file thất bại.
        }
    }
}
