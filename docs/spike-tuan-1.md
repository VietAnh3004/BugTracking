# Spike tuần 1 — Ba nguồn bằng chứng có liên kết được theo `file:line` không?

**Ngày**: 26/07/2026
**Mã nguồn**: [case-study/spike/](../case-study/spike/)

## Câu hỏi

Toàn bộ Unified Evidence Model dựa trên giả định rằng cảnh báo phân tích tĩnh, stack trace của
test hỏng và số liệu độ phủ đều quy chiếu về cùng một `file:line`. Nếu giả định này sai, cơ chế
liên kết warning-code-test phải thiết kế lại — và tuần 1 là lúc rẻ nhất để biết.

## Cách làm

Một dự án Maven nhỏ chứa:

- `UserRepository.findEmailDomain()` — lỗi NPE thật: `users.get()` trả về `null` rồi bị gọi
  `toLowerCase()` ngay sau đó.
- `countUsersWithDomain()` — kiểm tra null thừa, kiểu mã sinh cảnh báo nhưng không phải lỗi.
- Ba test JUnit, trong đó một test cố tình hỏng để tạo `FAILED_TEST` + `STACK_TRACE`.

Chạy `mvn test` (Surefire + JaCoCo) và `mvn spotbugs:spotbugs`, rồi đối chiếu bằng
[`check_alignment.py`](../case-study/spike/check_alignment.py).

## Kết quả

### Đã kiểm chứng được

| Nguồn | Artifact | Trạng thái |
|---|---|---|
| `TEST_RESULT` | `target/surefire-reports/TEST-*.xml` | 3 test, phân loại pass/fail chính xác |
| `FAILED_TEST` | cùng file | bóc được kiểu lỗi và thông điệp assertion |
| `STACK_TRACE` | cùng file | bóc được frame `UserRepository.findEmailDomain` → dòng 29 |
| `COVERAGE_INFORMATION` | `target/site/jacoco/jacoco.xml` | 12/13 dòng, đủ để phân biệt COVERED / PARTIAL / MISSED |

**Đối chiếu**: dòng 29 trong stack trace khớp chính xác với dòng 29 trong dữ liệu độ phủ, trạng
thái `COVERED`. Frame còn lại nằm trong lớp test nên không có số liệu — đúng như dự kiến, vì
JaCoCo chỉ đo mã production.

→ **Liên kết `STACK_TRACE` ↔ `COVERAGE_INFORMATION` ↔ vị trí mã nguồn là khả thi.**

Điều này cũng xác nhận cơ chế sinh quan hệ `CONTRADICTS` là hiện thực được: biết một dòng có
được test phủ hay không chính là cơ sở để nói "cảnh báo này nằm ở đoạn mã đã được kiểm thử và
không test nào hỏng".

### Chưa kiểm chứng được

**SpotBugs không chạy trên JDK 25** (`Error scanning java/lang/Object`, kết quả 0 cảnh báo — cả
bản 4.8.6 lẫn 4.9.3). Chân `STATIC_WARNING` của spike vì vậy còn bỏ ngỏ.

Đây không phải vấn đề của thiết kế mà của môi trường; xử lý ở [ADR 0003](adr/0003-chuan-hoa-jdk-21.md):
chuẩn hoá JDK 21 và chạy analyzer trong container.

**Semgrep chưa thử**: hỗ trợ Windows kém vì core là binary OCaml. Sẽ chạy qua container, đúng
như thiết kế đã định.

## Việc cần làm tiếp

1. Cài JDK 21, chạy lại `mvn spotbugs:spotbugs` và bổ sung kết quả vào mục "chưa kiểm chứng".
2. Chạy Semgrep qua Docker trên cùng dự án spike, kiểm tra `file:line` trong SARIF có khớp với
   vị trí JaCoCo và stack trace hay không.
3. Điểm cần chú ý khi hiện thực adapter: analyzer trả về **0 cảnh báo** phải bị coi là đáng ngờ
   và báo lỗi, không được lặng lẽ ghi một `analysis_run` rỗng — spike này là ví dụ đúng cho tình
   huống đó.
