package vn.hust.datn.evidence.service.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import vn.hust.datn.evidence.domain.EvidenceType;

/**
 * Vector embedding của bằng chứng: sinh sau mỗi lần phân tích, và tìm theo nghĩa cho
 * {@code search_evidence}.
 *
 * <h2>Không bao giờ làm hỏng phân tích hay tìm kiếm</h2>
 *
 * Embedding là phần TĂNG CƯỜNG cho so chữ, không phải điều kiện. Gemini hết quota hay mất mạng thì
 * lần phân tích vẫn {@code SUCCEEDED} và tìm kiếm vẫn chạy bằng so chữ — nhưng lý do được ghi vào
 * {@code analysis_run.embedding_error} và hiện ra giao diện. Lặng lẽ tắt tìm theo nghĩa là để Agent
 * nhận "không tìm thấy" rồi kết luận {@code INSUFFICIENT_EVIDENCE} nhầm mà không ai biết vì sao.
 *
 * <h2>Không gọi mạng trong transaction</h2>
 *
 * Gọi agent-service xong mới mở transaction để ghi: giữ kết nối DB suốt một lượt gọi mạng vài
 * giây là cách nhanh nhất vét cạn pool khi có vài lần phân tích cùng lúc.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Trần độ dài văn bản đưa vào mô hình — summary dài bất thường không được làm nổ request. */
    static final int MAX_TEXT_CHARS = 2000;

    /** Giới hạn {@code EmbedRequest.texts} phía agent-service. */
    private static final int REQUEST_BATCH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    public EmbeddingService(
            JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Sinh vector cho mọi bằng chứng của run; KHÔNG ném. Thất bại được ghi vào
     * {@code analysis_run.embedding_error}.
     */
    public void embedRunQuietly(UUID runId) {
        try {
            embedRun(runId);
        } catch (RuntimeException e) {
            log.warn("Khong sinh duoc vector cho run {}: {}", runId, e.getMessage());
            recordFailure(runId, e);
        }
    }

    /** Ghi lý do thất bại lên run, để màn hình cảnh báo hiện ra thay vì im lặng. */
    public void recordFailure(UUID runId, RuntimeException cause) {
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        jdbcTemplate.update(
                "UPDATE analysis_run SET embedding_error = ? WHERE id = ?",
                reason.length() > 1000 ? reason.substring(0, 1000) + "…" : reason,
                runId);
    }

    /** Như {@link #embedRunQuietly} nhưng NÉM khi thất bại — cho endpoint người dùng bấm tay. */
    public void embedRun(UUID runId) {
        List<EvidenceText> evidence = jdbcTemplate.query(
                """
                SELECT id, type, summary, symbol, file_path FROM evidence_record
                WHERE analysis_run_id = ? ORDER BY seq_in_run
                """,
                (rs, i) -> new EvidenceText(
                        rs.getObject("id", UUID.class),
                        textOf(
                                rs.getString("type"),
                                rs.getString("summary"),
                                rs.getString("symbol"),
                                rs.getString("file_path"))),
                runId);

        String model = null;
        List<List<Double>> vectors = new ArrayList<>();
        for (int start = 0; start < evidence.size(); start += REQUEST_BATCH) {
            List<String> texts = evidence.subList(start, Math.min(start + REQUEST_BATCH, evidence.size())).stream()
                    .map(EvidenceText::text)
                    .toList();
            EmbeddingClient.Result result = embeddingClient.embed(texts, "document");
            model = result.model();
            vectors.addAll(result.vectors());
        }

        String usedModel = model;
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < evidence.size(); i++) {
                EvidenceText item = evidence.get(i);
                List<Double> vector = vectors.get(i);
                jdbcTemplate.update(
                        """
                        INSERT INTO evidence_embedding
                            (chunk_id, model, evidence_record_id, chunk_ordinal, text_chunk, dims, embedding)
                        VALUES (?, ?, ?, 0, ?, ?, ?::vector)
                        ON CONFLICT (chunk_id, model) DO UPDATE
                            SET text_chunk = EXCLUDED.text_chunk, dims = EXCLUDED.dims, embedding = EXCLUDED.embedding
                        """,
                        item.id() + ":0",
                        usedModel,
                        item.id(),
                        item.text(),
                        vector.size(),
                        toVectorLiteral(vector));
            }
            jdbcTemplate.update(
                    "UPDATE analysis_run SET embedding_model = ?, embedding_count = ?, embedding_error = NULL WHERE id = ?",
                    usedModel,
                    evidence.size(),
                    runId);
        });
    }

    /**
     * Evidence ID gần nghĩa nhất với câu hỏi, trong ĐÚNG một run. Run chưa có vector, hay không
     * nhúng được câu hỏi ⇒ danh sách rỗng — người gọi vẫn còn kết quả so chữ.
     *
     * <p>Thứ tự tất định: khoảng cách cosine, rồi {@code seq_in_run} để phá hoà — thực nghiệm chạy
     * mỗi cảnh báo 3 lần và cần cùng một câu hỏi cho cùng một danh sách.
     */
    public List<UUID> searchSemantic(UUID runId, String query, Set<EvidenceType> types, String filePath, int limit) {
        // queryForList chứ không queryForObject: run chưa có vector là ca BÌNH THƯỜNG (0 hàng), và
        // queryForObject ném EmptyResultDataAccessException cho đúng ca đó.
        List<String> models = jdbcTemplate.queryForList(
                "SELECT embedding_model FROM analysis_run WHERE id = ? AND embedding_count > 0", String.class, runId);
        String model = models.isEmpty() ? null : models.getFirst();
        if (model == null) {
            return List.of();
        }
        List<Double> queryVector;
        try {
            queryVector = embeddingClient.embed(List.of(query), "query").vectors().getFirst();
        } catch (EmbeddingUnavailableException e) {
            log.warn("Khong nhung duoc cau hoi cho run {}, chi tim bang so chu: {}", runId, e.getMessage());
            return List.of();
        }

        List<Object> args = new ArrayList<>(List.of(model, runId));
        StringBuilder sql = new StringBuilder(
                """
                SELECT e.id FROM evidence_record e
                JOIN evidence_embedding v ON v.evidence_record_id = e.id AND v.model = ? AND v.chunk_ordinal = 0
                WHERE e.analysis_run_id = ?
                """);
        if (types != null && !types.isEmpty()) {
            sql.append(" AND e.type IN (")
                    .append(types.stream().map(t -> "?").collect(Collectors.joining(",")))
                    .append(')');
            types.stream().map(Enum::name).sorted().forEach(args::add);
        }
        if (filePath != null && !filePath.isBlank()) {
            sql.append(" AND e.file_path = ?");
            args.add(filePath);
        }
        sql.append(" ORDER BY v.embedding <=> ?::vector, e.seq_in_run LIMIT ?");
        args.add(toVectorLiteral(queryVector));
        args.add(limit);

        return jdbcTemplate.queryForList(sql.toString(), UUID.class, args.toArray());
    }

    /**
     * Văn bản đưa vào mô hình: loại + tóm tắt + symbol + đường dẫn. Kèm loại để câu hỏi "test nào
     * hỏng" gần với bằng chứng FAILED_TEST hơn là với cảnh báo có chữ "test" trong tên luật.
     */
    static String textOf(String type, String summary, String symbol, String filePath) {
        StringBuilder text = new StringBuilder(type).append(": ").append(summary == null ? "" : summary);
        if (symbol != null && !symbol.isBlank()) {
            text.append(" | ").append(symbol);
        }
        if (filePath != null && !filePath.isBlank()) {
            text.append(" | ").append(filePath);
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text.toString();
    }

    static String toVectorLiteral(List<Double> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private record EvidenceText(UUID id, String text) {}
}
