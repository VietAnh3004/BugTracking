# Kế hoạch thực hiện ĐATN — Evidence-Based Program Analysis with AI Agents

> **Bản trong repo.** Bản gốc do Claude Code quản lý nằm ở
> `C:\Users\Admin\.claude\plans\d-study-datn-project-de-cuong-datn-nguy-giggly-sketch.md`.
> Chép vào đây để nằm trong git history và để phiên làm việc mới đọc được — thư mục `.claude`
> không tự động được đọc.
>
> **Phân vai giữa hai bản** (chốt 03/08/2026, sau khi phát hiện chúng đã lệch nhau):
> - Bản gốc giữ **thiết kế và lý do**: kiến trúc, mô hình dữ liệu, chuẩn code, thực nghiệm.
> - Bản này là **nguồn sự thật duy nhất về TIẾN ĐỘ** (mục dưới đây và §G) — vì nó nằm trong git
>   history nên đối chiếu được với commit thật.
>
> Sửa nội dung *kế hoạch* thì cập nhật cả hai bản. Cập nhật *tiến độ* thì chỉ sửa bản này — ghi ở
> cả hai nơi là cách chắc chắn để chúng lệch nhau.

---

## Trạng thái tiến độ

*Cập nhật: 06/08/2026*

| Giai đoạn | Trạng thái |
|---|---|
| **Tuần 1** — skeleton, CI, hạ tầng, spike | ✅ Xong |
| **Tuần 2** — vulnshop + artifact thật + khung khảo sát | ✅ Xong |
| **Tuần 3** — Unified Evidence Model | ✅ Xong (xem §D2) |
| **Tuần 4** — API contract + OpenAPI | ✅ Xong (xem §E2) |
| **Tuần 5-7** — core backend CRUD + JWT auth + frontend routing | ✅ Xong (xem §G) |
| **Tuần 8-9** — parser analyzer → Evidence Store + Docker sandbox | ✅ Xong: A, B, C, D (xem §H) |
| **Tuần 10-11** — EvidenceLinker 3 tầng, CODE_CONTEXT, pgvector, Evidence Explorer | ✅ Xong (xem §I) |
| **Tuần 12-13** — agent loop, 5 tool, CitationValidator, chat + SSE | ✅ Xong (xem §J) |

### §G — Tuần 5-7: tiến độ chi tiết (đã xong)

Kế hoạch thực thi đầy đủ (18 hạng mục A→E) nằm ở
`C:\Users\Admin\.claude\plans\ti-p-t-c-i-nh-calm-pony.md`. Tóm tắt trạng thái từng hạng mục:

| # | Hạng mục | Trạng thái |
|---|---|---|
| A | Dọn 3 endpoint `_probe` khỏi spec OpenAPI công khai | ✅ Xong |
| B1 | Migration V3: `app_user`, `refresh_token`, `snapshot_file`, `project.owner_id`, `repository_snapshot.{status,source_type,file_count,total_size_bytes,error_message}` | ✅ Xong |
| B2 | Bật `required` trong spec OpenAPI (`@Schema(requiredMode=...)` trên `ProjectResponse`/`CreateProjectRequest`/`PageResponse`) | ✅ Xong |
| B3 | Spring Security 2 filter chain (JWT Bearer + service token cho `/api/v1/tools/**`) + CORS qua filter | ✅ Xong |
| B4 | Đăng ký/đăng nhập/`/auth/me` | ✅ Xong |
| B5 | Refresh token xoay vòng + phát hiện tái sử dụng | ✅ Xong |
| B6 | Gán owner cho project + authorization + migration V4 (backfill + `SET NOT NULL` cho `project.owner_id`) | ✅ Xong |
| C1 | MinIO object storage client (`ObjectStorageClient` + `MinioObjectStorageClient`) | ✅ Xong |
| C2 | Upload zip an toàn (`ZipExtractor`, `SnapshotService`, chống zip-slip/zip-bomb) + lập chỉ mục `snapshot_file` | ✅ Xong |
| C3 | API duyệt cây file + đọc nội dung theo khoảng dòng (`SnapshotQueryService`, 4 endpoint GET) | ✅ Xong |
| D1-D7 | Frontend: Tailwind v4, routing, auth + ADR 0006, single-flight refresh, upload, cây file + source viewer | ✅ Xong |
| E1-E2 | Kiểm chứng end-to-end thật, ADR 0007, cập nhật tài liệu | ✅ Xong |

**E — kiểm chứng end-to-end thật (03/08/2026), không phải suy luận từ test:**

Chạy hạ tầng thật (Postgres + MinIO qua docker compose) + backend thật, kiểm bằng `curl`:

| # | Kiểm | Kết quả |
|---|---|---|
| 1 | Preflight `OPTIONS` không kèm token | 200 + đủ header CORS |
| 2 | Response 401 vẫn có `Access-Control-Allow-Origin` | Có (thiếu thì FE thấy "CORS error" thay vì 401) |
| 3 | Đăng ký → đăng nhập → tạo dự án | OK |
| 4 | Upload `case-study/vulnshop` thật (35 file) | `READY`, `fileCount=35`, 72.657 byte |
| 5 | `GET /files` | 66 mục = 31 thư mục tự sinh + 35 file |
| 6 | Object thật trên MinIO | đúng 35 object dưới `snapshots/{id}/`, giữ cấu trúc thư mục |
| 7 | Đọc file: `totalLines` | 53, khớp `wc -l` của file gốc |
| 8 | Đọc khoảng dòng 10-14 | khớp từng dòng với file trên đĩa (1-based đúng) |
| 9 | Người dùng khác truy cập project/snapshot/files/upload | 404 ở cả bốn (không phải 403) |
| 10 | Dùng lại refresh token đã xoay | 401 `refresh-token-reused` |
| 11 | Token **mới nhất** sau khi phát hiện tái sử dụng | 401 — cả họ bị thu hồi, đúng thiết kế |

**Phát hiện một bug thật nhờ bước này** (không test nào bắt được vì nó chỉ xuất hiện khi thiếu biến
môi trường thật): thiếu `CORS_ORIGINS` thì ứng dụng **vẫn khởi động** và báo `UP`, với origin là
chuỗi literal `"${CORS_ORIGINS}"`. Nguyên nhân: `@ConfigurationProperties` dùng placeholder resolver
**lenient** (khác `@Value` strict), và `@NotBlank` chấp nhận `"${CORS_ORIGINS}"` vì nó không rỗng.
Các biến khác chỉ *tình cờ* fail-fast nhờ validation tầng dưới. Đã sửa bằng
`UnresolvedPlaceholderValidator` — xem CLAUDE.md.

**Chưa kiểm được bằng công cụ dòng lệnh** (cần trình duyệt thật, nhưng đã có test tự động canh):
F5 tại deep link không nhảy về `/login` (`ProtectedRoute.test.tsx`), và ba request song song chỉ
gọi `/auth/refresh` đúng một lần (`apiClientRefresh.test.ts`, đã mutation-test).

**C2 — ghi chú kỹ thuật đáng chú ý:**
- Ranh giới transaction tách thành 3 lớp: `SnapshotPersistenceService.createPending` (REQUIRES_NEW,
  bia mộ commit ngay) → giải nén + ghi MinIO NGOÀI transaction nào → `finalizeReady` (một
  transaction ngắn, chỉ `saveAll` + đổi status). `markFailed` cũng REQUIRES_NEW. Ba method này
  BẮT BUỘC nằm khác class với `SnapshotService.upload()` — gọi qua `this.` sẽ là self-invocation,
  bỏ qua proxy Spring, khiến `REQUIRES_NEW` không hoạt động (không lỗi, chỉ lặng lẽ sai) — cùng họ
  bug với bài học `@Transactional(noRollbackFor=...)` đã ghi trong `CLAUDE.md`.
- `SnapshotService.upload()` phải dùng giá trị `RepositorySnapshot` do `finalizeReady(...)` TRẢ VỀ
  làm response, không phải biến `pending` tạo từ `createPending(...)` — bug tự phát hiện lúc code
  review: `pending` là entity từ một transaction khác đã commit và detach, không tự phản ánh
  `status=READY`/`fileCount` được set ở transaction sau.
- Tự bắt được 2 bug lúc viết `ZipExtractorTest`: (1) `normalizePath` ban đầu CẮT dấu `/` đầu thay
  vì TỪ CHỐI — biến `/etc/passwd` thành path hợp lệ `etc/passwd`, vô hiệu hoá phòng thủ zip-slip
  cho đúng trường hợp kinh điển nhất; (2) `java.util.zip.ZipOutputStream` tự chặn hai entry cùng
  tên ở lúc BUILD (`ZipException`), nên test "hai entry trùng path" phải dùng hai path khác nhau
  theo byte thô nhưng trùng nhau SAU chuẩn hoá (`dir/a.txt` vs `dir\a.txt`) mới chạm được nhánh
  code `seenPaths` — kịch bản tấn công thật hơn dự tính ban đầu.

**C3 — ghi chú kỹ thuật đáng chú ý:**
- Bốn endpoint: `GET /projects/{id}/snapshots` (phân trang), `GET /snapshots/{id}` (metadata),
  `GET /snapshots/{id}/files` (danh sách PHẲNG, FE tự dựng cây), `GET /snapshots/{id}/file?path=&fromLine=&toLine=`.
- `path` là **query param**, không phải path variable wildcard — đường dẫn chứa `/` nên wildcard
  buộc phải tự giải mã phần đuôi URL, mở lại đúng lớp lỗi encoding/traversal mà `ZipExtractor` vừa
  chặn ở C2. Tra bằng `UNIQUE (snapshot_id, path)` nên chỉ khớp chính xác.
- `SnapshotQueryService.splitLines()` **phải khớp từng chi tiết** với `ZipExtractor.countLines()`:
  chỉ tách theo `'\n'`, cố tình KHÔNG dùng `String.lines()` (nó còn tách theo `'\r'` đơn lẻ, cho
  số dòng khác bộ đếm lúc upload trên file dùng CR cũ). Hai đoạn code đếm dòng khác nhau = hai cơ
  hội lệch nhau, và lệch một dòng ở đây làm source viewer highlight sai **mọi** evidence. Có canh
  bằng log WARN khi `line_count` trong chỉ mục khác số dòng đọc được thật.
- `GET /snapshots/{id}` **không** đòi `READY` (khác `/files` và `/file`, hai cái đó trả 409
  `snapshot-not-ready`): đây chính là endpoint để FE đọc `status`/`errorMessage` của một snapshot
  `FAILED` — bắt nó đòi READY thì không bao giờ xem được lý do hỏng.
- `toLine = fromLine + số dòng trả về - 1` là công thức duy nhất, không có nhánh đặc biệt: lát rỗng
  tự nhiên cho ra `toLine = fromLine - 1` (file rỗng, `fromLine=1` ⇒ `toLine=0`).

**Điều chỉnh quan trọng so với plan gốc** (đọc trước khi làm B6, kẻo backfill hai lần):
`project.owner_id` được thêm ở V3 dưới dạng **nullable**, KHÔNG `NOT NULL` như plan gốc dự định —
vì tại thời điểm viết V3 chưa có Spring Security nên `ProjectService.create()` chưa có cách nào
biết "user hiện tại" để gán owner. Việc backfill dữ liệu cũ (tài khoản mồ côi vô hiệu hoá) +
`ALTER COLUMN owner_id SET NOT NULL` dời sang **migration V4** ở bước B6, đúng lúc auth đã tồn
tại và `ProjectService`/`ProjectController` bắt buộc nhận `ownerId` ở mọi lối vào. Đây là ví dụ
cụ thể cho nguyên tắc "mỗi commit trên `main` phải xanh độc lập" — không gộp một cột NOT NULL với
tính năng chưa tồn tại để gán giá trị cho nó.

Xem `git log --oneline -10` để biết commit cụ thể đã xong; message ghi chi tiết lý do.

### Việc còn dở của tuần 2 (không chặn tuần 3)

- **Danh sách tài liệu mới tìm được 16/25 bài**, đã xác minh link thật (mục 8 của
  `docs/related-work.md`), nhưng **chưa đọc và chưa điền template** (bài toán, phương pháp, số
  liệu, khoảng trống). Đây là việc của người dùng, không phải việc code. Còn thiếu ~9 bài, chủ
  yếu ở chủ đề 1 (baseline ML xếp hạng cảnh báo) và mở rộng cho chủ đề 3–5.

Đã xử lý xong:

