-- Unified Evidence Model — tầng lõi, đủ cho tuần 4 (API contract) dùng.
--
-- Phạm vi tuần 3 chỉ 5 bảng xương sống: analysis_run, warning, evidence_record, evidence_link,
-- claim (+ claim_citation, tách riêng để giữ mẫu số cho Unsupported Claim Rate). Các bảng "thô"
-- mirror trực tiếp từng công cụ (test_case, stack_frame, coverage_line, coverage_method,
-- warning_location) CHƯA tạo ở đây — chúng không có consumer nào cho tới EvidenceLinker (tuần
-- 10-11) và parser thật (tuần 8-9); tạo sớm sẽ là bảng rỗng không ai biết sai cho tới lúc đó.
-- Thiết kế đầy đủ của chúng nằm ở docs/evidence-model.md, mục "Thiết kế cho V3 (tuần 8-9)".
--
-- Bốn phát hiện từ artifact thật (docs/samples/vulnshop/) quyết định các lựa chọn dưới đây —
-- xem docs/adr/0004-unified-evidence-model-hai-tang.md để biết đầy đủ lý do:
--   1. 3/28 cảnh báo SpotBugs có nhiều hơn 1 vị trí, 5/28 thiếu vị trí "primary" (nhưng vẫn có
--      đúng 1 vị trí) -> vị trí phụ và metadata riêng của tool nằm trong content JSONB, không
--      tách bảng riêng vì tỉ lệ quá nhỏ để trả giá một bảng.
--   2. 3/5 test hỏng không có stack frame nào trỏ về mã production -> không có cột nào ở đây
--      bắt buộc "vị trí production", vì artifact thật không luôn có.
--   3. JaCoCo không cho biết "dòng X thuộc method nào" (method chỉ có dòng đầu, không có dòng
--      cuối, và không theo thứ tự dòng) -> không mô hình hoá quan hệ evidence_record <-> method.
--   4. Không artifact nào có commit SHA -> evidence_record không có cột commit_sha; định danh
--      thật của một lần phân tích là analysis_run_id (qua repository_snapshot).

-- ==========================================================================================
-- analysis_run — một lần chạy đủ 4 analyzer trên một snapshot.
-- ==========================================================================================
CREATE TABLE analysis_run (
    id             UUID PRIMARY KEY,
    snapshot_id    UUID        NOT NULL REFERENCES repository_snapshot (id) ON DELETE CASCADE,
    status         TEXT        NOT NULL DEFAULT 'RUNNING'
                       CHECK (status IN ('RUNNING', 'SUCCEEDED', 'SUCCEEDED_CLEAN', 'FAILED')),
    finding_count  INT         NOT NULL DEFAULT 0,
    clean_ack_note TEXT,
    error_message  TEXT,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,

    -- "0 cảnh báo = lỗi" (ADR 0003) ép ở đây, không chỉ trong code Java: một run không thể
    -- chuyển sang SUCCEEDED nếu không có finding nào, trừ khi đi qua SUCCEEDED_CLEAN kèm lý do
    -- tường minh (dự án sạch thật là một trạng thái riêng, không phải một run rỗng bị bỏ qua).
    CONSTRAINT ck_run_not_silently_empty
        CHECK (status <> 'SUCCEEDED' OR finding_count > 0),
    CONSTRAINT ck_run_clean_is_explicit
        CHECK (status <> 'SUCCEEDED_CLEAN' OR clean_ack_note IS NOT NULL)
);

COMMENT ON TABLE analysis_run IS
    'Một lần chạy pipeline phân tích trên một repository_snapshot. Đơn vị "0 cảnh báo phải bị coi là lỗi" áp dụng ở đây.';
COMMENT ON COLUMN analysis_run.clean_ack_note IS
    'Bắt buộc khi status=SUCCEEDED_CLEAN: lý do vì sao dự án thật sự sạch (không phải analyzer im lặng hỏng).';

