# Danh mục lỗi gieo có chủ đích trong vulnshop

**Ngày lập**: 27/07/2026 · **Người lập**: Nguyễn Việt Anh

Đây là **ý đồ thiết kế**, ghi lại lúc *viết mã nguồn* — trước khi có bất kỳ output nào của AI
Agent. Nó không phải nhãn ground truth cuối cùng, xem mục "Quan hệ với ground truth" ở cuối.

---

## 1. Phương pháp và giới hạn cần khai báo trong báo cáo

vulnshop là case study **gieo lỗi có chủ đích** (fault seeding). Ưu điểm: biết trước đáp án nên
không phụ thuộc vào phán đoán chủ quan lúc gán nhãn. Nhược điểm phải nói thẳng: lỗi gieo có xu
hướng "sạch" và dễ nhận ra hơn lỗi phát sinh tự nhiên trong dự án thật.

Vì vậy báo cáo **bắt buộc tách riêng hai nhóm** khi trình bày kết quả:

- **Cảnh báo seeded** — nằm trong bảng ở mục 2 và 3 dưới đây.
- **Cảnh báo tự nhiên** — công cụ tìm ra nhưng không nằm trong ý đồ ban đầu (mục 4). Nhóm này
  gần với thực tế hơn và là phép thử nghiêm khắc hơn cho hệ thống.

Báo một con số độ chính xác gộp chung sẽ bị hỏi ngay là "cao như vậy có phải vì lỗi cài quá lộ
liễu không" — và câu hỏi đó hoàn toàn chính đáng.

---

## 2. Lỗi thật đã gieo (kỳ vọng nhãn: TRUE POSITIVE)

| # | Vị trí | Loại lỗi | Công cụ bắt được (thực tế) |
|---|---|---|---|
| TP-01 | `UserRepository.findByUsername:35` | SQL injection qua nối chuỗi | SpotBugs `SQL_INJECTION_SPRING_JDBC` + Semgrep `spring-sqli` |
| TP-02 | `ProductRepository.searchByKeyword:31` | SQL injection trong LIKE | SpotBugs + Semgrep |
| TP-03 | `FileStorageService.readUserFile:41` | Path traversal | SpotBugs `PATH_TRAVERSAL_IN` — **Semgrep bỏ sót** |
| TP-04 | `FileStorageService.readFirstByte:72` | Rò rỉ tài nguyên (stream không đóng) | SpotBugs `OBL_UNSATISFIED_OBLIGATION` + `OS_OPEN_STREAM` |
| TP-05 | `PasswordHasher.hashPassword:40` | MD5 để băm mật khẩu | SpotBugs `WEAK_MESSAGE_DIGEST_MD5` + Semgrep `use-of-md5` |
| TP-06 | `Product.equals:45` | equals() có, hashCode() không | SpotBugs `HE_EQUALS_USE_HASHCODE` |
| TP-07 | `OrderStatusChecker.isShipped:18` | So sánh chuỗi bằng `==` | SpotBugs `ES_COMPARING_PARAMETER_STRING_WITH_EQ` |
| TP-08 | `SessionRegistry.ACTIVE_SESSIONS:24` | HashMap tĩnh, không đồng bộ | SpotBugs `MS_MUTABLE_COLLECTION_PKGPROTECT` |
| TP-09 | `AuthController.loginRedirect:49` | Chuyển hướng không kiểm tra đích | Semgrep `unvalidated-redirect` — **SpotBugs bỏ sót** |
| TP-10 | `AuditLogger.logLogin:24` | Chèn dữ liệu chưa lọc vào nhật ký | SpotBugs `CRLF_INJECTION_LOGS` |
| TP-11 | `UserService.emailDomainOf:30` | NPE khi không tìm thấy người dùng | **KHÔNG công cụ nào bắt** — chỉ test hỏng phát hiện |
| TP-12 | `PaginationHelper.totalPages:21` | Sai một đơn vị | **KHÔNG công cụ nào bắt** — chỉ test hỏng phát hiện |
| TP-13 | `CartService.averagePrice:25` | Chia cho 0 khi giỏ rỗng | **KHÔNG công cụ nào bắt** — chỉ test hỏng phát hiện |
| TP-14 | `SecurityConstants.JWT_SIGNING_KEY:12` | Khoá bí mật nhúng trong mã | **KHÔNG công cụ nào bắt** (ngoài dự kiến) |
| TP-15 | `SecurityConstants.DEFAULT_ADMIN_PASSWORD:19` | Mật khẩu mặc định trong mã | **KHÔNG công cụ nào bắt** (ngoài dự kiến) |
| TP-16 | `UserService.displayNameOf:66` | NPE — cùng lỗi TP-11 nhưng kiểm null nằm trong cùng method | SpotBugs `NP_NULL_ON_SOME_PATH` **và** test hỏng trỏ đúng dòng |

