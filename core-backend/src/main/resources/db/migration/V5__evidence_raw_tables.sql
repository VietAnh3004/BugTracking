-- ==========================================================================================
-- V5 — năm bảng "thô" mirror trực tiếp từng công cụ, + bộ đếm chuẩn hoá đường dẫn.
--
-- Kế hoạch 20 tuần và docs/evidence-model.md ban đầu gọi đợt này là "V3"; tuần 5-7 đã dùng mất
-- V3 và V4 nên số hiệu thật là V5. Nội dung không đổi so với thiết kế ở mục 7 của tài liệu.
--
-- Vì sao tới giờ mới tạo (ADR 0004 quyết định 1): tạo ở V2 thì suốt 5 tuần chúng là bảng rỗng
-- không parser, không test, không ai biết thiết kế sai cho tới lúc có artifact thật chạy qua.
--
-- MỌI CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN, và mỗi cột đúng MỘT CHECK inline,
-- để Postgres tự sinh tên '<table>_<column>_check' — EnumCheckConstraintTest tra cứu theo đúng
-- quy ước đó. Hai CHECK inline trên cùng một cột sẽ cho cái thứ hai tên '..._check1' và test
-- không tìm thấy. Ràng buộc nhiều cột thì đặt tên tường minh 'ck_<table>_<mục đích>'.
-- ==========================================================================================

-- ==========================================================================================
-- warning_location — vị trí của một cảnh báo. 1:N, KHÔNG theo khuôn 1:1 như warning.
--
-- Phát hiện 1 (docs/evidence-model.md mục 6): 25/28 BugInstance thật có đúng 1 SourceLine con
-- trực tiếp, nhưng 3/28 có 2-3 vị trí — một UNSAFE_HASH_EQUALS trỏ tới 3 file KHÁC NHAU. Nhét
-- vào một hàng 1:1 thì mất 2 trong 3 vị trí, và mất im lặng.
--
-- FK trỏ tới warning chứ KHÔNG phải evidence_record: để chính DB cấm gắn vị trí cảnh báo vào
-- một evidence coverage/test. Vì warning.evidence_record_id CHÍNH LÀ evidence_record.id (PK=FK),
-- truy vấn vẫn join thẳng warning_location -> evidence_record một hop, không mất gì.
-- ==========================================================================================
CREATE TABLE warning_location (
    evidence_record_id  UUID    NOT NULL REFERENCES warning (evidence_record_id) ON DELETE CASCADE,
    -- Thứ tự xuất hiện trong artifact gốc. Là khoá tự nhiên có thật, nên dùng làm PK tổ hợp
    -- thay UUID surrogate — chặn luôn việc chèn trùng khi ingest lại cùng một run.
    ordinal             INT     NOT NULL,
    -- SpotBugs SourceLine/@role, vd SOURCE_LINE_KNOWN_NULL, SOURCE_LINE_OBLIGATION_CREATED.
    -- KHÔNG enum hoá có chủ đích: detector mới sinh role mới, và một CHECK ở đây sẽ làm HỎNG CẢ
    -- RUN chỉ vì một chuỗi vô hại. 27/33 vị trí thật không có role -> nullable.
    role_raw            TEXT,
    -- false cho 5/28 BugInstance thật chỉ có synthetic='true' (parser phải fallback chọn vị trí
    -- đầu). Giữ cờ này để phân biệt "parser đoán" với "tool chỉ đích danh".
    is_primary          BOOLEAN NOT NULL DEFAULT false,
    -- Đường dẫn NGUYÊN VĂN tool khai (4 định dạng khác nhau, phát hiện 4). Giữ lại để reparse
    -- được khi heuristic chuẩn hoá sai — cùng lý do stack_frame.raw_line tồn tại.
    path_raw            TEXT    NOT NULL,
    -- Đã chuẩn hoá về snapshot_file.path. NULL = KHÔNG đối chiếu được với snapshot; khi đó vẫn
    -- giữ nguyên hàng (không bao giờ làm mất bằng chứng) và đếm vào
    -- analysis_run.unresolved_location_count.
    file_path           TEXT,
    -- Nullable: không phải SourceLine nào cũng có số dòng.
    start_line          INT,
    end_line            INT,
    class_name          TEXT,
    method_name         TEXT,

    PRIMARY KEY (evidence_record_id, ordinal),
    CONSTRAINT ck_warning_location_ordinal_nonneg CHECK (ordinal >= 0),
    CONSTRAINT ck_warning_location_line_range
        CHECK (start_line IS NULL OR end_line IS NULL OR end_line >= start_line)
);

