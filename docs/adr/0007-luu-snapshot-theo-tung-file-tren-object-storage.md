# ADR 0007 — Lưu snapshot theo từng file trên object storage, chỉ mục phẳng trong Postgres

- **Trạng thái**: Chấp nhận
- **Ngày**: 03/08/2026

## Bối cảnh

Tuần 5-7 cần đưa mã nguồn của người dùng vào hệ thống để tuần 8-9 chạy analyzer lên đó và tuần
10-11 hiển thị "vùng bằng chứng" theo `file:line`. Câu hỏi phải chốt: lưu mã nguồn ở dạng nào.

Ràng buộc có sẵn:

- FE/BE deploy độc lập, **không chia sẻ filesystem** ([ADR 0002](0002-frontend-cau-hinh-luc-chay.md)).
- Truy vấn chủ đạo của toàn dự án là **"cho tôi dòng 40-60 của file X"** — `evidence_record` lưu
  `file_path` + `start_line`/`end_line` ([ADR 0004](0004-unified-evidence-model-hai-tang.md)).
- Repo cỡ đồ án: vài trăm file, vài MB.

## Quyết định

**Mỗi file một object** trên object storage với key `snapshots/{snapshotId}/{path}`, cộng một
**bảng chỉ mục phẳng** `snapshot_file` trong Postgres (`path`, `parent_path`, `name`, `entry_type`,
`size_bytes`, `line_count`, `sha256`, `is_binary`, `stored`, `storage_key`).

**Postgres là nguồn sự thật; object storage được phép có rác.**

Ba hệ quả về thứ tự thao tác, ghi thành quy tắc trong `SnapshotService`:

1. Ghi **object trước, hàng DB sau** trong vòng lặp. Hàng DB trỏ tới object không tồn tại làm người
   dùng thấy lỗi khi mở file (tệ); object không có hàng DB chỉ tốn dung lượng (chấp nhận được).
2. Rác đặt tên theo `snapshotId` **không bao giờ tái sử dụng** (UUID mới mỗi lần upload), nên không
   có nguy cơ một snapshot đọc nhầm object của snapshot khác.
3. Hàng `status = FAILED` là **bia mộ**: nó ghi lại việc đã có object nào đó được tạo, để job dọn
   sau này biết prefix nào cần xoá.

**Tuyệt đối không bọc transaction quanh vòng lặp upload.** Hàng nghìn lượt gọi mạng tới MinIO bên
trong một transaction sẽ giữ một connection Postgres suốt thời gian đó và cạn
`hikari.maximum-pool-size: 10`, treo toàn bộ ứng dụng cho mọi request khác. Vì vậy `SnapshotService`
hoàn toàn không mang `@Transactional`; mọi thao tác DB đi qua `SnapshotPersistenceService`, mỗi
thao tác một transaction ngắn riêng biệt.

## Vì sao không lưu nguyên file zip

Đây là phương án đơn giản nhất và đã bị loại vì nó phá đúng truy vấn chủ đạo: để đọc dòng 40-60 của
một file, phải tải toàn bộ zip về, giải nén trong bộ nhớ, rồi mới cắt lát — **mỗi lần mở một file**.
Với source viewer (người dùng bấm qua lại giữa các file) và với agent (mỗi lời gọi `get_code_context`
là một lần đọc), chi phí này lặp lại liên tục. Ngoài ra, không có zip nào cho biết "repo này có bao
nhiêu file `.java`" mà không giải nén trước.

## Vì sao không lưu nội dung file vào Postgres

Cân nhắc nghiêm túc vì nó cho phép đọc dòng bằng SQL thuần. Loại vì: mã nguồn là dữ liệu lớn, bất
biến, và không cần truy vấn theo nội dung ở đợt này — đúng loại dữ liệu mà object storage sinh ra
để chứa. Nhồi vào Postgres làm phình `pg_dump`, phình WAL, và trộn lẫn dữ liệu quan hệ (thứ cần sao
lưu/khôi phục cẩn thận) với blob (thứ tạo lại được từ zip gốc nếu cần).

## Vì sao chỉ mục PHẲNG chứ không phải cây lồng nhau