CREATE INDEX idx_analysis_run_snapshot ON analysis_run (snapshot_id, started_at DESC);

-- ==========================================================================================
-- evidence_record — đơn vị citable duy nhất mà Agent/LLM trích dẫn qua short_id.
-- ==========================================================================================
CREATE TABLE evidence_record (
    id              UUID PRIMARY KEY,
    -- Số thứ tự trong phạm vi một run; nguồn sinh short_id ngắn gọn cho prompt. Ứng dụng phải
    -- tự cấp số này tăng dần trong cùng run (tuần 8-9, khi EvidenceMaterializer ra đời);
    -- tuần 3 chỉ dựng schema + entity, chưa có service sinh số tự động.
    seq_in_run      INT         NOT NULL,
    -- Dạng EV-XXXX cho LLM trích dẫn: 3 hex đếm trong run (đủ cho hàng nghìn evidence/run) +
    -- 1 ký tự băm từ UUID. Ký tự băm khiến LLM bịa ID gần như luôn resolve thất bại
    -- (claim_citation.resolution='UNKNOWN_ID') thay vì vô tình trỏ đúng evidence khác.
    short_id        TEXT GENERATED ALWAYS AS (
                        'EV-' || upper(lpad(to_hex(seq_in_run), 3, '0'))
                        || upper(substr(md5(id::text), 1, 1))
                    ) STORED,
    analysis_run_id UUID        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    type            TEXT        NOT NULL
                        CHECK (type IN ('STATIC_WARNING', 'CODE_CONTEXT', 'TEST_RESULT',
                                         'FAILED_TEST', 'STACK_TRACE', 'COVERAGE_INFORMATION')),
    source          TEXT        NOT NULL
                        CHECK (source IN ('spotbugs', 'semgrep', 'surefire', 'jacoco', 'parser')),
    -- Vị trí: nullable vì không phải mọi evidence có toạ độ file:line (vd. evidence tổng hợp
    -- coverage cấp file). Không có cột commit_sha: không artifact nào có (phát hiện 4 ở trên).
    file_path       TEXT,
    start_line      INT,
    end_line        INT,
    -- Khoá liên kết phụ, KHÔNG phải khoá chính như sketch ban đầu ở kế hoạch: Semgrep không
    -- bao giờ có symbol (không logicalLocations trong SARIF), nên nó phải nullable và chỉ dùng
    -- khi có.
    symbol          TEXT,
    -- Câu tóm tắt 1 dòng, luôn dựng được kể cả khi vị trí/symbol đều NULL — dùng cho prompt/UI.
    summary         TEXT        NOT NULL,
    -- Payload đặc thù theo loại + toàn bộ metadata riêng của tool không đáng tách cột riêng.
    content         JSONB       NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (analysis_run_id, seq_in_run),
    UNIQUE (analysis_run_id, short_id),
    CHECK (jsonb_typeof(content) = 'object')
);

COMMENT ON TABLE evidence_record IS
    'Đơn vị bằng chứng có thể trích dẫn (Evidence ID). 6 loại theo mục 3.3 đề cương.';
COMMENT ON COLUMN evidence_record.symbol IS
    'FQN class#method khi biết được; NULL cho Semgrep (SARIF không có logicalLocations).';
COMMENT ON COLUMN evidence_record.short_id IS
    'Dạng EV-XXXX dùng trong prompt LLM và UI — UUID đầy đủ dễ bị LLM chép sai ký tự.';

CREATE INDEX idx_evidence_record_run_type ON evidence_record (analysis_run_id, type);
CREATE INDEX idx_evidence_record_location ON evidence_record (analysis_run_id, file_path, start_line)
    WHERE file_path IS NOT NULL;

