# ADR 0004 — Unified Evidence Model hai tầng, TEXT + CHECK thay enum native, hoãn cột vector

- **Trạng thái**: Chấp nhận
- **Ngày**: 30/07/2026

## Bối cảnh

Tuần 3 thiết kế Unified Evidence Model. Đảo case study lên trước thiết kế (tuần 2, xem
[docs/ke-hoach-20-tuan.md §7bis](../ke-hoach-20-tuan.md)) đúng như dự đoán: đã tự parse toàn bộ
4 artifact thật trong [`docs/samples/vulnshop/`](../samples/vulnshop/) và phát hiện sketch schema
ban đầu (kế hoạch mục 2) không biểu diễn được dữ liệu thật ở bốn điểm — chi tiết đầy đủ và số
liệu đã đếm ở [`docs/evidence-model.md` mục 6](../evidence-model.md#6-bốn-phát-hiện-từ-artifact-thật-quyết-định-thiết-kế):

1. 3/28 cảnh báo SpotBugs có nhiều hơn 1 vị trí (một cái trỏ tới 3 file khác nhau); 5/28 có 1 vị
   trí nhưng không đánh dấu `primary`.
2. 3/5 test hỏng không có stack frame nào trỏ về mã production.
3. JaCoCo không cho biết "dòng X thuộc method nào" — cấu trúc kép, không lồng nhau.
4. 4 định dạng đường dẫn khác nhau, không join trực tiếp được; không artifact nào có commit SHA.

## Quyết định

1. **Chỉ tạo 5 bảng xương sống ở V2**: `analysis_run`, `evidence_record`, `warning`,
   `evidence_link`, `claim` (+ `claim_citation`). Các bảng "thô" mirror trực tiếp từng công cụ
   (`test_case`, `stack_frame`, `coverage_line`, `coverage_method`, `warning_location`) hoãn sang
   V3 (tuần 8-9), chỉ thiết kế trước trong tài liệu.
2. **`warning` là bảng mở rộng 1:1 của `evidence_record`** (`evidence_record_id` vừa PK vừa FK).
3. **`TEXT + CHECK` thay enum native Postgres** cho mọi cột kiểu liệt kê.
4. **`short_id` dạng `EV-XXXX`** sinh bằng generated column: counter trong phạm vi run + 1 ký tự
   băm từ UUID.
5. **"0 cảnh báo = lỗi" ép bằng CHECK constraint** trên `analysis_run.status`, không chỉ trong code.
6. **`claim_citation` giữ nguyên văn** Evidence ID mà LLM trích dẫn, không xoá dòng nào, kể cả
   khi không resolve được.
7. **Không tạo `evidence_embedding` ở V2.** Xác nhận chuỗi pgvector chạy được bằng test riêng
   (bảng tạm, tự `CREATE EXTENSION`), không migrate cột vector thật cho tới khi chọn model.

## Lý do

**(1) Phạm vi 5 bảng.** Bảng thô không có consumer nào cho tới EvidenceLinker (tuần 10-11) và
parser thật (tuần 8-9) — tạo sớm là bảng rỗng, không parser, không test, sai gì cũng không ai
biết cho tới lúc đó. Migration của dự án là append-only (`baseline-on-migrate: false`), nên sửa
bảng rỗng ở V3 gần như miễn phí; 5 bảng còn lại là thứ tuần 4 (API contract) thật sự cần và thứ
sai thì phải làm lại ở tuần 15 (kế hoạch mục 2 đã cảnh báo riêng cho `claim`).

**(2) Mở rộng 1:1.** Không lặp `file_path`/`start_line`/`symbol` ở hai bảng. "Trích dẫn một
warning" và "trích dẫn bất kỳ evidence" nhờ vậy là cùng một cơ chế: CitationValidator một nhánh,
SQL tính Unsupported Claim Rate ở tuần 15 chỉ join `evidence_record` một kiểu. Khuôn này dùng lại
được cho `test_case`/`coverage_*` ở V3.

**(3) TEXT + CHECK.** Tuần 3 là lần đầu dự án có entity JPA và lần đầu `ddl-auto: validate` chạy
thật (trước đó 0 entity, validate là no-op). Enum native Postgres cần `@JdbcTypeCode(NAMED_ENUM)`
và hay báo lệch kiểu — không nên cộng thêm một lớp type mapping lạ đúng lúc đang gỡ lỗi validate
lần đầu. Đổi tập giá trị bằng migration Flyway thường, không vướng ràng buộc transaction của
`ALTER TYPE ... ADD VALUE`. Bù lại: `EnumCheckConstraintTest` đọc `pg_get_constraintdef` và so
bằng nhau tuyệt đối với `Enum.values()` cho cả 11 CHECK kiểu liệt kê — bắt đúng lỗi mà enum
native lẽ ra bắt hộ.

**(4) `short_id`.** Random 4 hex đụng birthday collision ở quy mô vài trăm evidence/run. Ký tự
băm từ UUID khiến LLM bịa `EV-0007` gần như luôn resolve thất bại (`UNKNOWN_ID`) thay vì vô tình
trỏ đúng evidence khác — quan trọng cho tính đúng của Evidence Citation Accuracy.

**(5) CHECK cho "0 cảnh báo = lỗi".** Đã hỏng thật hai lần (SpotBugs trên JDK 25; find-sec-bugs
khai sai chỗ trong pom.xml, xem ADR 0003) — cả hai lần pipeline vẫn báo "thành công". Ràng buộc
tầng DB khiến con đường ghi im lặng bị chặn bằng cấu trúc, không bằng thiện chí của code:
`SUCCEEDED` bắt buộc `finding_count > 0`; dự án sạch thật đi qua `SUCCEEDED_CLEAN` kèm
`clean_ack_note` bắt buộc.

**(7) Hoãn vector.** Số chiều embedding phụ thuộc model chưa chọn (tuần 10-11); tạo bảng với số
chiều đoán trước gần như chắc chắn phải migrate lại. `infra/init/01-extensions.sql` không chạy
trong Testcontainers, nên `PgvectorSmokeTest` tự `CREATE EXTENSION IF NOT EXISTS vector` trong
mỗi test — xác nhận toàn chuỗi pgvector (image `pgvector/pgvector:pg16`, extension, truy vấn
cosine) chạy được ngay tuần 3 mà không phải chốt số chiều sớm.

## Hệ quả

- Bảng thô V3 sẽ cần entity + parser + migration `ALTER`/`CREATE` mới ở tuần 8-9; thiết kế chi
  tiết (cột, lý do) đã ghi sẵn ở `docs/evidence-model.md` mục "Thiết kế cho V3".
  > *Ghi chú thêm 04/08/2026 (không sửa nội dung quyết định gốc):* số hiệu migration thật của đợt
  > bảng thô là **`V5`**, không phải V3 — tuần 5-7 đã dùng mất V3 và V4. Mục tương ứng trong
  > `docs/evidence-model.md` nay tên là "Thiết kế cho V5".
- `claim.message_id` chưa có ràng buộc FK — bảng `message`/`conversation` thuộc phạm vi tuần
  12-13, ràng buộc sẽ thêm bằng migration riêng khi đó.
- Rủi ro lớn nhất còn lại: chuẩn hoá đường dẫn (4 định dạng khác nhau, `originalUriBaseIds`
  không tồn tại trong SARIF thật) là heuristic có thể hỏng im lặng thành 0 `evidence_link` —
  trông y hệt "không có bằng chứng liên quan". Việc viết hàm chuẩn hoá + test đối chiếu 4 định
  dạng thuộc tuần 8-9 khi có parser thật; khi đó bắt buộc có cột đếm kiểu
  `unresolved_location_count` để thất bại hiện thành số, không im lặng — đúng nguyên tắc đã áp
  dụng cho "0 cảnh báo = lỗi" ở ADR này.
- `EnumCheckConstraintTest` phải cập nhật cùng lúc mỗi khi thêm giá trị enum mới — nếu quên, CI
  đỏ ngay chứ không lộ ra khi đã chạy thực nghiệm.
