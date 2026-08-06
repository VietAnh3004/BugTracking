# ADR 0009 — Liên kết bằng chứng ba tầng, và schema embedding trước khi chọn mô hình

- **Trạng thái**: Chấp nhận
- **Ngày**: 06/08/2026
- **Liên quan**: [ADR 0004](0004-unified-evidence-model-hai-tang.md) (Unified Evidence Model, hoãn
  `evidence_embedding`), [ADR 0005](0005-evidence-tool-api-la-rpc.md) (tách hai không gian tên API),
  [docs/evidence-model.md](../evidence-model.md) mục 6 (bốn phát hiện từ artifact thật)

## Bối cảnh

Hết tuần 8-9, Evidence Store có đủ bảy bảng và 90 `evidence_record` trên vulnshop, nhưng các bằng
chứng **nằm rời nhau**. Hệ quả cụ thể: ba trong năm evidence tool (`get_code_context`,
`get_related_tests`, `get_coverage`) luôn trả mảng rỗng, vì cả ba đọc `evidence_link` mà chưa có gì
ghi vào bảng đó.

Kế hoạch gốc §3 gọi phần liên kết là **rủi ro cao nhất của đồ án**, và chỉ định chiến lược ba tầng.
Tài liệu này ghi lại những gì phải quyết khi hiện thực nó — gồm bốn chỗ mà dữ liệu thật buộc phải
đi khác kế hoạch.

## Quyết định

### 1. Ba tầng, mỗi tầng một `method` riêng, và `method` bắt buộc lộ ra tới tận giao diện

| Tầng | Quan hệ | `method` | Căn cứ |
|---|---|---|---|
| 1 — vị trí | `LOCATED_IN` | `LINE_EXACT` / `RANGE_OVERLAP` | Dòng cảnh báo nằm trong phạm vi method (JavaParser) |
| 2a — độ phủ | `COVERS` | `LINE_EXACT` / `LINE_NEAREST_EXECUTABLE` | `coverage_line` tại đúng dòng |
| 2b — quy ước tên | `SUPPORTS` / `CONTRADICTS` | `METHOD_AGGREGATE` / `NAMING_CONVENTION` | `FooTest` ↔ `Foo` |
| 3 — stack trace | `SUPPORTS` | `STACK_FRAME` | Frame production trùng `(file, line)` với cảnh báo |

`weight` là **độ tin cậy của LIÊN KẾT**, không phải mức độ phủ và không phải mức nghiêm trọng của
cảnh báo. Trạng thái phủ (`COVERED`/`PARTIAL`/`MISSED` kèm `mi/ci/mb/cb`) nằm ở `rationale`. Trộn
hai thứ đó thì mọi xếp hạng bằng chứng ở tuần 12-13 sai.

### 2. Dedup trong bộ nhớ theo thứ bậc, KHÔNG dùng `ON CONFLICT DO NOTHING`

`UNIQUE (from_evidence_id, to_evidence_id, relation)` sẽ vỡ khi hai tầng cùng đề xuất một cặp —
chuyện xảy ra thật: một cảnh báo có nhiều vị trí trong cùng một method sinh cùng một liên kết
`LOCATED_IN` nhiều lần.

Chữa bằng `ON CONFLICT DO NOTHING` thì **thứ tự chèn quyết định độ tin cậy được ghi lại**: một liên
kết `STACK_FRAME` (weight 1.0) có thể bị `NAMING_CONVENTION` (weight 0.3) chiếm chỗ, và tỉ lệ "liên
kết yếu" mà tuần 15 báo cáo sai theo hướng bi quan mà không ai phát hiện.

Nên: dedup ở `LinkAccumulator` theo weight rồi tới thứ bậc method, và INSERT **không** có
`ON CONFLICT`. Trùng sau dedup nghĩa là bug, và bug đó phải nổ chứ không được nuốt.

### 3. `CONTRADICTS` đòi ba điều kiện, và mang sẵn câu cảnh báo trong dữ liệu

Chỉ sinh khi: (1) dòng cảnh báo **thực sự được chạy** (`ci > 0`), (2) cảnh báo **chưa có**
`SUPPORTS` nào, (3) có ít nhất một test PASSED khớp theo quy ước tên.

Điều kiện (1) dễ quên nhất — test xanh mà không đi qua dòng đó thì không phản bác được gì.

`rationale` của mọi liên kết `CONTRADICTS` viết thẳng câu *"KHÔNG chứng minh cảnh báo sai"*. Lý do:
vulnshop cố tình đặt bẫy đúng chỗ này — `findByUsername` có SQL injection thật, test đường đi bình
thường vẫn xanh, và có một test xanh khác còn **chứng minh** lỗ hổng có thật. Đặt cảnh báo vào chính
dữ liệu (thay vì chỉ vào prompt) khiến nó đi theo bằng chứng tới mọi nơi bằng chứng đó tới.

### 4. CODE_CONTEXT sinh ở đường ingest, KHÔNG ở bước liên kết

`EvidenceLinker` chạy lại được; CODE_CONTEXT thì **không được phép**. `short_id` sinh từ
`seq_in_run`, nên mỗi lần sinh/xoá lại evidence là mọi `claim_citation` đã ghi trỏ vào hư không —
trong khi `resolution` của chúng vẫn ghi `RESOLVED`. Đó là hỏng im lặng phá thẳng chỉ số Evidence
Citation Accuracy.

