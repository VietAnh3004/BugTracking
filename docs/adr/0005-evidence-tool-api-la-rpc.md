# ADR 0005 — Evidence Tool API là RPC theo tên tool, tách khỏi REST resource của UI

- **Trạng thái**: Chấp nhận
- **Ngày**: 30/07/2026

## Bối cảnh

Tuần 4 thiết kế hợp đồng API. Kế hoạch (§4) đặt tên sẵn 5 evidence tool mà Agent Service gọi:
`get_warning`, `get_code_context`, `get_related_tests`, `get_coverage`, `search_evidence`. Câu hỏi
cần chốt: 5 tool này có nên dùng chung một bộ REST resource với các màn hình UI (project, evidence
list...) hay tách riêng?

Ràng buộc kiến trúc đã chốt: agent-service **không** truy cập DB, chỉ gọi Evidence Tool API của
core-backend (§1). `experiments/runner.py` (§6) chạy **batch** 3 cấu hình × 3 lần trên toàn bộ
warning — không có phiên người dùng nào để gắn vào các lệnh gọi đó.

## Quyết định

1. **Evidence Tool API là RPC đặt tên theo tool**, không phải REST resource:
   ```
   POST /api/v1/tools/get_warning        body = ĐÚNG object arguments LLM sinh ra (snake_case)
   POST /api/v1/tools/get_code_context
   POST /api/v1/tools/get_related_tests
   POST /api/v1/tools/get_coverage
   POST /api/v1/tools/search_evidence
   ```
   DTO tool gắn `@JsonNaming(SnakeCaseStrategy.class)` **trên từng record**, không đổi naming
   strategy toàn cục của `ObjectMapper`.

2. **`/api/v1/tools/**` dùng service token riêng**, không forward JWT người dùng (enforce ở tuần
   5-7, chưa có Spring Security ở tuần 4). Phân quyền "user nào xem được run nào" xảy ra ở entry
   point: core-backend xác thực user trước khi gọi agent, truyền xuống `analysis_run_id` đã duyệt.

3. Controller tool và controller UI (`ProjectController`, và các controller UI tương lai) gọi
   **cùng một** `EvidenceQueryService` — khác nhau ở DTO đầu ra, không khác ở truy vấn SQL.

4. Quy ước đặt tên DTO: nhóm tool dùng hậu tố `...Request`/`...Result`
   (`GetWarningRequest`/`GetWarningResult`), nhóm UI dùng `...Response`/`...Request`. springdoc đặt
   tên schema OpenAPI theo simple name — hai DTO trùng tên ở hai package sẽ đè nhau **im lặng**.

## Lý do

**(1) RPC thay vì REST resource.** Ba lý do, theo thứ tự sức nặng:

- **Naming convention không dung hoà được.** Tool call của LLM theo `snake_case` (đề cương đặt
  tên tool `get_warning`, `search_evidence`); DTO Java serialize `camelCase`. Gộp chung buộc
  agent-service phải có tầng dịch tên trường — làm hỏng chính luận điểm §1 "5 tool là ranh giới
  kiến trúc thật, dễ log/đo", vì cái được log ở BE không còn giống cái LLM sinh ra.
- **Ngữ nghĩa phân trang khác về bản chất.** UI cần `page/size/total` để bấm trang. LLM **không**
  phân trang được — chỉ có `limit` + `total` để biết đang nhìn phần bị cắt. Đưa `page` vào tool
  schema là mời LLM gọi `page=2` rồi loop.
