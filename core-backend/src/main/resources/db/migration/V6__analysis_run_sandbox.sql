-- ==========================================================================================
-- V6 — cột dấu vết của Docker sandbox trên analysis_run, và ràng buộc canary.
--
-- Tới V5, một analysis_run chỉ ghi được KẾT QUẢ phân tích. Từ giai đoạn D nó còn phải trả lời
-- được "lần chạy này được sinh ra NHƯ THẾ NÀO" — thứ mà phần thực nghiệm tuần 15-16 cần để nói
-- "kết quả này tái lập được", và người vận hành cần để chẩn đoán khi số liệu lệch.
--
-- Xem docs/adr/0008-chay-analyzer-trong-docker-sandbox.md.
--
-- MỌI CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN, mỗi cột đúng MỘT — để Postgres tự
-- sinh tên '<table>_<column>_check' đúng quy ước mà EnumCheckConstraintTest tra cứu.
-- ==========================================================================================

ALTER TABLE analysis_run
    -- Nguồn của artifact. Hai đường tồn tại SONG SONG chứ không thay thế nhau: nạp thủ công là
    -- đường demo được ngay bằng docs/samples/, và vẫn là đường dự phòng khi máy chủ không có
    -- Docker daemon. Không có cột này thì hai lần chạy cho ra cùng số liệu nhưng khác hoàn toàn
    -- về mức độ đáng tin, mà không ai phân biệt được.
    ADD COLUMN artifact_source TEXT NOT NULL DEFAULT 'MANUAL_UPLOAD'
        CHECK (artifact_source IN ('MANUAL_UPLOAD', 'DOCKER_SANDBOX')),

    -- Digest của image analyzer ĐÃ THỰC SỰ CHẠY, không phải tag. Tag `evidence/analyzer-java:latest`
    -- trỏ tới image khác nhau sau mỗi lần dựng lại; digest thì không. Đây là điều kiện để tuần
    -- 15-16 nói được "cùng một bộ analyzer" thay vì "cùng một cái tên".
    ADD COLUMN analyzer_image_digest TEXT,

    -- Thư mục gốc của dự án trong cây đã upload (thư mục chứa pom.xml nông nhất). Lưu lại vì
    -- SnapshotPathResolver đối chiếu đường dẫn analyzer khai với snapshot_file.path, và khi số
    -- liệu lệch thì "sandbox đã coi đâu là gốc" là câu hỏi đầu tiên.
    ADD COLUMN source_root TEXT,

    -- Canary đã ra đủ finding của CẢ HAI bộ rule hay chưa. NULL cho run nạp thủ công — ở đó không
    -- có canary để mà kiểm, và điều đó khác hẳn "có canary nhưng nó đỏ".
    ADD COLUMN canary_ok BOOLEAN,

    -- Số finding của mã canary, đã LOẠI khỏi finding_count. Giữ lại để khi canary đỏ thì đọc được
    -- "ra mấy cái" chứ không chỉ true/false.
    ADD COLUMN canary_finding_count INT NOT NULL DEFAULT 0,

    ADD COLUMN sandbox_duration_ms BIGINT,

    -- Một run SUCCEEDED sinh từ sandbox mà canary KHÔNG xanh nghĩa là bộ rule không nạp được,
    -- hoặc analyzer không thật sự chạy — dù nó có thể vẫn ra hàng chục cảnh báo từ những rule
    -- còn sống. Chặn bằng cấu trúc, cùng tinh thần ck_run_not_silently_empty.
    ADD CONSTRAINT ck_run_sandbox_needs_canary
        CHECK (artifact_source <> 'DOCKER_SANDBOX'
               OR status NOT IN ('SUCCEEDED', 'SUCCEEDED_CLEAN')
               OR canary_ok IS TRUE),

    -- Nạp thủ công thì KHÔNG có canary, và cũng không được giả vờ có: canary_ok = true trên một
    -- run MANUAL_UPLOAD sẽ là một lời khai không có gì chống lưng.
    ADD CONSTRAINT ck_run_canary_only_from_sandbox
        CHECK (artifact_source = 'DOCKER_SANDBOX' OR canary_ok IS NULL),

    ADD CONSTRAINT ck_run_canary_count_nonneg CHECK (canary_finding_count >= 0);

COMMENT ON COLUMN analysis_run.artifact_source IS
    'MANUAL_UPLOAD (nạp artifact qua API) hoặc DOCKER_SANDBOX (core-backend tự chạy analyzer).';
COMMENT ON COLUMN analysis_run.analyzer_image_digest IS
    'Digest chứ không phải tag: tag :latest trỏ tới image khác nhau sau mỗi lần dựng lại.';
COMMENT ON COLUMN analysis_run.canary_ok IS
    'NULL cho run nạp thủ công (không có canary để kiểm) — khác hẳn false (có canary và nó đỏ).';

-- Truy vấn của tuần 15-16: "mọi run sinh từ sandbox, gom theo digest image" — để so kết quả giữa
-- hai phiên bản bộ analyzer.
CREATE INDEX idx_analysis_run_sandbox ON analysis_run (artifact_source, analyzer_image_digest)
    WHERE artifact_source = 'DOCKER_SANDBOX';