COMMENT ON TABLE warning_location IS
    'Vị trí của cảnh báo. 1:N vì 3/28 BugInstance thật có nhiều vị trí (phát hiện 1).';
COMMENT ON COLUMN warning_location.role_raw IS
    'SourceLine/@role nguyên văn, KHÔNG enum hoá — detector mới sinh role mới.';

CREATE INDEX idx_warning_location_file ON warning_location (file_path, start_line)
    WHERE file_path IS NOT NULL;
-- Truy vấn "vị trí nào chưa đối chiếu được" phải rẻ: nó là chỉ số sức khoẻ của chuẩn hoá path.
CREATE INDEX idx_warning_location_unresolved ON warning_location (evidence_record_id)
    WHERE file_path IS NULL;

-- ==========================================================================================
-- test_case — mở rộng 1:1 cho type=TEST_RESULT/FAILED_TEST. Theo đúng khuôn của warning
-- (ADR 0004 quyết định 2): đúng một test case = đúng một đơn vị trích dẫn.
-- ==========================================================================================
CREATE TABLE test_case (
    evidence_record_id      UUID PRIMARY KEY REFERENCES evidence_record (id) ON DELETE CASCADE,
    class_name              TEXT NOT NULL,
    method_name             TEXT NOT NULL,
    -- <testcase @name> nguyên văn: test tham số hoá có dạng 'ten[1]', khác method_name.
    display_name            TEXT NOT NULL,
    status                  TEXT NOT NULL CHECK (status IN ('PASSED', 'FAILED', 'SKIPPED')),
    -- FAILURE = thẻ <failure> (assertion thất bại); ERROR = thẻ <error> (ngoại lệ thoát ra).
    -- Tên hai thẻ XML NGƯỢC trực giác nên ghi rõ ở đây. NULL khi PASSED/SKIPPED.
    failure_kind            TEXT CHECK (failure_kind IN ('FAILURE', 'ERROR')),
    failure_type            TEXT,
    failure_message         TEXT,
    -- Text thô nguyên vẹn của <failure>/<error>: NGUỒN DUY NHẤT để reparse khi heuristic tách
    -- frame sai. stack_frame là bản đã diễn giải, không phải bản gốc.
    stack_trace_raw         TEXT,
    duration_ms             INT,
    -- Provenance CẤU TRÚC (trace này thuộc test nào), KHÔNG phải evidence_link ngữ nghĩa. Nhờ
    -- cột này tuần 8-9 dùng được ngay mà không cần EvidenceLinker (tuần 10-11).
    stack_trace_evidence_id UUID REFERENCES evidence_record (id) ON DELETE SET NULL,
    -- Tên file TEST-*.xml đã sinh ra hàng này — cần để truy ngược khi số liệu lệch.
    report_file             TEXT NOT NULL,

    CONSTRAINT ck_test_case_failure_kind
        CHECK ((status = 'FAILED') = (failure_kind IS NOT NULL)),
    CONSTRAINT ck_test_case_trace_only_when_failed
        CHECK (stack_trace_evidence_id IS NULL OR status = 'FAILED')
);

COMMENT ON COLUMN test_case.failure_kind IS
    'FAILURE = <failure> (assertion), ERROR = <error> (ngoại lệ). Tên thẻ XML ngược trực giác.';

-- Liên kết theo quy ước tên (FooTest <-> Foo) là BẮT BUỘC chứ không phải phương án phụ: 3/5
-- test hỏng thật không có frame nào trỏ về mã production (phát hiện 2), nên chỉ dựa vào stack
-- trace là mất 60% bằng chứng test hỏng.
CREATE INDEX idx_test_case_class ON test_case (class_name);
CREATE INDEX idx_test_case_failed ON test_case (evidence_record_id) WHERE status = 'FAILED';