- **Vòng đời phiên bản khác nhau.** Tool contract ràng buộc với prompt + JSON Schema của LLM; UI
  contract ràng buộc với màn hình React — breaking ở hai nhịp khác nhau (§1 điểm 6: "hợp đồng API
  là ranh giới phiên bản").

Hệ quả: 1 tool call = 1 POST là quan hệ **1:1 tuyệt đối**, đếm/log/đo được thật qua
`ToolCallLoggingInterceptor`; agent-service tuần 12-13 chỉ còn
`httpx.post(f"{base}/api/v1/tools/{tool_name}", json=tool_args)` — một hàm cho cả 5 tool.

Đánh đổi: POST cho thao tác đọc phá semantics HTTP và mất cache. Với 5 tool đọc thuần, lợi ích
"arguments verbatim" (LLM gọi gì, BE nhận đúng thế, không qua tầng dịch) lớn hơn.

**(2) Service token thay vì forward JWT.** Batch experiment runner không có phiên người dùng.
Forward JWT sẽ vỡ ở đúng thực nghiệm cốt lõi của đồ án (§6), và kéo theo cần cột `owner` trên
`project` + join `analysis_run → repository_snapshot → project` — cả hai chưa tồn tại ở tuần 4.

## Hệ quả

- `/api/v1/openapi` nằm dưới `/api/**` → khi tuần 5-7 bật Spring Security, snapshot test OpenAPI
  sẽ đỏ (401/302) trừ khi loại trừ rõ ràng. Đó là tín hiệu tốt (test bắt được thay đổi thật), cần
  biết trước để không hoảng khi gặp.
- Service layer (`EvidenceQueryService`) phải giữ **đúng một** chỗ resolve
  `analysis_run_id → project` — chỗ tuần 5-7 cắm authorization vào.
- Không có nhóm REST resource UI cho `analysis-runs`/`evidence` ở tuần 4 (hoãn tới tuần 10-11 khi
  Evidence Explorer cần) — quyết định *tách* hai không gian tên vẫn giữ nguyên ở đây, chỉ hoãn
  phần *hiện thực*.
- `EvidenceQueryService` ném 4 exception nghiệp vụ (`EvidenceNotFoundException`,
  `EvidenceWrongRunException`, `EvidenceNotAWarningException`, `RunNotQueryableException`), ánh xạ
  1-1 với `ClaimCitationResolution` đã có từ ADR 0004 — CitationValidator tuần 12-13 dùng lại
  nguyên xi, không phải thiết kế lại phân loại lỗi.

## Bổ sung 19/11/2026 — `get_code_context` phải trả MÃ, và bỏ `context_lines`

**Lỗi phát hiện.** Tool được mô tả là "lấy đoạn mã nguồn", system prompt bảo agent "đọc mã bằng
`get_code_context`", nhưng `ToolEvidenceItem` không có trường nào chứa mã: item CODE_CONTEXT chỉ có
`summary` kiểu "dòng 38-45", dù `evidence_record.content.snippet` đã lưu sẵn thân method. Agent gọi tool
nhiều lần, lần nào cũng không thấy mã, cạn ngân sách vòng truy xuất rồi kết luận dựa vào tên lớp và mức
nghiêm trọng. Ví dụ: cảnh báo `WEAK_MESSAGE_DIGEST_MD5` ở `checksumForDeduplication` (MD5 băm nội dung
tệp, false positive) bị kết luận "lỗi thật" y như ca băm mật khẩu.

**Quyết định.**

1. Thêm trường `snippet` vào `ToolEvidenceItem` — chỉ có ở item `CODE_CONTEXT` (lấy từ
   `content.snippet`), vắng mặt trong JSON ở các loại khác (`@JsonInclude(NON_NULL)`). Thêm trường là
   thay đổi tương thích ngược, không phải breaking change.
2. Mã dài quá 3000 ký tự thì cắt ở ranh giới dòng và **ghi rõ đã cắt** (ngân sách bằng chứng của cả
   phiên chỉ ~12000 ký tự; cắt im lặng khiến model tưởng đã đọc hết method).
3. `context_lines` **bỏ khỏi JSON Schema gửi cho model**: mã trả về là nguyên method/lớp chứa dòng bị
   cảnh báo, không phải "N dòng quanh dòng đó", nên tham số này không có tác dụng và chỉ mời model gọi
   lại vô ích. Backend vẫn chấp nhận trường này (bị bỏ qua) để request cũ không vỡ.

**Hệ quả cho thực nghiệm.** Mọi lượt hỏi Agent chạy TRƯỚC thay đổi này đã trả lời khi không thấy mã
nguồn, nên số liệu của chúng (Evidence Citation Accuracy, tỉ lệ kết luận đúng) không so sánh trực tiếp
được với số liệu sau thay đổi; cần chạy lại trước khi đưa vào chương 4. Prompt version vẫn `v1` vì
prompt không đổi — cột `llm_trace.created_at` là mốc phân biệt hai giai đoạn.
