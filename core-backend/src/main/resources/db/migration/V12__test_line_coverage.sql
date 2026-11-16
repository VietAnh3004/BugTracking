-- ==========================================================================================
-- V12 — coverage THEO TỪNG TEST: test nào chạy qua dòng nào.
--
-- Vì sao: jacoco.xml chỉ cho độ phủ GỘP của cả bộ test, nên trước V12 EvidenceLinker nối test ↔
-- cảnh báo theo quy ước tên lớp (FooTest ↔ Foo) — liên kết yếu nhất, và trượt mọi test của lớp
-- khác chạy qua dòng đó (test controller gọi xuống service…). Analyzer trong sandbox nay ghi thêm
-- per-test-coverage.json (listener JUnit + agent JaCoCo, xem infra/analyzer/java/pertest).
--
-- Chốt với người dùng 16/11/2026: run CÓ dữ liệu này thì liên kết test ↔ cảnh báo dựng THEO DỮ
-- LIỆU (test thật sự chạy qua dòng cảnh báo), THAY cho liên kết theo tên. Run nạp tay không có
-- dữ liệu này giữ cách cũ.
--
-- Bảng dữ liệu thô, cùng vai trò coverage_line: không sinh evidence_record nào (không phải đơn vị
-- trích dẫn), chỉ là đầu vào cho linker. Cha là evidence_record của TEST (TEST_RESULT/FAILED_TEST),
-- nên xoá run là xoá theo.
-- ==========================================================================================
CREATE TABLE test_line_coverage (
    test_evidence_id UUID NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    -- Đường dẫn ĐÃ chuẩn hoá về snapshot (cùng SnapshotPathResolver với jacoco.xml), để so thẳng
    -- với warning_location.file_path.
    file_path        TEXT NOT NULL,
    line_number      INT  NOT NULL,

    PRIMARY KEY (test_evidence_id, file_path, line_number),
    CONSTRAINT ck_test_line_coverage_line_positive CHECK (line_number > 0)
);

-- Linker hỏi "test nào chạy qua (file, dòng) này?" cho từng vị trí cảnh báo.
CREATE INDEX idx_test_line_coverage_location ON test_line_coverage (file_path, line_number);

COMMENT ON TABLE test_line_coverage IS
    'Coverage theo từng test (sandbox). Có dữ liệu thì linker nối test ↔ cảnh báo theo dòng thật thay vì theo tên lớp.';
