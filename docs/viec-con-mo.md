# Việc còn mở

Các lỗi và việc đã phát hiện nhưng **chủ ý để làm sau**. Mỗi mục ghi: triệu chứng, nguyên nhân đã
xác minh, cách sửa đề xuất, và cách kiểm sau khi sửa. Làm xong thì xoá mục đó khỏi file (commit
message ghi lại lý do).

Việc còn mở riêng cho triển khai VPS nằm ở [deploy-vps.md](deploy-vps.md) mục 11.

---

## 1. Agent không thấy ngữ cảnh NGOÀI dòng bị cảnh báo (kiểu được dùng, nơi gọi hàm)

*Phát hiện 20/11/2026 — người dùng chọn GIỮ NGUYÊN (hướng C), không sửa.*

**Triệu chứng.** Hỏi Agent về EV-00CA (`ProductRepository.findAllSorted:50`, SpotBugs
`SQL_INJECTION_SPRING_JDBC`, nhãn đúng = false positive FP-01) cho `INSUFFICIENT_EVIDENCE`. Agent liệt kê
đúng thứ nó thiếu: "kiểm tra định nghĩa `SortField`" và "kiểm tra nơi gọi `findAllSorted`".

**Nguyên nhân (đã xác minh trên DB và mã nguồn).**

1. `CodeContextExtractor` chỉ lưu `CODE_CONTEXT` cho những chỗ có cảnh báo hoặc test. `SortField` (enum chỉ
   có 3 hằng số) và `ProductController.list` (khai tham số kiểu `SortField`, là chỗ Spring chặn mọi chuỗi
   lạ) không có cảnh báo hay test nên không có bản ghi nào để `get_code_context` hay `search_evidence`
   trả về.
2. Không có tool tìm nơi gọi hàm; năm tool chỉ truy xuất bằng chứng đã lưu.
3. Không phải do thiếu test: EV-00CA có liên kết `COVERS` ghi `Dòng 50: MISSED (ci=0 mi=6)` — nghĩa là
   dòng KHÔNG được chạy (xem Javadoc `EvidenceLinker.covers`, cố ý sinh liên kết cả khi dòng không được
   phủ để phân biệt "chưa ai kiểm" với "chưa nối được bằng chứng").

**Quyết định (20/11/2026): giữ nguyên.** Coi `INSUFFICIENT_EVIDENCE` ở loại ca này là hành vi đúng thiết
kế: Agent biết mình thiếu gì và nói ra thay vì đoán. Ca FP-01 vì thế được tính là "chưa trả lời được",
không phải lỗi dữ liệu cần sửa. **Phải nêu như một hạn chế trong chương 4**: các false positive mà lý do
nằm ở tầng khác với dòng bị cảnh báo (FP-01; có thể cả FP-04 — chưa kiểm. FP-05 từng nằm trong danh sách này, nay đã gán lại thành lỗi thật, xem mục 2) nằm ngoài tầm với của
Agent hiện tại. Khi test prompt, đừng đọc kết quả `INSUFFICIENT_EVIDENCE` ở các ca này là prompt kém.

**Hướng sửa đã cân nhắc, để dành nếu sau này cần (hướng A).** Mở rộng `CodeContextExtractor`: với mỗi hàm bị
cảnh báo, thêm bản ghi `CODE_CONTEXT` cho (a) kiểu được tham chiếu trong chữ ký/thân hàm và được khai
báo trong dự án, (b) các hàm gọi nó, khớp theo TÊN (không có symbol solver). Nối vào cảnh báo bằng quan hệ
và `method` mới với `weight` thấp (yếu như `NAMING_CONVENTION`), giới hạn số nơi gọi mỗi cảnh báo vì
ngân sách bằng chứng của phiên chỉ ~12000 ký tự. Cần: migration cho quan hệ/method mới, test, và cập nhật
ADR 0009. Chọn A thay vì thêm tool đọc file (B) vì nội dung Agent tự đọc không có Evidence ID để trích dẫn,
mà `CitationValidator` chỉ chấp nhận ID có trong `evidence_record`.

**Cách kiểm sau khi sửa (nếu làm A).** Hỏi lại EV-00CA trên run VulShop: `get_code_context` phải trả thêm
mã của `SortField` và `ProductController#list`, và Agent không còn xin "kiểm tra định nghĩa `SortField`".

---

## 2. Javadoc trong mã vulnshop còn ghi nhãn cũ của FP-05 và FP-06

**Triệu chứng.** Ngày 23/11/2026, FP-05 (`TextNormalizer.normalizeStatusCode:24`) và FP-06
(`AuditLogger.logLogout:29`) được gán lại thành lỗi thật trong `case-study/vulnshop/SEEDED.md` (mục 3)
và `case-study/ground-truth/vulnshop-seeded.yaml`. Nhưng chú thích trong chính mã vulnshop vẫn nói ngược lại:

- `TextNormalizer.java` dòng 11–18: "SEEDED — false positive", "đầu vào là mã trạng thái nội bộ… Kết quả
  không đổi dù locale nào". Sai: với locale `tr`, `"SHIPPED".toLowerCase()` ra `"shıpped"`.
- `AuditLogger.java` dòng 27: "Ghi nhật ký đúng cách: dùng tham số hoá của SLF4J." Sai: `{}` không lọc
  `\r`/`\n`.
- `TextNormalizerTest.java` dòng 10: chú thích gọi test xanh là "bằng chứng MÂU THUẪN" với cảnh báo. Test
  xanh chỉ vì máy chạy test không dùng locale `tr`.

Người đọc mã (hội đồng, người gán nhãn thứ hai nếu được xem mã) sẽ thấy tài liệu và mã mâu thuẫn nhau.

**Vì sao để sau.** Snapshot vulnshop đang dùng cho thực nghiệm (run `ab3e8b0b`) đã tải lên với chú thích cũ.
Sửa mã nghĩa là snapshot mới khác snapshot cũ, cần quyết định có phân tích lại hay không.

**Cách sửa.**
1. Viết lại ba chú thích trên theo lý do ở SEEDED.md mục 3. **Giữ nguyên số dòng** (chỉ thay nội dung trong
   khối chú thích, không thêm/bớt dòng) để mọi `file:line` trong SEEDED.md, `vulnshop-seeded.yaml`,
   `expected-analysis.json` và nhãn đã gán trong DB vẫn khớp.
2. Không đổi mã thực thi: hai lỗi này giờ là lỗi thật có chủ đích, phải giữ lại.

**Cách kiểm sau khi sửa.**
- `git diff --stat` chỉ đổi số dòng sửa, không có dòng thêm/bớt ròng trong ba file.
- `./case-study/run-analysis.sh vulnshop --check` vẫn khớp `expected-analysis.json` (cùng số cảnh báo, cùng dòng).
- Nếu phân tích lại: `DM_CONVERT_CASE` vẫn ở `TextNormalizer.java:24`, `CRLF_INJECTION_LOGS` vẫn ở
  `AuditLogger.java:29`.
