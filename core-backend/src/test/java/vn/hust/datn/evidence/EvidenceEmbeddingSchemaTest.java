package vn.hust.datn.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.hust.datn.evidence.domain.AnalysisRun;
import vn.hust.datn.evidence.domain.EvidenceRecord;
import vn.hust.datn.evidence.domain.EvidenceSource;
import vn.hust.datn.evidence.domain.EvidenceType;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Bảng {@code evidence_embedding} của V7 — dựng schema trước, sinh vector sau (tuần 12-13).
 *
 * <p>Bản nâng cấp có schema THẬT của {@link PgvectorSmokeTest}: cái kia chứng minh chuỗi pgvector
 * chạy được trên một bảng tạm, cái này chứng minh chính bảng mà Flyway tạo dùng được.
 *
 * <p>Ba giả định của V7 được kiểm ở đây thay vì tin vào trí nhớ, vì cả ba đều thuộc loại chỉ lộ ra
 * lúc chạy: cột {@code vector} không khai số chiều có ghi được không, CHECK khoá dẫn xuất có chặn
 * đúng không, và PK tổ hợp có cho phép cùng một đoạn tồn tại dưới hai mô hình không.
 */
class EvidenceEmbeddingSchemaTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestFixtures fixtures;

    private static final String INSERT =
            """
            INSERT INTO evidence_embedding
                (chunk_id, model, evidence_record_id, chunk_ordinal, text_chunk, dims, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector)
            """;

    private EvidenceRecord anEvidenceRecord() {
        Project project = fixtures.project("p-" + System.nanoTime());
        RepositorySnapshot snapshot = fixtures.snapshot(project);
        AnalysisRun run = fixtures.succeededRun(snapshot, 1);
        return fixtures.evidenceRecord(run, EvidenceType.STATIC_WARNING, EvidenceSource.spotbugs, "PasswordHasher.java", 40);
    }

    private void insert(UUID recordId, int ordinal, String model, String vector, int dims) {
        jdbcTemplate.update(
                INSERT, recordId + ":" + ordinal, model, recordId, ordinal, "doan van ban", dims, vector);
    }

    /**
     * Lý do tồn tại của cả migration: bảng phải nhận được vector mà KHÔNG cần biết trước số chiều,
     * vì mô hình embedding chỉ chốt ở tuần 12-13 (ADR 0004 §6).
     */
    @Test
    void cotVectorKhongKhaiSoChieu_nhanDuocMoiSoChieu() {
        UUID a = anEvidenceRecord().getId();
        UUID b = anEvidenceRecord().getId();

        insert(a, 0, "text-embedding-3-small", "[0.1,0.2,0.3]", 3);
        insert(b, 0, "mot-model-khac", "[1,2,3,4,5,6,7,8]", 8);

        List<Integer> dims = jdbcTemplate.queryForList(
                "SELECT dims FROM evidence_embedding WHERE evidence_record_id IN (?, ?) ORDER BY dims", Integer.class, a, b);

        assertThat(dims).containsExactly(3, 8);
    }

    /** Truy vấn cosine phải chạy được ngay trên bảng thật, không chỉ trên bảng tạm. */
    @Test
    void truyVanCosineChayDuocTrenBangThat() {
        UUID gan = anEvidenceRecord().getId();
        UUID xa = anEvidenceRecord().getId();
        insert(gan, 0, "m", "[1,0,0]", 3);
        insert(xa, 0, "m", "[0,1,0]", 3);

        UUID nearest = jdbcTemplate.queryForObject(
                """
                SELECT evidence_record_id FROM evidence_embedding
                 WHERE model = 'm' AND evidence_record_id IN (?, ?)
                 ORDER BY embedding <=> '[1,0,0]'::vector LIMIT 1
                """,
                UUID.class,
                gan,
                xa);

        assertThat(nearest).isEqualTo(gan);
    }

    /**
     * {@code model} nằm trong PK để tuần 16 so được nhiều mô hình trên CÙNG một tập bằng chứng —
     * đổi mô hình là thêm hàng, không phải ghi đè dữ liệu cũ.
     */
    @Test
    void cungMotDoanDuoiHaiModel_cungTonTai() {
        UUID recordId = anEvidenceRecord().getId();

        insert(recordId, 0, "model-a", "[1,0,0]", 3);
        insert(recordId, 0, "model-b", "[0,0,1,0]", 4);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence_embedding WHERE evidence_record_id = ?", Integer.class, recordId);

        assertThat(count).isEqualTo(2);
    }

    /** Nhúng lại cùng một đoạn phải là UPSERT, không sinh hàng trùng — đó là lý do chunk_id dẫn xuất. */
    @Test
    void nhungLaiCungMotDoan_laUpsertKhongSinhHangTrung() {
        UUID recordId = anEvidenceRecord().getId();
        insert(recordId, 0, "m", "[1,0,0]", 3);

        jdbcTemplate.update(
                """
                INSERT INTO evidence_embedding
                    (chunk_id, model, evidence_record_id, chunk_ordinal, text_chunk, dims, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (chunk_id, model)
                DO UPDATE SET embedding = EXCLUDED.embedding, dims = EXCLUDED.dims, text_chunk = EXCLUDED.text_chunk
                """,
                recordId + ":0",
                "m",
                recordId,
                0,
                "ban da sua",
                3,
                "[0,1,0]");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evidence_embedding WHERE evidence_record_id = ?", Integer.class, recordId);
        String text = jdbcTemplate.queryForObject(
                "SELECT text_chunk FROM evidence_embedding WHERE evidence_record_id = ?", String.class, recordId);

        assertThat(count).isEqualTo(1);
        assertThat(text).isEqualTo("ban da sua");
    }

    /** Quy ước chunk_id là RÀNG BUỘC, không phải thiện chí — nếu không, upsert idempotent vô nghĩa. */
    @Test
    void chunkIdKhongDungCongThuc_biTuChoi() {
        UUID recordId = anEvidenceRecord().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT, "toi-tu-dat-ten", "m", recordId, 0, "x", 3, "[1,2,3]"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evidence_embedding_chunk_id_derived");
    }

    /**
     * Cột {@code dims} phải NÓI THẬT về vector đi kèm. Không có ràng buộc này thì việc trộn nhầm
     * hai mô hình khác số chiều chỉ lộ ra ở kết quả tìm kiếm — tức là không bao giờ lộ ra.
     */
    @Test
    void dimsKhaiSaiSoChieu_biTuChoi() {
        UUID recordId = anEvidenceRecord().getId();

        assertThatThrownBy(() -> insert(recordId, 0, "m", "[1,2,3]", 99))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_evidence_embedding_dims_truthful");
    }

    /**
     * Canh chính đánh đổi đã chấp nhận ở V7: cột không khai số chiều thì KHÔNG index được. Test
     * này tồn tại để khi tuần 12-13 chốt mô hình và đổi sang {@code vector(N)}, nó đỏ và nhắc
     * người sửa tạo index HNSW — thay vì lặng lẽ chạy quét tuần tự trên hàng chục nghìn vector.
     */
    @Test
    void chuaIndexDuocVectorKhiChuaChotSoChieu() {
        assertThatThrownBy(() -> jdbcTemplate.execute(
                        "CREATE INDEX idx_thu_nghiem ON evidence_embedding USING hnsw (embedding vector_cosine_ops)"))
                .hasMessageContaining("does not have dimensions");
    }
}