Ranh giới: **chạy lại tính lại được CÁCH KHỚP, không tính lại được KẾT QUẢ PARSE.** Sửa
`CodeContextExtractor`, `StackTraceParser` hay chuẩn hoá đường dẫn thì phải phân tích lại từ đầu.

### 5. `evidence_embedding` khai `vector` KHÔNG kèm số chiều

ADR 0004 quyết định 6 hoãn bảng này tới khi chọn được mô hình. Đã cân nhắc lấy số chiều từ một
placeholder Flyway và **từ chối**: Flyway tính checksum trên file thô, placeholder thay lúc chạy
không làm đổi checksum — nên đổi biến môi trường sau đó thì schema im lặng giữ số chiều cũ và mọi
insert lỗi ở runtime. Placeholder chỉ tạo ảo giác linh hoạt.

Đánh đổi đã đo trên chính Postgres của dự án: cột không khai số chiều **không tạo được index
HNSW/IVFFlat** (`ERROR: column does not have dimensions`). Chấp nhận vì tuần 10-11 chưa có vector
nào để tìm kiếm. Tuần 12-13 sau khi chốt mô hình: `ALTER COLUMN ... TYPE vector(N)` rồi tạo HNSW —
**không** IVFFlat, vì nó cần dữ liệu để huấn luyện danh sách và tạo trên bảng rỗng cho ra một index
vô dụng mà không báo gì.

`PRIMARY KEY (chunk_id, model)` để tuần 16 so được nhiều mô hình trên cùng tập bằng chứng.
`chunk_id` là khoá dẫn xuất `<evidence_record_id>:<chunk_ordinal>` ép bằng CHECK, nhờ vậy nhúng lại
là upsert idempotent.

### 6. REST UI cho evidence là MỘT endpoint gộp

`GET /api/v1/analysis-runs/{runId}/evidence/{shortId}` trả neo + cả bốn nhóm liên quan trong một
lượt. ADR 0005 tách hai *không gian tên* (RPC snake_case cho LLM, REST camelCase cho UI) — nó không
nói REST phải là bốn resource nhỏ ánh xạ 1-1 với bốn tool.

Ba lý do gộp: FE/BE khác domain nên mỗi request kéo theo một preflight; backend chỉ quét
`evidence_link` một lần; và **"rỗng" phải là một sự kiện nguyên tử** — bốn panel tải độc lập sẽ có
khoảnh khắc "test rỗng nhưng coverage đang tải", mà phân biệt được "đã kiểm và không thấy" với
"chưa kiểm" chính là điều kiện để `INSUFFICIENT_EVIDENCE` có nghĩa.

Nội dung mã nguồn **không** nằm trong response: lấy qua endpoint `/snapshots/{id}/file` đã có từ
tuần 5-7. Nhân đôi taxonomy lỗi `binary-file`/`file-content-not-stored` là tạo chỗ thứ hai để sai.

## Bốn chỗ dữ liệu thật buộc đi khác kế hoạch

1. **Tầng 3 phục hồi 0%, không phải ~40%.** `docs/evidence-model.md` phát hiện 2 ghi "3/5 test hỏng
   không có frame production", ngụ ý 2/5 còn lại dùng được. Thực tế cả hai frame đó trỏ vào
   `CartService:25` và `UserService:30` — mà **không analyzer nào cảnh báo trên hai file đó**.
   Có frame production mới là điều kiện *cần*; frame còn phải rơi vào một dòng đã có cảnh báo.
   Đã gieo TP-16 để vulnshop có một ca `SUPPORTS` thật (xem `SEEDED.md`).

2. **Tầng 2 mới là tầng gánh chính**, không phải phương án lùi. Trên vulnshop: 40
   `METHOD_AGGREGATE` + 10 `NAMING_CONVENTION` so với 1 `STACK_FRAME`.

3. **`CONTRADICTS` nhiều gấp hơn 5 lần `SUPPORTS`** (43 so với 8), vì mỗi test xanh trong lớp test
   khớp đều sinh một liên kết. Về dữ liệu là đúng, nhưng tuần 12-13 phải cân nhắc khi dựng prompt:
   một LLM đọc 43 dòng "test xanh phủ dòng này" rất dễ nghiêng về `LIKELY_FALSE_POSITIVE`.

4. **Semgrep không khai tên lớp** (SARIF thật không có `logicalLocations`), nên tầng 2 phải lấy tên
   lớp từ `symbol` của CODE_CONTEXT bao quanh. Không có bước này thì mọi cảnh báo Semgrep tự động
   rơi khỏi tầng 2.

## Hệ quả

- Tỉ lệ liên kết yếu đọc thẳng từ `analysis_run.link_counts_by_method`, không phải tính lại bằng
  SQL ad-hoc ở tuần 15.
- `ck_run_links_not_silently_empty` biến "có ngữ cảnh mã mà 0 liên kết" thành lỗi ở tầng DB. Vế
  `code_context_count = 0` tránh đỏ giả cho repo không có Java.
- `POST /analysis-runs/{id}/links/rebuild` cho phép sửa heuristic mà không phải chạy lại sandbox.
  Ra 0 liên kết thì ném và rollback — một lần chạy lại xoá sạch rồi báo thành công là mất dữ liệu
  trong im lặng.
- Bộ đếm liên kết đi qua `RunOutcome` ở đường ingest, KHÔNG do linker tự UPDATE: `finalizeRun` là
  `REQUIRES_NEW` nên nó treo transaction ngoài và sẽ ghi đè bằng giá trị cũ.