**Ba lỗi TP-11 → TP-13 là hạt nhân của đề tài.** Phân tích tĩnh im lặng hoàn toàn, chỉ có test
hỏng kèm stack trace mới chứng minh được. Đây là bằng chứng thực nghiệm trực tiếp cho luận điểm
"một nguồn bằng chứng là không đủ" — và nó tái hiện đúng phát hiện của spike tuần 1.

**TP-16 gieo ở tuần 10-11, và nó là ca ĐỐI CHỨNG của TP-11.** Cùng một lỗi NullPointerException,
khác đúng một điểm: phép kiểm null nằm **trong cùng method** với chỗ giải tham chiếu, nên SpotBugs
nhìn thấy nhánh mà giá trị chắc chắn null vẫn đi tới lệnh gọi. Ở TP-11 việc trả về null nằm trong
một method riêng (`findFirst`) và công cụ im lặng.

Lý do gieo thêm nó là một **khiếm khuyết đo được của case study**, phát hiện khi hiện thực
EvidenceLinker: trước TP-16, vulnshop **không có bất kỳ trường hợp nào** mà một cảnh báo tĩnh và
một stack frame của test hỏng cùng trỏ về đúng một dòng. Hai frame production duy nhất của cả bộ
test (`CartService:25`, `UserService:30`) đều nằm ở TP-11/TP-13 — đúng nhóm không công cụ nào bắt.
Hệ quả: quan hệ `SUPPORTS` suy từ stack frame, **loại bằng chứng thuyết phục nhất** cho verdict
`LIKELY_TRUE_POSITIVE`, không có mẫu nào để demo ở tuần 12-13 hay để đo ở tuần 15.

Cặp TP-11 ↔ TP-16 còn có giá trị riêng cho báo cáo: hai lỗi **giống hệt nhau về bản chất**, chỉ
khác cách viết, mà một cái bị bắt còn một cái không. Đó là minh hoạ gọn nhất cho việc phạm vi của
phân tích tĩnh phụ thuộc vào hình dạng mã chứ không phụ thuộc mức nghiêm trọng của lỗi.

**TP-14, TP-15 nằm ngoài dự kiến**: cả SpotBugs (kể cả find-sec-bugs) lẫn Semgrep `p/java` đều
không báo hardcoded secret. Đây là **false negative có thật**, cần ghi vào phần hạn chế của công
cụ trong báo cáo chứ không được lặng lẽ bỏ qua.

---

## 3. Dương tính giả đã gieo (kỳ vọng nhãn: FALSE POSITIVE)

| # | Vị trí | Cảnh báo sinh ra | Vì sao không phải lỗi |
|---|---|---|---|
| FP-01 | `ProductRepository.findAllSorted:50` | SpotBugs `SQL_INJECTION_SPRING_JDBC` | Giá trị nối vào ORDER BY đến từ `enum SortField`, người dùng không truyền chuỗi tuỳ ý được. ORDER BY không dùng được tham số ràng buộc nên whitelist bằng enum là cách xử lý đúng. **Semgrep không báo chỗ này** |
| FP-02 | `PasswordHasher.checksumForDeduplication:53` | SpotBugs `WEAK_MESSAGE_DIGEST_MD5` + Semgrep `use-of-md5` | MD5 dùng để phát hiện tệp trùng, không đóng vai trò bảo mật. Tạo được va chạm cũng không thu lợi gì |
| FP-03 | `ColorPicker.RANDOM:21` | SpotBugs `PREDICTABLE_RANDOM` | Giá trị chỉ dùng chọn màu nền avatar. Đoán trước được màu avatar không mang lại lợi thế nào |
| FP-04 | `UserService.countByRole:42` | SpotBugs `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` | Kiểm tra null thừa vì repository luôn trả về danh sách. Rườm rà chứ không gây hại |
| FP-05 | `TextNormalizer.normalizeStatusCode:24` | SpotBugs `DM_CONVERT_CASE` | Đầu vào là mã trạng thái ASCII nội bộ, không đến từ người dùng. Kết quả không đổi dù locale nào |
| FP-06 | `AuditLogger.logLogout:29` | SpotBugs `CRLF_INJECTION_LOGS` | Đã dùng tham số hoá SLF4J (`log.info("...{}", username)`) — cách phòng chống đúng. Công cụ vẫn báo |