-- ==========================================================================================
-- warning — mở rộng 1:1 của evidence_record cho type=STATIC_WARNING.
--
-- Không lặp file_path/start_line/symbol ở đây: chúng đã có trên evidence_record. Nhờ vậy
-- "trích dẫn một warning" và "trích dẫn bất kỳ evidence" là CÙNG một cơ chế (CitationValidator
-- một nhánh, SQL tính Unsupported Claim Rate ở tuần 15 chỉ join evidence_record một kiểu).
-- Khuôn 1:1 này sẽ dùng lại cho test_case/coverage_* ở V3.
-- ==========================================================================================
CREATE TABLE warning (
    evidence_record_id        UUID PRIMARY KEY REFERENCES evidence_record (id) ON DELETE CASCADE,
    tool                      TEXT NOT NULL CHECK (tool IN ('spotbugs', 'semgrep')),
    -- SpotBugs: BugInstance/@type (vd WEAK_MESSAGE_DIGEST_MD5).
    -- Semgrep:  result.ruleId NGUYÊN VĂN, có prefix rác 'work.semgrep-rules.' của container.
    rule_id                   TEXT NOT NULL,
    rule_id_normalized        TEXT NOT NULL,
    -- SpotBugs: rules[].defaultConfiguration.level KHÔNG tồn tại trên result -> luôn NULL cho
    -- Semgrep trừ khi parser join sang mảng rules[]; SpotBugs cũng không có "level", chỉ có
    -- priority/rank riêng -> cột này nullable, thang gốc lưu trong content JSONB của
    -- evidence_record.
    severity_raw              TEXT,
    -- Chuẩn hoá bắt buộc vì hai tool dùng hai thang rời rạc không giao nhau
    -- (SpotBugs priority 1-3 + rank 1-20; Semgrep level error/warning/note + precision).
    severity_normalized       TEXT NOT NULL CHECK (severity_normalized IN ('HIGH', 'MEDIUM', 'LOW', 'INFO')),
    -- SpotBugs @cweid thiếu ở 8/28 BugInstance thật; Semgrep CWE nhúng trong string tag
    -- "CWE-328: ..." -> mảng, nullable.
    cwe_ids                   INT[],
    -- false cho 5/28 BugInstance không có SourceLine primary='true' (vẫn có đúng 1 vị trí,
    -- nhưng parser phải tự chọn theo quy tắc fallback — xem docs/evidence-model.md).
    has_primary_location      BOOLEAN NOT NULL DEFAULT true,
    -- Thứ tự phần tử trong file gốc — khoá tự nhiên đáng tin duy nhất; KHÔNG dùng
    -- instanceHash/fingerprint làm khoá (Semgrep fingerprint giống nhau ở mọi result).
    source_index              INT     NOT NULL
);

COMMENT ON TABLE warning IS
    'Mở rộng 1:1 của evidence_record cho cảnh báo tĩnh (SpotBugs/Semgrep).';
COMMENT ON COLUMN warning.severity_raw IS
    'Priority/level nguyên bản của tool nếu có; NULL không có nghĩa là thiếu dữ liệu (SpotBugs không có level).';

-- ==========================================================================================
-- evidence_link — quan hệ giữa hai evidence_record (SUPPORTS/CONTRADICTS/COVERS/LOCATED_IN).
-- ==========================================================================================
CREATE TABLE evidence_link (
    id               UUID PRIMARY KEY,
    from_evidence_id UUID           NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    to_evidence_id   UUID           NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    relation         TEXT           NOT NULL CHECK (relation IN ('SUPPORTS', 'CONTRADICTS', 'COVERS', 'LOCATED_IN')),
    weight           NUMERIC(4, 3)  NOT NULL DEFAULT 1.0,
    -- Cách link được sinh ra — cho phép tuần 15 đo "bao nhiêu % link chỉ dựa vào quy ước tên"
    -- thay vì stack trace thật. Cần thiết vì 3/5 test hỏng không có stack frame production.
    method           TEXT           NOT NULL
                         CHECK (method IN ('LINE_EXACT', 'LINE_NEAREST_EXECUTABLE', 'RANGE_OVERLAP',
                                            'METHOD_AGGREGATE', 'STACK_FRAME', 'NAMING_CONVENTION',
                                            'SYMBOL_MATCH', 'CROSS_TOOL_AGREEMENT')),
    rationale        TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    UNIQUE (from_evidence_id, to_evidence_id, relation),
    CHECK (from_evidence_id <> to_evidence_id)
);