- ✅ **CI không kiểm vulnshop** — thêm `expected-analysis.json` + cờ `summarize.py --check` +
  job CI `case-study`. Lúc kiểm chứng còn bắt được một lỗi thật: `run-analysis.sh` thiếu
  `mvn clean` khiến JaCoCo đếm sai độ phủ giữa các lần chạy — đã vá.
- ✅ **`.gitleaks.toml`** — đã xoá. Kiểm chứng lại bằng thực nghiệm: gitleaks không bắt credential
  gieo trong vulnshop kể cả khi hoàn toàn không có file config nào, nên allowlist chưa từng có
  tác dụng thật. CI không tham chiếu file này (job `secrets` không truyền `--config`) nên xoá
  không ảnh hưởng gì.

### Việc đã làm khác kế hoạch gốc

- Case study `vulnshop` **đảo từ tuần 5–7 lên tuần 2** để thiết kế Evidence Model dựa trên
  artifact thật. Xem §7bis.
- Bộ rule Semgrep được vendor vào `case-study/semgrep-rules/` để phân tích tái lập và chạy offline.

---

### §H — Tuần 8-9: tiến độ chi tiết (đã xong)

Kế hoạch thực thi đầy đủ ở `C:\Users\Admin\.claude\plans\ti-p-t-c-i-nh-calm-pony.md`. Bốn giai
đoạn A→D; **sandbox xếp CUỐI có chủ đích** vì nó rủi ro nhất mà lại không phải đóng góp của luận
văn — nếu nó trượt, mọi thứ khác vẫn xong và vẫn demo được.

| # | Hạng mục | Trạng thái |
|---|---|---|
| A1 | `check-evidence-model.py` đối chiếu hai chiều **theo từng bảng**, đọc mọi file migration | ✅ Xong |
| A2 | Sửa nguyên tắc chuẩn hoá path Semgrep trong `evidence-model.md`; V3 → V5 | ✅ Xong |
| A3 | **Migration V5**: 5 bảng thô + `unresolved_location_count` + 5 entity + 4 enum + provenance | ✅ Xong |
| A4 | `SnapshotPathResolver` — khớp hậu tố dài nhất, duy nhất | ✅ Xong |
| A5 | `SpotbugsXmlAdapter` + `XmlParsers` | ✅ Xong |
| A6 | `SemgrepSarifAdapter` | ✅ Xong |
| A7 | `SurefireResultCollector` + `StackTraceParser` | ✅ Xong |
| A8 | `JacocoCoverageCollector` | ✅ Xong |
| B1 | `EvidenceMaterializer` cấp `seq_in_run`, ghi batch bảy bảng | ✅ Xong |
| B2 | `AnalyzerRegistry` nhận dạng theo nội dung + `SurefireArchiveReader` | ✅ Xong |
| B3 | `AnalysisRunService` + `AnalysisRunPersistenceService` (quyết định trạng thái) | ✅ Xong |
| B4 | `POST /snapshots/{id}/analysis-runs` nạp artifact | ✅ Xong |
| B5 | Endpoint đọc analysis run + danh sách cảnh báo cho UI | ✅ Xong |
| C | Màn hình danh sách cảnh báo (frontend) | ✅ Xong |
| D1 | Spike SpotBugs CLI + **ADR 0008** | ✅ Xong |
| D2 | Hai image analyzer + script dựng-và-đối-chiếu | ✅ Xong |
| D3 | V6: cột sandbox + ràng buộc canary cho `analysis_run` | ✅ Xong |
| D4 | Cổng `SandboxRunner` + dựng cây mã nguồn thành tar | ✅ Xong |
| D5 | Kiểm tính tỉnh táo của analyzer + canary (phía backend) | ✅ Xong |
| D6 | `DockerSandboxRunner` trên `docker-java` | ✅ Xong |
| D7 | Điều phối chạy phân tích bất đồng bộ + gắn Docker socket | ✅ Xong |

Sau A: **216 test xanh**, 4 parser đọc được đủ 4 artifact thật, 75 dòng provenance cho 7 bảng khớp
hai chiều với SQL.

Sau B: **270 test backend xanh**. Một lệnh `POST` với 4 artifact thật của vulnshop cho ra đúng
**90 `evidence_record`** = 28 warning SpotBugs + 5 Semgrep + 33 `test_case` + 5 `STACK_TRACE` + 19
coverage, kèm 33 `warning` · 38 `warning_location` · 44 `stack_frame` · 171 `coverage_line` · 73
`coverage_method`, và **`unresolved_location_count = 0`** trên cây nguồn thật.

Sau C: **88 test frontend xanh**. Deliverable gốc của tuần 8-9 — *"1 lệnh → ra warning + kết quả
test trong DB"* — đã đạt, và nhìn thấy được bằng mắt.

**Kế hoạch gốc chia giai đoạn B thành 3 commit; thực tế thành 5.** Tách thêm hai chỗ vì mỗi chỗ
giải quyết một rủi ro riêng biệt, gộp vào sẽ không kiểm được độc lập: (B2) nhận dạng parser theo
NỘI DUNG chứ không theo tên file + chốt thứ tự nạp, và (B3) tầng điều phối quyết định trạng thái
run trước khi chạm DB.

#### Hai chỗ kế hoạch gốc ghi sai, đã sửa

- **Số hiệu migration**: kế hoạch ghi `V3__evidence_model_v3.sql`, nhưng tuần 5-7 đã dùng mất V3
  và V4 → số thật là **`V5`**. `docs/evidence-model.md` và ADR 0004 đã có ghi chú đối chiếu.
- **"≥60 warning"**: vulnshop thật chỉ có **33** (28 SpotBugs + 5 Semgrep). Con số ≥60 không có
  căn cứ; tiêu chí đổi thành *khớp `case-study/vulnshop/expected-analysis.json`*, vốn là nguồn sự
  thật duy nhất và đã được CI đối chiếu sẵn.

#### Ba lỗi tồn đọng mà giai đoạn A lộ ra

Đều là lỗi có sẵn trong repo, không phải do đợt này gây ra:

1. **Một cột chưa từng được kiểm.** `evidence_record.analysis_run_id` (V2 dòng 68) chỉ có *một*
   dấu cách sau tên cột, mà regex của `check-evidence-model.py` đòi ≥2 — nên nó chưa bao giờ được
   trích ra khỏi SQL. Kiểm một chiều cũ (chỉ báo khi cột SQL thiếu provenance) không thể lộ ra.
2. **`resolve_semgrep` là stub**: bỏ qua tham số, luôn trả `results[0].ruleId` → mọi dòng
   provenance Semgrep "khớp" một cách vô nghĩa.
3. **`evidence_link.method` ghi sai trong tài liệu**: giá trị `COVERAGE` chưa bao giờ tồn tại
   trong CHECK của V2 lẫn enum `EvidenceLinkMethod`; giá trị đúng là `METHOD_AGGREGATE`.

#### Sai lệch có chủ đích so với kế hoạch

- **`EvidenceDraft.summary()` dùng đường dẫn NGUYÊN VĂN**, không phải đường dẫn đã chuẩn hoá. Kế
  hoạch mô tả summary hiển thị "(chưa đối chiếu được với snapshot)" khi path NULL, nhưng điều đó
  buộc parser phải biết kết quả chuẩn hoá — tức là hết thuần hàm và hết unit-test được bằng
  artifact thật. Trạng thái đối chiếu nằm ở `content.path_match_kind` và UI hiển thị thành nhãn
  riêng (giai đoạn C).
- **`PathMatchKind` không lên DB thành cột riêng** — sẽ phải thêm một CHECK + một method
  `EnumCheckConstraintTest` cho thứ chưa có consumer. Nó nằm trong `content`, đủ để tuần 15 đo tỉ
  lệ "chỉ khớp được bằng tên file".

#### Nợ kỹ thuật của giai đoạn A — **đã trả xong**

- ~~`SnapshotFileRepository.findFilePathsBySnapshotId` chưa có caller~~ → `AnalysisRunService` là
  caller đầu tiên (B3), có test.
- ~~`TestFixtures` chưa có builder cho bảng mới~~ → thêm `readySnapshotWithFiles` và `reloadRun`
  (B1/B3). Không thêm builder cho `test_case`/`stack_frame`/`coverage_*` vì hoá ra không cần: test
  materialize dữ liệu THẬT rồi đọc lại, không dựng hàng giả.
- ~~Chưa có registry chọn adapter/collector~~ → `AnalyzerRegistry` (B2).
- Ngoài ra `EvidenceQueryService.requireRunOwnedBy` (viết từ tuần 5-7, chưa từng có caller) nay đã
  có caller đầu tiên là `AnalysisRunQueryService` — đúng như Javadoc của nó dự liệu.

#### Bốn phát hiện của giai đoạn B-C

1. **`short_id` KHÔNG tái lập được giữa hai run**, vì ký tự cuối băm từ UUID ngẫu nhiên của từng
   hàng. Đó là thiết kế chống LLM bịa ID, không phải khiếm khuyết — nhưng nó có nghĩa là thí
   nghiệm tái lập ở tuần 15-16 phải so theo `seq_in_run` + nội dung bằng chứng, **không so theo
   Evidence ID**. Ghi thành comment trong `EvidenceMaterializerTest`.
2. **`ZipExtractor` không dùng lại được cho zip báo cáo Surefire**: nó lọc bỏ mọi entry dưới thư
   mục tên `target`, mà báo cáo Surefire nằm đúng ở `target/surefire-reports/`. Dùng lại sẽ cho ra
   "0 test" không kèm lỗi nào. Đã tách `SurefireArchiveReader` riêng, dùng chung giới hạn
   chống zip-bomb của `UploadProperties`.
3. **Nhánh `SUCCEEDED_CLEAN` hiện chưa chạm tới được**, vì `SpotbugsXmlAdapter` từ chối artifact
   0 cảnh báo ngay ở tầng parser. Nhánh đó dành cho **giai đoạn D**, nơi bốn tầng kiểm tính tỉnh
   táo (canary, tổng số class đã quét, phiên bản JDK, plugin đã nạp) mới đủ dữ kiện phân biệt "dự
   án sạch thật" với "analyzer im lặng hỏng" — điều mà một artifact do người dùng tự tải lên không
   cho biết. Đã ghi thành một test có tên nói rõ điều này.
4. **Vitest đỏ toàn bộ vì quá nhiều worker**, với ĐÚNG thông điệp lỗi mà happy-dom đã dựng lên để
   tránh — chi tiết và cách phân biệt hai nguyên nhân ghi ở `CLAUDE.md`.

#### Giai đoạn D — hai kết quả đo được, và một chỗ kế hoạch phải sửa

**Spike SpotBugs CLI (D1) thành công dứt khoát.** CLI độc lập cho **28/28** `BugInstance` trùng
khít Maven plugin — cùng bộ `(type, vị trí)`, cùng `total_classes`, cùng plugin đã nạp. Không cần
phương án lùi. Chi tiết ở [docs/spike-spotbugs-cli.md](spike-spotbugs-cli.md).

Phát hiện phụ **quan trọng hơn kết quả chính**: bỏ `-auxclasspath` thì SpotBugs mất 3/28 cảnh báo
mà **vẫn exit 0, XML vẫn hợp lệ, `total_classes` vẫn đúng 20**. Ca này lọt qua cả tầng kiểm số 2
lẫn canary — bằng chứng cụ thể rằng bốn tầng chống 0-cảnh-báo không thay thế được nhau.

**Sandbox (D2) đạt tiêu chí nghiệm thu**: chạy thật qua `docker create`/`cp`/`start`, không bind
mount, cho ra `28 SpotBugs · 5 Semgrep · 33 test (5 hỏng) · 82/171 dòng phủ` — **trùng khít**
`run-analysis.sh` chạy trên host.

**Chỗ ADR 0008 phải sửa ngay sau khi chốt**: kế hoạch ghi `--network=none` cho mọi thứ kèm
`mvn -o`. Điều đó **không sống sót khi viết image** — Maven offline chỉ chạy được nếu mọi plugin
và phụ thuộc của repo người dùng đã nằm sẵn trong seed, mà với repo bất kỳ là bất khả. Đổi thành
**hai pha**: `resolve` có mạng (chỉ phân giải phụ thuộc, không chạy test) và `analyze` không mạng
(chạy test + SpotBugs). Ranh giới giữ được điều quan trọng nhất: **pha nguy hiểm là pha không có
mạng**.

Năm cạm bẫy khi dựng image ghi ở [infra/analyzer/README.md](../infra/analyzer/README.md); bốn cái
chung nhất đã đưa vào `CLAUDE.md`.

