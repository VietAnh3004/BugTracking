# Bộ 8 cảnh báo và cách chia nhóm

Bộ này **cố định cho mọi người tham gia** — thay đổi giữa chừng là làm hỏng khả năng so sánh. Nếu
phải đổi (ví dụ một cảnh báo biến mất sau khi chạy lại analyzer), ghi lý do vào biên bản và **loại
người tham gia trước đó khỏi phần so sánh định lượng**.

Nguồn nhãn: [`case-study/vulnshop/SEEDED.md`](../../case-study/vulnshop/SEEDED.md). Nhãn được giữ
kín với người tham gia và với chính người điều phối trong lúc quan sát.

## Bộ cảnh báo

| # | Vị trí | Cảnh báo | Nhãn kỳ vọng | Vì sao chọn |
|---|---|---|---|---|
| 1 | `UserRepository.findByUsername:35` | SpotBugs `SQL_INJECTION_SPRING_JDBC` + Semgrep | **TP-01** | **Bẫy trung tâm của đồ án**: có test đường đi bình thường XANH, và có một test xanh khác *chứng minh* lỗ hổng có thật. Ai kết luận "có test phủ + test xanh ⇒ báo nhầm" là sai |
| 2 | `ProductRepository.findAllSorted:50` | SpotBugs `SQL_INJECTION_SPRING_JDBC`, **Semgrep không báo** | **FP-01** | Ca đắt giá nhất: hai công cụ trái ngược ở cùng một dòng. Phải đọc `enum SortField` ở file khác mới quyết được — đúng loại tình huống cần bằng chứng ngoài phạm vi dòng bị cảnh báo |
| 3 | `PasswordHasher.hashPassword:40` | `WEAK_MESSAGE_DIGEST_MD5` + Semgrep | **TP-05** | MD5 dùng đúng chỗ nguy hiểm |
| 4 | `PasswordHasher.checksumForDeduplication:53` | `WEAK_MESSAGE_DIGEST_MD5` + Semgrep | **FP-02** | **Cùng luật, cùng file, cùng lớp với #3** nhưng nhãn ngược lại. Cặp này đo được chính xác điều đang hỏi: người dùng có phân biệt được theo *ngữ cảnh sử dụng* hay chỉ phản xạ theo tên luật |
| 5 | `UserService.displayNameOf:66` | `NP_NULL_ON_SOME_PATH` | **TP-16** | Ca **duy nhất** của vulnshop có liên kết `SUPPORTS` thật từ một test hỏng trỏ đúng dòng bị cảnh báo. Nếu Evidence Agent có ích ở đâu thì phải là ở đây |
| 6 | `AuditLogger.logLogout:29` | `CRLF_INJECTION_LOGS` | **FP-06** | Đã dùng tham số hoá SLF4J — biện pháp phòng chống đúng, công cụ vẫn báo. Quyết được chỉ bằng cách đọc đúng một dòng mã |
| 7 | `FileStorageService.readUserFile:41` | `PATH_TRAVERSAL_IN`, **Semgrep bỏ sót** | **TP-03** | Lỗ hổng thật mà chỉ một công cụ bắt được |
| 8 | `PasswordHasher.matches:48` | `UNSAFE_HASH_EQUALS` | **chưa gán nhãn** | Cảnh báo **phát sinh tự nhiên**, không do gieo. Nhãn có sau khi gán mù ở tuần 15. Giữ trong bộ vì thực tế lập trình viên gặp loại này nhiều hơn loại đã được thiết kế sẵn |

Cân đối: 4 nhãn TRUE POSITIVE, 3 nhãn FALSE POSITIVE, 1 chưa gán nhãn. Có chủ ý **không** cân bằng
tuyệt đối — một bộ 50/50 mà người tham gia đoán ra sẽ khiến họ tự cân đối câu trả lời.

## Chia nhóm (cân bằng thứ tự)

Hai block, mỗi block 4 cảnh báo:

- **Block A**: #1, #3, #5, #7
- **Block B**: #2, #4, #6, #8

Hai block chia đôi cả ba cặp có quan hệ với nhau (#1↔#2 cùng luật SQL injection, #3↔#4 cùng luật
MD5), nên không có block nào chứa cả hai vế của một cặp. Nếu cùng một người thấy cả hai vế trong
cùng một điều kiện, họ sẽ suy ra "chắc một cái đúng một cái sai" và trả lời theo cấu trúc bài chứ
không theo mã nguồn.

| Người tham gia | Điều kiện block A | Điều kiện block B | Thứ tự |
|---|---|---|---|
| 01, 03, 05, 07 | **Có Agent** | Đối chứng (chỉ output thô) | A trước, B sau |
| 02, 04, 06, 08 | Đối chứng | **Có Agent** | B trước, A sau |

Trong mỗi block, thứ tự 4 cảnh báo **xáo ngẫu nhiên** cho từng người và ghi lại vào biên bản.

## Điều kiện "đối chứng" nghĩa là gì

Người tham gia dùng **cùng một ứng dụng**, được phép:

- xem danh sách cảnh báo, mở Evidence Explorer (ngữ cảnh mã, test liên quan, độ phủ);
- mở source viewer, đọc mã nguồn thoải mái.

**Không** được mở màn hình hỏi đáp với Agent.

Tức là đối chứng ở đây là **"đọc bằng chứng có cấu trúc mà không có AI"**, không phải "đọc file
XML thô". So với một người ngồi đọc `spotbugs.xml` bằng tay thì đây là một đối chứng *mạnh hơn*,
nên chênh lệch đo được sẽ **nhỏ hơn** — và vì thế đáng tin hơn. Điều này phải nêu rõ trong báo cáo
thay vì để người đọc hiểu nhầm là so với "không có gì".

## Kiểm tra trước buổi thử

Chạy trên đúng lần phân tích sẽ dùng, ngay trước buổi đầu tiên:

- [ ] Cả 8 cảnh báo còn tồn tại và mở được bằng Evidence ID (đối chiếu với `expected-analysis.json`).
- [ ] Cảnh báo #5 vẫn còn liên kết `SUPPORTS` từ test hỏng — nếu mất, cả buổi thử mất ca thuyết
      phục nhất.
- [ ] `LLM_PROVIDER` trỏ tới provider thật, không phải `scripted`.
- [ ] Mỗi người tham gia có tài khoản riêng: `UNIQUE (evidence_record_id, reviewer_id)` nghĩa là
      hai người dùng chung một tài khoản sẽ **ghi đè quyết định của nhau**.
