# Phiên bản prompt của Agent

`llm_trace.prompt_version` ghi phiên bản prompt đã sinh ra từng lượt gọi model, lấy từ biến môi
trường `PROMPT_VERSION` của agent-service (mặc định trong `config.py` và docker-compose). Đổi nội
dung prompt mà không tăng số này thì kết quả thực nghiệm của hai prompt khác nhau trộn vào nhau mà
không phân biệt được — vì vậy **mọi thay đổi `system.jinja2`, `_SUBMIT_NOW`, `_BUDGET_EXHAUSTED` đều
phải tăng phiên bản và ghi vào đây**.

Văn bản đầy đủ của từng phiên bản nằm trong lịch sử git (`git show <commit>:agent-service/src/agent_service/prompts/system.jinja2`).

| Phiên bản | Văn bản prompt | Dùng từ | Ghi chú |
|---|---|---|---|
| v1 | commit `c0138ff` (07/08/2026) | đầu dự án tới 19/11/2026 | Bản gốc. Từ 19/11/2026 chạy cùng tool `get_code_context` đã sửa (có `snippet`, ADR 0005) |
| v2 | commit chứa file này | 19/11/2026 | Xem bên dưới |

Số liệu v1 còn chia hai giai đoạn theo `llm_trace.created_at`: **trước** và **sau** khi
`get_code_context` trả mã thật (19/11/2026). Giai đoạn trước agent trả lời khi không thấy mã.

## v2 — thay đổi

Quan sát (19/11/2026) trên cảnh báo `WEAK_MESSAGE_DIGEST_MD5` ở một hàm băm nội dung tệp (nhãn ground
truth: false positive, vì thuật toán yếu không dùng cho mục đích bảo mật): sau khi `get_code_context`
đã trả mã, cả Claude Sonnet 5 và Gemini 3.5 flash-lite đều nhận ra mục đích sử dụng nhưng vẫn kết
luận `LIKELY_TRUE_POSITIVE`. Hai nguyên nhân, đã xác minh qua `llm_trace`:

1. **Định nghĩa kết luận thiếu ca "công cụ phát hiện đúng nhưng ngữ cảnh vô hại".** v1 định nghĩa
   FP là "công cụ báo nhầm" và chỉ liệt kê lý do về luồng mã (đầu vào đã làm sạch, nhánh không tới
   được, đã kiểm null). Model đọc "mẫu mã có thật ⇒ không phải báo nhầm ⇒ lỗi thật". Mức nghiêm
   trọng HIGH của công cụ còn đóng vai neo.
2. **Bước nộp câu trả lời có cấu trúc làm rơi nhận định đi ngược kết luận.** Gemini ghi nhận mục
   đích dedup trong phân tích tự do rồi bỏ nó khi nộp, nên người dùng không thấy.

Thay đổi:

- Định nghĩa lại `LIKELY_TRUE_POSITIVE` / `LIKELY_FALSE_POSITIVE` theo câu hỏi "rủi ro mà quy tắc
  nhắm tới có xảy ra ở đây không"; thêm mục "Công cụ phát hiện đúng chưa phải lỗi thật".
- Mức nghiêm trọng của công cụ không phải bằng chứng.
- Ngữ cảnh vô hại phải chứng minh bằng thân hàm (không phải tên hàm, không phải test xanh); điều
  chưa tra được (không có tool tìm nơi gọi) thì nói rõ, không coi là lỗi thật cũng không khẳng định
  vô hại.
- Câu trả lời phải giữ mọi điểm phân tích kể cả điểm đi ngược kết luận, và `verdict` phải khớp với
  các nhận định (lặp lại ở `_SUBMIT_NOW` và `_BUDGET_EXHAUSTED`).

**Hạn chế phải nêu trong chương 4.** v2 được viết SAU khi quan sát ca MD5 trên vulnshop — chính tập
dùng để đo. Prompt cố ý viết tổng quát và có test chặn việc nhắc tên ca cụ thể
(`test_v2_prompt_khong_nhac_ten_ca_cu_the_cua_case_study`), nhưng vẫn có nguy cơ thiên lệch: số liệu
v2 trên vulnshop không nên coi là đo độc lập. Cần đo thêm trên một tập chưa dùng để chỉnh prompt,
hoặc trình bày vulnshop như ví dụ minh hoạ và v1 → v2 như một vòng cải tiến có quan sát.

### Kết quả đo sơ bộ v1 → v2 (19/11/2026, Gemini 3.5 flash-lite, gọi thẳng agent-service)

Cảnh báo EV-0052 (MD5 băm nội dung tệp, nhãn đúng = false positive), cùng run, cùng model:

| Phiên bản | Số lần chạy | `LIKELY_FALSE_POSITIVE` | `LIKELY_TRUE_POSITIVE` |
|---|---|---|---|
| v1 | 5 | 0 | 5 |
| v2 | 6 | 2 | 4 |

- **Cỡ mẫu quá nhỏ để kết luận** (chênh lệch này chưa có ý nghĩa thống kê). Chỉ nên đọc là "v2 có xu
  hướng tốt hơn", không phải "v2 sửa được". Cần đo lặp nhiều lần hơn và trên nhiều cảnh báo.
- **Điểm cải thiện rõ nhất là bước nộp:** ở v2 phần lớn lần chạy giữ lại nhận định "MD5 này chỉ dùng
  để khử trùng lặp tệp, không phải mật khẩu" (v1 bỏ nó). Nhưng model nhỏ vẫn có lúc nêu nhận định đó
  rồi để `verdict` là TP (chưa tuân thủ yêu cầu "verdict khớp các nhận định").
- **Đối chứng "lỗi thật" không bị nới lỏng:** MD5 băm mật khẩu (EV-006B, 2 lần), SQL injection
  `findByUsername` (EV-015C) đều vẫn `LIKELY_TRUE_POSITIVE`.
- **Một lần chạy bị nhiễm và đã loại:** bản nháp đầu của v2 có ví dụ về bộ sinh số ngẫu nhiên xáo thứ
  tự hiển thị, quá giống ca `ColorPicker` (EV-01B0) của vulnshop. Ví dụ đã đổi sang lĩnh vực khác
  (quyền tệp tạm) và các số liệu ở bảng trên đều đo SAU khi đổi.
- **Chưa đo:** Claude Sonnet 5 trên v2 (tốn tiền thật), và các cảnh báo khác ngoài 4 ca đã thử.