-- ==========================================================================================
-- stack_frame — từng dòng 'at ...' của một evidence type=STACK_TRACE. 1:N.
--
-- Tách bảng riêng thay vì chỉ giữ text thô vì phát hiện 2: heuristic phân loại frame có thể
-- sai, và khi sai thì phải reparse được mà không cần quay lại artifact gốc.
-- ==========================================================================================
CREATE TABLE stack_frame (
    evidence_record_id UUID NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    ordinal            INT  NOT NULL,
    -- Dòng 'at ...' NGUYÊN VĂN. Regex tách frame đã sai một lần ngay trong repo này
    -- (case-study/summarize.py bỏ sót tiền tố module 'java.base/' nên bỏ luôn cả frame).
    raw_line           TEXT NOT NULL,
    -- 'java.base' (module JPMS) hoặc 'app' (classloader) — phần trước dấu '/' cuối cùng.
    module_prefix      TEXT,
    declaring_class    TEXT,
    method_name        TEXT,
    -- NULL cho '(Native Method)' / '(Unknown Source)': KHÔNG CÓ tên file. Khác hẳn "có tên file
    -- nhưng không đối chiếu được với snapshot" (khi đó source_file_name có, file_path NULL).
    source_file_name   TEXT,
    line_number        INT,
    file_path          TEXT,
    -- UNKNOWN là giá trị THÊM so với thiết kế ở mục 7: một dòng 'at ' không parse được vẫn phải
    -- có hàng. Bỏ qua im lặng là đúng thứ dự án này chống.
    frame_kind         TEXT NOT NULL
                           CHECK (frame_kind IN ('PRODUCTION', 'TEST', 'TEST_FRAMEWORK',
                                                 'JDK', 'LIBRARY', 'UNKNOWN')),

    PRIMARY KEY (evidence_record_id, ordinal),
    CONSTRAINT ck_stack_frame_ordinal_nonneg CHECK (ordinal >= 0)
);

-- Truy vấn chủ đạo của EvidenceLinker tuần 10-11: "cảnh báo ở file:line này có frame nào trỏ tới?"
CREATE INDEX idx_stack_frame_production ON stack_frame (file_path, line_number)
    WHERE frame_kind = 'PRODUCTION' AND file_path IS NOT NULL;

-- ==========================================================================================
-- coverage_line — độ phủ từng dòng. Cha là evidence_record cấp SOURCEFILE (1 record/1 file),
-- KHÔNG phải 1 record/1 dòng: 171 dòng của vulnshop thành 171 "đơn vị trích dẫn" là vô nghĩa
-- với LLM, và ở repo gấp 100 lần thì evidence_record phồng lên 17.000 hàng mỗi run.
-- ==========================================================================================
CREATE TABLE coverage_line (
    evidence_record_id UUID NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    line_number        INT  NOT NULL,
    mi                 INT  NOT NULL,   -- missed instructions
    ci                 INT  NOT NULL,   -- covered instructions
    mb                 INT  NOT NULL,   -- missed branches
    cb                 INT  NOT NULL,   -- covered branches
    -- Giữ nguyên 4 bộ đếm gốc thay vì thu về boolean: PARTIAL (dòng có chạy nhưng còn nhánh
    -- chưa chạy) là bằng chứng KHÁC HẲN COVERED cho câu hỏi "cảnh báo này có được test phủ
    -- không". 5/171 dòng thật của vulnshop là PARTIAL — thu về boolean là mất đúng 5 ca thú vị
    -- nhất.
    --
    -- ĐỪNG "SỬA" THÀNH 77: JaCoCo định nghĩa "dòng được phủ" = ci > 0 = COVERED + PARTIAL
    -- = 77 + 5 = 82, khớp counter LINE covered='82' của chính JaCoCo và expected-analysis.json.
    status             TEXT GENERATED ALWAYS AS (
                           CASE WHEN ci = 0                        THEN 'MISSED'
                                WHEN mi > 0 OR (mb > 0 AND cb > 0) THEN 'PARTIAL'
                                ELSE 'COVERED' END
                       ) STORED
                       CHECK (status IN ('COVERED', 'MISSED', 'PARTIAL')),

    PRIMARY KEY (evidence_record_id, line_number),
    CONSTRAINT ck_coverage_line_counters_nonneg
        CHECK (mi >= 0 AND ci >= 0 AND mb >= 0 AND cb >= 0)
);

COMMENT ON COLUMN coverage_line.status IS
    'Sinh từ mi/ci/mb/cb. "Được phủ" theo JaCoCo là ci>0, tức COVERED + PARTIAL.';

CREATE INDEX idx_coverage_line_executed ON coverage_line (evidence_record_id, line_number)
    WHERE ci > 0;

