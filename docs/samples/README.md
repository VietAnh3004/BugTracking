# Artifact mẫu từ công cụ phân tích

Output **thật** của bốn công cụ chạy trên `case-study/vulnshop`, sinh ngày 27/07/2026.

## Vì sao commit thay vì sinh lại khi cần

Hai công dụng, cả hai đều quan trọng:

1. **Căn cứ thiết kế Unified Evidence Model** (tuần 3). Mỗi trường trong schema phải chỉ ra được
   nó lấy từ đâu trong những file này. Thiết kế schema bằng cách hình dung output trông thế nào
   là cách chắc chắn phải làm lại — spike tuần 1 đã cho thấy output thật khác hình dung.

2. **Fixture cho unit test của parser** (tuần 8–9). Test parser bằng XML tự viết tay thì luôn
   sạch hơn thực tế: thiếu namespace lạ, thiếu ký tự escape, thiếu trường rỗng. Dữ liệu thật giữ
   nguyên mọi thứ đó.

`.gitignore` mặc định bỏ qua `*.sarif` và `surefire-reports/`; thư mục này được mở ngoại lệ riêng.

## Nội dung

| File | Nguồn | Loại bằng chứng tương ứng |
|---|---|---|
| `vulnshop/spotbugs.xml` | SpotBugs 4.9.3 + find-sec-bugs 1.13.0 | `STATIC_WARNING` |
| `vulnshop/semgrep.sarif` | Semgrep, ruleset `p/java` đã vendor | `STATIC_WARNING` |
| `vulnshop/surefire-reports/TEST-*.xml` | Maven Surefire | `TEST_RESULT`, `FAILED_TEST`, `STACK_TRACE` |
| `vulnshop/jacoco.xml` | JaCoCo 0.8.12 | `COVERAGE_INFORMATION` |

Số liệu kỳ vọng nằm trong [`case-study/vulnshop/expected-analysis.json`](../../case-study/vulnshop/expected-analysis.json)
— nguồn sự thật duy nhất, CI đối chiếu tự động.

### `vulnshop-sandbox/` — cùng dự án, chạy trong Docker sandbox

Tuần 8-9 (giai đoạn D) thêm thư mục thứ hai: artifact do **core-backend tự chạy analyzer trong
container** sinh ra, thay vì `run-analysis.sh` chạy trên host. Cùng mã nguồn, khác hoàn toàn cách
thực hiện — hai image analyzer, `mvn` chạy offline ở pha không có mạng, đường dẫn khác vì cây mã
nguồn được dựng từ snapshot đã upload.

Hai công dụng:

1. **Đối chiếu** — tiêu chí nghiệm thu của giai đoạn D là hai đường phải cho **trùng khít** số
   liệu. Lệch nghĩa là sandbox đang thấy một dự án khác với cái ta nghĩ.
2. **Fixture cho `AnalyzerSanityCheckerTest`** (đường dẫn lấy qua `SampleArtifacts`). Bốn tầng
   chống "chạy xong nhưng 0 cảnh báo" chỉ kiểm được bằng artifact do **chính sandbox** sinh ra:
   artifact chạy trên host không có mã canary, mà canary là một trong bốn tầng.

Vì vậy thư mục này chỉ có `spotbugs.xml` và `semgrep.sarif` — đó là hai file mà
`AnalyzerSanityChecker` đọc (số class đã quét, phiên bản JDK, plugin đã nạp, canary), không phải
một bản sao đầy đủ của lần chạy.

Xem [infra/analyzer/README.md](../../infra/analyzer/README.md) và
[ADR 0008](../adr/0008-chay-analyzer-trong-docker-sandbox.md).

```bash
python case-study/summarize.py vulnshop            # xem số liệu
python case-study/summarize.py vulnshop --check    # đối chiếu
```

## Khi chạy lại

```bash
./case-study/run-analysis.sh vulnshop --check
```

Script tự chạy `mvn clean test` — **`clean` là bắt buộc**, không phải tuỳ chọn: class file cũ
còn sót trong `target/` từ lần chạy trước có thể làm JaCoCo đếm sai số dòng được phủ (đã gặp
thật — 82/171 tụt thành 83/173 chỉ vì chạy lại mà không dọn).

Các file sẽ hiện ra là "đã sửa" trong `git status` **kể cả khi kết quả không đổi** — SpotBugs ghi
`analysisTimestamp`, Surefire ghi thời gian chạy từng test. Đây là nhiễu, không phải thay đổi
thực chất.

Cách phân biệt: `--check` xanh thì `git checkout -- docs/samples/` để bỏ thay đổi. Nếu `--check`
đỏ thì có gì đó đã đổi thật — script sẽ gợi ý nguyên nhân thường gặp.
