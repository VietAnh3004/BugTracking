# Triển khai lên VPS — vsleuth.id.vn

Hướng dẫn đưa cả hệ thống lên **một VPS có IP công khai**, chạy sau Nginx + HTTPS (Let's Encrypt).
Docker sandbox **tắt mặc định, chỉ bật lúc demo** (mục 9). Viết ngày 18/11/2026, **trước khi** có
VPS — mọi lệnh dưới đây chưa được chạy trên máy thật. Lần dựng đầu tiên gặp gì khác thì sửa tài
liệu này ngay, cùng commit với bản vá.

| File | Vai trò |
|---|---|
| [`infra/docker-compose.vps.yml`](../infra/docker-compose.vps.yml) | Compose riêng cho VPS, đứng một mình (không chồng lên file dev). Sandbox TẮT, không mount socket |
| [`infra/docker-compose.vps.sandbox.yml`](../infra/docker-compose.vps.sandbox.yml) | Ghép thêm khi demo: mount socket + bật sandbox. Không ghép = tắt |
| [`infra/.env.vps.example`](../infra/.env.vps.example) | Mẫu biến môi trường. Bản thật `infra/.env.vps` bị `.gitignore` chặn |
| [`infra/vps/nginx-vsleuth.conf`](../infra/vps/nginx-vsleuth.conf) | Nginx trên host: proxy vào frontend + backend, tắt buffering cho SSE |

---

## 0. Trạng thái

| Hạng mục | Trạng thái (18/11/2026) |
|---|---|
| Tên miền | ✅ **`vsleuth.id.vn`** đã mua ở PA Vietnam (18/11/2026). Không mua gói hosting (shared hosting chỉ chạy PHP/MySQL, không chạy được Java, Python, Postgres hay Docker) |
| VPS | **Chưa thuê** |
| Cấu hình trong repo | Đã có bốn file ở bảng trên, chưa chạy thử trên máy thật |

Việc còn lại theo thứ tự: thuê VPS → mục 3 trở đi.

---

## 1. Hình dạng hệ thống trên VPS

```
Internet ──443──► Nginx (host) ─┬─ vsleuth.id.vn      ──► 127.0.0.1:3000 ─► frontend (container)
           80 ──► chuyển 443    └─ api.vsleuth.id.vn  ──► 127.0.0.1:8080 ─► core-backend (container)
                                                                               │  mạng Docker
                                              ┌────────────────┬───────────────┼───────────────┐
                                              ▼                ▼               ▼               ▼
                                          postgres          minio       agent-service    Docker daemon
                                     (127.0.0.1:5432)  (127.0.0.1:9001)  (không cổng)   (qua socket — CHỈ
                                                                                         khi bật sandbox)
```

Chỉ ba cổng mở ra Internet: **22** (SSH), **80** và **443** (Nginx). Mọi thứ khác gắn vào
`127.0.0.1` hoặc không có cổng nào — kể cả khi tường lửa cấu hình sai.

---

## 2. Quyết định đã chốt với người dùng (18/11/2026)

| Hạng mục | Lựa chọn | Lý do / đánh đổi |
|---|---|---|
| HTTPS | **Nginx + Certbot** trên host | Phổ biến, nhiều tài liệu. Certbot cài bằng apt tự gia hạn. Phải tự lo `client_max_body_size` và `proxy_buffering off` — đã làm trong file cấu hình |
| Tên miền | **Hai tên miền con**: `vsleuth.id.vn` (frontend), `api.vsleuth.id.vn` (backend) | Giữ ràng buộc "FE và BE deploy độc lập" (ADR 0002): CORS được kiểm thật, mai này tách backend sang máy khác chỉ cần đổi DNS |
| Compose | **File riêng độc lập** `docker-compose.vps.yml` | File dev mở cổng ra `0.0.0.0` và mount Docker socket; bản override sẽ tự thừa hưởng mọi thứ thêm vào file dev sau này. Đánh đổi: lặp danh sách biến môi trường — thêm biến ở file nào thì thêm ở cả hai |
| Docker sandbox | Ban đầu chốt BẬT thường trực; **đổi thành TẮT mặc định, chỉ bật lúc demo** (cùng ngày) sau khi thấy rủi ro dưới đây | Vẫn demo được "Phân tích tự động" và vẫn có coverage từng test. VPS vẫn cần ≥ 8 GB RAM cho lúc bật |

### Rủi ro của việc bật sandbox trên máy công khai — vì sao chỉ bật lúc demo

Mount `/var/run/docker.sock` vào core-backend = **core-backend có quyền root trên VPS**. Một lỗ
hổng thực thi mã từ xa trong core-backend (hoặc trong thư viện nó dùng) là đủ để chiếm cả máy.
Mã người dùng upload chạy trong container bị cắt mạng, non-root, giới hạn RAM/CPU/tiến trình
(ADR 0008), nhưng container **không phải ranh giới bảo mật trước lỗ hổng kernel**.

**Một giả định của ADR 0008 KHÔNG còn đúng trên VPS này.** ADR 0008 chấp nhận rủi ro sandbox vì
*"hệ thống chỉ nhận repo từ người dùng đã xác thực — người vận hành chịu trách nhiệm về việc ai
được cấp tài khoản"*. Nhưng hiện **ai cũng tự đăng ký được**: xác thực email (ADR 0012) chỉ chứng
minh người đó sở hữu một hộp thư, không chứng minh họ đáng tin. Một người lạ trên Internet đăng ký,
xác thực bằng email của chính họ, rồi upload một repo có `mvn test` độc hại — và nó chạy trên VPS
này qua sandbox. Tức là mở đăng ký + bật sandbox trên IP công khai = đúng loại "dịch vụ công cộng"
mà ADR 0008 đã loại trừ.

Có hai cách đóng lỗ này. **Người dùng chọn cách 1** (18/11/2026):

1. ✅ **Chỉ bật sandbox lúc cần chạy phân tích**, còn lại tắt. Không cần sửa mã. Hiện thực bằng
   file ghép riêng `docker-compose.vps.sandbox.yml` thay vì comment/bỏ comment từng dòng, để không
   thể bật nửa vời. Quy trình ở mục 9.
2. Đóng đăng ký tự do (danh sách email được phép) — **không làm**. Cần sửa core-backend
   (`AuthService.register` + đăng nhập Google tạo tài khoản mới). Xem lại nếu sau này muốn sandbox
   bật thường trực.

**Vì sao cách 1 chặt hơn nghe có vẻ:** sandbox chỉ cần lúc **phân tích**, không cần lúc **xem**.
Một lần "Phân tích tự động" ghi mọi thứ vào DB — cảnh báo, kết quả test, cả coverage từng test
(V12) — và chúng còn nguyên sau khi tắt. Vậy buổi demo hay user study không cần sandbox bật suốt
buổi: bật → phân tích vulnshop → tắt, xong **trước** khi người tham gia đến. Socket chỉ lộ vài phút.

Rủi ro còn lại phải chấp nhận: trong vài phút bật đó, một tài khoản lạ (nếu đã có) vẫn bấm được
"Phân tích tự động". Muốn chặn cả cửa sổ đó thì phải làm cách 2.

Giảm nhẹ khác đã có: dùng VPS riêng, không chung với dịch vụ hay dữ liệu nào khác ngoài đồ án.

---

## 3. Thuê VPS

| | Tối thiểu | Ghi chú |
|---|---|---|
| RAM | **8 GB** | Bốn service + Postgres ~3 GB, mỗi lần chạy sandbox chiếm thêm tới 2 GB, cộng Maven trong image analyzer |
| CPU | 4 vCPU | Sandbox giới hạn 2 CPU/lần (`SANDBOX_CPUS`) |
| Ổ cứng | 60 GB SSD | Hai image analyzer + `.m2` seed đã vài GB |
| Hệ điều hành | **Ubuntu 24.04 LTS, x86_64** | Không chọn máy ARM: image analyzer chưa từng dựng trên ARM |

---

## 4. Trỏ tên miền về VPS (trang quản lý DNS của PA Vietnam)

| Loại | Tên | Giá trị |
|---|---|---|
| A | `@` | IP của VPS |
| A | `api` | IP của VPS |

Nếu VPS có IPv6 thì thêm hai bản ghi **AAAA** tương ứng — hoặc KHÔNG thêm gì cả. Thêm AAAA mà
Nginx không nghe IPv6 thì một phần người dùng không vào được (file cấu hình đã có `listen [::]:80`).

Kiểm từ máy bạn (chờ vài phút tới vài giờ để DNS lan):

```bash
nslookup vsleuth.id.vn
nslookup api.vsleuth.id.vn       # cả hai phải ra đúng IP của VPS
```

**Phải xong bước này trước mục 7** — Let's Encrypt kiểm quyền sở hữu tên miền bằng cách gọi vào
chính tên miền đó.

Nếu đã đăng ký DNSSEC ở PA mà sau này chuyển DNS sang nơi khác (Cloudflare…), **tắt DNSSEC
trước** — quên là tên miền mất truy cập hoàn toàn.

---

## 5. Chuẩn bị server (một lần)

Đăng nhập lần đầu bằng thông tin nhà cung cấp gửi, rồi:

```bash
# 5.1 User thường có sudo — không làm việc bằng root
adduser deploy && usermod -aG sudo deploy

# 5.2 Cập nhật + tự cài bản vá bảo mật
apt update && apt upgrade -y
apt install -y unattended-upgrades git python-is-python3
#   python-is-python3: infra/analyzer/build.sh gọi `python`, mà Ubuntu chỉ có `python3`.
#   (Ngược với máy dev Windows, nơi `python3` mới là lệnh hỏng — CLAUDE.md.)
```

**SSH bằng khoá, tắt mật khẩu.** Máy có IP công khai bị dò mật khẩu SSH trong vài phút đầu. Trên
**máy bạn** (Git Bash):

```bash
ssh-keygen -t ed25519                      # nếu chưa có ~/.ssh/id_ed25519
ssh-copy-id deploy@<IP_VPS>
ssh deploy@<IP_VPS>                        # phải vào được KHÔNG hỏi mật khẩu, rồi mới làm tiếp
```

Trên VPS, sửa `/etc/ssh/sshd_config`: `PasswordAuthentication no`, `PermitRootLogin no`, rồi
`sudo systemctl restart ssh`. **Giữ nguyên phiên SSH đang mở** và thử đăng nhập ở một cửa sổ
khác trước khi đóng — sai cấu hình là tự khoá mình ngoài.

**Tường lửa:**

```bash
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
sudo ufw enable
```

> UFW **không chặn được** cổng mà Docker publish: Docker chèn luật iptables đứng trước UFW. Đó là
> lý do `docker-compose.vps.yml` gắn mọi cổng vào `127.0.0.1` thay vì trông vào tường lửa. Mục 8
> kiểm lại điều này từ bên ngoài.

**Docker** (theo hướng dẫn chính thức cho Ubuntu, gồm cả plugin `docker compose`):

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy             # đăng xuất rồi vào lại để có hiệu lực
docker compose version                     # cần >= 2.20
```

**Nginx + Certbot:**

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

---

## 6. Đưa mã lên và cấu hình

```bash
git clone https://github.com/VietAnh3004/BugTracking.git ~/vsleuth
cd ~/vsleuth
```

Repo private thì `git clone` đòi xác thực. Cách gọn nhất: tạo khoá riêng trên VPS
(`ssh-keygen -t ed25519 -f ~/.ssh/github_deploy`), thêm public key vào GitHub → repo → Settings →
**Deploy keys** (chỉ quyền đọc), rồi clone bằng URL dạng `git@github.com:...`.

**Biến môi trường:**

```bash
cp infra/.env.vps.example infra/.env.vps
chmod 600 infra/.env.vps                   # chỉ chủ file đọc được — nó chứa mọi bí mật
stat -c %g /var/run/docker.sock            # → điền vào DOCKER_SOCKET_GID (chỉ dùng khi bật sandbox)
openssl rand -base64 48                    # chạy nhiều lần, mỗi bí mật một giá trị
nano infra/.env.vps                        # thay MỌI dòng change-me
```

`JWT_SECRET` và `SERVICE_TOKEN` phải **khác nhau** và **khác bản trên máy dev**.

**Tài khoản quản trị (`ADMIN_USERNAME` / `ADMIN_PASSWORD`).** Một tài khoản duy nhất, đăng nhập ở
`https://vsleuth.id.vn/admin/login` (không có link nào trỏ tới — tự gõ địa chỉ). Trong trang quản trị
admin xem và **xoá** được mọi tài khoản; xoá một tài khoản là xoá luôn dự án, snapshot, bằng chứng, file
trên MinIO và hội thoại của họ. Vì VPS ra Internet mà tài khoản này có quyền đó, **mật khẩu phải là chuỗi
ngẫu nhiên dài** (`openssl rand -base64 24`), không dùng lại mật khẩu trên máy dev. Để trống cả hai = không
có admin (trang `/admin` không vào được). Đổi giá trị trong `.env.vps` rồi `up -d core-backend` là có
hiệu lực: mật khẩu được băm lại, mọi phiên cũ của admin bị thu hồi, và tên đăng nhập cũ (nếu đổi) bị hạ về
người dùng thường. Không ai khác thành admin được: đăng ký và đăng nhập Google luôn tạo tài khoản thường.

**Hạng dự án Free/Premium và model Premium (`PREMIUM_LLM_*`).** Mỗi dự án có hạng `FREE` (mặc định) hoặc
`PREMIUM`; chỉ admin đổi, ở tab "Dự án" của trang quản trị (`/admin/projects`). Dự án Free chỉ hỏi Agent
bằng model `LLM_*` (Gemini miễn phí); dự án Premium cho chủ và mọi thành viên chọn **từng câu hỏi** giữa
Gemini (mặc định) và model Premium. Quyền gắn với DỰ ÁN, không với người. Model Premium khai bằng
`PREMIUM_LLM_PROVIDER` / `PREMIUM_LLM_API_KEY` / `PREMIUM_LLM_MODEL` (mặc định `anthropic` / `claude-sonnet-5`)
trong `.env.vps` — **tự điền khoá Claude, đừng commit**. Để trống khoá thì tier premium tắt: hỏi Premium báo
lỗi rõ ràng chứ không âm thầm dùng Gemini. Mỗi câu Premium tốn tiền thật (~13k token vào + ~0,7k token ra,
5 lượt gọi/câu) nên chỉ nâng hạng cho dự án thật sự cần; hạ về Free có hiệu lực từ câu hỏi kế tiếp.

**Dựng hai image analyzer** — làm một lần, dù sandbox tắt mặc định: lúc bật để demo, chúng phải
nằm sẵn trong Docker của host (core-backend ra lệnh cho daemon của host chạy chúng). Để tới hôm
demo mới dựng thì mất thêm 5-15 phút đúng lúc không có thời gian.

```bash
./infra/analyzer/build.sh                  # dựng + tự đối chiếu với expected-analysis.json
```

Lần đầu mất 5-15 phút. Script **dừng nếu số liệu lệch** — đừng bỏ qua bằng `--no-check`, một
image analyzer "dựng xong mà ra 0 cảnh báo" là đúng thứ cả đồ án chống.

**Chạy hệ thống:**

```bash
docker compose -f infra/docker-compose.vps.yml --env-file infra/.env.vps up -d --build
docker compose -f infra/docker-compose.vps.yml --env-file infra/.env.vps ps      # chờ tất cả "healthy"
```

Thiếu biến bắt buộc thì lệnh `up` dừng ngay với thông điệp `Thieu ... trong infra/.env.vps` —
trước khi tạo container nào. Lệnh này chạy **không** có sandbox; bật sandbox ở mục 9.

---

## 7. Nginx + HTTPS

```bash
sudo cp infra/vps/nginx-vsleuth.conf /etc/nginx/sites-available/vsleuth.conf
sudo ln -s /etc/nginx/sites-available/vsleuth.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx

sudo certbot --nginx -d vsleuth.id.vn -d api.vsleuth.id.vn
#   chọn "redirect" khi được hỏi: mọi http → https
sudo certbot renew --dry-run               # xác nhận gia hạn tự động chạy được
```

Sau lệnh `certbot --nginx`, file trên VPS đã được certbot sửa (thêm 443 + chứng chỉ). **Đừng chép
đè bản trong repo lên nữa** — sẽ mất phần HTTPS. Sửa thẳng bản ở `/etc/nginx/sites-available/`.

---

## 8. Kiểm chứng — đừng tin "chạy rồi", kiểm từng điều

**Từ máy bạn, KHÔNG phải từ VPS** (từ VPS thì 127.0.0.1 luôn thông, kiểm vậy là vô nghĩa):

| # | Kiểm | Kỳ vọng |
|---|---|---|
| 1 | `curl -I http://vsleuth.id.vn` | `301` chuyển sang `https://` |
| 2 | `curl https://api.vsleuth.id.vn/actuator/health` | `{"status":"UP",...}` |
| 3 | `curl -m 5 http://<IP_VPS>:5432` · `:8080` · `:9000` · `:9001` · `:8000` · `:3000` | **Tất cả** timeout hoặc connection refused. Cái nào trả lời là cổng đó đang lộ ra Internet |
| 4 | Mở `https://vsleuth.id.vn`, DevTools → Console | Không có dòng *"blocked by CORS policy"*. Có thì `CORS_ORIGINS` lệch origin — kể cả lệch `https`/`http` hay thừa dấu `/` |
| 5 | Đăng ký tài khoản | Thư xác thực tới hộp thư (nếu đã điền `MAIL_*`), link trong thư trỏ về `https://vsleuth.id.vn` chứ không phải `localhost` |
| 6 | Upload `vulnshop.zip` | `READY`. Ăn `413` nghĩa là `client_max_body_size` của Nginx nhỏ hơn file |
| 7a | Sandbox đang TẮT: bấm "Phân tích tự động" | Báo lỗi **503** kèm lý do "sandbox đang tắt" — không phải 500 |
| 7b | Bật sandbox (mục 9), phân tích vulnshop, rồi TẮT | `SUCCEEDED` sau 2-4 phút, số liệu khớp `case-study/vulnshop/expected-analysis.json`. Sau khi tắt, mở lại run đó: cảnh báo và liên kết vẫn còn đủ |
| 7c | Sau khi tắt: `docker inspect evidence-agent-core-backend-1 --format '{{json .Mounts}}'` trên VPS | **Không** có `docker.sock`. Còn thì lệnh tắt chưa chạy thật |
| 8 | Hỏi Agent về một cảnh báo | Các dòng tiến trình hiện ra **dần dần** trong lúc chờ. Đứng im rồi hiện một cục ở cuối = Nginx đang buffer SSE (`proxy_buffering off` bị mất, thường do chép đè cấu hình) |
| 9 | Tải CSV ở dashboard, mở bằng Excel | Tiếng Việt hiện đúng |

Mục 3 và 7c quan trọng nhất về bảo mật. Mục 8 là mục hay hỏng nhất mà không báo lỗi.

---

## 9. Vận hành hằng ngày

Viết tắt cho gọn (thêm vào `~/.bashrc` trên VPS):

```bash
alias dc='docker compose -f ~/vsleuth/infra/docker-compose.vps.yml --env-file ~/vsleuth/infra/.env.vps'
alias dc-sandbox='docker compose -f ~/vsleuth/infra/docker-compose.vps.yml -f ~/vsleuth/infra/docker-compose.vps.sandbox.yml --env-file ~/vsleuth/infra/.env.vps'
```

`dc` = trạng thái thường (sandbox TẮT). `dc-sandbox` = có sandbox. **Mọi lệnh hằng ngày dùng
`dc`** — lỡ gõ `dc up -d` trong lúc đang bật thì sandbox tự tắt, đó là chiều an toàn.

### Bật sandbox cho demo — và tắt ngay sau đó

Sandbox chỉ cần lúc **chạy phân tích**. Kết quả nằm trong DB, nên phân tích xong là tắt được,
**trước** khi người xem/người tham gia user study đến.

```bash
dc-sandbox up -d            # 1. BẬT — core-backend tạo lại, web gián đoạn ~1 phút
                            # 2. Trên web: snapshot vulnshop → "Phân tích tự động" → chờ SUCCEEDED
                            #    (2-4 phút; ĐỪNG tắt khi run còn RUNNING)
dc up -d                    # 3. TẮT — core-backend tạo lại KHÔNG còn mount socket
docker inspect evidence-agent-core-backend-1 --format '{{json .Mounts}}' | grep -c docker.sock
                            # 4. Phải ra 0
```

Muốn demo **trực tiếp** nút "Phân tích tự động" trước hội đồng thì bật ngay trước phần đó và tắt
ngay sau. Phần còn lại của demo (Evidence Explorer, hỏi Agent, review) không cần sandbox.

### Việc thường ngày

| Việc | Lệnh |
|---|---|
| Xem trạng thái | `dc ps` |
| Xem log một service | `dc logs -f --tail=200 core-backend` |
| Cập nhật lên mã mới | `git pull && dc up -d --build` — **bắt buộc `--build`**, thiếu thì chạy lại image cũ mà không hề biết |
| Sửa analyzer (`infra/analyzer/`) | Chạy lại `./infra/analyzer/build.sh` trước `dc up` |
| Khởi động lại một service | `dc restart core-backend` |

**Vào database / MinIO từ máy bạn** — qua đường hầm SSH, không mở cổng nào:

```bash
ssh -L 5432:127.0.0.1:5432 -L 9001:127.0.0.1:9001 deploy@<IP_VPS>
# giữ cửa sổ này mở, rồi trên máy bạn:
#   psql/DBeaver → localhost:5432   (user/pass trong infra/.env.vps)
#   trình duyệt  → http://localhost:9001   (trang quản trị MinIO)
```

Lưu ý: trên máy bạn mà Postgres dev cũng đang chạy ở 5432 thì đổi cổng bên trái, ví dụ
`-L 15432:127.0.0.1:5432`.

**Sao lưu** — đặc biệt trước buổi user study và trước khi bảo vệ, vì `human_review`,
`ground_truth_label`, `llm_trace` là dữ liệu thực nghiệm **không tái tạo được**:

```bash
dc exec -T postgres pg_dump -U evidence -d evidence_agent -Fc > ~/backup-$(date +%F).dump
scp deploy@<IP_VPS>:~/backup-*.dump .      # chạy trên máy bạn — bản sao lưu phải nằm NGOÀI VPS
```

---

## 10. Cạm bẫy đã biết trước (chưa gặp thật — gặp thì ghi thêm triệu chứng)

| Cạm bẫy | Triệu chứng | Đã chặn bằng |
|---|---|---|
| Docker vượt mặt UFW | `ufw deny 5432` mà cổng vẫn mở | Gắn mọi cổng vào `127.0.0.1` |
| Nginx giới hạn body 1 MB | Upload zip ăn `413 Request Entity Too Large` | `client_max_body_size 100m` |
| Nginx buffer SSE | Hỏi Agent: màn hình đứng im rồi hiện một cục | `proxy_buffering off` |
| Nginx cắt kết nối sau 60s | Lượt hỏi dài bị đứt giữa chừng, không có lỗi rõ | `proxy_read_timeout 300s` |
| Socket gid khác 0 trên Linux | Sandbox chết lượt đầu, lỗi kết nối tới daemon | `DOCKER_SOCKET_GID` bắt buộc trong file sandbox |
| Quên tắt sandbox sau demo | Không có triệu chứng gì — chính vì vậy mà nguy hiểm | Lệnh kiểm ở bước 4 mục 9; mọi lệnh hằng ngày dùng `dc` (tự tắt) |
| `python` không có trên Ubuntu | `build.sh` chết ở bước đối chiếu | `python-is-python3` (mục 5) |
| Chép đè cấu hình Nginx sau certbot | Mất HTTPS, hoặc mất `proxy_buffering off` | Ghi chú ở mục 7 và đầu file cấu hình |
| Google chỉ nhận origin HTTPS | Nút đăng nhập Google báo lỗi origin | Thêm `https://vsleuth.id.vn` vào Authorized JavaScript origins |
| `--build` bị quên | Sửa mã, `up -d`, mà vẫn thấy hành vi cũ | Ghi ở mục 9 |
| `up --build` báo `additional privileges requested: pass "--allow=fs.read=..."` | Không container nào được tạo | Gặp thật trên máy dev Windows (Compose v5 build qua bake). Chưa biết có lặp lại trên Linux không. Gặp thì build từng image bằng `docker build -t evidence-agent-<service> ./<service>` rồi `dc up -d --no-build` — xem CLAUDE.md |

---

## 11. Việc còn mở

| Việc | Vì sao quan trọng |
|---|---|
| Thuê VPS, chạy mục 3 → 8 | Chưa có gì được chạy trên máy thật |
| ~~Chọn cách đóng lỗ "đăng ký tự do + sandbox"~~ | ✅ Đã chọn: chỉ bật sandbox lúc demo (mục 2, mục 9) |
| Thêm `X-Accel-Buffering: no` vào SSE của core-backend (mục 12) | Không bắt buộc cho VPS này, chỉ cho chắc |
| Cấu hình Resend + DNS gửi thư ([docs/email.md](email.md)) | Trên VPS, người dùng không đọc được log nên link xác thực phải đi qua email. Làm được NGAY, không cần chờ VPS |

## 12. Một khác biệt giữa kế hoạch và mã, phát hiện khi viết tài liệu này

Kế hoạch §1 điểm 5 ghi *"BE gửi header `X-Accel-Buffering: no`"* cho SSE. **Mã chưa làm**
(`ConversationController`/`ConversationService`, kiểm 18/11/2026). Hiện chặn bằng
`proxy_buffering off` ở Nginx, nên VPS này không bị ảnh hưởng. Nhưng nếu sau này đặt backend sau
một proxy khác mà không cấu hình được (một số dịch vụ PaaS), SSE sẽ bị gom lại. Muốn chắc ở cả hai
phía thì thêm header đó vào response SSE của core-backend.