COMMENT ON TABLE evidence_link IS
    'Quan hệ giữa hai evidence_record. method ghi lại độ tin cậy của cách suy ra quan hệ.';
COMMENT ON COLUMN evidence_link.method IS
    'STACK_FRAME đáng tin nhất; NAMING_CONVENTION yếu nhất — chỉ dùng khi không còn cách nào khác.';

CREATE INDEX idx_evidence_link_from ON evidence_link (from_evidence_id, relation);
CREATE INDEX idx_evidence_link_to ON evidence_link (to_evidence_id, relation);

-- ==========================================================================================
-- claim + claim_citation — nhận định của Agent và việc kiểm chứng trích dẫn.
--
-- claim là bảng riêng (không nhét JSON trong message) vì Unsupported Claim Rate và Evidence
-- Citation Accuracy được tính bằng SQL trực tiếp trên hai bảng này (mục 6 kế hoạch).
-- claim.message_id CHƯA có FK: bảng conversation/message thuộc phạm vi tuần 12-13 (agent
-- service + chat). Cột giữ chỗ, ràng buộc FK sẽ thêm bằng migration riêng khi bảng đó ra đời.
-- ==========================================================================================
CREATE TABLE claim (
    id                       UUID          PRIMARY KEY,
    message_id               UUID,
    ordinal                  INT           NOT NULL,
    text                     TEXT          NOT NULL,
    verdict                  TEXT          NOT NULL
                                 CHECK (verdict IN ('LIKELY_TRUE_POSITIVE', 'LIKELY_FALSE_POSITIVE',
                                                     'INSUFFICIENT_EVIDENCE')),
    -- Verdict gốc trước khi CitationValidator hạ xuống INSUFFICIENT_EVIDENCE vì thiếu trích
    -- dẫn hợp lệ. NULL nghĩa là chưa từng bị hạ. Claim KHÔNG bao giờ bị xoá khi hạ verdict —
    -- cần giữ để tính Unsupported Claim Rate.
    downgraded_from_verdict  TEXT
                                 CHECK (downgraded_from_verdict IN ('LIKELY_TRUE_POSITIVE',
                                                                     'LIKELY_FALSE_POSITIVE',
                                                                     'INSUFFICIENT_EVIDENCE')),
    confidence               NUMERIC(4, 3),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE claim IS
    'Một nhận định của Agent. Không xoá khi verdict bị CitationValidator hạ xuống INSUFFICIENT_EVIDENCE.';
COMMENT ON COLUMN claim.message_id IS
    'Chưa có FK: bảng message ra đời ở tuần 12-13 (agent service + chat). Thêm ràng buộc bằng migration riêng khi đó.';

CREATE TABLE claim_citation (
    id                  UUID        PRIMARY KEY,
    claim_id            UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    -- Nguyên văn chuỗi LLM viết ra, kể cả khi không tồn tại (vd 'EV-ZZZZ') — đây là mẫu số
    -- thật của Evidence Citation Accuracy, không được chuẩn hoá hay loại bỏ.
    cited_short_id_raw  TEXT        NOT NULL,
    evidence_record_id  UUID        REFERENCES evidence_record (id),
    resolution          TEXT        NOT NULL
                             CHECK (resolution IN ('RESOLVED', 'UNKNOWN_ID', 'WRONG_RUN', 'MALFORMED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (resolution <> 'RESOLVED' OR evidence_record_id IS NOT NULL)
);

COMMENT ON TABLE claim_citation IS
    'Mỗi lượt LLM trích dẫn một Evidence ID trong một claim. Không xoá dòng nào — là mẫu số của Unsupported Claim Rate và Evidence Citation Accuracy.';

CREATE INDEX idx_claim_citation_claim ON claim_citation (claim_id);
