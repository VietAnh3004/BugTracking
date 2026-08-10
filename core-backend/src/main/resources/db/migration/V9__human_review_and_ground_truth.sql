-- ==========================================================================================
-- V9 — quyết định của người dùng, và chỗ cho nhãn chuẩn.
--
-- Tuần 14 khép mắt xích cuối của luồng end-to-end. Tới hết tuần 12-13, hệ thống ghi lại được
-- Agent nghĩ gì (`message`, `claim`, `claim_citation`) nhưng KHÔNG ghi lại được NGƯỜI DÙNG nghĩ
-- gì — trong khi `human_review` được nhắc tới trong kế hoạch từ tuần 3 (§2 và §6).
--
-- MỌI CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN, mỗi cột đúng MỘT — để Postgres tự
-- sinh tên '<table>_<column>_check' đúng quy ước mà EnumCheckConstraintTest tra cứu. Ràng buộc
-- nhiều cột thì đặt tên tường minh 'ck_<table>_<mục đích>'.
-- ==========================================================================================


-- ==========================================================================================
-- 1. human_review — một người phán xét MỘT CẢNH BÁO.
--
-- Neo vào cảnh báo chứ không vào câu trả lời của Agent (ADR 0011). Lý do quyết định: user study
-- ở tuần 16 chia người dùng làm hai nhóm — một nửa dùng Agent, một nửa đọc output công cụ thô
-- (kế hoạch §6). Nếu bảng này neo vào `message` thì nhóm thứ hai KHÔNG CÓ CHỖ NÀO để ghi, mà đó
-- lại chính là nhóm đối chứng.
--
-- Nhờ neo vào cảnh báo, một bảng phục vụ được cả hai chỉ số: Human acceptance rate (so `decision`
-- với `message.verdict`) và so sánh giữa hai nhóm (`message_id IS NULL`).
-- ==========================================================================================
CREATE TABLE human_review (
    id                 UUID        PRIMARY KEY,
    -- Phi chuẩn hoá CÓ CHỦ ĐÍCH, cùng lý do với evidence_link.analysis_run_id ở V7: mọi truy vấn
    -- thống kê đều lọc theo run, và nó biến bất biến "một review không vắt qua hai run" thành
    -- thứ KIỂM ĐƯỢC thay vì một lời hứa trong code.
    analysis_run_id    UUID        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    evidence_record_id UUID        NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    reviewer_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    decision           TEXT        NOT NULL
                           CHECK (decision IN ('ACCEPT', 'REJECT', 'UNCERTAIN', 'INSUFFICIENT_EVIDENCE')),

    -- Câu trả lời của Agent mà người này đã đọc TRƯỚC khi quyết. NULL nghĩa là họ không dùng
    -- Agent — đây là BIẾN THỰC NGHIỆM của user study, không phải metadata trang trí.
    --
    -- ON DELETE SET NULL chứ không CASCADE: xoá một hội thoại không được phép xoá luôn quyết
    -- định của người dùng. Mất quyết định là mất dữ liệu thực nghiệm không tái tạo được; mất
    -- liên kết tới hội thoại thì chỉ mất ngữ cảnh.
    message_id         UUID        REFERENCES message (id) ON DELETE SET NULL,

    note               TEXT,
    -- Do client gửi nên KHÔNG đáng tin (người dùng đổi tab, để máy chạy). Ghi lại vì user study
    -- cần một số đo thô, nhưng nó KHÔNG phải "Time to understand" của kế hoạch §6 — cái đó đo
    -- trong phiên có người quan sát.
    seconds_spent      INT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Một người MỘT quyết định cho một cảnh báo. Đổi ý thì UPDATE. Không có ràng buộc này thì
    -- Human acceptance rate đếm trùng người đổi ý nhiều lần, và không gì báo.
    UNIQUE (evidence_record_id, reviewer_id),

    -- 0 giây là dữ liệu rác, không phải "rất nhanh".
    CONSTRAINT ck_human_review_seconds_positive
        CHECK (seconds_spent IS NULL OR seconds_spent > 0)
);

COMMENT ON TABLE human_review IS
    'Một người phán xét một cảnh báo. message_id NULL = không dùng Agent (nhóm đối chứng user study).';
COMMENT ON COLUMN human_review.message_id IS
    'Câu trả lời Agent đã đọc trước khi quyết. Biến thực nghiệm, backend kiểm chứ không tin client.';
COMMENT ON COLUMN human_review.seconds_spent IS
    'Client tự đo nên chỉ tham khảo. KHÔNG phải "Time to understand" — cái đó đo trong phiên quan sát.';

CREATE INDEX idx_human_review_run ON human_review (analysis_run_id, reviewer_id);
CREATE INDEX idx_human_review_evidence ON human_review (evidence_record_id);


-- ==========================================================================================
-- 2. ground_truth_label — nhãn chuẩn, DỰNG TRƯỚC dùng sau.
--
-- Tuần 15 mới gán nhãn (kế hoạch §5). Dựng schema ở đây vì dashboard tuần 14 có sẵn ô cho
-- "Warning assessment accuracy", và vì thêm cột vào một bảng đã có dữ liệu tốn hơn nhiều so với
-- khai đủ ngay từ đầu.
--
-- Khoá chính là evidence_record_id: một cảnh báo MỘT nhãn chuẩn. Nhãn của người gán nhãn mù thứ
-- hai (để tính Cohen's kappa) dùng chung bảng, phân biệt bằng `source` — nên PK phải kèm source.
-- ==========================================================================================
CREATE TABLE ground_truth_label (
    evidence_record_id UUID        NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    -- SEEDED lấy thẳng từ case-study/vulnshop/SEEDED.md; MANUAL do người gán nhãn chính;
    -- BLIND_SECOND_RATER là người thứ hai gán độc lập — có mặt TỪ ĐẦU vì tuần 15 cần hai người
    -- để tính Cohen's kappa, và thêm giá trị vào CHECK sau sẽ phải sửa cả truy vấn thống kê.
    source             TEXT        NOT NULL
                           CHECK (source IN ('SEEDED', 'MANUAL', 'BLIND_SECOND_RATER')),
    label              TEXT        NOT NULL
                           CHECK (label IN ('TRUE_POSITIVE', 'FALSE_POSITIVE', 'UNCERTAIN')),
    -- NOT NULL có chủ đích. Kế hoạch §5 đòi "lý do 1-2 câu", và bắt viết lý do là cách rẻ nhất
    -- để chặn cái bẫy circularity mà chính mục đó cảnh báo: gán nhãn theo output của Agent sẽ
    -- lộ ra ngay khi phải viết ra vì sao.
    rationale          TEXT        NOT NULL,
    labeler            TEXT        NOT NULL,
    labeled_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (evidence_record_id, source),
    CONSTRAINT ck_ground_truth_rationale_not_blank CHECK (btrim(rationale) <> '')
);

COMMENT ON TABLE ground_truth_label IS
    'Nhãn chuẩn TP/FP/UNCERTAIN. Dựng ở tuần 14, nạp dữ liệu ở tuần 15. Nhiều source để tính kappa.';
COMMENT ON COLUMN ground_truth_label.rationale IS
    'Bắt buộc: viết được lý do nghĩa là đã đọc mã, không phải chép kết luận của Agent (kế hoạch §5).';

CREATE INDEX idx_ground_truth_source ON ground_truth_label (source);
