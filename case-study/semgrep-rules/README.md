# Bộ rule Semgrep đã vendor

`p-java.yaml` là ruleset `p/java` của Semgrep Registry (60 rule), tải về ngày 27/07/2026 và
commit vào repo.

## Vì sao không dùng thẳng `--config=p/java`

Tuỳ chọn đó tải rule từ registry **mỗi lần chạy**. Hệ quả:

- Kết quả phụ thuộc thời điểm chạy — registry cập nhật rule thì số cảnh báo đổi theo, mà thực
  nghiệm của đồ án cần con số ổn định để so sánh ba cấu hình.
- Cần mạng. Người chấm dựng lại dự án sau vài tháng có thể không chạy được.

Vendor rule vào repo khiến phân tích **tái lập được và chạy offline**. Đã kiểm chứng bằng
`--network=none`: cùng 60 rule, cùng kết quả.

## Cách chạy

```bash
docker run --rm --network=none \
  -v "/duong/dan/case-study:/work" \
  semgrep/semgrep \
  semgrep scan --config=/work/semgrep-rules/p-java.yaml \
                --sarif --output=/work/<project>/semgrep.sarif \
                --metrics=off /work/<project>
```

Trên Windows dùng Git Bash, thêm `MSYS_NO_PATHCONV=1` trước `docker` và viết đường dẫn mount
dạng `//d/study/...` để Git Bash không tự chuyển đổi đường dẫn.

## Cập nhật rule

Chỉ cập nhật khi có lý do rõ ràng, và phải **chạy lại toàn bộ thực nghiệm** sau đó — không được
trộn kết quả của hai phiên bản ruleset trong cùng một bảng số liệu.

```bash
curl -fsSL "https://semgrep.dev/c/p/java" -o p-java.yaml
```

Ghi lại ngày cập nhật và số rule mới vào file này.

## Lưu ý về phạm vi

`p/java` chủ yếu là rule **bảo mật** (path traversal, SQL injection, weak crypto…). Nó không bắt
các lỗi logic như NPE hay off-by-one — chạy trên `case-study/spike` cho 0 kết quả trong khi
SpotBugs tìm được 1 cảnh báo. Đây là lý do đồ án dùng cả hai công cụ: chúng phủ hai lớp vấn đề
khác nhau, và đó cũng chính là luận điểm về giá trị của bằng chứng đa nguồn.
