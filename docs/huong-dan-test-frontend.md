# Hướng dẫn kiểm thử frontend bằng trình duyệt thật

Dành cho việc nghiệm thu tuần 8-9. Đọc mục 1 trước nếu chưa rõ bốn khái niệm; muốn bắt tay ngay
thì nhảy tới mục 2.

> **Shell**: máy này mặc định mở **PowerShell**, còn `CLAUDE.md` và phần lớn script trong repo viết
> cho **Git Bash**. Hai shell không dùng chung cú pháp — PowerShell 5.1 **không hiểu `&&`**, không
> có `zip`, và `curl` ở đó là alias của `Invoke-WebRequest`. Mọi lệnh dưới đây có bản cho **cả
> hai**; chọn đúng tab của shell bạn đang dùng.

**Vì sao phải kiểm bằng tay khi đã có 88 test tự động**: test dùng `happy-dom` (không có bố cục
và cuộn thật), `MemoryRouter` (không có History API thật), mock `fetch` (không có CORS thật) và
tắt CSS (`css: false`). Bốn thứ đó về mặt cấu trúc không kiểm được bằng test — phần còn lại thì
kiểm tay chỉ là phí thời gian.

---

## 1. Bốn khái niệm

### Snapshot — ảnh chụp mã nguồn tại một thời điểm

Người dùng nén thư mục dự án Java thành `.zip` rồi tải lên. Hệ thống giải nén, lưu **từng file**
lên object storage (MinIO) và lập chỉ mục vào bảng `snapshot_file`.

Gọi là *snapshot* vì nó **bất biến**: mã nguồn có sửa sau đó thì snapshot cũ vẫn nguyên. Đây là
điều kiện để một bằng chứng còn kiểm chứng được — nói "lỗi ở `CartService.java` dòng 25" chỉ có
nghĩa khi biết chắc đang nói về bản mã nguồn nào.

Một dự án có nhiều snapshot (mỗi lần upload là một cái). Trong đồ án này ta luôn dùng
**`case-study/vulnshop`** — một ứng dụng Spring Boot nhỏ **cố tình gieo lỗi**, và bộ số liệu kỳ
vọng của nó đã chốt ở `case-study/vulnshop/expected-analysis.json`.

### Cảnh báo (warning) — một công cụ phân tích tĩnh nói "chỗ này có vấn đề"

Phân tích **tĩnh** = đọc mã nguồn mà không chạy nó. Hai công cụ được dùng:

- **SpotBugs** (kèm plugin **find-sec-bugs**) — đọc bytecode Java, tìm bug và lỗ hổng bảo mật
- **Semgrep** — khớp mẫu trên mã nguồn theo bộ rule đã vendor trong `case-study/semgrep-rules/`

Trên vulnshop chúng cho ra **34 cảnh báo** (29 SpotBugs + 5 Semgrep). Mỗi cảnh báo gồm: mã luật (`ruleId`), vị trí
`file:dòng`, và một câu tóm tắt. Ví dụ thật: `WEAK_MESSAGE_DIGEST_MD5` ở `PasswordHasher.java` —
mật khẩu băm bằng MD5.

> **Bẫy trung tâm của cả đồ án**: không phải cảnh báo nào cũng là lỗi thật (false positive), và
> **test xanh không chứng minh cảnh báo là sai**. vulnshop cố tình đặt bẫy này: `findByUsername`
> có lỗ hổng SQL injection nhưng test đường đi bình thường vẫn xanh. Đó chính là lý do tuần 10-11
> mới có AI Agent để phán xét, và mọi nhận định của nó bắt buộc gắn Evidence ID.

### Mức nghiêm trọng (severity) và màu của nó

Hai công cụ dùng hai thang đo **không giao nhau** (SpotBugs có priority 1-3 + rank 1-20; Semgrep
có level + precision), nên hệ thống chuẩn hoá về **bốn mức**, mỗi mức một màu:

| Mức | Màu | Ý nghĩa |
|---|---|---|
| `HIGH` | 🔴 đỏ | Nghiêm trọng — SQL injection, mã hoá yếu |
| `MEDIUM` | 🟠 cam | Đáng sửa |
| `LOW` | 🟡 vàng | Nhỏ |
| `INFO` | 🔵 xanh dương | Chỉ để biết |

Trên UI, mỗi dòng cảnh báo có một **nhãn nhỏ hình chữ nhật** ghi chữ `HIGH`/`MEDIUM`/… và **nền
màu tương ứng**. Đó là "màu severity".

