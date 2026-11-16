package vn.hust.datn.evidence.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kết quả nạp file nhãn. Dòng không khớp cảnh báo nào được LIỆT KÊ chứ không lặng lẽ bỏ: một nhãn
 * rơi mất vì đường dẫn lệch một ký tự trông y hệt "cảnh báo đó chưa ai gán", và người dùng phải
 * thấy được để sửa.
 */
public record GroundTruthImportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số nhãn đã ghi (một mục có thể khớp nhiều cảnh báo)")
                int labelsWritten,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Số mục trong file khớp ít nhất một cảnh báo")
                int entriesMatched,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Mục không khớp cảnh báo nào, dạng tool/luật@file:dòng")
                List<String> unmatched) {}
