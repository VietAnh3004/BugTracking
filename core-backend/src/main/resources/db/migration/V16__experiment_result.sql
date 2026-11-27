-- ==========================================================================================
-- V16 — kết quả thực nghiệm 3 cấu hình (LLM-Code / LLM-Static / Evidence-Agent).
-- ==========================================================================================

-- Mỗi dòng là MỘT Ô: một cảnh báo × một cấu hình × một lần lặp. Bảng riêng, KHÔNG ghi vào
-- conversation/message: RunMetricsService và RunExportService ghép câu trả lời Agent với cảnh báo
-- theo focus_short_id, nên câu trả lời của thực nghiệm mà nằm ở đó sẽ trộn ba cấu hình với nhau
-- và với dữ liệu user study. Giao thức: docs/thuc-nghiem.md.
CREATE TABLE experiment_result (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_run_id         UUID        NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    evidence_record_id      UUID        NOT NULL REFERENCES evidence_record (id) ON DELETE CASCADE,
    short_id                TEXT        NOT NULL,
    config                  TEXT        NOT NULL
        CHECK (config IN ('EVIDENCE_AGENT', 'LLM_CODE', 'LLM_STATIC')),
    repeat_index            INTEGER     NOT NULL CHECK (repeat_index >= 1),
    status                  TEXT        NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    -- Verdict SAU hậu kiểm trích dẫn (CitationValidator), giống câu trả lời Agent thật.
    verdict                 TEXT
        CHECK (verdict IN ('LIKELY_TRUE_POSITIVE', 'LIKELY_FALSE_POSITIVE', 'INSUFFICIENT_EVIDENCE')),
    downgraded_from_verdict TEXT
        CHECK (downgraded_from_verdict IN ('LIKELY_TRUE_POSITIVE', 'LIKELY_FALSE_POSITIVE')),
    claim_count             INTEGER     NOT NULL DEFAULT 0,
    unsupported_claim_count INTEGER     NOT NULL DEFAULT 0,
    citation_count          INTEGER     NOT NULL DEFAULT 0,
    citation_resolved_count INTEGER     NOT NULL DEFAULT 0,
    -- Câu trả lời đã hậu kiểm, đủ từng nhận định và từng trích dẫn — để đọc lại khi phân tích lỗi.
    answer                  JSONB,
    model                   TEXT,
    prompt_version          TEXT,
    llm_calls               INTEGER     NOT NULL DEFAULT 0,
    tool_calls              INTEGER     NOT NULL DEFAULT 0,
    input_tokens            INTEGER     NOT NULL DEFAULT 0,
    output_tokens           INTEGER     NOT NULL DEFAULT 0,
    latency_ms              INTEGER     NOT NULL DEFAULT 0,
    error_message           TEXT,
    -- Nhật ký từng vòng gọi model, cùng hình dạng với llm_trace (bảng đó buộc có conversation_id).
    traces                  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Ô hỏng được chạy lại và GHI ĐÈ; ô đã thành công thì bị bỏ qua khi "Chạy tiếp".
    CONSTRAINT uq_experiment_cell UNIQUE (analysis_run_id, evidence_record_id, config, repeat_index),
    CONSTRAINT ck_experiment_verdict_when_succeeded
        CHECK ((status = 'SUCCEEDED') = (verdict IS NOT NULL))
);

CREATE INDEX ix_experiment_result_run ON experiment_result (analysis_run_id);

COMMENT ON TABLE experiment_result IS
    'Thực nghiệm 3 cấu hình: một dòng = một cảnh báo × một cấu hình × một lần lặp (docs/thuc-nghiem.md).';
