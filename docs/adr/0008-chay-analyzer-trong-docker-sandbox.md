# ADR 0008 — Chạy analyzer trong Docker sandbox

- **Trạng thái**: Chấp nhận
- **Ngày**: 05/08/2026
- **Liên quan**: [ADR 0003](0003-chuan-hoa-jdk-21.md) (JDK 21), [ADR 0004](0004-unified-evidence-model-hai-tang.md)
  (chuẩn hoá đường dẫn là rủi ro lớn nhất còn lại), [ADR 0007](0007-luu-snapshot-theo-tung-file-tren-object-storage.md)
  (snapshot lưu từng file trên object storage), [spike SpotBugs CLI](../spike-spotbugs-cli.md)

## Bối cảnh

Tới hết giai đoạn B-C của tuần 8-9, hệ thống nhận artifact analyzer **do người dùng tự tải lên**
rồi parse thành bằng chứng. Đường đó chạy đúng và sẽ không bị bỏ đi. Nhưng nó đòi người dùng tự
cài JDK 21, tự cấu hình `spotbugs-maven-plugin` kèm find-sec-bugs, tự chạy Semgrep với ruleset
đúng — tức đòi họ làm chính xác cái việc mà nền tảng này hứa sẽ làm hộ.

Bước còn thiếu: **core-backend tự chạy analyzer trên mã nguồn đã upload**.

Việc đó có nghĩa là **thực thi mã của người lạ**. `mvn test` chạy code trong repo; và không có
tập con nào an toàn — một `<plugin>` chạy được ở phase `validate`, trước cả khi biên dịch dòng
đầu tiên.

## Quyết định

### 1. Docker-out-of-Docker qua Docker API, chôn sau interface `SandboxRunner`

core-backend nói chuyện với Docker daemon của **host** qua socket. Tách thành một service
`analyzer-runner` riêng là đúng về kiến trúc nhưng sai thời điểm: nó thêm một tiến trình, một hợp
đồng mạng và một đường triển khai nữa vào lúc chưa cần. Interface `SandboxRunner` giữ chỗ để tách
sau trong nửa ngày.

### 2. KHÔNG có bind mount nào

Nguồn vào bằng `copyArchiveToContainer`, artifact ra bằng `copyArchiveFromContainer`, vùng ghi
tạm dùng volume **theo tên**.

Đây không phải sở thích. `docker run -v /workspace/abc:/work` phát ra từ *bên trong* core-backend
được daemon giải theo filesystem của **HOST**. Host không có đường dẫn đó ⇒ daemon **lặng lẽ tạo
một thư mục rỗng** ⇒ analyzer quét 0 class ⇒ **0 cảnh báo, exit 0**. Rơi thẳng vào đúng lớp
hỏng-im-lặng mà ADR 0003 và `ck_run_not_silently_empty` sinh ra để chặn.

Stream qua Docker API thì không có chuỗi đường dẫn nào để hai bên hiểu khác nhau — và chạy giống
hệt trên Windows, Linux, hay daemon từ xa, xoá luôn nhu cầu `MSYS_NO_PATHCONV`.

### 3. SpotBugs CLI ĐỘC LẬP, không `mvn spotbugs:spotbugs`

[Spike](../spike-spotbugs-cli.md) đã chứng minh CLI cho kết quả **trùng khít** với Maven plugin:
28/28 `BugInstance`, cùng bộ `(type, vị trí)`, cùng `total_classes`, cùng plugin đã nạp.

Vì sao không dùng Maven plugin: `run-analysis.sh` chạy được là nhờ pom của vulnshop **khai sẵn**
plugin + find-sec-bugs + `effort=Max` + `threshold=Low`. Repo người dùng nói chung không khai gì;
khi đó `mvn spotbugs:spotbugs` vẫn "thành công" nhưng mất toàn bộ rule bảo mật. Không chữa được
bằng `-D` vì `<plugins><plugin>` là tham số kiểu phức hợp.

**Hệ quả bắt buộc từ spike**: `-auxclasspath` phải được dựng bằng `mvn dependency:build-classpath`
trên repo người dùng. Thiếu nó, SpotBugs mất 3/28 cảnh báo mà **vẫn exit 0, XML vẫn hợp lệ,
`total_classes` vẫn đúng**. Bước dựng auxclasspath hỏng ⇒ run `FAILED`, không được chạy tiếp.

### 4. Bốn tầng chống "chạy xong nhưng 0 cảnh báo"

Bốn tầng **không thay thế được nhau** — mỗi tầng bắt một kiểu hỏng mà ba tầng kia mù.