**Một dự đoán của kế hoạch KHÔNG xảy ra.** Kế hoạch cảnh báo V6 sẽ làm đỏ
`AnalysisRunNotSilentlyEmptyTest` (test đó insert `SUCCEEDED_CLEAN` không có `canary_ok`) và dặn
sửa test trong cùng commit với migration. Không đỏ: `ck_run_sandbox_needs_canary` chỉ áp cho
`DOCKER_SANDBOX`, và `artifact_source` mặc định `MANUAL_UPLOAD`, nên mọi hàng có sẵn từ trước V6
vẫn hợp lệ. Đã thêm một test canh đúng điều đó thay vì sửa test cũ.

**Ba lỗi mà chỉ test chạy container thật bắt được** (D6) — cả ba đều lọt qua toàn bộ unit test:

1. **Tar thiếu entry thư mục.** Không có chúng thì `copyArchiveToContainer` để Docker tự tạo thư
   mục trung gian, và Docker tạo chúng thuộc về **root**; container non-root không tạo được
   `target/` và Maven chết với *"Cannot create resource output directory"*. Phép kiểm "tar có đủ
   file không" **vẫn xanh** suốt thời gian lỗi tồn tại: danh sách file đúng, chỉ thiếu thứ không
   ai nghĩ tới.
2. **tmpfs mặc định của Docker thuộc root với mode 0755**, nên container non-root không ghi được
   vào chính thư mục vừa được mount cho nó. Phải khai `mode=1777`, và phải khai cho HOME của **cả
   hai** image.
3. **Tôi tự làm một lỗ hổng đúng loại đang chống.** Semgrep trả exit code khác 0 khi *có* finding
   nên tôi nới lỏng exit code cho nó — và lần nới lỏng đó nuốt luôn một lần Semgrep **crash** vì
   `--read-only`. Cả pipeline "thành công" với đủ artifact của SpotBugs, chỉ thiếu `semgrep.sarif`.
   Bài học chung: **mỗi lần nới lỏng một phép kiểm phải bù bằng một phép kiểm khác**, không được
   bỏ trống.

#### Rà soát cuối tuần 8-9 — một khiếm khuyết thật, và bốn món còn nợ

Rà bằng cách đối chiếu tài liệu với mã nguồn, không dựa vào trí nhớ:

**`SandboxReaper` được nhắc trong 3 chỗ code + ADR nhưng KHÔNG tồn tại.** Nhãn `evidence.run-id`
vẫn được gắn đầy đủ, nhưng không có gì đọc chúng: nếu JVM bị giết giữa chừng (deploy lại, OOM),
container + volume rò **vĩnh viễn** — mỗi lần một container 2 GB và một volume chứa cả cây mã
nguồn người dùng. Khối `finally` của `DockerSandboxRunner` chỉ chạy khi tiến trình còn sống. **Đã
hiện thực**, kèm hai test chạy Docker thật; điểm khó không phải "dọn được rác" mà là "dọn rác cũ
mà KHÔNG đụng vào run đang chạy" — vốn mang đúng cùng một nhãn. Phân biệt bằng thời gian ân hạn
1 giờ + bỏ qua container `running`.

Bốn món còn nợ, đều **có chủ đích**, không phải quên:

| Món | Vì sao hoãn |
|---|---|
| Chưa có nút "chạy sandbox" trên UI | Endpoint và kiểu TypeScript đã có; nút bấm chỉ có nghĩa khi vận hành đã bật socket, mà đó là quyết định của người vận hành chứ không phải mặc định |
| Chưa có job CI cho sandbox | Job đó phải dựng hai image (~5 phút) và cần Docker trên runner. Thêm một file YAML **không chạy thử được** vào repo là tự tạo một thứ "trông như đang kiểm" — đúng loại hỏng-im-lặng đang chống. `./infra/analyzer/build.sh` đã tự đối chiếu, chạy tay được ngay |
| `AnalyzerRegistry.adapterFor`/`collectorFor` chỉ có caller trong test | Mã production chọn parser bằng `detect()` (theo nội dung). Giữ lại vì giai đoạn D có thể cần chọn theo enum; nếu tới tuần 12 vẫn không ai gọi thì xoá |
| `check-evidence-model.py` không phủ `analysis_run` | Phạm vi của nó là bảy bảng **bằng chứng**; cột của `analysis_run` không đến từ artifact analyzer. Năm cột V6 nay được ghi ở mục 12bis của `evidence-model.md` |

**Kết thúc giai đoạn D**: `POST /api/v1/snapshots/{id}/analysis-runs/sandbox` trả 202 kèm run
`RUNNING`; FE theo dõi qua `GET /analysis-runs/{id}`. Sandbox **tắt mặc định** và mount Docker
socket để **comment** trong `docker-compose.yml` — bật sandbox là một quyết định vận hành có ý
thức, không phải thứ bật được bằng cách đổi một biến môi trường, vì mount socket đó tương đương
cấp quyền root trên máy chủ.

#### Sai lệch có chủ đích ở giai đoạn B-C

- **Endpoint nạp artifact dùng MỘT trường lặp `artifacts`**, không phải bốn trường có tên
  `spotbugs`/`semgrep`/`surefire`/`jacoco` như kế hoạch mô tả. Vì việc chọn parser hoàn toàn dựa
  vào nội dung file, bốn trường có tên sẽ là một lời hứa sai.
- **Route màn hình cảnh báo đặt NGOÀI `snapshots/:snapshotId`** dù URL lồng nhau: bảng cảnh báo
  chiếm toàn bộ bề ngang, không cần cây file bên trái.

---

### §I — Tuần 10-11: tiến độ chi tiết (đã xong)

Deliverable của lộ trình — *"mở 1 warning → thấy code context + test liên quan + coverage"* — đã
đạt, và nhìn thấy được bằng mắt. Quyết định kiến trúc ở
[ADR 0009](adr/0009-lien-ket-bang-chung-ba-tang.md).

| # | Hạng mục | Trạng thái |
|---|---|---|
| 1 | Sửa `relatedEvidence` ghi đè mất link khi một cặp có nhiều quan hệ | ✅ Xong |
| 2 | V7: `evidence_embedding`, bộ đếm linker, `evidence_link.analysis_run_id` | ✅ Xong |
| 3 | CODE_CONTEXT bằng JavaParser + EvidenceLinker tầng 1 (`LOCATED_IN`) | ✅ Xong |
| 4 | EvidenceLinker tầng 3 (`SUPPORTS` theo stack frame) | ✅ Xong |
| 5 | Gieo TP-16 để vulnshop có một ca `SUPPORTS` thật | ✅ Xong |
| 6 | EvidenceLinker tầng 2 (`COVERS` + quy ước tên) + `CONTRADICTS` | ✅ Xong |
| 7 | REST UI chi tiết evidence | ✅ Xong |
| 8 | Endpoint chạy lại linker | ✅ Xong |
| 9-11 | Frontend: bề ngang, deep-link khoảng dòng, Evidence Explorer | ✅ Xong |

**Số liệu thật trên vulnshop**: 124 evidence · 103/103 vị trí đối chiếu được · 30 ngữ cảnh mã nguồn
từ 15 file · **132 liên kết, 0 cảnh báo trơ trọi**.

| Quan hệ | | Phương pháp | |
|---|---|---|---|
| `LOCATED_IN` | 36 | `LINE_EXACT` | 67 |
| `COVERS` | 36 | `METHOD_AGGREGATE` | 46 |
| `SUPPORTS` | 8 | `NAMING_CONVENTION` | 13 |
| `CONTRADICTS` | 52 | `RANGE_OVERLAP` · `STACK_FRAME` · `LINE_NEAREST_EXECUTABLE` | 4 · 1 · 1 |

Tỉ lệ liên kết chỉ dựa vào quy ước tên: **13/132 ≈ 10%** — con số tuần 15 phải báo cáo, nay đọc thẳng
từ `analysis_run.link_counts_by_method` thay vì tính lại bằng SQL ad-hoc.

384 test backend · 109 test frontend (14 file).

#### Bốn chỗ dữ liệu thật buộc đi khác kế hoạch

1. **Tầng 3 phục hồi 0%, không phải ~40%.** `evidence-model.md` phát hiện 2 ghi "3/5 test hỏng
   không có frame production", ngụ ý 2/5 còn lại dùng được. Thực tế cả hai frame đó trỏ vào
   `CartService:25` và `UserService:30` — **không analyzer nào cảnh báo trên hai file đó**. Có frame
   production mới là điều kiện *cần*; frame còn phải rơi vào một dòng đã có cảnh báo.
2. **Tầng 2 mới là tầng gánh chính**, không phải phương án lùi: 59/132 liên kết đến từ nó.
3. **`CONTRADICTS` nhiều gấp hơn 6 lần `SUPPORTS`.** Xem mục rủi ro dưới đây.
4. **Semgrep không khai tên lớp** nên tầng 2 phải lấy từ `symbol` của CODE_CONTEXT bao quanh.
5. **Quy ước tên phải sinh DANH SÁCH ứng viên.** `UserRepositoryIntegrationTest` bỏ hậu tố ra
   `UserRepositoryIntegration` — lớp không tồn tại — nên bằng chứng kiểm thử của `findByUsername`,
   cạm bẫy trung tâm của case study, biến mất trong im lặng. Chỉ lộ ra khi viết test trên dữ liệu
   thật.

#### Rà soát cuối tuần 10-11 — ba việc phải làm nốt

Rà bằng cách đối chiếu kế hoạch với mã nguồn, không dựa vào trí nhớ:

| Phát hiện | Xử lý |
|---|---|
| Bộ đếm linker có trong DB nhưng **không có trong `AnalysisRunResponse`** — cơ chế chống hỏng-im-lặng mới có hai phần ba, muốn biết 30/34 cảnh báo trơ trọi phải mở DB | Đưa ra API và hiện trên màn hình cảnh báo, cạnh tỉ lệ vị trí đối chiếu được |
| `getCodeContext` vẫn còn nhánh fallback theo vị trí — trả item với `relation`/`method` NULL, **che một linker hỏng bằng dữ liệu trông hợp lý**. Quyết định bỏ đã ghi trong kế hoạch nhưng chưa thực thi | Bỏ hẳn; không có liên kết ⇒ trả `[]` và để bộ đếm nói ra vì sao |
| Không test nào khoá số liệu linker trên dữ liệu thật, cũng không test nào chứng minh 5 tool đã hết rỗng | `EvidenceLinkerVulnshopTest` (9 ca) + `TestClassNameConventionTest` (10 ca) + ca mới trong `EvidenceToolControllerTest` |

Chính test trên dữ liệu thật lôi ra lỗi số 5 ở trên, và lôi ra hai assertion ngây thơ của chính nó
(một cái giả định mọi ngữ cảnh của cảnh báo ở `PasswordHasher` đều thuộc file đó — sai vì cảnh báo
nhiều vị trí; một cái chạy `SELECT 1` rồi so hai số bằng nhau nên luôn xanh).

#### Một khiếm khuyết của case study, đã sửa

Tới hết tuần 8-9, vulnshop **không có bất kỳ trường hợp nào** mà một cảnh báo tĩnh và một stack
frame của test hỏng cùng trỏ về đúng một dòng — nên quan hệ `SUPPORTS` suy từ stack frame, loại bằng
chứng thuyết phục nhất cho verdict `LIKELY_TRUE_POSITIVE`, không có mẫu nào để demo ở tuần 12-13 hay
để đo ở tuần 15.

Đã gieo **TP-16** (`UserService.displayNameOf:66`): cùng lỗi NPE với TP-11 nhưng phép kiểm null nằm
trong cùng method, nên SpotBugs bắt được. Cặp TP-11 ↔ TP-16 còn có giá trị riêng cho báo cáo: hai
lỗi **giống hệt nhau về bản chất, chỉ khác cách viết**, mà một cái bị bắt còn một cái không.

Số liệu vulnshop đổi theo: `33 → 35` test (`5 → 6` hỏng) · `28 → 29` cảnh báo SpotBugs ·
`82/171 → 85/174` dòng phủ. Lịch sử thay đổi ghi trong `expected-analysis.json`.

Việc này cũng lộ ra hai lỗi tồn đọng: `SEEDED.md` **tự mâu thuẫn** từ tuần 2 (bảng mục 5 khai
`UserService:30` là ca `SUPPORTS` trong khi TP-11 khai chính nó là "không công cụ nào bắt"), và
`infra/analyzer/build.sh` **không sinh lại được** bộ mẫu sandbox (README bảo copy từ thư mục tạm mà
thư mục đó bị `trap cleanup EXIT` xoá) — nay có cờ `--emit-samples`.

