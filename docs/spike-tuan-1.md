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

### Cập nhật sau khi cài JDK 21

SpotBugs chạy sạch trên JDK 21 (`mvn spotbugs:spotbugs`, xem
[ADR 0003](adr/0003-chuan-hoa-jdk-21.md)) và tìm ra 1 cảnh báo:

```
DM_CONVERT_CASE  pri=3  UserRepository.java:29-29
  Use of non-localized String.toUpperCase() or String.toLowerCase()
```

**Cả ba nguồn bằng chứng giờ trỏ về cùng dòng 29:**

| Nguồn | Vị trí | Nội dung |
|---|---|---|
| `STATIC_WARNING` (SpotBugs) | `UserRepository.java:29` | `DM_CONVERT_CASE` |
| `STACK_TRACE` (Surefire) | `UserRepository.java:29` | NPE tại `toLowerCase()` |
| `COVERAGE_INFORMATION` (JaCoCo) | `UserRepository.java:29` | `COVERED` |

Đáng chú ý: SpotBugs không báo `NP_NULL_ON_SOME_PATH` cho lỗi NPE thật ở dòng này — công cụ phân
tích tĩnh và bằng chứng runtime (stack trace) phát hiện **hai vấn đề khác nhau** ở cùng một vị
trí. Đây là minh hoạ trực tiếp cho lý do đề tài cần kết hợp nhiều loại bằng chứng thay vì chỉ
dựa vào một nguồn: SpotBugs một mình sẽ bỏ sót lỗi NPE, nhưng bằng chứng kiểm thử tại đúng vị
trí đó lại cho thấy code này đã crash trên thực tế.

**Kết luận đầy đủ**: liên kết ba chiều `STATIC_WARNING ↔ CODE_CONTEXT ↔ TEST_RESULT/STACK_TRACE/
COVERAGE_INFORMATION` theo `file:line` là khả thi trên toàn bộ pipeline, không chỉ một phần.

**Semgrep chưa thử** — hỗ trợ Windows kém vì core là binary OCaml; sẽ chạy qua container đúng
như thiết kế đã định, việc này để tuần 2.

## Việc cần làm tiếp

1. Chạy Semgrep qua Docker trên cùng dự án spike ở tuần 2, kiểm tra `file:line` trong SARIF có
   khớp với vị trí JaCoCo và stack trace hay không — làm nốt phần còn thiếu duy nhất.
2. Điểm cần chú ý khi hiện thực adapter: analyzer trả về **0 cảnh báo** phải bị coi là đáng ngờ
   và báo lỗi, không được lặng lẽ ghi một `analysis_run` rỗng — lần chạy trên JDK 25 (0 cảnh
   báo do môi trường hỏng chứ không phải code sạch) là ví dụ thật cho đúng tình huống này.
3. Ghi nhận phát hiện "SpotBugs bỏ sót NPE mà stack trace bắt được" vào phần motivation của
   chương 2 báo cáo — đây là bằng chứng thực nghiệm sớm nhất cho luận điểm trung tâm của đề tài.