-- ==========================================================================================
-- coverage_method — độ phủ cấp method. 1:N, và KHÔNG có FK sang coverage_line.
--
-- Phát hiện 3: quan hệ method <-> dòng KHÔNG TỒN TẠI trong dữ liệu thật. JaCoCo chỉ cho
-- method/@line = dòng ĐẦU, không có dòng cuối, và method KHÔNG theo thứ tự dòng
-- (SortField.<clinit> line=9 đứng SAU column line=22). Suy ra "dòng 42 thuộc method nào" là
-- bịa dữ liệu, không phải suy luận — nên không mô hình hoá quan hệ đó.
-- ==========================================================================================
CREATE TABLE coverage_method (
    -- Surrogate UUID thay PK tổ hợp: khoá tự nhiên là (record, class, method, desc) với
    -- method_desc dài (vd '(Ljava/lang/String;)Z'); làm PK thì mọi FK tương lai phải chép cả
    -- bốn cột. UNIQUE ở dưới vẫn ép đúng ràng buộc đó.
    id                   UUID PRIMARY KEY,
    evidence_record_id   UUID NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    -- Dạng JaCoCo, phân tách bằng '/': vn/hust/datn/casestudy/vulnshop/order/OrderStatusChecker
    class_name           TEXT NOT NULL,
    method_name          TEXT NOT NULL,   -- isShipped | <init> | <clinit>
    method_desc          TEXT NOT NULL,   -- phân biệt overload
    first_line           INT,             -- KHÔNG có end_line: JaCoCo không cung cấp
    missed_instructions  INT  NOT NULL DEFAULT 0,
    covered_instructions INT  NOT NULL DEFAULT 0,
    missed_branches      INT  NOT NULL DEFAULT 0,
    covered_branches     INT  NOT NULL DEFAULT 0,
    missed_lines         INT  NOT NULL DEFAULT 0,
    covered_lines        INT  NOT NULL DEFAULT 0,

    UNIQUE (evidence_record_id, class_name, method_name, method_desc)
);

CREATE INDEX idx_coverage_method_symbol ON coverage_method (class_name, method_name);

-- ==========================================================================================
-- analysis_run — bộ đếm chuẩn hoá đường dẫn.
--
-- ADR 0004 gọi đích danh: chuẩn hoá đường dẫn là heuristic "có thể hỏng im lặng thành 0
-- evidence_link — trông y hệt không có bằng chứng liên quan", và yêu cầu bắt buộc có cột đếm
-- để thất bại hiện thành SỐ. Đây là cột đó.
-- ==========================================================================================
ALTER TABLE analysis_run
    ADD COLUMN location_count            INT NOT NULL DEFAULT 0,
    ADD COLUMN unresolved_location_count INT NOT NULL DEFAULT 0,
    -- Phiên bản tool đọc từ CHÍNH artifact (BugCollection/@version,
    -- tool.driver.semanticVersion). JaCoCo không khai version -> khoá 'jacoco' vắng mặt là
    -- BÌNH THƯỜNG, không phải thiếu dữ liệu. Cần cho tính tái lập ở tuần 15-16.
    ADD COLUMN tool_versions             JSONB,
    ADD CONSTRAINT ck_run_unresolved_le_total
        CHECK (unresolved_location_count <= location_count),
    -- Cùng tinh thần ck_run_not_silently_empty: nếu KHÔNG vị trí nào khớp được snapshot_file
    -- thì EvidenceLinker sẽ sinh 0 link — trông y hệt "không có bằng chứng liên quan". Chặn
    -- bằng cấu trúc, không bằng thiện chí của code.
    ADD CONSTRAINT ck_run_paths_not_all_unresolved
        CHECK (status <> 'SUCCEEDED'
               OR location_count = 0
               OR unresolved_location_count < location_count),
    ADD CONSTRAINT ck_run_tool_versions_object
        CHECK (tool_versions IS NULL OR jsonb_typeof(tool_versions) = 'object');

COMMENT ON COLUMN analysis_run.unresolved_location_count IS
    'Số vị trí KHÔNG đối chiếu được với snapshot_file.path; mẫu số là location_count. Frame JDK/JUnit KHÔNG tính vào cả hai — nếu tính, tỉ lệ luôn ~84% và mất hết ý nghĩa cảnh báo.';

-- ==========================================================================================
-- warning — thứ tự sắp xếp theo mức độ nghiêm trọng.
--
-- severity_normalized là TEXT nên ORDER BY cho ra HIGH, INFO, LOW, MEDIUM (thứ tự chữ cái) —
-- SAI, và rất khó phát hiện qua mắt thường vì HIGH vẫn đứng đầu. Generated column là chỗ DUY
-- NHẤT định nghĩa thứ tự, dùng chung cho UI và SQL thống kê tuần 15.
-- ==========================================================================================
ALTER TABLE warning
    ADD COLUMN severity_rank INT GENERATED ALWAYS AS (
        CASE severity_normalized WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1
                                 WHEN 'LOW'  THEN 2 ELSE 3 END
    ) STORED;

CREATE INDEX idx_warning_severity ON warning (severity_rank, evidence_record_id);
