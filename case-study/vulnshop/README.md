# vulnshop — case study có gieo lỗi

> **CẢNH BÁO:** mã nguồn trong dự án này **cố tình chứa lỗ hổng bảo mật và lỗi logic**.
> Đây là *đối tượng phân tích* của đồ án, không phải một phần của sản phẩm.
> Không dùng làm mẫu cho mã thật, không triển khai ra môi trường có thật.

Ứng dụng bán hàng tối giản với bốn nhóm chức năng theo đề cương mục 5.3: đăng nhập, xử lý input,
truy vấn cơ sở dữ liệu, tải tệp lên.

## Con số kỳ vọng

Nằm trong [`expected-analysis.json`](expected-analysis.json) — **nguồn sự thật duy nhất** cho số
test, số cảnh báo của từng công cụ và độ phủ. CI đối chiếu tự động mỗi lần push.

Cố ý không chép lại con số ra đây: chép ra nhiều chỗ thì sớm muộn cũng lệch nhau, và lúc đó
không biết chỗ nào đúng.

```bash
python ../summarize.py vulnshop            # xem số liệu hiện tại
python ../summarize.py vulnshop --check    # đối chiếu, lệch thì exit 1
```

Lệch nghĩa là môi trường đã đổi — `summarize.py` gợi ý nguyên nhân thường gặp (JDK sai phiên bản,
find-sec-bugs không nạp, bộ rule Semgrep bị cập nhật).

## Vì sao dùng JdbcTemplate chứ không dùng JPA

JPA sinh câu lệnh SQL tự động nên sẽ che mất lỗi SQL injection — mà đó là loại cảnh báo tiêu biểu
nhất cần có trong case study. JdbcTemplate cho phép viết cả câu lệnh an toàn (tham số ràng buộc)
lẫn câu lệnh có lỗ hổng (nối chuỗi) cạnh nhau để đối chiếu.

H2 in-memory giữ cho case study tự chứa: chạy test không cần Docker hay cài đặt gì thêm.

## Chạy

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"   # bắt buộc JDK 21, xem ADR 0003
../run-analysis.sh vulnshop
```

Script chạy cả bốn công cụ rồi thu artifact về `docs/samples/vulnshop/`. Xem thống kê:

```bash
python ../summarize.py vulnshop
```

## Điểm đáng chú ý

Chi tiết đầy đủ trong [SEEDED.md](SEEDED.md). Bốn điều quan trọng nhất:

1. **Ba lỗi không công cụ nào bắt được** (NPE, off-by-one, chia cho 0) — chỉ test hỏng phát hiện.
   Đây là hạt nhân luận điểm của đề tài.
2. **Hai công cụ bất đồng** tại `ProductRepository:50`: SpotBugs báo SQL injection, Semgrep thì
   không. Phán đoán đúng đòi hỏi đọc `SortField` và chữ ký hàm ở controller — ngữ cảnh nằm ngoài
   dòng bị cảnh báo.
3. **Test xanh không có nghĩa là an toàn.** `findByUsername` có lỗ hổng SQL injection nhưng test
   đường đi bình thường vẫn xanh; test kế bên còn *chứng minh* lỗ hổng có thật mà vẫn xanh.
4. **Tám file không được test phủ** — có chủ đích, để sinh ra các cảnh báo không có bằng chứng
   kiểm thử, tạo trường hợp `INSUFFICIENT_EVIDENCE`.