`GET /snapshots/{id}/files` trả **danh sách phẳng toàn snapshot**, frontend tự dựng cây
(`buildFileTree.ts`). Với repo vài trăm file, JSON phẳng nhỏ hơn cây lồng (không lặp lại cấu trúc
bao bọc), backend đơn giản hơn (một truy vấn, một `ORDER BY`), và frontend giữ được trạng thái thư
mục đang mở khi đổi file — thứ mà API trả cây theo từng mức không làm được nếu không gọi lại API
mỗi lần bung một thư mục.

Cột `parent_path` + index `idx_snapshot_file_dir` vẫn được tạo dù chưa dùng: rẻ, và là đường thoát
sẵn sàng nếu sau này gặp repo lớn tới mức phải liệt kê từng mức.

## Bảo mật: vì sao zip-slip vẫn nguy hiểm dù không ghi ra filesystem

Mô hình đe doạ ở đây **khác** với zip-slip cổ điển. Ta không bao giờ gọi `new File(baseDir, entryName)`,
nên không có chuyện ghi đè `/etc/passwd`. Nhưng `path` sau chuẩn hoá trở thành một phần **key của
object storage**, mà key S3 là chuỗi opaque — nó **không tự chuẩn hoá** `../`. Một entry tên
`../B/x.txt` sẽ được lưu nguyên văn thành `snapshots/A/../B/x.txt`, phá vỡ giả định "prefix
`snapshots/{id}/` cô lập từng snapshot" — nền tảng của cả cơ chế dọn rác theo prefix lẫn phân quyền.

Vì vậy đường dẫn bị **từ chối**, không phải "làm sạch", và được chặn ở **ba tầng độc lập**:
`ZipExtractor` (Java), `CHECK` constraint `ck_snapshot_file_path_safe` (Postgres), và
`buildFileTree` (frontend, bỏ qua kèm `console.warn`).

## Hệ quả

**Tích cực**

- Đọc một khoảng dòng chỉ tốn đúng một lần `getObject` + cắt lát ở server.
- Cây file dựng được mà không chạm object storage lần nào (chỉ đọc bảng chỉ mục).
- `line_count` tính một lần lúc upload, mọi lần đọc sau dùng lại — và có log WARN canh khi chỉ mục
  lệch nội dung thật.
- Nhiều instance backend cùng đọc được một snapshot; không có state trên đĩa cục bộ.

**Tiêu cực, chấp nhận có ý thức**

- Upload một repo là hàng trăm lượt `putObject`, chậm hơn ghi một file zip. Chấp nhận vì upload là
  thao tác một lần, còn đọc thì lặp lại liên tục.
- Object mồ côi tồn tại khi upload hỏng giữa chừng. Không có job dọn ở tuần 5-7 — hàng `FAILED` đã
  ghi đủ thông tin để dọn sau.
- File vượt `max-stored-file-bytes` (mặc định 2MB) chỉ được lập chỉ mục, không có nội dung
  (`stored = false`). Cây file vẫn hiện chúng — cây phải trung thực với những gì có trong repo —
  nhưng đọc nội dung trả 409 kèm lý do rõ ràng.
- `line_count` được tính bằng `ZipExtractor.countLines()` lúc upload và **phải khớp từng chi tiết**
  với `SnapshotQueryService.splitLines()` lúc đọc. Hai đoạn code đếm dòng khác nhau là hai cơ hội
  lệch nhau, và lệch một dòng làm source viewer highlight sai **mọi** evidence trỏ vào file đó.

## Kiểm chứng thực tế (03/08/2026)

Upload repo `case-study/vulnshop` thật (35 file, zip 40KB) qua API trên hạ tầng thật:
snapshot `READY`, `fileCount = 35`, `totalSizeBytes = 72657`; `GET /files` trả 66 mục (31 thư mục
tự sinh + 35 file); MinIO có đúng **35 object** dưới prefix `snapshots/{id}/` giữ nguyên cấu trúc
thư mục. Đọc `AuthController.java` cho `totalLines = 53`, khớp `wc -l` của file gốc; đọc khoảng
dòng 10-14 trả về đúng nội dung năm dòng đó trên đĩa.