**FP-01 là trường hợp đắt giá nhất**: hai công cụ cho kết luận trái ngược nhau ở cùng một dòng.
Muốn phán đoán đúng phải đọc định nghĩa `SortField` và chữ ký hàm ở `ProductController` — tức là
cần **ngữ cảnh mã nguồn ngoài phạm vi dòng bị cảnh báo**. Đây chính xác là loại tình huống mà đề
tài lập luận rằng AI cần bằng chứng có cấu trúc chứ không chỉ nội dung cảnh báo.

**Ghi chú về path traversal**: dự kiến ban đầu `readUserFileSafely` cũng sẽ bị báo nhầm (đã
`normalize()` + kiểm tra `startsWith`). Thực tế **không công cụ nào báo** — cả hai đều nhận ra
biện pháp phòng vệ. Đây là điểm tốt cho công cụ, và cần ghi nhận trung thực thay vì cố ép thành
false positive.

---

## 4. Cảnh báo phát sinh tự nhiên (không nằm trong ý đồ)

Những cảnh báo dưới đây do công cụ tìm ra mà không phải do gieo. **Chưa gán nhãn** — sẽ gán độc
lập ở tuần 15 bằng cách đọc mã nguồn.

| Cảnh báo | Số lượng | Nhận xét sơ bộ |
|---|---|---|
| `EI_EXPOSE_REP2` | 5 | Lưu tham chiếu tới đối tượng truyền vào constructor. Với bean Spring bất biến thì gần như chắc chắn là dương tính giả |
| `SPRING_ENDPOINT` | 4 | Chỉ đánh dấu "đây là endpoint HTTP", mang tính thông tin |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 1 | `FileStorageService.store:83` — `getParent()` có thể trả về null. **Có vẻ là lỗi thật mà không hề cố ý gieo** |
| `UNSAFE_HASH_EQUALS` | 1 | `PasswordHasher.matches:48` — so sánh mã băm không dùng thuật toán chống tấn công thời gian |
| `RV_ABSOLUTE_VALUE_OF_HASHCODE` | 1 | `ColorPicker.colorFor:34` — `Math.abs(Integer.MIN_VALUE)` vẫn âm. Lỗi thật, không cố ý |

Ba dòng cuối đáng chú ý: chúng là **lỗi thật do công cụ tự tìm ra**, không phải do gieo. Chính
những trường hợp này làm cho thực nghiệm gần với thực tế hơn.

---

## 5. Thiết kế độ phủ kiểm thử

Ba trạng thái bằng chứng đều phải xuất hiện được, nếu không hệ thống sẽ không bao giờ demo được
đủ ba verdict:

| Trạng thái | Ví dụ | Dùng để |
|---|---|---|
| Có cảnh báo + test hỏng trỏ đúng dòng | `UserService.displayNameOf:66` (TP-16) | Bằng chứng `SUPPORTS` → `LIKELY_TRUE_POSITIVE` |
| **Không** cảnh báo + test hỏng trỏ đúng dòng | `UserService:30`, `PaginationHelper:21`, `CartService:25` | Chỉ test bắt được — không có cảnh báo nào để `SUPPORTS` |
| Có cảnh báo + test phủ và pass | `PasswordHasher:53`, `TextNormalizer:24` | Bằng chứng có thể `CONTRADICTS` |
| Có cảnh báo + **không test nào phủ** | `SessionRegistry:24`, `ColorPicker:21`, `AuditLogger:24`, `AuthController:49` | → `INSUFFICIENT_EVIDENCE` |

**Bẫy cố ý đặt ra cho AI Agent** — hai trường hợp mà "test xanh" *không* có nghĩa là an toàn:

1. `UserRepositoryIntegrationTest.findByUsername_dauVaoBinhThuong` — test **xanh** trên hàm
   **có lỗ hổng SQL injection**. Đường đi bình thường chạy đúng nên test xanh, nhưng lỗ hổng vẫn
   nguyên đó.
2. `UserRepositoryIntegrationTest.findByUsername_sqlInjection_traVeToanBoBang` — test **xanh** và
   nội dung của nó **chứng minh lỗ hổng có thật** (truyền `' OR '1'='1` lấy được toàn bộ bảng).

Agent nào kết luận "có test phủ và test xanh nên cảnh báo là dương tính giả" sẽ sai ở cả hai chỗ.
Phân biệt được "test xanh" với "test xanh chứng minh điều gì" là một trong những phép thử tốt
nhất cho hệ thống.

`OrderStatusCheckerTest.isShipped_chuoiHang_traVeDung` là biến thể thứ ba: test xanh vì JVM gộp
chuỗi hằng, nhưng hàm vẫn sai — và test kế bên với chuỗi tạo lúc chạy thì hỏng.