| # | Tầng | Bắt được gì |
|---|---|---|
| 1 | Exit code + artifact tồn tại, khác rỗng, parse được — **kể cả các lệnh chuẩn bị** | Analyzer chết giữa chừng; `dependency:build-classpath` hỏng |
| 2 | Locator cấu trúc trong chính artifact | Analyzer chạy nhưng không đọc được class của người dùng |
| 3 | Canary — mã mồi có lỗi biết trước, đưa vào **cùng lời gọi** | Analyzer "còn sống" nhưng rule không nạp được |
| 4 | Quyết định trạng thái, `clean_ack_note` **sinh từ số đo thật** | Nhầm "dự án sạch" với "analyzer im lặng hỏng" |

Locator của tầng 2, đã xác minh trên `docs/samples/`:

- `FindBugsSummary/@total_classes > 0`
- `FindBugsSummary/@java_version` bắt đầu bằng `21.` — **ADR 0003 thành assertion trên dữ liệu**
- `Errors/@errors == 0`
- `count(//Plugin[@id='com.h3xstream.findsecbugs']) >= 1`
- Semgrep: `invocations[0].executionSuccessful == true` và `len(tool.driver.rules) > 0`
  (0 rule = ruleset không nạp được, mà SARIF vẫn hoàn toàn hợp lệ)

**KHÔNG kiểm `missingClasses`.** Spike cho thấy nó là tín hiệu **tương đối**: 6 là sàn của chính
vulnshop, và nó nhảy lên 13 khi thiếu auxclasspath — nhưng mọi ngưỡng tuyệt đối đều là báo động
giả cho repo khác. Nó chỉ có nghĩa khi so hai lần chạy của cùng một repo.

Spike cũng cho một bằng chứng cụ thể rằng bốn tầng không thừa: ca "thiếu auxclasspath" **lọt qua
cả tầng 2 lẫn tầng 3** (canary nằm trong image nên không phụ thuộc classpath của người dùng), chỉ
tầng 1 bắt được — và chỉ bắt được nếu tầng 1 bao gồm cả lệnh chuẩn bị, không chỉ lệnh analyzer.

### 4bis. HAI PHA: `resolve` có mạng, `analyze` không mạng

Kế hoạch ban đầu ghi `--network=none` cho mọi thứ kèm `mvn -o`. **Điều đó không sống sót khi viết
image**: Maven offline chỉ chạy được nếu MỌI plugin và phụ thuộc của repo người dùng đã nằm sẵn
trong seed. Với một repo bất kỳ điều đó là bất khả — và hậu quả không phải "chậm" mà là *không
chạy được gì cả*.

Chia làm hai lần chạy container, với hai mức đặc quyền khác nhau:

| Pha | Mạng | Chạy gì | Vì sao mức này chấp nhận được |
|---|---|---|---|
| `resolve` | **Có** | `mvn -B dependency:go-offline` + `dependency:build-classpath` | Chỉ phân giải phụ thuộc; KHÔNG chạy vòng đời build của người dùng, nên không thực thi test hay plugin ở phase `validate` |
| `analyze` | **`--network=none`** | `mvn -o test` (Surefire + JaCoCo) → SpotBugs CLI → Semgrep | Đây mới là pha thực thi mã của người lạ, và nó bị cắt mạng hoàn toàn |

Ranh giới quan trọng: **pha nguy hiểm là pha không có mạng**. `mvn test` chạy code trong repo, nên
nó không bao giờ được nói chuyện ra ngoài. Pha `resolve` có mạng nhưng chỉ đọc pom và tải jar.

Đây không phải là chỗ hở tuyệt đối — một pom độc hại vẫn có thể khai `<extensions>` để Maven nạp
code lúc phân giải. Nhưng khoảng cách giữa "nạp extension lúc resolve" và "chạy toàn bộ test suite"
là rất lớn, và mọi biện pháp ở mục 5 (non-root, cap-drop, pids-limit, read-only) vẫn áp cho **cả
hai** pha.

Seed `.m2` chỉ đọc trong image vẫn giữ nguyên tác dụng: nó làm pha `resolve` gần như tức thời cho
dự án Spring Boot điển hình, và là **phương án dự phòng** khi máy chủ không có mạng ra ngoài —
khi đó repo trùng seed vẫn phân tích được, repo khác thì `resolve` hỏng và run `FAILED` **kèm lý
do đọc được**, không phải một lần quét thiếu phụ thuộc trông như thành công.

