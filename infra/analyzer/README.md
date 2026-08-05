# Image analyzer cho Docker sandbox

Hai image chạy analyzer trên mã nguồn người dùng upload. Quyết định kiến trúc đầy đủ ở
[ADR 0008](../../docs/adr/0008-chay-analyzer-trong-docker-sandbox.md); spike chứng minh dùng được
SpotBugs CLI ở [docs/spike-spotbugs-cli.md](../../docs/spike-spotbugs-cli.md).

```bash
./infra/analyzer/build.sh              # dựng + đối chiếu với expected-analysis.json
./infra/analyzer/build.sh --no-check   # chỉ dựng
```

| Image | Nội dung |
|---|---|
| `evidence/analyzer-java` | JDK 21 + Maven + SpotBugs CLI 4.9.3 + find-sec-bugs 1.13.0 + canary + seed `.m2` |
| `evidence/analyzer-semgrep` | Semgrep 1.170.1 + ruleset đã vendor (60 rule Java) |

## Hai pha, hai mức đặc quyền

```
analyze.sh resolve <sourceRoot>   CÓ mạng     — chỉ phân giải phụ thuộc, không chạy test
analyze.sh analyze <sourceRoot>   KHÔNG mạng  — chạy test + SpotBugs; đây là pha thực thi mã người lạ
```

Ranh giới: **pha nguy hiểm là pha không có mạng.** Xem ADR 0008 mục 4bis.

## Kết quả đối chiếu trên vulnshop

```
OK  spotbugs  28 canh bao nguoi dung, 5 loai canary, 21 class, JDK 21.0.11
OK  semgrep   5 canh bao, 60 rule da nap
OK  surefire  33 test (5 hong) trong 9 bao cao
OK  jacoco    82/171 dong duoc phu
```

Trùng khít với `run-analysis.sh` chạy trên host — tiêu chí nghiệm thu của giai đoạn D.

## Năm cạm bẫy đã vấp khi dựng, đừng vấp lại

Cả năm đều thuộc loại **hỏng-im-lặng hoặc báo lỗi ở chỗ chẳng liên quan**.

### 1. `docker cp <thư mục>` gán quyền ROOT, container non-root không ghi được

Triệu chứng: Maven chết với `Cannot create resource output directory: /work/src/target/classes` —
một lỗi về trình biên dịch, **không hề nhắc tới quyền**.

Cách đúng: tự dựng tar với uid/gid của user trong image rồi đưa qua stdin (`docker cp -` giữ
nguyên uid/gid trong tar):

```bash
tar --owner=10001 --group=10001 --numeric-owner -cf - -C "$WORK" src | docker cp - "$name:/work"
```

Đích phải là thư mục **đã tồn tại** (`/work`), nên tar mang sẵn tiền tố `src/`.

### 2. `dependency:go-offline` KHÔNG kéo provider của Surefire

Surefire phân giải `surefire-junit-platform` **lúc chạy**, sau khi dò được khung test đang dùng —
nên nó không nằm trong đồ thị phụ thuộc mà `go-offline` biết. Và nó còn kéo tiếp
`junit-platform-launcher`, một mắt xích nữa.

Hậu quả nếu bỏ qua: pha `analyze` offline chết với `Cannot access central ... in offline mode`,
**mất toàn bộ test + độ phủ**, trong khi SpotBugs vẫn chạy xong ngon lành và script vẫn exit 0.

Đã thử `mvn test -Dtest=<lớp không tồn tại>` để ép Surefire tự phân giải — **không ăn**, nó thoát
trước khi tới bước đó. Cách dùng được: đọc phiên bản từ `help:effective-pom` rồi `dependency:get`
thẳng, và lấy phiên bản `junit-platform` từ `dependency:list` chứ không suy từ phiên bản jupiter
(5.12.2 → 1.12.2 là một quy ước có thể đổi).

### 3. `-fn` của Maven nuốt luôn mọi lỗi khác

`-fn` (fail-never) là **bắt buộc** vì 5 test hỏng của vulnshop là dữ liệu đầu vào của đồ án, không
phải lỗi build. Nhưng nó cũng làm cho lỗi hạ tầng (cạm bẫy #2) không dừng pipeline.

Vì vậy `analyze.sh` kiểm **sau khi chạy xong**, và những kiểm tra đó là bắt buộc chứ không phải
cho chắc: có `target/classes`, có ≥1 `TEST-*.xml`, có `jacoco.xml`. Thiếu bất kỳ cái nào ⇒ dừng.

### 4. Hai image ghi vào cùng thư mục với hai uid khác nhau

`analyzer-java` chạy uid 10001; image Semgrep chính chủ chạy user `semgrep` của nó. Triệu chứng:
Semgrep quét xong xuôi, in đủ `Ran 60 rules on 20 files: 5 findings`, rồi chết ở bước **ghi** với
`PermissionError` — kết quả phân tích đúng nhưng không ai đọc được.

Đã sửa bằng `chmod 0777` cho `/work/.analysis`. Cố ý **không** ép hai image dùng chung uid: đó là
một ràng buộc ngầm giữa hai Dockerfile, sớm muộn có người đổi một bên.

### 5. Git Bash có hai cái bẫy đường dẫn NGƯỢC CHIỀU nhau

- Đường dẫn **trong container** (`/work`, `name:/work/src`) bị MSYS biến thành
  `C:/Program Files/Git/work` ⇒ cần `MSYS_NO_PATHCONV=1`.
- Đường dẫn **trên host** (build context, nguồn của `docker cp`) ở dạng `/d/study/...` thì daemon
  Windows không hiểu ⇒ cần `cygpath -m`.

Chỉ tắt path conversion là hỏng cái còn lại: `unable to prepare context: path "/d/study/..." not
found`. `build.sh` bật `MSYS_NO_PATHCONV` cho mọi lệnh docker **và** chuyển tay mọi đường dẫn host
qua `hostpath()`.

## Canary

`java/canary/Canary.java` mang ba lỗi biết trước, phủ **cả hai** bộ rule:

| Lỗi | Bộ rule | Chứng minh gì |
|---|---|---|
| `WEAK_MESSAGE_DIGEST_MD5` | find-sec-bugs | Plugin bảo mật đã nạp |
| `SQL_INJECTION_JDBC` | find-sec-bugs | — |
| `DM_DEFAULT_ENCODING` | SpotBugs core | Engine gốc còn sống |

Đưa vào **cùng một lời gọi** SpotBugs với mã người dùng — chạy riêng chỉ chứng minh "SpotBugs chạy
được ở đâu đó", không chứng minh "lần quét NÀY có rule bảo mật". Finding của canary bị loại khỏi
`finding_count` theo package `evidence.canary`.

**Đừng "sửa" ba lỗi đó.** Chúng là thiết bị đo, cùng tinh thần với năm test hỏng của vulnshop.

Canary **không** thay thế được các tầng kiểm khác: nó nằm trong image nên không phụ thuộc
auxclasspath của người dùng, vì vậy ca "thiếu auxclasspath" (mất 3/28 cảnh báo, xem spike) vẫn cho
canary xanh. Đó là lý do có bốn tầng chứ không phải một.
