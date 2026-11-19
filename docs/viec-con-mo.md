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
