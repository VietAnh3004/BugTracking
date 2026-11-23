# Việc còn mở

Các lỗi và việc đã phát hiện nhưng **chủ ý để làm sau**. Mỗi mục ghi: triệu chứng, nguyên nhân đã
xác minh, cách sửa đề xuất, và cách kiểm sau khi sửa. Làm xong thì xoá mục đó khỏi file (commit
message ghi lại lý do).

Việc còn mở riêng cho triển khai VPS nằm ở [deploy-vps.md](deploy-vps.md) mục 11.

---

## 1. Nhúng vector (embedding) qua Gemini free hỏng toàn bộ khi run có > 100 bằng chứng

*Phát hiện 19/11/2026 — chưa sửa, người dùng chọn để sau.*

**Triệu chứng.** Màn hình cảnh báo báo:

> Tìm theo nghĩa chưa chạy cho lần phân tích này — Agent chỉ tìm được bằng so khớp chữ. Lý do:
> agent-service tra ve 502: {"detail":"Gemini embedding tra ve HTTP 429: RESOURCE_EXHAUSTED ...
> Quota exceeded for metric: generativelanguage.googleapis.com/embed_content_free_tier_requests,
> limit: 100, model: gemini-embedding-1.0 ... Please retry in 6.44s."}

Bấm "Sinh lại" bao nhiêu lần cũng hỏng y hệt.

**Nguyên nhân (đã xác minh trên DB và mã nguồn).**

1. Bản free của model embedding giới hạn **100 yêu cầu/phút**, và Google đếm **từng đoạn văn bản**
   là một yêu cầu — kể cả khi gom nhiều đoạn vào một lần gọi `batchEmbedContents`.
2. Run của vulnshop có **124 bằng chứng**. `agent-service/src/agent_service/embeddings/gemini.py`
   chia lô `BATCH_SIZE = 100` rồi gửi các lô **liền nhau**: lô 1 (100 đoạn) dùng hết hạn mức của
   phút đó, lô 2 (24 đoạn) gửi ngay nên nhận 429.
3. Gặp 429 là **bỏ cuộc ngay**, không chờ dù Google đã nói rõ số giây cần chờ.
4. Nhúng theo kiểu **được ăn cả, ngã về không**: lô 2 hỏng thì 100 vector của lô 1 cũng không được
   lưu. DB ghi **0/124** cho cả bốn run hiện có (`evidence_embedding` rỗng).

Hệ quả: với bản free, mọi dự án có hơn 100 bằng chứng **không bao giờ** nhúng được.

**Cách sửa đề xuất (đã chốt hướng: phương án A).** Trong `gemini.py`, gặp 429 thì đọc thời gian chờ
Google trả về (`RetryInfo.retryDelay` trong `error.details`, hoặc câu "Please retry in Ns"), chờ
đúng chừng đó rồi gửi lại **chính lô đó** — có giới hạn số lần thử và tổng thời gian chờ, hết giới
hạn thì vẫn báo lỗi rõ như bây giờ. 124 bằng chứng sẽ mất khoảng một phút.

Hai phương án còn lại, chỉ ghi để khỏi bàn lại:

- **B. Bật billing** cho project Google Cloud: giới hạn cao hơn hẳn, embedding rất rẻ. Không thay
  được A — bản trả phí cũng có giới hạn, chạm một lần là cả lượt vẫn hỏng.
- **C. `EMBEDDING_PROVIDER=none`**: hết báo lỗi nhưng mất tìm kiếm theo nghĩa, Agent chỉ so khớp chữ.

**Lưu ý khi sửa.**

- Test phải giả lập 429 (mock HTTP), **không** gọi Gemini thật — mỗi lần thử ăn hạn mức của người
  dùng, và embedding dùng chung project Google Cloud với model chat.
- Chờ trong `agent-service` làm request từ core-backend kéo dài: kiểm lại timeout của `AgentClient`
  phía core-backend cho đường gọi embedding, nếu không core-backend tự cắt trước khi lô 2 kịp gửi.
- Cân nhắc lưu từng lô thành công thay vì được ăn cả ngã về không — nhưng khi đó phải có cách biết
  run nào nhúng THIẾU, nếu không đó lại là một kiểu hỏng im lặng.

**Kiểm sau khi sửa.** Với run vulnshop (124 bằng chứng) trên khoá free: bấm "Sinh lại", chờ khoảng
một phút, cảnh báo trên màn hình biến mất và truy vấn sau ra 124:

```sql
SELECT count(*) FROM evidence_embedding v
JOIN evidence_record e ON e.id = v.evidence_record_id
WHERE e.analysis_run_id = '<run id>';
```

---

## 2. Agent không thấy ngữ cảnh NGOÀI dòng bị cảnh báo (kiểu được dùng, nơi gọi hàm)

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
nằm ở tầng khác với dòng bị cảnh báo (FP-01; có thể cả FP-04 — chưa kiểm. FP-05 từng nằm trong danh sách này, nay đã gán lại thành lỗi thật, xem mục 3) nằm ngoài tầm với của
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

## 3. Javadoc trong mã vulnshop còn ghi nhãn cũ của FP-05 và FP-06

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