**Vì sao phải nhìn tận mắt**: Tailwind quét class bằng phân tích **tĩnh trên văn bản**. Nếu tên
class được ghép động (`` `bg-sev-${severity}` ``) thì nó **không bao giờ được sinh ra**, và hỏng
hoàn toàn im lặng — **nhãn vẫn hiện đủ chữ, chỉ mất màu**. Test đọc chữ nên xanh y như thường. Đây
là lý do `src/index.css` khai `@theme static` và `SeverityBadge` dùng bảng tra chứa **tên class
nguyên văn**.

### Sandbox — hệ thống tự chạy công cụ phân tích, trong container cách ly

Có **hai cách** để cảnh báo vào được hệ thống:

| Cách | Ai chạy analyzer | Trạng thái |
|---|---|---|
| **Nạp artifact thủ công** | Bạn tự chạy, rồi tải file kết quả lên qua API | Dùng ngay được — **hướng dẫn này đi đường này** |
| **Docker sandbox** | Hệ thống tự chạy trong container | Đã xong nhưng **tắt mặc định** |

Sandbox tắt mặc định vì bật nó đòi gắn Docker socket vào backend, mà **việc đó tương đương cấp
quyền root trên máy chủ** (xem `infra/README.md`). Nó cũng thực thi mã của người lạ — `mvn test`
chạy code trong repo người dùng — nên container bị cắt mạng, chạy non-root, giới hạn RAM/CPU/tiến
trình.

*Artifact* = file kết quả do công cụ sinh ra: `spotbugs.xml`, `semgrep.sarif`, `jacoco.xml`, và
các `TEST-*.xml` của JUnit. Repo đã có sẵn bản THẬT ở **`docs/samples/vulnshop/`** — dùng luôn,
không cần chạy lại công cụ.

---

## 2. Chuẩn bị (một lần)

### 2.1. Hạ tầng + backend

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres minio
cd core-backend && mvn spring-boot:run
```

Đợi tới khi `curl http://localhost:8080/actuator/health` trả `{"status":"UP"}`.

### 2.2. Frontend

```bash
cd frontend && npm run dev      # http://localhost:5173
```

`frontend/public/config.js` đã trỏ sẵn `apiBaseUrl: 'http://localhost:8080'`, và `5173` đã nằm
trong `CORS_ORIGINS` của `infra/.env`.

### 2.3. Nén vulnshop thành zip

Nén **nội dung bên trong** `case-study/vulnshop/`, sao cho `pom.xml` nằm ở **gốc zip**. Loại
`target/` là bắt buộc: nó là sản phẩm biên dịch, không thuộc mã nguồn.

<details open>
<summary><b>PowerShell</b> (mặc định của Windows Terminal / VS Code trên máy này)</summary>

Windows PowerShell 5.1 **không có** lệnh `zip`, và **không hiểu toán tử `&&`** — dùng
`Compress-Archive`:

```powershell
$src   = 'D:\study\DATN\Project\case-study\vulnshop'
$stage = Join-Path $env:TEMP ('vulnshop-stage-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$zip   = Join-Path $env:TEMP 'vulnshop.zip'

New-Item -ItemType Directory -Force -Path $stage | Out-Null
Get-ChildItem -Path $src -Exclude 'target' | Copy-Item -Destination $stage -Recurse -Force
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip

"Zip: $zip"
```

Phải qua thư mục trung gian vì `Compress-Archive` **không có tham số loại trừ**.

Kiểm lại zip đúng hình dạng (48 entry, `pom.xml` ở gốc, không có `target/`):

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($zip)
"so entry      = $($z.Entries.Count)"
"pom.xml o goc = $([bool]($z.Entries | Where-Object { $_.FullName -eq 'pom.xml' }))"
"con target/   = $([bool]($z.Entries | Where-Object { $_.FullName -like 'target/*' }))"
$z.Dispose()
```

</details>

<details>
<summary><b>Git Bash</b></summary>

```bash
cd case-study/vulnshop && zip -r /tmp/vulnshop.zip . -x 'target/*' && cd -
```

</details>

---

## 3. Đi qua giao diện

1. Mở `http://localhost:5173` → **Đăng ký** (email, mật khẩu ≥ 8 ký tự, tên hiển thị).
2. Tạo một dự án, ví dụ `vulnshop`.
3. Vào dự án → tải `/tmp/vulnshop.zip` lên.
   Kỳ vọng: snapshot chuyển **READY**, khoảng 30 file.
