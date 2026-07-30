package vn.hust.datn.evidence.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tự định nghĩa thay vì trả {@code org.springframework.data.domain.Page} trực tiếp: shape JSON
 * của {@code Page} không ổn định giữa các bản Spring Boot (đổi ở 3.3+), và nó làm bẩn hợp đồng
 * OpenAPI với ~10 property nội bộ (pageable, sort, ...) không ai dùng.
 */
public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
