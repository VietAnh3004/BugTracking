-- ==========================================================================================
-- V7 — schema embedding (chưa sinh vector) + bộ đếm của EvidenceLinker.
--
-- Tuần 10-11 làm ba việc: liên kết bằng chứng (EvidenceLinker), sinh CODE_CONTEXT bằng
-- JavaParser, và DỰNG SẴN chỗ cho embedding. Migration này lo phần schema của cả ba.
--
-- MỌI CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN, mỗi cột đúng MỘT — để Postgres tự
-- sinh tên '<table>_<column>_check' đúng quy ước mà EnumCheckConstraintTest tra cứu. Ràng buộc
-- nhiều cột thì đặt tên tường minh 'ck_<table>_<mục đích>'.
-- ==========================================================================================


-- ==========================================================================================
-- 1. evidence_link.analysis_run_id — phi chuẩn hoá CÓ CHỦ ĐÍCH.
--
-- Suy được bằng join qua evidence_record, nhưng giữ ở đây đổi lấy hai thứ:
--
--   (a) EvidenceLinker chạy lại được (POST .../links/rebuild) phải xoá sạch link của ĐÚNG một
--       run trước khi ghi lại. Không có cột này, lệnh xoá là hai subquery IN trên from lẫn to;
--       có nó thì là một lần quét index.
--   (b) Biến bất biến "một link không bao giờ vắt qua hai run" thành thứ KIỂM ĐƯỢC. Trước đây
--       nó chỉ là một lời hứa trong code — mà chuẩn hoá đường dẫn sai thì linker hoàn toàn có
--       thể nối nhầm sang evidence của run khác trên cùng snapshot, và không gì phát hiện ra.
-- ==========================================================================================
ALTER TABLE evidence_link ADD COLUMN analysis_run_id UUID REFERENCES analysis_run (id) ON DELETE CASCADE;

-- Backfill: bảng hiện chưa có hàng nào trên môi trường thật (chưa có writer nào ghi
-- evidence_link tới hết tuần 8-9), nhưng vẫn phải viết — dữ liệu test/dev có thể đã có.
UPDATE evidence_link l
   SET analysis_run_id = (SELECT e.analysis_run_id FROM evidence_record e WHERE e.id = l.from_evidence_id);

ALTER TABLE evidence_link ALTER COLUMN analysis_run_id SET NOT NULL;

COMMENT ON COLUMN evidence_link.analysis_run_id IS
    'Run của CẢ HAI đầu link. Phi chuẩn hoá để rebuild xoá theo run bằng một lần quét index.';

-- Truy vấn của rebuild ("mọi link của run này") và của thống kê tuần 15 ("phân bố theo relation").
CREATE INDEX idx_evidence_link_run ON evidence_link (analysis_run_id, relation);