4. Bấm vào snapshot → thấy **cây file bên trái**, bấm một file bất kỳ để xem mã nguồn.

Ghi lại `snapshotId` trên URL (`/snapshots/<ĐÂY>`), bước sau cần.

---

## 4. Nạp cảnh báo vào hệ thống

**Chưa có nút bấm cho việc này** — endpoint đã xong nhưng UI nạp artifact chưa làm (ghi ở §H của
kế hoạch).

Không cần nén `TEST-*.xml` thành zip: trường `artifacts` lặp được, và backend **nhận dạng theo nội
dung file** chứ không theo tên.

<details open>
<summary><b>PowerShell</b></summary>

`Invoke-RestMethod` của PowerShell 5.1 **không gửi được multipart** (tham số `-Form` chỉ có từ
PowerShell 6.1), nên phần tải file phải dùng `curl.exe` — Windows 10/11 có sẵn ở
`C:\Windows\System32\curl.exe`.

⚠️ Gõ **`curl.exe`** đầy đủ: `curl` trần trong PowerShell là **alias của `Invoke-WebRequest`**, cú
pháp hoàn toàn khác và sẽ báo lỗi khó hiểu.

```powershell
$repo    = 'D:\study\DATN\Project'
$api     = 'http://localhost:8080'
$samples = Join-Path $repo 'docs\samples\vulnshop'

# 1. Lấy access token — JSON thuần thì Invoke-RestMethod dùng tốt
$body  = @{ email = 'BAN_VUA_DANG_KY'; password = 'MAT_KHAU' } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/login" `
            -ContentType 'application/json' -Body $body).accessToken

# 2. snapshotId lấy từ URL ở bước 3  (/snapshots/<ĐÂY>)
$snapshotId = '...'

# 3. Dựng danh sách tham số rồi splat sang curl.exe
$curlArgs = @(
    '-s', '-X', 'POST', "$api/api/v1/snapshots/$snapshotId/analysis-runs",
    '-H', "Authorization: Bearer $token",
    '-F', "artifacts=@$samples\spotbugs.xml",
    '-F', "artifacts=@$samples\semgrep.sarif",
    '-F', "artifacts=@$samples\jacoco.xml"
)
Get-ChildItem "$samples\surefire-reports\TEST-*.xml" | ForEach-Object {
    $curlArgs += '-F'; $curlArgs += "artifacts=@$($_.FullName)"
}

(& curl.exe @curlArgs) | ConvertFrom-Json |
    Format-List id, status, findingCount, unresolvedLocationCount
```

Đúng thì có **12 lần `-F`** (3 artifact + 9 báo cáo test). Kiểm nhanh:
`($curlArgs | Where-Object { $_ -eq '-F' }).Count`

</details>

<details>
<summary><b>Git Bash</b></summary>

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"BAN_VUA_DANG_KY","password":"MAT_KHAU"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

SNAPSHOT_ID=...   # lấy từ URL ở bước 3

curl -X POST "http://localhost:8080/api/v1/snapshots/$SNAPSHOT_ID/analysis-runs" \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifacts=@docs/samples/vulnshop/spotbugs.xml" \
  -F "artifacts=@docs/samples/vulnshop/semgrep.sarif" \
  -F "artifacts=@docs/samples/vulnshop/jacoco.xml" \
  $(for f in docs/samples/vulnshop/surefire-reports/TEST-*.xml; do printf -- " -F artifacts=@%s" "$f"; done)
```

</details>

**Kỳ vọng trong JSON trả về**: `"status":"SUCCEEDED"` · `"findingCount":34` ·
`"unresolvedLocationCount":0`.

`findingCount` **chỉ đếm cảnh báo tĩnh**, không đếm 124 bằng chứng (34 cảnh báo + 35 kết quả test +
6 stack trace + 19 độ phủ + 30 ngữ cảnh mã nguồn) — cố ý như vậy, xem `MaterializationResult`.

---

## 5. Sáu điều cần nhìn tận mắt

Quay lại trình duyệt, mở lại trang snapshot. Giờ góc trên phải có link **"Cảnh báo (34)"**.

### ✅ 1. Màu severity thật sự hiện ra

Bấm vào link đó. Bảng cảnh báo phải có **nhãn nhiều màu khác nhau** ở cột đầu.

