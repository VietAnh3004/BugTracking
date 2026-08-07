-- ==========================================================================================
-- V8 — hội thoại, tin nhắn, và nhật ký gọi LLM.
--
-- Tuần 12-13 nối agent vào hệ thống. V2 đã dựng sẵn `claim` và `claim_citation` nhưng để
-- `claim.message_id` KHÔNG có FK, kèm comment "bảng message ra đời ở tuần 12-13". Migration này
-- trả nợ đúng chỗ đó, và thêm hai bảng còn thiếu để một lượt hỏi-đáp truy vết được đầy đủ.
--
-- MỌI CHECK cho cột kiểu liệt kê viết INLINE KHÔNG ĐẶT TÊN, mỗi cột đúng MỘT — để Postgres tự
-- sinh tên '<table>_<column>_check' đúng quy ước mà EnumCheckConstraintTest tra cứu. Ràng buộc
-- nhiều cột thì đặt tên tường minh 'ck_<table>_<mục đích>'.
-- ==========================================================================================


-- ==========================================================================================
-- 1. conversation — một phiên hỏi đáp, NEO VÀO ĐÚNG MỘT analysis_run.
--
-- Neo vào run chứ không vào project là điều kiện để hậu kiểm trích dẫn có nghĩa: short_id chỉ
-- duy nhất trong phạm vi một run (UNIQUE (analysis_run_id, short_id) ở V2). Không có neo này
-- thì 'EV-01A3' là một chuỗi mơ hồ, và resolution WRONG_RUN không thể tồn tại.
-- ==========================================================================================
CREATE TABLE conversation (
    id              UUID        PRIMARY KEY,
    -- Phân quyền đi qua owner_id ở đây, KHÔNG suy ngược qua analysis_run -> snapshot -> project:
    -- suy ngược là ba lần join trên mọi request, và mỗi chỗ quên join là một lỗ rò dữ liệu.
    owner_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    analysis_run_id UUID        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    -- Cảnh báo đang bàn tới. NULL nghĩa là hỏi ở phạm vi cả run (vd "có bao nhiêu lỗi nghiêm
    -- trọng?"). Lưu short_id chứ không lưu UUID: đây là thứ người dùng thấy trên URL và là thứ
    -- LLM trích dẫn — giữ cùng một định danh xuyên suốt thì không có chỗ nào để dịch sai.
    focus_short_id  TEXT,
    title           TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE conversation IS
    'Một phiên hỏi đáp với Agent, neo vào đúng một analysis_run — phạm vi mà Evidence ID có nghĩa.';
COMMENT ON COLUMN conversation.focus_short_id IS
    'Cảnh báo đang bàn tới (EV-XXXX). NULL = hỏi ở phạm vi cả run.';

CREATE INDEX idx_conversation_owner ON conversation (owner_id, updated_at DESC);
CREATE INDEX idx_conversation_run ON conversation (analysis_run_id);


-- ==========================================================================================
-- 2. message — một lượt trong hội thoại.
--
-- Phản hồi của agent KHÔNG phải văn bản tự do: nó có verdict, có danh sách claim (bảng riêng),
-- và có thể bị CitationValidator hạ cấp. Ba cột verdict/downgraded_from_verdict/summary phản
-- chiếu đúng khuôn đã dùng ở `claim` để hai tầng đọc bằng cùng một logic.
-- ==========================================================================================
CREATE TABLE message (
    id                      UUID        PRIMARY KEY,
    conversation_id         UUID        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    ordinal                 INT         NOT NULL,
    role                    TEXT        NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    -- Câu hỏi của người dùng, hoặc `summary` của agent. Toàn văn claim nằm ở bảng `claim`.
    content                 TEXT        NOT NULL,
    verdict                 TEXT
                                CHECK (verdict IN ('LIKELY_TRUE_POSITIVE', 'LIKELY_FALSE_POSITIVE',
                                                    'INSUFFICIENT_EVIDENCE')),
    downgraded_from_verdict TEXT
                                CHECK (downgraded_from_verdict IN ('LIKELY_TRUE_POSITIVE',
                                                                    'LIKELY_FALSE_POSITIVE',
                                                                    'INSUFFICIENT_EVIDENCE')),
    -- Việc người dùng nên kiểm thêm. Mảng chuỗi thuần, không trích dẫn Evidence ID — nếu cho
    -- phép trích dẫn ở đây thì có HAI đường sinh citation, và mẫu số của Evidence Citation
    -- Accuracy hết rõ ràng (xem ADR 0010).
    suggested_next_checks   JSONB       NOT NULL DEFAULT '[]',
    -- Bằng chứng đi ngược lại kết luận, nguyên văn LLM viết. CÓ CHỦ ĐÍCH không đi qua
    -- claim_citation: đây không phải nhận định nên không thuộc mẫu số Unsupported Claim Rate.
    contradicting_short_ids JSONB       NOT NULL DEFAULT '[]',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (conversation_id, ordinal),

    -- Một lượt của agent LUÔN có verdict; câu hỏi của người dùng thì không bao giờ có. Không ép
    -- ở đây thì một lượt agent hỏng nửa chừng vẫn ghi được thành message trông như bình thường.
    CONSTRAINT ck_message_verdict_matches_role
        CHECK ((role = 'ASSISTANT') = (verdict IS NOT NULL)),
    -- Hạ cấp chỉ có MỘT chiều: xuống INSUFFICIENT_EVIDENCE. Ghi downgraded_from mà verdict hiện
    -- tại lại là thứ khác nghĩa là logic hậu kiểm hỏng — bắt ở tầng DB vì con số này đi thẳng
    -- vào báo cáo tuần 15.
    CONSTRAINT ck_message_downgrade_is_one_way
        CHECK (downgraded_from_verdict IS NULL OR verdict = 'INSUFFICIENT_EVIDENCE'),
    CONSTRAINT ck_message_suggested_is_array
        CHECK (jsonb_typeof(suggested_next_checks) = 'array'),
    CONSTRAINT ck_message_contradicting_is_array
        CHECK (jsonb_typeof(contradicting_short_ids) = 'array')
);

COMMENT ON TABLE message IS
    'Một lượt hỏi hoặc đáp. Lượt của agent luôn có verdict và có thể bị CitationValidator hạ cấp.';
COMMENT ON COLUMN message.contradicting_short_ids IS
    'Không đi qua claim_citation có chủ đích: không phải claim nên không thuộc mẫu số Unsupported Claim Rate.';

CREATE INDEX idx_message_conversation ON message (conversation_id, ordinal);


-- ==========================================================================================
-- 3. claim.message_id — trả nợ FK mà V2 để lại.
--
-- Bảng `claim` chưa có writer nào tới hết tuần 10-11 nên không cần backfill; vẫn viết UPDATE
-- phòng dữ liệu dev/test đã có hàng mồ côi, vì SET NOT NULL sẽ đỏ khi gặp NULL.
-- ==========================================================================================
DELETE FROM claim WHERE message_id IS NULL;

ALTER TABLE claim
    ALTER COLUMN message_id SET NOT NULL,
    ADD CONSTRAINT fk_claim_message FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    ADD CONSTRAINT ux_claim_message_ordinal UNIQUE (message_id, ordinal),
    -- Cùng bất biến một chiều như trên message. V2 chưa ép được vì lúc đó chưa có bước hạ cấp
    -- nào tồn tại để mà sai.
    ADD CONSTRAINT ck_claim_downgrade_is_one_way
        CHECK (downgraded_from_verdict IS NULL OR verdict = 'INSUFFICIENT_EVIDENCE');

CREATE INDEX idx_claim_message ON claim (message_id, ordinal);


-- ==========================================================================================
-- 4. llm_trace — nhật ký TỪNG LƯỢT gọi model, không phải từng câu trả lời.
--
-- Kế hoạch §4 gọi đây là "nguồn cho toàn bộ chương 4 báo cáo, không thể tái tạo sau". Một lượt
-- hỏi đáp gọi model nhiều lần (mỗi vòng tool-call một lần), nên khoá là (message, iteration)
-- chứ không phải message.
--
-- agent-service KHÔNG ghi bảng này: nó không có kết nối DB (ADR 0001). Nó trả số đo về trong
-- payload, core-backend ghi cùng transaction với message — nên không có trạng thái "đã trả lời
-- nhưng chưa có trace".
-- ==========================================================================================
CREATE TABLE llm_trace (
    id               UUID        PRIMARY KEY,
    conversation_id  UUID        NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    -- NULL khi lượt hỏi đáp hỏng trước lúc sinh được message. Chính những lượt đó mới cho biết
    -- agent thất bại ở vòng thứ mấy — xoá đi là mất đúng phần dữ liệu khó tái tạo nhất.
    message_id       UUID        REFERENCES message (id) ON DELETE CASCADE,
    iteration        INT         NOT NULL,
    provider         TEXT        NOT NULL,
    model            TEXT        NOT NULL,
    prompt_version   TEXT        NOT NULL,
    input_tokens     INT         NOT NULL DEFAULT 0,
    output_tokens    INT         NOT NULL DEFAULT 0,
    latency_ms       INT         NOT NULL DEFAULT 0,
    stop_reason      TEXT,
    -- Toàn bộ hội thoại gửi lên model ở vòng này. Nặng, nhưng thiếu nó thì không trả lời được
    -- câu hỏi "vì sao model kết luận thế" khi phản biện.
    request_messages JSONB       NOT NULL DEFAULT '[]',
    -- Tool model gọi ở vòng này: [{name, arguments, duration_ms, item_count}]. Đây là số liệu
    -- biến luận điểm "5 tool là ranh giới kiến trúc thật, đo được" thành bảng trong báo cáo.
    tool_calls       JSONB       NOT NULL DEFAULT '[]',
    response_content TEXT,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (conversation_id, message_id, iteration),
    CONSTRAINT ck_llm_trace_request_is_array
        CHECK (jsonb_typeof(request_messages) = 'array'),
    CONSTRAINT ck_llm_trace_tool_calls_is_array
        CHECK (jsonb_typeof(tool_calls) = 'array')
);

COMMENT ON TABLE llm_trace IS
    'Một lượt gọi model (một vòng của agent loop). Nguồn số liệu chương 4; không tái tạo được sau.';
COMMENT ON COLUMN llm_trace.message_id IS
    'NULL khi lượt hỏi đáp hỏng trước lúc sinh message — giữ lại để biết agent thất bại ở vòng nào.';

CREATE INDEX idx_llm_trace_conversation ON llm_trace (conversation_id, created_at);


-- ==========================================================================================
-- 5. Index trên evidence_record.short_id — trả nợ TODO ghi trong EvidenceRecordRepository.
--
-- UNIQUE (analysis_run_id, short_id) KHÔNG phục vụ được lookup chỉ có short_id (thiếu cột đầu).
-- Tới hết tuần 10-11 chỗ duy nhất cần nó là phân biệt 404 evidence-not-found với
-- evidence-wrong-run, chạy hoạ hoằn. Từ tuần 12-13, CitationValidator chạy đúng truy vấn đó
-- MỘT LẦN CHO MỖI TRÍCH DẪN KHÔNG KHỚP — và trích dẫn không khớp chính là thứ ta mong đợi
-- nhiều khi LLM bịa ID, tức là đường chậm lại là đường hay đi nhất.
--
-- KHÔNG unique: cùng một short_id tồn tại hợp lệ ở nhiều run khác nhau.
-- ==========================================================================================
CREATE INDEX idx_evidence_record_short_id ON evidence_record (short_id);