#### Rủi ro cần theo dõi ở tuần 12-13

`CONTRADICTS` (52) nhiều gấp hơn 6 lần `SUPPORTS` (8), vì mỗi test xanh trong lớp test khớp đều sinh
một liên kết. Về dữ liệu là đúng — mỗi test là một bằng chứng riêng — nhưng một LLM đọc 52 dòng
*"test xanh phủ dòng này"* rất dễ nghiêng về `LIKELY_FALSE_POSITIVE`, **đúng cái bẫy vulnshop cố ý
gài** (`findByUsername` có SQL injection thật mà test vẫn xanh).

Hai lớp chống đã có: weight của `CONTRADICTS` luôn thấp hơn `SUPPORTS`, và `rationale` viết thẳng
câu cảnh báo vào chính dữ liệu để LLM đọc chứ không phải tự suy. Nếu chưa đủ thì phải gom nhóm hoặc
cắt theo weight ở tầng prompt builder.

#### Món còn nợ có chủ đích

| Món | Vì sao hoãn |
|---|---|
| Chưa sinh vector embedding | Số chiều do mô hình quyết định, mà mô hình chỉ chốt ở tuần 12-13 khi có agent loop để đo retrieval có tốt hơn thật không. Schema và test tích hợp đã sẵn |
| Chưa có nút "chạy lại liên kết" trên UI | Endpoint đã xong; nút chỉ có nghĩa khi đã có người sửa heuristic, tức là từ tuần 12-13 |
| Chưa tô màu cú pháp trong khối mã | Prop `render` của `CodeLine` vẫn để trống: "vùng bằng chứng" là nhiều DÒNG, mà mô hình dữ liệu không có `start_col`/`end_col`. Dùng `render` bây giờ là bịa ranh giới cột |

---

### §J — Tuần 12-13: tiến độ chi tiết (đã xong)

Deliverable của lộ trình — *"hỏi 'test nào liên quan?' → trả lời có citation click được"* — đã đạt.
Quyết định kiến trúc ở [ADR 0010](adr/0010-vong-lap-agent-va-hau-kiem-trich-dan.md).

| # | Hạng mục | Trạng thái |
|---|---|---|
| 1 | V8: `conversation`, `message`, `llm_trace`, trả nợ FK `claim.message_id` | ✅ Xong |
| 2 | `CitationValidator` ở core-backend, độc lập với LLM | ✅ Xong |
| 3 | agent-service: 5 evidence tool gọi ngược core-backend | ✅ Xong |
| 4 | `AnthropicProvider` + `ScriptedProvider` (chạy end-to-end không cần khoá API) | ✅ Xong |
| 5 | Agent loop tool-call + prompt Jinja2 version hoá + structured output | ✅ Xong |
| 6 | `POST /analyze` stream tiến trình qua SSE | ✅ Xong |
| 7 | core-backend: hội thoại, gọi agent, hậu kiểm, ghi `claim`/`claim_citation`/`llm_trace` | ✅ Xong |
| 8 | SSE proxy ra trình duyệt | ✅ Xong |
| 9 | Frontend: trang hỏi đáp + trích dẫn bấm được sang Evidence Explorer | ✅ Xong |

**Luồng một câu trả lời**, và chỗ nào của nó là ràng buộc chứ không phải quy ước:

```
trình duyệt ──SSE──> core-backend ──SSE──> agent-service ──5 tool──> Evidence Store
                          │                     │
                    CitationValidator      không có kết nối DB
```

agent-service **không thể** tự tuyên bố một trích dẫn là hợp lệ — nó không có đường tới Evidence
Store. Đó là thứ biến "hậu kiểm độc lập với LLM" từ một câu trong đề cương thành một ràng buộc
kiến trúc.

#### Ba chỗ dữ liệu và API thật buộc đi khác dự tính

1. **`LLM_TEMPERATURE=0.0` trong `.env.example` sẽ làm MỌI request 400.** Đặt từ tuần 1 với lý do
   "để kết quả thực nghiệm bớt dao động", nhưng các model từ Claude Opus 4.7 trở đi — gồm cả
   `claude-sonnet-5` đang khai — từ chối tham số này. Tức là ngay khi bật provider thật, không
   request nào chạy. Đã đổi mặc định thành "không gửi" và cho `AnthropicProvider` nổ lúc khởi
   động nếu thấy biến này. Độ ổn định vẫn báo cáo được bằng cách chạy lặp rồi đo phương sai (§6).
2. **Ép định dạng đầu ra và cho phép gọi tool loại trừ nhau.** Ép nộp nghĩa là buộc model gọi
   đúng một tool, nên bật từ vòng đầu thì nó không bao giờ truy xuất được bằng chứng nào. Bước
   nộp phải là một lượt gọi RIÊNG sau vòng lặp.
3. **Trình duyệt không dùng được `EventSource`** — nó không gửi được header tuỳ ý nên không gắn
   được `Authorization: Bearer`, và chỉ làm được `GET`. Phải tự đọc `ReadableStream` bằng `fetch`.

#### Rủi ro của tuần 10-11 đã xử lý thế nào

§I gắn cờ: `CONTRADICTS` (52) nhiều gấp hơn 6 lần `SUPPORTS` (8), và một LLM đọc 52 dòng *"test
xanh phủ dòng này"* rất dễ rơi vào cái bẫy `findByUsername`. Ba lớp chống, cả ba đều nằm trong dữ
liệu hoặc prompt chứ không trong code suy luận:

- Mô tả tool `get_related_tests` viết thẳng: `CONTRADICTS` **không** chứng minh cảnh báo sai.
- System prompt dành hẳn một mục: số lượng liên kết không có ý nghĩa; muốn kết luận
  `LIKELY_FALSE_POSITIVE` thì cần một lý do **trong mã** và phải đọc mã rồi nêu ra.
- `rationale` mang sẵn câu cảnh báo từ tuần 10-11.

Đo thật ở tuần 15 mới biết đủ hay chưa. Nếu chưa, cần gom nhóm hoặc cắt theo weight ở prompt builder.

#### Số liệu

444 test backend · 61 test agent-service · 122 test frontend (16 file).

#### Nghiệm thu bằng cách CHẠY THẬT, và bốn lỗi nó lôi ra

Dựng cả stack (Postgres + MinIO + core-backend + agent-service với `LLM_PROVIDER=scripted`), nạp
vulnshop, rồi đi hết luồng bằng `curl`. Toàn bộ 435+50+122 test đều xanh trước khi làm việc này —
bốn lỗi dưới đây **không lỗi nào bị test bắt**, vì cả bốn nằm ở ranh giới giữa hai tiến trình
hoặc ở hành vi của thư viện:

1. **JDK HttpClient thử nâng cấp `h2c` và làm MẤT thân request.** FastAPI trả 422 "Field required"
   cho một `POST` mà phía Java tin là đã gửi đủ. Chỉ tìm ra bằng cách dựng một server nghe thử rồi
   in nguyên văn thứ đi qua dây. Chữa bằng cách ghim HTTP/1.1.
2. **`LLM_PROVIDER=scripted` — đường mà chính tài liệu bảo dùng để nghiệm thu — chết ở lượt thứ
   hai.** Một lượt hỏi đáp gọi model ít nhất hai lần, mà kịch bản mặc định chỉ có một phản hồi.
3. **`search_evidence` lọc `types` SAU khi đã cắt `limit`**, nên hỏi 3 cảnh báo tĩnh trả về rỗng dù
   run có hàng chục — và truy vấn không có `ORDER BY` nên kết quả đổi giữa các lần chạy. Với LLM,
   "không tìm thấy" là một câu trả lời sai mà nó tin ngay.
4. **Thông báo lỗi `"agent-service tra ve 422"` không nói được gì.** Kèm thân response vào là biết
   ngay nguyên nhân.

Lỗi số 3 còn suýt lừa được chính việc nghiệm thu: lần chạy đầu tôi tưởng đã kiểm chứng bản vá,
nhưng backend đang chạy là JVM **cũ** (`TaskStop` giết wrapper Maven chứ không giết JVM đã fork,
nên lần khởi động sau thất bại vì cổng bị giữ trong khi `/actuator/health` vẫn trả `UP`) — thứ tôi
thấy chỉ là tính bất định của chính lỗi đó.

Ba nhánh hậu kiểm được xác nhận trên stack thật: trích dẫn đúng giữ nguyên verdict; ID bịa thành
`UNKNOWN_ID` và nhận định bị hạ cấp mà **không** bị xoá, không tạo liên kết tới ID không tồn tại;
nhận định không dẫn nguồn cũng bị hạ cấp. Verdict tổng thể vẫn trụ nhờ nhận định thứ nhất.

Bốn bất biến của một lượt trả lời được ép ở **tầng DB** chứ không chỉ trong Java: lượt của agent
luôn có verdict, hạ cấp chỉ đi một chiều (xuống `INSUFFICIENT_EVIDENCE`), claim không mồ côi,
`ordinal` không trùng. Cả bốn đều canh cùng một loại hỏng — một lượt ghi xuống trông bình thường
trong khi số liệu tuần 15 đọc từ nó đã sai.

#### Rà soát cuối tuần 12-13 — năm việc phải làm nốt

Rà bằng cách đối chiếu kế hoạch với mã nguồn, không dựa vào trí nhớ. Cả năm đều là **lời hứa
không được kiểm**, không phải lỗi chức năng:

| Phát hiện | Xử lý |
|---|---|
| `docker-compose.yml` **không truyền `SERVICE_TOKEN`** cho agent-service, mà biến đó nay bắt buộc — nên `docker compose up` làm container chết ngay lúc khởi động. Đúng thứ §10 "Đóng gói" đòi | Thêm vào compose và `.env.example`, kèm ghi chú phải trùng với core-backend |
| `evidence_context_budget` khai trong `Settings` và `.env.example` nhưng **không dùng ở đâu** | Nối vào agent loop: cắt kết quả tool theo ngân sách, và **nói ra** khi cắt |
| `AbortSignal` có trong chữ ký `askAgent` nhưng hook **không bao giờ truyền** — tham số chết, và §J đã nói quá lời | Nối `AbortController` thật: hỏi câu mới hoặc rời trang thì huỷ lượt cũ |
| **Không test nào canh prompt.** Xoá mục cảnh báo `CONTRADICTS` đi thì mô hình vẫn chạy, vẫn đúng lược đồ, chỉ là kết luận sai đúng theo cách case study dựng lên để bắt | `test_prompts.py` (9 ca) canh từng luận điểm |
| `ConversationController` **không có test HTTP** trong khi 9 controller khác đều có | `ConversationControllerTest` (9 ca): 401, 404-thay-vì-403, 400, 409, và service token không dùng được cho đường người dùng |

Việc viết `test_prompts.py` lôi ra thêm một thiếu sót: mô tả tool `get_related_tests` **không nói
khi nào nên gọi**, trong khi bốn tool kia đều có. Mô tả tool là thứ mô hình đọc để quyết định gọi
hay không, nên thiếu vế đó là để nó tự đoán.

Cắt ngữ cảnh mà **im lặng** còn tệ hơn không cắt: mô hình tưởng đã xem hết bằng chứng và kết luận
chắc nịch trên một nửa dữ liệu. Nay phần bị cắt kèm một câu nói rõ, và bảo nó ghi phần chưa xem
được vào `suggested_next_checks`.

#### Món còn nợ có chủ đích

| Món | Vì sao hoãn |
|---|---|
| Chưa sinh vector embedding | Vẫn hoãn. Có agent loop rồi nhưng chưa đo được retrieval hiện tại (ILIKE) yếu ở đâu; chốt mô hình trước khi có số đo là chọn bừa |
| `OpenAIProvider` / `OllamaProvider` | Interface và factory đã sẵn; chỉ cần khi tuần 15 chạy thực nghiệm nhiều provider |
| Chưa có nút huỷ một lượt hỏi đang chạy | `AbortSignal` đã nối xuyên suốt (hỏi câu mới hoặc rời trang là huỷ lượt cũ), nhưng chưa có nút bấm và phía server vẫn chạy nốt lượt đã bắt đầu |

---

## Context

Đề cương ĐATN (Nguyễn Việt Anh — 20224923, GVHD: PGS.TS. Huỳnh Quyết Thắng) đặt bài toán:
công cụ phân tích tĩnh sinh ra rất nhiều cảnh báo, trong đó có false positive và cảnh báo khó
diễn giải; LLM có thể giải thích nhưng dễ đưa ra nhận định thiếu căn cứ. Giải pháp đề xuất là
một nền tảng web tổ chức **chuỗi bằng chứng** (static warning + code context + test evidence)
vào một Unified Evidence Model, rồi để **AI Agent truy xuất bằng chứng qua tool-call** và trả
lời qua **Conversational Analysis Chatbot**, với mỗi claim bắt buộc gắn Evidence ID kiểm chứng
được.