### 5. Bảo mật — nói thẳng

Áp **tất cả**, không chọn lọc:

| Biện pháp | Chặn cái gì |
|---|---|
| `--network=none` + `mvn -o` **ở pha `analyze`** (xem 4bis) | Rò dữ liệu ra ngoài, tải payload về |
| non-root + `--cap-drop=ALL` + `no-new-privileges` | Leo thang đặc quyền trong container |
| `--pids-limit=512` | Fork bomb — **timeout không chặn được vì máy chết trước** |
| `--memory=2g` **kèm `--memory-swap` bằng nhau** | Quên `--memory-swap` là swap không giới hạn |
| `--cpus` | Chiếm CPU |
| `--read-only` + tmpfs | Ghi bậy vào filesystem của container |
| Timeout tường | Treo vô hạn |
| `AutoRemove(false)` | `--rm` làm container biến mất **trước khi đọc được log và exit code** |
| Không bao giờ mount Docker socket vào container analyzer | Analyzer tự điều khiển daemon = thoát hoàn toàn |

Mọi container/volume mang label `evidence.run-id` để `SandboxReaper` dọn được đồ mồ côi.

**Không dùng volume `.m2` dùng chung.** Repo A khai `<repositories>` lạ, tải jar về cache chung;
repo B của người dùng khác khai đúng `groupId:artifactId:version` đó và **nhận jar của kẻ tấn
công** mà không hề gọi mạng. Cộng thêm ghi tuỳ ý và hỏng do đồng thời. Đổi lại chỉ tiết kiệm vài
chục giây. Thay bằng seed **chỉ đọc** nướng sẵn trong image:
`-Dmaven.repo.local=/work/.m2 -Dmaven.repo.local.tail=/opt/m2-seed`.

## Rủi ro còn lại — không giải được trong khuôn khổ đồ án

**Container không phải ranh giới bảo mật trước lỗ hổng kernel.** Một CVE cho phép thoát container
⇒ quyền root trên host. Mọi biện pháp ở mục 5 làm cho việc đó khó hơn, không làm cho nó bất khả.

Cách chữa thật là sandbox có kernel riêng — gVisor, Kata Containers, hoặc VM dùng một lần cho mỗi
lần chạy. Cả ba đều ngoài phạm vi đồ án (đòi hạ tầng riêng, và không chạy được trên Docker Desktop
của máy phát triển).

**Giới hạn sử dụng được chấp nhận tường minh**: hệ thống chỉ nhận repo từ **người dùng đã xác
thực**, không phải một dịch vụ công cộng ẩn danh. Người vận hành chịu trách nhiệm về việc ai được
cấp tài khoản.

Ghi rủi ro này ra đây là quyết định có chủ đích: nêu ra ở buổi bảo vệ thì đó là hiểu biết về giới
hạn của giải pháp; giấu đi rồi bị hỏi thì mất điểm.

## Cắt ngay từ đầu (rủi ro/chi phí > giá trị)

- Service `analyzer-runner` riêng — interface đã giữ chỗ, tách sau khi có nhu cầu thật
- Volume `.m2` dùng chung — xem mục 5
- SSE cho tiến trình — polling 2 giây là đủ; SSE để tuần 12-13 làm một lần cho chat
- Hỗ trợ Gradle / multi-module — **từ chối tường minh kèm thông điệp rõ**, không đoán
- `DELETE /analysis-runs/{runId}` — timeout + reaper đã chặn hết trạng thái xấu

## Hệ quả

- `app.analysis.runner` mặc định **`disabled`**: `POST` chạy sandbox trả 503 kèm thông điệp rõ,
  ứng dụng **vẫn khởi động bình thường**. Deploy lên môi trường không có Docker daemon vẫn chạy
  được mọi thứ khác — đường nạp artifact thủ công không phụ thuộc gì vào giai đoạn D.
- Cần thêm cột sandbox và ràng buộc canary vào `analysis_run` (migration V6).
- Tiêu chí nghiệm thu: sandbox phải cho **đúng cùng số liệu** như lúc chạy trên host bằng
  `run-analysis.sh` — 28 SpotBugs + 5 Semgrep, 33 test, 82/171 dòng phủ. Lệch một con số là có gì
  đó sai.
- Test Docker thật đặt `@EnabledIfDockerAvailable` và dùng `alpine`, không dùng image analyzer đầy
  đủ: chúng kiểm **cơ chế** (cp-vào/cp-ra, `--network=none` thật sự chặn, timeout kill được, reaper
  dọn được), không kiểm analyzer.
