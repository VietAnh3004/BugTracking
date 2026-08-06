package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

/**
 * Tổng hợp độ phủ ở mức method.
 *
 * <p>{@code className} giữ nguyên dạng JaCoCo — dùng dấu {@code /} chứ không phải {@code .}. Đổi
 * dạng ở tầng đọc là tạo một phép biến đổi nữa để sai; việc quy về cùng dạng làm ngay tại chỗ so.
 *
 * <p>KHÔNG có quan hệ nào sang {@code coverage_line}: JaCoCo không cho biết "dòng 42 thuộc method
 * nào" (cấu trúc kép, method chỉ có dòng đầu và không theo thứ tự dòng — phát hiện 3).
 */
public record CoverageMethodRow(
        UUID evidenceRecordId, String className, String methodName, int coveredInstructions) {

    /** Method có thực sự được chạy hay không. Khai báo có mặt trong báo cáo không có nghĩa là đã chạy. */
    public boolean executed() {
        return coveredInstructions > 0;
    }
}