**Hỏng thì trông thế nào**: nhãn vẫn hiện đủ chữ `HIGH`/`MEDIUM`, nhưng **tất cả cùng một màu**
hoặc không nền màu. Nghĩa là Tailwind không sinh ra class — 88 test vẫn xanh vì chúng đọc chữ.

Đồng thời kiểm phần đầu trang: `34 cảnh báo tĩnh` và `.../… vị trí đối chiếu được với mã nguồn`,
con số sau phải **bằng nhau** (0 vị trí hỏng).

### ✅ 2. Bấm một dòng thì cuộn tới đúng dòng

Bấm vào vị trí của một cảnh báo (ví dụ `.../PasswordHasher.java:19`).

Phải mở đúng file **và** dòng được **tô nền vàng** phải nằm trong tầm nhìn — không phải cuộn tay
mới thấy. URL đúng đã được test canh; *cuộn* thì `happy-dom` không làm được.

### ✅ 3. F5 tại deep link vẫn ở nguyên trang

Đang ở màn hình cảnh báo, nhấn **F5**. Phải quay lại đúng trang đó, không bị đá về `/projects` hay
`/login`. (Nợ từ tuần 5-7 — test dùng `MemoryRouter` nên không có History API thật.)

### ✅ 4. Console không có lỗi CORS

Mở **DevTools → Console** ngay từ lúc mở app. Không được có dòng nào chứa *"blocked by CORS
policy"*.

`curl` **không phát hiện được** lỗi này vì nó không ép chính sách same-origin — đúng là cách lỗi
`/actuator/health` từng lọt qua ở tuần 5-7.

### ✅ 5. Token hết hạn gọi đúng MỘT lần `/auth/refresh`

Mở **DevTools → Network**, lọc `refresh`. Để yên **15 phút** (access token hết hạn), rồi bấm nhanh
nhiều thứ cùng lúc.

Phải thấy **đúng một** request `/auth/refresh`. Nhiều hơn nghĩa là single-flight hỏng. (Nợ từ tuần
5-7 — logic có test, nhưng thời gian thật và nhiều tab thì chưa.)

### ✅ 6. Bộ lọc hoạt động

Trên màn hình cảnh báo, chọn **Công cụ = semgrep** → còn **5** dòng. Chọn **Mức = HIGH** → chỉ còn
dòng đỏ. Xoá lọc → về 33.

---

## 6. Hai thứ bạn sẽ KHÔNG thấy, và đó là đúng

**Nhãn "chưa đối chiếu được với snapshot"** không xuất hiện, vì vulnshop đối chiếu được 100%. Muốn
thấy thì nạp artifact của vulnshop vào một snapshot **khác** — khi đó run sẽ `FAILED` với lý do
"không đối chiếu được vị trí nào". Đường đó đã có test ở cả hai tầng, không cần kiểm tay.

**Nút chạy sandbox** chưa có trên UI. Muốn thử sandbox thì:

```bash
./infra/analyzer/build.sh          # dựng 2 image, tự đối chiếu 28/5/33/82
# đặt ANALYSIS_RUNNER=docker trong infra/.env
# gỡ comment mount docker.sock trong infra/docker-compose.yml  (ĐỌC CẢNH BÁO Ở ĐÓ TRƯỚC)
curl -X POST "http://localhost:8080/api/v1/snapshots/$SNAPSHOT_ID/analysis-runs/sandbox" \
     -H "Authorization: Bearer $TOKEN"
```

Trả **202** kèm run `RUNNING`; theo dõi bằng `GET /api/v1/analysis-runs/{runId}` cho tới khi
`SUCCEEDED`. Mất khoảng 2-4 phút.

---

## 7. Gặp lỗi thì tra ở đâu

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Màn hình trắng, console báo thiếu `__APP_CONFIG__` | `public/config.js` chưa nạp được |
| Mọi request 401 | Access token hết hạn 15 phút mà refresh hỏng — xem tab Network |
| Upload zip báo lỗi | Zip phải có `pom.xml` ở **gốc**, không phải trong một thư mục con |
| `POST analysis-runs` trả 400 | Sai file artifact; backend nhận dạng theo nội dung, kiểm lại đường dẫn |
| `POST analysis-runs` trả 409 | Snapshot chưa `READY`, đợi giải nén xong |
| Không thấy link "Cảnh báo" | Snapshot đó chưa có run nào — quay lại mục 4 |

Cạm bẫy môi trường (JDK 21, Docker, Testcontainers, Vitest) ghi ở `CLAUDE.md`.
