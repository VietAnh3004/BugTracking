package vn.hust.datn.evidence.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nội dung một file theo khoảng dòng. Cắt lát ở SERVER, không gửi cả file rồi để client cắt — file
 * 12.000 dòng gửi nguyên si làm chậm mọi lần mở file trong source viewer.
 *
 * <p>{@code lines} là danh sách dòng đã tách sẵn (không phải một chuỗi lớn): source viewer render
 * từng dòng kèm số dòng, tách ở server tránh việc FE phải đoán lại quy ước xuống dòng của file
 * (LF/CRLF) và làm lệch số dòng so với {@code totalLines} — đúng loại hỏng im lặng dự án này chống.
 *
 * <p>{@code totalLines} lấy nguyên từ {@code snapshot_file.line_count} (tính một lần lúc upload),
 * KHÔNG đếm lại khi đọc — hai lần đếm bằng hai đoạn code khác nhau là hai cơ hội lệch nhau.
 */
public record FileContentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dòng đầu tiên trả về, đánh số từ 1")
                int fromLine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dòng cuối cùng trả về (bao gồm), 0 nếu file rỗng")
                int toLine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Tổng số dòng của cả file, lấy từ chỉ mục")
                int totalLines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true khi chưa trả hết file — FE hiển thị banner")
                boolean truncated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> lines) {}