-- ==========================================================================================
-- 2. Bộ đếm của EvidenceLinker trên analysis_run.
--
-- Cùng lý do tồn tại với unresolved_location_count ở V5: một liên kết KHÔNG sinh ra được trông
-- y hệt "không có bằng chứng liên quan". Không đếm thì hỏng-im-lặng, và UI hiện ba panel trống
-- giống hệt nhau ở cả hai trường hợp.
-- ==========================================================================================
ALTER TABLE analysis_run
    ADD COLUMN link_count             INT NOT NULL DEFAULT 0,
    ADD COLUMN code_context_count     INT NOT NULL DEFAULT 0,
    -- Thất bại MỘT PHẦN cũng phải hiện thành số: 30/33 cảnh báo không có link nào là một tín
    -- hiệu hoàn toàn khác với 0/33, mà link_count đơn lẻ không phân biệt được.
    ADD COLUMN unlinked_warning_count INT NOT NULL DEFAULT 0,
    -- Phân bố theo EvidenceLinkMethod, vd {"STACK_FRAME": 7, "NAMING_CONVENTION": 21}. Đây chính
    -- là số liệu tuần 15 cần để báo cáo "bao nhiêu % liên kết chỉ dựa vào quy ước đặt tên".
    -- JSONB chứ không phải bảng riêng: 8 giá trị enum, đọc nguyên khối, không truy vấn lẻ.
    ADD COLUMN link_counts_by_method  JSONB,

    ADD CONSTRAINT ck_run_link_counts_nonneg
        CHECK (link_count >= 0 AND code_context_count >= 0 AND unlinked_warning_count >= 0),

    ADD CONSTRAINT ck_run_link_methods_object
        CHECK (link_counts_by_method IS NULL OR jsonb_typeof(link_counts_by_method) = 'object'),

    -- Cùng họ ck_run_not_silently_empty và ck_run_paths_not_all_unresolved.
    --
    -- Đây KHÔNG phải phán đoán heuristic mà là bất biến CẤU TRÚC: một CODE_CONTEXT chỉ được sinh
    -- ra VÌ có một vị trí cảnh báo trỏ vào nó, nên hễ code_context_count > 0 thì bắt buộc phải
    -- có ít nhất một liên kết LOCATED_IN. Bằng 0 nghĩa là tầng 1 của linker hỏng — và hỏng theo
    -- kiểu pipeline vẫn báo thành công.
    --
    -- Vế code_context_count = 0 tránh đỏ giả: repo không có file .java nào (chỉ Semgrep quét)
    -- thì không có context nào để mà liên kết, và đó là hợp lệ.
    ADD CONSTRAINT ck_run_links_not_silently_empty
        CHECK (status <> 'SUCCEEDED' OR code_context_count = 0 OR link_count > 0);

COMMENT ON COLUMN analysis_run.link_counts_by_method IS
    'Phân bố evidence_link theo method — nguồn số liệu "tỉ lệ liên kết yếu" của tuần 15.';
COMMENT ON COLUMN analysis_run.unlinked_warning_count IS
    'Số cảnh báo không có liên kết nào. Tách khỏi link_count để thất bại MỘT PHẦN cũng thấy được.';