Trục xuyên suốt: `Static Warning → Code/Test Evidence → Evidence Retrieval → AI Reasoning →
Conversational Answer → Evidence Traceability → Human Decision`.

Thư mục `d:\study\DATN\Project` hiện **chỉ có file đề cương .docx**, chưa có mã nguồn — đây là
kế hoạch xây dựng từ con số không.

### Quyết định đã chốt với người dùng

| Hạng mục | Lựa chọn |
|---|---|
| Case study | Java (ứng dụng Spring Boot nhỏ) → SpotBugs/Semgrep + JUnit + JaCoCo |
| Backend | Spring Boot (core) + service Python FastAPI (AI Agent), giao tiếp REST |
| LLM | Provider-agnostic (interface `LlmProvider`, cấu hình Claude / OpenAI / local) |
| Thời gian | ~20 tuần, bắt đầu 27/07/2026, bảo vệ dự kiến giữa tháng 12/2026 |

### Kết quả mong muốn

Sản phẩm chạy được end-to-end + case study có ground truth + số liệu so sánh 3 cấu hình
(LLM-Code / LLM-Static / Evidence-Agent) đủ để trả lời RQ1–RQ3, đạt tối thiểu mức "Tốt" theo
tiêu chí mục 10 của đề cương.

---

## 1. Kiến trúc mục tiêu

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React + TypeScript + Vite)                        │
│  Project | Repo browser | Warning list | Evidence Explorer   │
│  | Chat panel | Human Review | Dashboard                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ REST + SSE (streaming chat)
┌───────────────────────────▼─────────────────────────────────┐
│  Core Backend — Spring Boot 3 (Java 21)                      │
│  ProjectService · RepositoryManager · AnalysisOrchestrator   │
│  StaticAnalysisAdapter · TestEvidenceCollector               │
│  EvidenceLinker · EvidenceStore · HumanReviewService         │
│  ExperimentRunner                                            │
└──────────┬────────────────────────────────┬─────────────────┘
           │ REST (evidence tool API)       │ JDBC