### Phát hiện ảnh hưởng tới thiết kế EvidenceLinker

Chạy thật cho thấy chỉ **3 trong 6** test hỏng sinh ra stack trace trỏ về mã production:

| Kiểu hỏng | Test | Stack trace trỏ về đâu |
|---|---|---|
| Ngoại lệ ném từ mã production | `CartServiceTest.averagePrice_gioRong`, `UserServiceTest.emailDomainOf_khongTonTai`, `UserServiceTest.displayNameOf_khongTonTai` | **Mã production** — `CartService:25`, `UserService:30`, `UserService:66` |
| Assertion thất bại | 3 test còn lại | **Chỉ mã test** — không có frame nào của mã production |

Lý do: `AssertionFailedError` được ném từ chính lớp test, nên chuỗi gọi không đi qua mã production
dù hàm đang kiểm thử có lỗi.

Hệ quả cho tuần 3: **liên kết theo stack trace chỉ dùng được cho test hỏng kiểu ngoại lệ.** Với
test hỏng kiểu assertion, phải liên kết bằng cách khác — theo độ phủ (dòng cảnh báo có nằm trong
vùng test đó chạy qua không) hoặc theo quy ước tên (`FooTest` ↔ `Foo`). Nếu chỉ hiện thực liên
kết theo stack trace thì sẽ mất phần lớn bằng chứng test hỏng mà không hề hay biết.

**Bổ sung ở tuần 10-11 — có frame production là CHƯA ĐỦ.** Khi hiện thực EvidenceLinker tầng 3 mới
lộ ra một điều kiện thứ hai mà bảng trên không nói: frame đó còn phải rơi vào **một dòng đã có
cảnh báo**. Ở thời điểm tuần 8-9, cả hai frame production của vulnshop (`CartService:25`,
`UserService:30`) đều thuộc TP-11/TP-13 — đúng nhóm **không công cụ nào bắt** — nên tầng 3 chạy
đúng mà sinh **0 liên kết**.

Nói cách khác, tỉ lệ dùng được của liên kết theo stack trace không phải 2/5 mà là **0/5**, cho tới
khi TP-16 được gieo. Đây là lý do bảng ở mục 5 trước đây khai `UserService:30` là ca `SUPPORTS`
trong khi mục 2 khai chính nó là "không công cụ nào bắt" — hai câu không thể cùng đúng, và mâu
thuẫn đó chỉ lộ ra khi có mã thật đi đối chiếu.

---

## 6. Quan hệ với ground truth

| | `SEEDED.md` (file này) | `ground-truth/labels.yaml` (tuần 15) |
|---|---|---|
| Lập khi nào | Lúc viết mã, tuần 2 | Tuần 15 |
| Nguồn nhãn | Ý đồ thiết kế | Đọc mã nguồn |
| Phạm vi | Chỉ cảnh báo gieo có chủ đích | Toàn bộ, gồm cả cảnh báo tự nhiên |
| Có nhìn output agent không | **Không** — file này có trước khi agent tồn tại | **Không** |

Nhãn ở đây không đến từ output của AI Agent, nên không có vấn đề circularity. Nhưng cần thêm một
bước để chắc chắn: nhờ người khác gán nhãn **mù** một tập con (~30 cảnh báo, không cho xem file
này), rồi đối chiếu. Việc đó vừa cho ra chỉ số đồng thuận giữa người gán nhãn, vừa đo được lỗi
gieo có thực tế hay không.

---

## 7. Cách tái lập

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"   # bắt buộc JDK 21, xem ADR 0003

mvn test                  # sinh Surefire XML + jacoco.xml; một số test hỏng là có chủ đích
mvn spotbugs:spotbugs     # sinh spotbugsXml.xml

docker run --rm --network=none -v "/duong/dan/case-study:/work" semgrep/semgrep \
  semgrep scan --config=/work/semgrep-rules/p-java.yaml \
    --sarif --output=/work/vulnshop/target/semgrep.sarif --metrics=off /work/vulnshop/src
# 5 cảnh báo
```

Hoặc chạy cả bốn công cụ bằng một lệnh, kèm đối chiếu số liệu:

```bash
./case-study/run-analysis.sh vulnshop --check
```

Artifact đã chạy được lưu tại [`docs/samples/vulnshop/`](../../docs/samples/vulnshop/).

Con số kỳ vọng nằm trong [`expected-analysis.json`](expected-analysis.json) — nguồn sự thật duy
nhất, CI đối chiếu tự động. Lệch khỏi đó nghĩa là môi trường đã đổi; `summarize.py` sẽ gợi ý
nguyên nhân thường gặp.