-- ==========================================================================================
-- 3. evidence_embedding — CHỈ schema, tuần này chưa sinh vector nào.
--
-- ADR 0004 quyết định 6 hoãn bảng này tới khi chọn được mô hình embedding, vì SỐ CHIỀU do mô
-- hình quyết định. Mô hình chỉ chốt ở tuần 12-13 (lúc có agent loop để đo retrieval có tốt hơn
-- thật không), nên bảng dựng ở đây phải chịu được việc CHƯA BIẾT số chiều.
--
-- CREATE EXTENSION nằm ở đây, không chỉ ở infra/init/01-extensions.sql: file init đó chỉ chạy
-- khi container Postgres khởi tạo LẦN ĐẦU với volume rỗng. Testcontainers và mọi môi trường
-- không dùng docker-compose của repo này đều không có nó — PgvectorSmokeTest phải tự CREATE
-- EXTENSION chính là bằng chứng. IF NOT EXISTS nên chạy lại vô hại, và Postgres bỏ qua trước
-- khi kiểm quyền nên không đòi superuser trên môi trường đã có sẵn extension.
-- ==========================================================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE evidence_embedding (
    -- Khoá DẪN XUẤT '<evidence_record_id>:<chunk_ordinal>', ép bằng CHECK phía dưới. Nhờ vậy
    -- nhúng lại cùng một đoạn là upsert idempotent (ON CONFLICT (chunk_id, model) DO UPDATE)
    -- thay vì sinh hàng trùng — và quy ước trở thành ràng buộc cấu trúc, không phải thiện chí.
    chunk_id           TEXT        NOT NULL,
    -- Trong PK để tuần 16 so được NHIỀU mô hình trên cùng một tập bằng chứng: đổi mô hình là
    -- thêm hàng, không phải ghi đè. Không có cột này thì thí nghiệm ablation phải xoá dữ liệu cũ.
    model              TEXT        NOT NULL,
    evidence_record_id UUID        NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    chunk_ordinal      INT         NOT NULL,
    -- Văn bản ĐÃ đưa vào mô hình, nguyên văn. Giữ lại vì cách chia đoạn là heuristic sẽ phải
    -- sửa, và khi sửa thì phải so được đoạn cũ với đoạn mới mà không cần dựng lại từ đầu.
    text_chunk         TEXT        NOT NULL,
    -- Số chiều thực tế của hàng này. Cần vì cột `embedding` KHÔNG khai số chiều (xem dưới) nên
    -- Postgres không tự chặn việc trộn hai mô hình khác số chiều vào cùng một model.
    dims               INT         NOT NULL,
    -- KHÔNG khai số chiều, có chủ đích.
    --
    -- `vector(N)` sẽ chốt cứng N ngay lần migrate đầu. Kể cả khi lấy N từ một placeholder Flyway
    -- cũng vậy: Flyway tính checksum trên FILE THÔ, placeholder thay lúc chạy KHÔNG làm đổi
    -- checksum — nên đổi biến môi trường sau đó thì schema im lặng giữ số chiều cũ và mọi insert
    -- lỗi ở runtime. Placeholder chỉ tạo ảo giác linh hoạt.
    --
    -- (Chính comment này từng làm hỏng migration: viết tên placeholder dạng đô-la-ngoặc-nhọn ngay
    -- trong comment cũng bị Flyway quét và đòi giá trị. Vì vậy ở đây gọi tên nó bằng lời.)
    --
    -- Đánh đổi đã đo trên chính Postgres của dự án: cột không khai số chiều KHÔNG tạo được
    -- index HNSW/IVFFlat ("ERROR: column does not have dimensions"). Chấp nhận được vì tuần này
    -- chưa có vector nào để tìm kiếm. Tuần 12-13, sau khi chốt mô hình:
    --     ALTER TABLE evidence_embedding ALTER COLUMN embedding TYPE vector(N);
    --     CREATE INDEX ... USING hnsw (embedding vector_cosine_ops);
    -- HNSW chứ không IVFFlat: IVFFlat cần dữ liệu để huấn luyện danh sách, tạo trên bảng rỗng
    -- cho ra một index vô dụng mà không báo gì.
    embedding          vector      NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (chunk_id, model),
    UNIQUE (evidence_record_id, chunk_ordinal, model),

    CONSTRAINT ck_evidence_embedding_chunk_id_derived
        CHECK (chunk_id = evidence_record_id::text || ':' || chunk_ordinal),
    CONSTRAINT ck_evidence_embedding_ordinal_nonneg CHECK (chunk_ordinal >= 0),
    -- Cột dims phải NÓI THẬT về vector đi kèm. Không có ràng buộc này thì nó chỉ là một con số
    -- người ta tin, và việc trộn nhầm hai mô hình sẽ lộ ra ở kết quả tìm kiếm chứ không ở lúc ghi.
    CONSTRAINT ck_evidence_embedding_dims_truthful CHECK (dims = vector_dims(embedding)),
    CONSTRAINT ck_evidence_embedding_dims_positive CHECK (dims > 0)
);

COMMENT ON TABLE evidence_embedding IS
    'Vector nhúng của từng đoạn bằng chứng. PK (chunk_id, model) để so nhiều mô hình ở tuần 16.';
COMMENT ON COLUMN evidence_embedding.embedding IS
    'Cố ý KHÔNG khai số chiều tới khi chốt mô hình (ADR 0004 §6). Hệ quả: chưa index được.';
COMMENT ON COLUMN evidence_embedding.chunk_id IS
    'Dẫn xuất "<evidence_record_id>:<chunk_ordinal>", ép bằng CHECK — để nhúng lại là upsert.';

CREATE INDEX idx_evidence_embedding_record ON evidence_embedding (evidence_record_id);