┌──────────▼──────────────────┐   ┌─────────▼──────────────────┐
│  Agent Service — FastAPI     │   │  PostgreSQL 16 + pgvector  │
│  AgentLoop · EvidenceTools   │   │  evidence_record, warning, │
│  Retriever (BM25 + vector)   │◄──┤  conversation, review,     │
│  LlmProvider (Claude/OpenAI) │   │  experiment_run, embedding │
│  CitationValidator           │   └────────────────────────────┘
└──────────────────────────────┘
```

**Nguyên tắc phân chia trách nhiệm quan trọng:** Agent Service **không** truy cập DB trực tiếp.
Nó gọi Evidence Tool API do Spring Boot expose. Điều này khiến 5 tool trong đề cương (mục 3.4.1)
trở thành ranh giới kiến trúc thật, dễ log/đo và dễ viết vào báo cáo.

**CitationValidator chạy ở Spring Boot**, không ở Agent Service — vì đề cương yêu cầu hậu kiểm
"độc lập với LLM" (mục 3.4.2). Đặt nó ngoài process của agent làm luận điểm này thuyết phục hơn
khi bảo vệ.

### Yêu cầu bắt buộc: FE và BE deploy độc lập

Frontend và backend phải triển khai được lên **hai server / hai domain khác nhau**, phát hành
phiên bản độc lập, không cái nào phụ thuộc build-time vào cái nào. Ràng buộc này quyết định
nhiều thứ ngay từ tuần 1, không thể sửa sau:

**1. FE là SPA tĩnh thuần** — build ra `dist/` gồm HTML/JS/CSS, phục vụ bởi Nginx hoặc bất kỳ
static host / CDN nào. Không SSR, không server-side rendering coupling, không proxy bắt buộc.

**2. API base URL cấu hình lúc *chạy*, không phải lúc *build*.** Đây là điểm hay bị làm sai:
nếu dùng `import.meta.env.VITE_API_URL` của Vite thì URL bị nướng cứng vào bundle, mỗi môi
trường phải build lại → không còn độc lập. Cách làm đúng:

```
frontend/public/config.js        # window.__APP_CONFIG__ = { apiBaseUrl: "__API_BASE_URL__" }
frontend/docker-entrypoint.sh    # envsubst thay placeholder rồi mới start nginx
```
Code FE đọc `window.__APP_CONFIG__.apiBaseUrl`. Kết quả: **build một image, deploy mọi môi
trường** chỉ bằng đổi biến môi trường container.

**3. CORS là bắt buộc, không né bằng proxy.** Spring Boot cấu hình
`allowed-origins` từ env var (danh sách, phân tách bằng dấu phẩy), `allowCredentials` chỉ bật nếu
thực sự dùng cookie. Phải test CORS **thật sự cross-origin** ngay từ tuần 5 (FE ở `:5173`,
BE ở `:8080`) — nếu dev bằng Vite proxy thì lỗi CORS sẽ chỉ lộ ra lúc deploy thật.

**4. Xác thực stateless bằng JWT Bearer token**, không dùng session cookie server-side. Lý do:
cookie cross-domain kéo theo `SameSite=None; Secure` + phải có HTTPS + cấu hình domain cha —
phức tạp và dễ hỏng khi FE/BE khác domain. JWT trong header `Authorization` không có vấn đề này
và cho phép BE scale ngang không cần sticky session.

**5. SSE streaming phải qua được reverse proxy.** Chat dùng SSE; Nginx mặc định buffer response
sẽ làm streaming đứng im rồi xả một cục. Cần `proxy_buffering off; proxy_read_timeout 300s;` và
BE gửi header `X-Accel-Buffering: no`. Kiểm chứng bằng cách chạy thật qua Nginx, không chỉ test
trực tiếp vào `:8080`.

**6. Hợp đồng API là ranh giới phiên bản.** BE expose OpenAPI spec (springdoc); FE sinh client
TypeScript từ spec đó (`openapi-typescript`). Mọi endpoint dưới `/api/v1/...`. Thay đổi phá vỡ
tương thích → thêm `v2`, không sửa `v1` — để FE cũ còn chạy được khi BE đã deploy bản mới.

**7. Ba container hoàn toàn tách rời**, mỗi cái một Dockerfile + một image, có healthcheck riêng:

| Service | Image | Cấu hình runtime |
|---|---|---|
| `frontend` | nginx + `dist/` | `API_BASE_URL` |
| `core-backend` | JRE 21 + jar | `DB_URL`, `DB_USER`, `DB_PASS`, `AGENT_SERVICE_URL`, `CORS_ORIGINS`, `JWT_SECRET` |
| `agent-service` | python + uvicorn | `CORE_API_URL`, `LLM_PROVIDER`, `LLM_API_KEY` |

`docker-compose.yml` chỉ để chạy local cho tiện — **không** được là điều kiện để hệ thống hoạt
động. Mỗi service phải khởi động được đơn lẻ chỉ với env var của nó. Test điều này bằng cách
chạy `frontend` trên một máy/port khác trỏ về BE, ít nhất một lần trước tuần 14.

**8. Không chia sẻ filesystem giữa các service.** Mã nguồn upload lưu ở object storage
(MinIO local / S3-compatible) hoặc DB, không phải volume dùng chung — nếu không, FE/BE buộc phải
nằm cùng máy.

### Cấu trúc thư mục monorepo

```
d:\study\DATN\Project\
├── docs/                       # đề cương, thiết kế, ADR, ảnh kiến trúc
├── core-backend/               # Spring Boot (Maven)
├── agent-service/              # FastAPI (uv/poetry)
├── frontend/                   # React + Vite
├── case-study/
│   ├── vulnshop/               # repo Java làm case study (Maven)
│   └── ground-truth/           # nhãn true/false positive (YAML/CSV)
├── experiments/                # script chạy 3 cấu hình, notebook phân tích
├── infra/                      # docker-compose.yml, Dockerfile, init.sql
└── report/                     # LaTeX/Word báo cáo + slide
```

---

## 1bis. Chuẩn code và quy ước kỹ thuật

Đây là ĐATN về **chất lượng phần mềm** — code luộm thuộm sẽ bị hỏi ngay khi bảo vệ. Các quy ước
dưới đây thiết lập từ tuần 1 và ép bằng công cụ tự động, không dựa vào ý chí.

### 1bis.1. Cấu hình qua biến môi trường (`.env`), không hardcode

**Tuyệt đối không** commit secret, connection string, API key. Quy tắc:

- Mỗi service có `.env.example` **được commit** (liệt kê đủ biến, giá trị giả) và `.env`
  **bị gitignore**.
- Spring Boot: `application.yml` chỉ chứa `${DB_URL}`, `${JWT_SECRET}`… — không có giá trị thật.
  Dùng `@ConfigurationProperties` gom theo nhóm, có validation (`@NotBlank`), **fail-fast khi
  thiếu biến bắt buộc** thay vì chạy rồi lỗi lúc runtime.
- FastAPI: `pydantic-settings` (`BaseSettings`) — cùng nguyên tắc fail-fast.
- Frontend: chỉ có `API_BASE_URL` qua runtime config (§1). **Không đặt secret nào ở FE** — mọi
  thứ trong bundle đều công khai, kể cả biến tên là `SECRET`.
- Thêm `gitleaks` vào pre-commit hook để chặn lỡ tay commit key.

### 1bis.2. Backend — phân tầng rõ ràng

```
controller/   → chỉ nhận request, validate DTO, gọi service. Không logic nghiệp vụ.
service/      → toàn bộ logic nghiệp vụ. Không biết gì về HTTP.
repository/   → truy cập dữ liệu (Spring Data JPA).
domain/       → entity + value object + enum.
dto/          → request/response record, tách biệt entity.
mapper/       → MapStruct, entity ↔ DTO.
config/       → bean, security, CORS, OpenAPI.
exception/    → exception nghiệp vụ + @RestControllerAdvice xử lý tập trung.
```

Nguyên tắc bắt buộc:
- **Không bao giờ trả entity JPA trực tiếp ra API.** Luôn qua DTO — tránh lộ trường nội bộ và
  lỗi lazy-loading serialization.
- **Constructor injection**, không `@Autowired` trên field (khó test, che giấu phụ thuộc).
- **Xử lý lỗi tập trung** bằng `@RestControllerAdvice`, trả về định dạng thống nhất
  (RFC 7807 Problem Details). Không `try/catch` rải rác trong controller.
- Mọi endpoint validate input bằng `@Valid` + Bean Validation.
- Migration DB **chỉ** qua Flyway, `ddl-auto: validate`. Không bao giờ `update` hay `create`.
- Adapter phân tích tĩnh thiết kế theo interface `StaticAnalysisAdapter` — thêm SpotBugs sau
  không phải sửa code cũ (Open/Closed). Đây cũng là điểm để nói về khả năng mở rộng khi bảo vệ.

### 1bis.3. Frontend — chia component có kỷ luật

```
src/
├── features/            # tổ chức theo tính năng, KHÔNG theo loại file
│   ├── warnings/  { components/  hooks/  api/  types.ts }
│   ├── evidence/  { components/  hooks/  api/  types.ts }
│   ├── chat/      { components/  hooks/  api/  types.ts }
│   └── review/    { ... }
├── shared/
│   ├── ui/              # Button, Modal, Badge… — thuần trình bày, không biết nghiệp vụ
│   ├── hooks/           # useDebounce, useSSE…
│   ├── lib/             # apiClient (sinh từ OpenAPI), formatters
│   └── config/          # đọc window.__APP_CONFIG__
└── app/                 # routing, providers, layout
```

Nguyên tắc bắt buộc:
- **Tách container / presentational**: component trong `shared/ui` nhận props và render, không
  gọi API, không biết `Warning` là gì. Component trong `features/*` lo dữ liệu và trạng thái.
- **Logic dữ liệu nằm trong custom hook**, không nằm trong JSX. `useWarnings()`,
  `useEvidenceChat()`. Component chỉ ráp UI.
- **Một component < ~150 dòng.** Vượt ngưỡng là tín hiệu tách. `EvidenceExplorer` và `ChatPanel`
  là hai chỗ chắc chắn sẽ phình — tách sớm, đừng đợi.
- **Không gọi `fetch` trực tiếp trong component.** Mọi request đi qua client sinh từ OpenAPI +
  TanStack Query (lo cache, loading, retry, invalidation — không tự viết lại bằng `useEffect`).
- TypeScript **`strict: true`**, không `any`. Kiểu API lấy từ OpenAPI generator nên FE/BE không
  bao giờ lệch kiểu.
- Mọi trạng thái async render đủ **loading / error / empty**, không chỉ happy path.

### 1bis.4. Agent Service (Python)

- Tách rõ `tools/` (5 evidence tool), `providers/` (Claude/OpenAI/Ollama), `agent/` (vòng lặp),
  `schemas/` (Pydantic). `LlmProvider` là abstract base class — thêm provider không sửa agent loop.
- Type hint đầy đủ, `mypy` bật. Pydantic cho mọi ranh giới vào/ra.
- **Không nhét prompt vào giữa code.** Prompt để trong `prompts/*.jinja2`, version hoá — vì
  báo cáo cần trích dẫn prompt chính xác và thực nghiệm cần biết prompt nào sinh ra kết quả nào.

### 1bis.5. Chất lượng ép bằng công cụ

| Lớp | Công cụ | Chặn ở đâu |
|---|---|---|
| Java | Spotless (format) + Checkstyle | pre-commit + CI |
| Python | Ruff (lint+format) + mypy | pre-commit + CI |
| TS/React | ESLint + Prettier + `tsc --noEmit` | pre-commit + CI |
| Secret | gitleaks | pre-commit |
| Test | JUnit + pytest + Vitest | CI |

GitHub Actions chạy lint + test + build trên mỗi push. **Dựng CI ở tuần 1–2**, lúc còn ít code —
dựng muộn thì sẽ có hàng trăm lỗi lint dồn lại và bạn sẽ tắt nó đi.

### 1bis.6. Quy ước Git

- Nhánh: `main` (luôn chạy được) + `feat/*`, `fix/*`, `docs/*`.
- Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`) — tiện viết mục "quá
  trình phát triển" trong báo cáo và sinh changelog.
- Commit nhỏ, một mục đích. **Không** commit kiểu "update code" cuối ngày.
- Tag `v0.1`, `v0.2`… ở mỗi mốc lộ trình, để demo được từng giai đoạn nếu cần.

### 1bis.7. Phạm vi test hợp lý (không chạy theo % coverage)

Ưu tiên test đúng chỗ có giá trị, vì thời gian eo hẹp:

- **Bắt buộc test kỹ**: parser SARIF/Surefire/JaCoCo (nhiều edge case, sai là hỏng toàn bộ dữ
  liệu), EvidenceLinker, CitationValidator (là cơ sở của chỉ số thực nghiệm — sai thì kết quả
  báo cáo sai).
- **Test tích hợp**: Evidence Tool API, dùng Testcontainers cho Postgres thật.
- **Test nhẹ**: CRUD thuần, UI trình bày.
- **Mock LLM trong test** — không gọi API thật trong CI (tốn tiền, không deterministic).

## 2. Mô hình dữ liệu (Unified Evidence Model)

Bảng trung tâm `evidence_record` — đúng các trường lõi ở mục 3.3 đề cương:

```sql
evidence_record(
  id            UUID PK,           -- Evidence ID được LLM trích dẫn
  project_id    UUID FK,
  analysis_run_id UUID FK,
  type          evidence_type,     -- enum 6 giá trị bên dưới
  source        TEXT,              -- 'semgrep' | 'spotbugs' | 'surefire' | 'jacoco' | 'parser'
  commit_sha    TEXT,
  file_path     TEXT,
  start_line    INT, end_line INT,
  symbol        TEXT,              -- FQN class#method — khoá liên kết chính
  content       JSONB,             -- payload đặc thù theo type
  severity      TEXT, status TEXT,
  created_at    TIMESTAMPTZ
)
evidence_link(from_id, to_id, relation, weight)   -- SUPPORTS / CONTRADICTS / COVERS / LOCATED_IN
evidence_embedding(evidence_id, vector VECTOR(1024), text_chunk)
```

`evidence_type` = `STATIC_WARNING | CODE_CONTEXT | TEST_RESULT | FAILED_TEST | STACK_TRACE |
COVERAGE_INFORMATION`.

**Dùng UUID hiển thị dạng ngắn** (ví dụ `EV-3F2A`) trong prompt và UI — LLM trích dẫn UUID đầy đủ
rất dễ sai ký tự, làm hỏng chỉ số Evidence Citation Accuracy một cách giả tạo. Map ngắn↔đầy đủ ở
tầng prompt builder.

Bảng phụ: `project`, `repository_snapshot`, `analysis_run`, `warning`, `conversation`, `message`,
`claim` (claim + evidence_ids + verdict + confidence + validation_status), `human_review`,
`experiment_run`, `experiment_result`.

**`claim` phải là bảng riêng, không nhét JSON trong `message`** — vì Unsupported Claim Rate và
Evidence Citation Accuracy được tính bằng SQL trên bảng này. Thiết kế sai chỗ này sẽ phải làm lại
ở tuần 15.

---

## 3. Pipeline liên kết warning ↔ code ↔ test

Đây là phần rủi ro cao nhất của đồ án (đề cương mục 11 cũng nhận diện). Chiến lược 3 tầng, làm
tuần tự, tầng sau chỉ làm khi tầng trước chạy ổn:

1. **Tầng vị trí (bắt buộc, làm trước):** warning có `file + line` → dùng JavaParser lấy
   method/class bao quanh → sinh `CODE_CONTEXT` với `symbol` = FQN. Đây là khoá nối duy nhất cần
   thiết cho MVP.
2. **Tầng coverage (giá trị cao nhất, làm tiếp):** parse `jacoco.xml` → biết dòng cảnh báo được
   test nào phủ. JaCoCo mặc định chỉ cho line coverage tổng, **không** cho biết test nào phủ dòng
   nào. Giải pháp: chạy JaCoCo **per-test** (dùng `jacoco:report` với execution tách theo test qua
   Surefire `forkCount` + `@Rule`/JUnit5 extension dump exec riêng). Nếu quá tốn thời gian, fallback
   sang liên kết theo class: test `FooTest` ↔ class `Foo`.
3. **Tầng stack trace (rẻ, giá trị cao):** parse stack trace của failed test trong Surefire XML →
   nếu frame chứa `file:line` trùng vị trí warning → link `SUPPORTS` với weight cao. Đây là loại
   evidence thuyết phục nhất cho verdict "Likely True Positive".

Quan hệ `CONTRADICTS` sinh khi: dòng warning **được phủ** bởi test **pass** và không có failed test
liên quan → bằng chứng phản bác. Đây chính là câu trả lời cho câu hỏi "Có bằng chứng nào mâu thuẫn
không?" trong đề cương mục 3.5.

---

## 4. AI Agent

**Vòng lặp** (đúng mục 3.4): nhận câu hỏi + scope → gọi tool → lọc/xếp hạng theo context budget →
build prompt có Evidence ID → structured output → citation validation → trả về.

**5 tool** đúng đề cương: `get_warning`, `get_code_context`, `get_related_tests`, `get_coverage`,
`search_evidence`. Khai báo qua JSON Schema dùng chung cho mọi provider.

**Structured output schema:**
```json
{
  "verdict": "LIKELY_TRUE_POSITIVE | LIKELY_FALSE_POSITIVE | INSUFFICIENT_EVIDENCE",
  "claims": [{ "text": "...", "evidence_ids": ["EV-3F2A"], "confidence": 0.0-1.0 }],
  "suggested_next_checks": ["..."]
}
```

**Ép trích dẫn:** claim không có `evidence_ids` hợp lệ → CitationValidator hạ verdict xuống
`INSUFFICIENT_EVIDENCE` và gắn cờ, **không** xoá claim (cần giữ để tính Unsupported Claim Rate).

**Provider-agnostic:** interface `LlmProvider` với `chat(messages, tools, response_schema)`.
Implement `AnthropicProvider`, `OpenAIProvider`, `OllamaProvider`. Cấu hình bằng env var.
Trước khi viết code gọi LLM, **load skill `claude-api`** để lấy model ID / pricing / tool-use API
chính xác thay vì viết theo trí nhớ.

**Log mọi lượt gọi LLM** (prompt, tool calls, token, latency, response) vào bảng `llm_trace` ngay
từ ngày đầu — dữ liệu này là nguồn cho toàn bộ chương 4 báo cáo, không thể tái tạo sau.

---

## 5. Case study & ground truth

Xây `case-study/vulnshop` — ứng dụng Spring Boot nhỏ (~15–25 class): đăng nhập, xử lý input,
truy vấn DB, upload file. Cài **có chủ đích**:

- ~10–15 lỗi thật (SQL injection nối chuỗi, path traversal, NPE, resource leak, off-by-one…)
- ~10–15 tình huống sinh cảnh báo nhưng **không** phải lỗi (input đã sanitize ở tầng trên,
  branch không reachable, giá trị đã null-check gián tiếp…) → nguồn false positive tự nhiên
- Bộ JUnit test: đa số pass, 3–5 test fail có assertion + stack trace rõ ràng

**Ground truth** (`ground-truth/labels.yaml`): mỗi warning → `TP/FP/UNCERTAIN` + lý do 1–2 câu +
người gán nhãn. Mục tiêu **60–100 warning đã gán nhãn** — đủ cho thống kê mô tả, vừa sức 20 tuần.
Nhờ GVHD hoặc 1 bạn có chuyên môn gán nhãn độc lập một tập con (~30 warning) để báo cáo được
inter-rater agreement (Cohen's kappa) — đây là điểm ăn mức "Xuất sắc".

**Bẫy cần tránh:** đừng vừa viết case study vừa tự gán nhãn theo ý mình rồi để agent đánh giá —
sẽ bị hỏi về circularity khi bảo vệ. Cách chống: viết case study ở tuần 5–7, gán nhãn ở tuần 15
**sau khi** đã quên chi tiết, và gán nhãn dựa trên đọc code chứ không dựa trên output của agent.

---

## 6. Thực nghiệm

`experiments/runner.py` chạy cùng một tập warning qua 3 cấu hình:

| Cấu hình | Input | Tool được phép |
|---|---|---|
| LLM-Code | code context | không |
| LLM-Static | code context + warning | không |
| Evidence-Agent | full evidence | cả 5 tool |

Cùng LLM, cùng temperature, **chạy 3 lần/warning** để báo cáo được độ ổn định (LLM không
deterministic — chạy 1 lần là điểm yếu chí mạng khi phản biện).

**Chỉ số & cách đo:**

| Chỉ số | Cách đo | Tự động? |
|---|---|---|
| Warning assessment accuracy | verdict vs ground truth | ✅ SQL |
| Unsupported claim rate | % claim không có evidence_id hợp lệ | ✅ SQL |
| Evidence citation accuracy | evidence_id trích dẫn có tồn tại & liên quan | ✅ SQL |
| Explanation correctness | rubric 3 mức, chấm thủ công (blind, xáo trộn cấu hình) | ❌ |
| Response usefulness | Likert 1–5 từ người dùng thử | ❌ |
| Human acceptance rate | từ bảng `human_review` | ✅ SQL |
| Time to understand | đo trong phiên user study | ❌ |

**User study nhỏ:** 5–8 người (bạn cùng lớp có nền lập trình), mỗi người xử lý 6–8 warning, một
nửa dùng chatbot, một nửa đọc output công cụ thô. Đủ để trả lời RQ3 ở mức định tính + định lượng
mô tả. Chuẩn bị form + kịch bản từ tuần 14, không để đến tuần 16.

---

## 7. Lộ trình 20 tuần (bắt đầu 27/07/2026)

| Tuần | Ngày | Việc chính | Deliverable kiểm chứng được |
|---|---|---|---|
| 1 | 27/07 | ~~Skeleton monorepo, docker-compose, lint/format/CI, spike xác thực pipeline~~ **XONG** | 4 commit; CI xanh; hạ tầng chạy; spike xác nhận 3 nguồn khớp `file:line` |
| 2 | 28/07–09/08 | **Case study vulnshop đầy đủ + chạy toàn bộ analyzer, thu artifact thật** (đảo lên sớm — xem §7bis); song song bạn đọc tài liệu theo khung đã lập | vulnshop build+test; `docs/samples/` có SARIF/Surefire/JaCoCo thật; `docs/related-work.md` có khung + danh sách |
| 3 | 10/08–16/08 | Thiết kế Unified Evidence Model **dựa trên artifact thật**; V2 migration, JPA entity, ERD, ADR 0004 | `docs/evidence-model.md`; V2 chạy trên DB thật; entity map đúng schema |
| 4 | 17/08–23/08 | API contract: DTO, Evidence Tool API khung, OpenAPI ổn định, FE sinh client TypeScript, integration test Testcontainers | Swagger UI đầy đủ; `npm run api:generate` ra type dùng được; test tích hợp xanh |
| 5–7 | 24/08–13/09 | Core backend (project/repo/snapshot CRUD, upload zip + clone git, JWT auth); frontend (routing, repo browser, warning list, source viewer) — **không còn phải chia thời gian cho case study** | Upload repo → xem file/dòng trên web; **FE chạy cross-origin thật với BE** |
| 8–9 | 14/09–27/09 | StaticAnalysisAdapter (Semgrep SARIF + SpotBugs XML → chuẩn hoá); TestEvidenceCollector (Surefire XML, JaCoCo XML); chạy analyzer trong Docker sandbox; **`V5__evidence_raw_tables.sql`** — 5 bảng thô đã thiết kế sẵn ở tuần 3 (`docs/evidence-model.md` mục 7): `test_case`, `stack_frame`, `coverage_line`, `coverage_method`, `warning_location` | 1 lệnh → warning + kết quả test trong DB, **số liệu khớp `case-study/vulnshop/expected-analysis.json`**; migration chạy được, entity map đúng |
| 10–11 | 28/09–11/10 | EvidenceLinker 3 tầng (§3); CODE_CONTEXT qua JavaParser; embedding + pgvector; Evidence Tool API; Evidence Explorer UI | Mở 1 warning → thấy code context + test liên quan + coverage |
| 12–13 | 12/10–25/10 | Agent Service: LlmProvider, agent loop, 5 tool, structured output, CitationValidator; chat UI + SSE streaming + click Evidence ID → nhảy tới nguồn | Hỏi "test nào liên quan?" → trả lời có citation click được |
| 14 | 26/10–01/11 | Human Review (Accept/Reject/Uncertain/Insufficient), dashboard, export report; chuẩn bị form user study | Luồng end-to-end hoàn chỉnh, demo được |
| 15–16 | 02/11–15/11 | Gán nhãn ground truth = nhãn seeded (`SEEDED.md`) **cộng** nhãn cho cảnh báo phát sinh tự nhiên; 1 người gán nhãn mù độc lập; chạy thực nghiệm 3 cấu hình × 3 lần; user study | `results.csv` đầy đủ; kappa; kết quả **tách riêng seeded vs tự nhiên** |
| 17–18 | 16/11–29/11 | Phân tích số liệu, vẽ biểu đồ, phân tích lỗi (error analysis theo loại evidence); sửa bug; đóng gói Docker | Chương 4 có số liệu; `docker compose up` từ máy sạch chạy được |
| 19–20 | 30/11–13/12 | Hoàn thiện báo cáo 5 chương, slide, video demo, tổng duyệt | Bản nộp + slide + demo |

**Đệm rủi ro:** lộ trình này không có tuần trống. Nếu trượt tiến độ, cắt theo thứ tự: (1) Semgrep
— chỉ giữ SpotBugs, vì SpotBugs đã chạy được ở tuần 1 còn Semgrep chưa xác thực; (2) coverage
per-test — fallback liên kết theo class; (3) user study — giảm còn 3–4 người, báo cáo định tính.

**Viết báo cáo song song, không dồn cuối:** kết thúc mỗi mốc, viết ngay phần tương ứng
(tuần 2 → chương 2; tuần 4 → chương 3; tuần 9/11/13 → chương 4 phần cài đặt). Tuần 19–20 chỉ còn
việc ráp và biên tập.

---

## 7bis. Giai đoạn 2 — chi tiết (tuần 2–4, 28/07–23/08)

### Vì sao đảo thứ tự case study lên trước thiết kế

Lộ trình gốc thiết kế Evidence Model ở tuần 3–4 rồi mới xây case study ở tuần 5–7. Spike tuần 1
cho thấy lý do nên đổi: SpotBugs báo `DM_CONVERT_CASE` chứ **không** báo NPE ở dòng có lỗi thật.
Output thật của analyzer khác với hình dung — thiết kế schema khi mới nhìn artifact của đúng một
file nhỏ gần như chắc chắn phải sửa lại khi gặp dữ liệu thật ở tuần 8–9.

Đổi lại: viết case study trước, chạy toàn bộ analyzer, **thiết kế schema trên dữ liệu thật**.
Việc này cũng giải phóng tuần 5–7 khỏi phải chia thời gian cho case study, nên lịch tổng thể
an toàn hơn chứ không chỉ đổi chỗ.

### A. Dọn nốt tuần 1 (~nửa ngày)

1. `setx JAVA_HOME "C:\Program Files\Java\jdk-21.0.12"` — hiện `JAVA_HOME` trỏ vào `D:\study\JDK`
   không còn tồn tại, mỗi phiên terminal mới phải set tay.
2. Chạy Semgrep qua Docker trên `case-study/spike`, hoàn tất chân còn thiếu duy nhất của spike.
   Image `semgrep/semgrep`; nếu Docker Hub lại đứt thì kéo qua `mirror.gcr.io/semgrep/semgrep`
   rồi `docker tag`, đúng cách đã dùng cho pgvector.
3. **Vendor bộ rule Semgrep vào repo.** `--config=p/java` tải rule từ registry mỗi lần chạy, nên
   kết quả phụ thuộc thời điểm và cần mạng. Tải một lần, commit vào `case-study/semgrep-rules/`,
   dùng `--config=./semgrep-rules`. Đây là điều kiện để thực nghiệm tái lập được — người chấm
   chạy lại sau vài tháng phải ra đúng số cảnh báo đó.

### B. Case study `vulnshop` (tuần 2)

**Kỹ thuật**: Spring Boot 3 + Java 21, Maven, project độc lập tại `case-study/vulnshop/`.
Giữ nguyên `case-study/spike/` làm chứng cứ tuần 1, không sửa.

Dùng **JDBC thuần (`JdbcTemplate`), không dùng JPA**. JPA sinh câu lệnh tự động sẽ che mất lỗi
SQL injection — mà đó là loại cảnh báo tiêu biểu nhất cần có. H2 in-memory cho test, để case
study tự chứa, không phụ thuộc Docker.

Bốn nhóm chức năng theo đề cương mục 5.3: đăng nhập, xử lý input, truy vấn DB, upload file.
~15–25 class.

**Danh mục lỗi cài đặt có chủ đích** (chi tiết đầy đủ đi kèm code, đây là các nhóm chính):

*Lỗi thật — mục tiêu ~12–15:*

| Loại | Vị trí dự kiến | Ai được kỳ vọng bắt |
|---|---|---|
| SQL injection (nối chuỗi) | `UserRepository.findByUsername`, `ProductRepository.search` | Semgrep + SpotBugs |
| Path traversal | `FileStorageService.load` | Semgrep + SpotBugs |
| Hardcoded credential, MD5 cho mật khẩu | `SecurityConstants`, `PasswordHasher` | Semgrep + SpotBugs |
| Resource leak, `String ==`, equals/hashCode lệch | `FileStorageService`, `OrderStatusChecker`, `Product` | SpotBugs |
| NPE, off-by-one, chia cho 0 | `UserService`, `PaginationHelper`, `CartService` | **chỉ test bắt được** |

Nhóm cuối quan trọng nhất về mặt luận điểm: nó tái hiện đúng phát hiện của spike — phân tích tĩnh
bỏ sót lỗi thật mà bằng chứng kiểm thử bắt được. Đây là bằng chứng thực nghiệm trực tiếp cho lý
do đề tài cần nhiều nguồn bằng chứng.

*False positive tự nhiên — mục tiêu ~10–12:* mã sinh cảnh báo nhưng không phải lỗi. Ví dụ: nối
chuỗi SQL nhưng giá trị đến từ `enum` đã whitelist; MD5 dùng cho checksum file chứ không phải mật
khẩu; `Random` không an toàn nhưng dùng sinh mã màu giao diện; null-check thừa vì constructor đã
đảm bảo; `toLowerCase()` thiếu `Locale` trên chuỗi ASCII nội bộ.

**Test suite (~25–30 test)** với ba yêu cầu tinh tế, dễ bỏ sót:

1. **3–5 test cố tình hỏng**, mỗi cái sinh stack trace trỏ đúng dòng lỗi thật → nguồn
   `FAILED_TEST` + `STACK_TRACE`.
2. **Một số cảnh báo phải nằm ở dòng có test phủ và test pass** → sinh quan hệ `CONTRADICTS`,
   trả lời được câu hỏi "có bằng chứng nào mâu thuẫn không?".
3. **Một số cảnh báo phải nằm ở dòng KHÔNG được test nào phủ.** Nếu mọi dòng đều được phủ thì
   không bao giờ demo được verdict `INSUFFICIENT_EVIDENCE` — mà đó là một trong ba verdict của
   hệ thống. Cần chủ đích chừa vùng trắng.

**`case-study/vulnshop/SEEDED.md` — và tính trung thực của phương pháp**

Vì lỗi được cài có chủ đích, ta biết trước đáp án. Đây là phương pháp *fault seeding* hợp lệ,
nhưng phải khai báo minh bạch trong báo cáo, và cần tách bạch hai thứ:

- `SEEDED.md` = **ý đồ thiết kế**: cài lỗi gì, ở đâu, kỳ vọng TP hay FP. Commit ngay từ tuần 2.
  Nhãn ở đây đến từ lúc *viết code*, không đến từ output của agent — nên không có circularity.
- `ground-truth/labels.yaml` (tuần 15) = **nhãn cuối cùng**, gồm nhãn seeded **cộng thêm** nhãn
  cho các cảnh báo phát sinh tự nhiên mà ta không cố ý tạo ra. Nhóm sau phải gán bằng cách đọc
  code, không nhìn output agent.

Báo cáo phải nêu rõ đây là case study có gieo lỗi, và **báo kết quả tách riêng cho cảnh báo
seeded và cảnh báo tự nhiên**. Nếu chỉ báo số tổng, người phản biện sẽ hỏi ngay liệu độ chính xác
có phải do lỗi được cài quá lộ liễu hay không.

Điểm cộng cho mức "Xuất sắc": nhờ người khác gán nhãn mù một tập con rồi đối chiếu với `SEEDED.md`
— vừa ra được inter-rater agreement, vừa đo được lỗi gieo có thực tế hay không.

### C. Thu artifact thật (cuối tuần 2)

Chạy trên vulnshop và **commit kết quả** vào `docs/samples/`:

| Nguồn | File |
|---|---|
| SpotBugs | `spotbugs.sarif`, `spotbugsXml.xml` |
| Semgrep | `semgrep.sarif` |
| Surefire | `TEST-*.xml` (gồm cả test hỏng) |
| JaCoCo | `jacoco.xml` |

Hai công dụng: (1) căn cứ thiết kế schema ở tuần 3; (2) **fixture cho unit test của parser** —
test parser bằng dữ liệu thật thay vì XML tự bịa, vốn luôn sạch hơn thực tế.

**Cần sửa `.gitignore`**: hiện `*.sarif` và `surefire-reports/` đang bị bỏ qua (dòng 50–51), phải
thêm ngoại lệ `!docs/samples/**` — nếu không artifact sẽ lặng lẽ không được commit.

### D. Unified Evidence Model (tuần 3)

Thiết kế đối chiếu trực tiếp với `docs/samples/`, mỗi trường trong schema phải chỉ ra được nó
lấy từ đâu trong artifact thật.

Sản phẩm:
- `docs/evidence-model.md` — 6 loại evidence, trường lõi, **kèm ví dụ thật trích từ samples**.
- ERD bằng mermaid, nhúng trong markdown (artifact render được mermaid, tiện đưa vào báo cáo).
- `V2__evidence_model.sql` — `evidence_record`, `evidence_link`, `evidence_embedding`,
  `analysis_run`, `warning`, `claim`. Giữ nguyên các quyết định đã chốt ở §2: `claim` là bảng
  riêng (không nhét JSON trong `message`), Evidence ID có dạng ngắn ở tầng prompt builder.
- JPA entity + repository trong `core-backend/src/main/java/vn/hust/datn/evidence/domain/`,
  `ddl-auto: validate` sẽ tự bắt lỗi nếu entity lệch schema.
- ADR 0004 ghi lại các quyết định schema và lý do.

Ràng buộc từ bài học tuần 1: **`analysis_run` với 0 cảnh báo phải bị coi là lỗi**, không được lưu
lặng lẽ. Lần chạy SpotBugs trên JDK 25 là ví dụ thật — pipeline "thành công" nhưng không có dữ
liệu nào.

### D2. Tuần 3 — đã hoàn thành (30/07/2026)

Trước khi viết schema đã tự parse toàn bộ 4 artifact thật và phát hiện sketch ở mục 2 không biểu
diễn được dữ liệu thật ở 4 điểm (đa vị trí của warning, stack trace không luôn chỉ ra production,
cấu trúc kép của JaCoCo, 4 định dạng path khác nhau) — chi tiết đầy đủ và lý do từng quyết định ở
[ADR 0004](adr/0004-unified-evidence-model-hai-tang.md). Quyết định phạm vi: **chỉ 5 bảng xương
sống ở V2** (`analysis_run`, `evidence_record`, `warning`, `evidence_link`, `claim` +
`claim_citation`) — bảng thô mirror từng công cụ hoãn sang V3 (tuần 8-9) vì không có consumer nào
trước EvidenceLinker, chỉ thiết kế trước trong `docs/evidence-model.md`.

Đã hiện thực:
- `V2__evidence_model.sql` + 7 entity JPA đầu tiên của dự án + repository.
- `docs/evidence-model.md` — bảng provenance (mỗi cột trỏ về đúng chỗ trong artifact thật), ERD
  mermaid, mục "Thiết kế cho V3".
- `docs/check-evidence-model.py` — đối chiếu tự động doc ↔ artifact ↔ SQL, thêm vào CI.
- Test tích hợp trên Testcontainers (`pgvector/pgvector:pg16`): Flyway V1→V2 + `ddl-auto: validate`
  qua được trên toàn bộ entity; `EnumCheckConstraintTest` đối chiếu 11 CHECK constraint với
  `Enum.values()`; `AnalysisRunNotSilentlyEmptyTest` xác nhận "0 cảnh báo = lỗi" là ràng buộc DB
  thật; `PgvectorSmokeTest` xác nhận chuỗi pgvector chạy được mà chưa cần chốt số chiều embedding.

Sự cố môi trường gặp phải (không phải lỗi thiết kế): Testcontainers 1.21.0 không đọc được response
`/info` của Docker Desktop 29.0.1 trên Windows (named pipe lẫn TCP, `BadRequestException` 400 với
body rỗng) — nâng lên `testcontainers.version` 1.21.4 trong `core-backend/pom.xml` giải quyết
triệt để.

### E. API contract + OpenAPI (tuần 4)

- DTO dạng record trong `dto/`, MapStruct mapper — **không trả entity JPA ra API** (§1bis.2).
- `ProjectController` + `EvidenceController` (khung 5 evidence tool), mọi endpoint dưới `/api/v1`.
- OpenAPI ổn định tại `/api/v1/openapi`; FE chạy `npm run api:generate` sinh
  `src/shared/lib/api-types.ts`.
- Integration test với Testcontainers dùng `pgvector/pgvector:pg16` — image này **đã có trong
  cache local**, nên không phụ thuộc mạng nữa.

### E2. Tuần 4 — đã hoàn thành (30/07/2026)

Quyết định kiến trúc trước khi viết code (chốt với người dùng, ghi vào
[ADR 0005](adr/0005-evidence-tool-api-la-rpc.md)):

1. **Evidence Tool API là RPC đặt tên theo tool** (`POST /api/v1/tools/get_warning`, ...), KHÔNG
   phải REST resource — vì naming convention của LLM (snake_case) không dung hoà được với DTO Java
   (camelCase) mà không phá luận điểm "5 tool là ranh giới kiến trúc thật, dễ log/đo" (§1); LLM
   cũng không phân trang được nên không dùng `page/size` kiểu UI.
2. **`/api/v1/tools/**` dùng service token riêng**, không forward JWT người dùng — vì
   `experiments/runner.py` (§6) chạy batch không có phiên người dùng nào để forward.

Ba thứ hỏng im lặng đã sửa trước khi viết controller: `pom.xml` thiếu `lombok-mapstruct-binding`
(MapStruct sinh mapper rỗng mà biên dịch vẫn thành công); `GlobalExceptionHandler` catch-all
`Exception` nuốt 4xx của Spring (UUID sai định dạng trả 500 thay vì 400); `OpenApiConfig` thiếu
`servers` (snapshot OpenAPI không bao giờ khớp giữa môi trường).

Đã hiện thực:
- 4 exception nghiệp vụ (`EvidenceNotFoundException`/`WrongRun`/`NotAWarning`/`RunNotQueryable`)
  ánh xạ 1-1 với `ClaimCitationResolution` đã có từ ADR 0004 — getter số ít trả 404 phân loại,
  list luôn trả mảng rỗng (không bao giờ 404), và mọi tool validate `analysis_run.status` trước
  (409 nếu đang RUNNING/FAILED) để "rỗng vì chưa build" không lẫn với "rỗng vì đúng là rỗng".
- `EvidenceQueryService` dùng chung cho cả tool và UI; `WarningView` qua JPQL constructor
  projection (không MapStruct) vì `Warning.evidenceRecord` là `@OneToOne LAZY @MapsId` một chiều.
- `ProjectController` (REST) + `EvidenceToolController` (5 tool RPC) + `ToolCallLoggingInterceptor`
  ghi `tool_name`/`duration_ms`/`outcome`.
- `docs/api/openapi.json` commit trong repo; `OpenApiSnapshotTest` (core-backend) đối chiếu hai
  chiều bằng canonicalize (không so byte — springdoc không deterministic mặc định).
- FE: `api:generate` đọc từ file commit (không cần BE chạy), `api:generate:live` cho lặp nhanh;
  màn hình `ProjectList` gọi thật `GET /api/v1/projects`; CI job `frontend` thêm bước
  `git diff --exit-code src/shared/lib/api-types.ts` chống drift.
- 56 test tích hợp mới (MockMvc + Testcontainers), toàn bộ xanh.

Sự cố gặp phải: `TestFixtures.warning()` gọi self-invocation `evidenceRecord(...)` mà không có
`@Transactional` → entity bị detach giữa hai lần `saveAndFlush`, Hibernate từ chối gán vào quan hệ
`@MapsId` ("detached entity passed to persist"). Sửa bằng `@Transactional` trên `warning()` để
self-invocation tham gia cùng transaction thay vì tự mở transaction riêng.

Hoãn có chủ đích (không phải thiếu sót): nhóm REST resource UI cho `analysis-runs`/`evidence`
(không màn hình nào tiêu thụ tới tuần 10-11); hạ tầng test FE (`@testing-library/react`, jsdom —
chưa có component nào đáng test); pytest đối chiếu tên tham số tool giữa Java và Python (
`agent-service/tools/` còn rỗng, agent loop là tuần 12-13, chưa có gì để đối chiếu).

### F. Khung khảo sát tài liệu (song song, bạn tự đọc)

Tôi tạo `docs/related-work.md` gồm: cấu trúc chương 2 theo đúng mục lục đề cương, danh sách
~25 tài liệu chia theo 5 chủ đề (static warnings & false positives · LLM cho phân tích mã ·
AI agent trong kỹ nghệ phần mềm · RAG và agentic retrieval · evidence grounding & traceability),
và template tóm tắt thống nhất cho từng bài (bài toán · phương pháp · kết quả · **khoảng trống mà
đồ án này lấp**).

Cột cuối cùng là cột quan trọng nhất — nó chính là phần "khoảng trống nghiên cứu" ở cuối chương 2,
và là thứ hay bị hỏi nhất khi bảo vệ.

---

## 8. Ưu tiên khi thời gian eo hẹp

Bám tiêu chí mục 10 đề cương:

- **Bắt buộc (không được cắt):** web chạy được · 1 static analyzer tích hợp · warning + code
  context lưu trong DB · có test evidence · chatbot trả lời dựa trên evidence · traceability ·
  case study.
- **Tốt:** liên kết warning-code-test hợp lý · retrieval ổn định · thực nghiệm 3 cấu hình có
  ground truth.
- **Xuất sắc:** phân tích sâu loại evidence nào hữu ích nhất (ablation theo evidence type) ·
  chứng minh giảm unsupported claims · inter-rater agreement · dữ liệu đủ để viết bài.

---

## 9. Bước tiếp theo ngay

**Tuần 1 đã xong** (4 commit): monorepo 3 service, CI, hạ tầng chạy được, spike xác nhận 3 nguồn
bằng chứng khớp `file:line`, 3 ADR.

Thứ tự thực hiện giai đoạn 2:

1. `setx JAVA_HOME` + Semgrep qua Docker trên spike + vendor rule Semgrep (§7bis.A).
2. Sửa `.gitignore` mở ngoại lệ `!docs/samples/**` — làm **trước** khi sinh artifact, tránh mất
   công phát hiện muộn.
3. Dựng `case-study/vulnshop`: Spring Boot + JdbcTemplate + H2, ~15–25 class theo danh mục lỗi
   ở §7bis.B, kèm `SEEDED.md`.
4. Viết test suite, chú ý ba yêu cầu ở §7bis.B (test hỏng có stack trace · vùng được phủ để sinh
   `CONTRADICTS` · vùng không phủ để demo `INSUFFICIENT_EVIDENCE`).
5. Chạy SpotBugs + Semgrep + Surefire + JaCoCo, commit artifact vào `docs/samples/`.
6. Tạo `docs/related-work.md` (khung + danh sách ~25 tài liệu + template) để bạn đọc song song.
7. Sang tuần 3: thiết kế Evidence Model đối chiếu với artifact thật.

---

## 10. Verification — làm sao biết từng phần chạy đúng

| Mốc | Cách kiểm chứng |
|---|---|
| Hạ tầng | `docker compose up` từ máy sạch → 3 service healthy, DB có extension vector |
| **Deploy độc lập** | Chạy `frontend` container với `API_BASE_URL` trỏ tới BE ở **host/port khác**, không qua proxy chung → app hoạt động đầy đủ, kể cả login và chat streaming. Cùng một FE image, đổi env → trỏ được sang BE khác mà **không build lại** |
| **CORS / SSE** | Mở DevTools: preflight `OPTIONS` trả 200, không lỗi CORS; chat SSE nhả token dần qua Nginx chứ không xả một cục cuối cùng |
| **vulnshop** (tuần 2) | `mvn verify` chạy; đúng số test hỏng như thiết kế; báo cáo JaCoCo cho thấy **có cả vùng phủ và vùng không phủ** ở các dòng có cảnh báo |
| **Artifact mẫu** (tuần 2) | `git status` sau khi sinh artifact → `docs/samples/*.sarif` hiện ra là file mới, **không** bị gitignore nuốt |
| **Độ phủ danh mục lỗi** (tuần 2) | Đối chiếu `SEEDED.md` với SARIF thật: mỗi lỗi seeded hoặc bị analyzer bắt, hoặc bị test bắt, hoặc được ghi rõ là "không công cụ nào bắt được" — không có mục nào bỏ trống |
| **Tái lập Semgrep** (tuần 2) | Ngắt mạng, chạy lại Semgrep với `--config=./semgrep-rules` → ra đúng số cảnh báo như lần trước |
| **Evidence Model** (tuần 3) | Mỗi trường trong `docs/evidence-model.md` chỉ được ra dòng tương ứng trong `docs/samples/`; V2 chạy trên DB thật; app khởi động với `ddl-auto: validate` không lỗi |
| **API contract** (tuần 4) | `npm run api:generate` chạy được, `tsc` xanh với type sinh ra; test Testcontainers xanh |
| Analyzer | Chạy trên vulnshop → số warning trong DB khớp số dòng trong SARIF |
| Test evidence | Số `TEST_RESULT` khớp số testcase trong Surefire XML; số `FAILED_TEST` khớp |
| Linker | Chọn ngẫu nhiên 10 warning, kiểm tra thủ công code context có đúng method bao quanh |
| Evidence tools | Integration test: gọi từng tool qua REST, assert schema + nội dung |
| Citation validation | Unit test với response giả có evidence_id bịa → phải bị đánh dấu unsupported |
| Agent end-to-end | Kịch bản demo trong đề cương mục 5.3, chạy được không lỗi |
| Thực nghiệm | `results.csv` có đủ N_warning × 3 cấu hình × 3 lần; script tính chỉ số reproducible |
| Chất lượng code | CI xanh: lint + format + `tsc --noEmit` + mypy + test đều pass; gitleaks không phát hiện secret |
| Cấu hình | Xoá `.env`, chạy service → **fail ngay lúc khởi động** với thông báo rõ biến nào thiếu (không phải lỗi mơ hồ lúc runtime) |
| Đóng gói | Người khác clone repo, đọc README, dựng lại được trong ≤30 phút |
