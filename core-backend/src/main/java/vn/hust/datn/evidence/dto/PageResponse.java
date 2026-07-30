package vn.hust.datn.evidence.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Tự định nghĩa thay vì trả {@code org.springframework.data.domain.Page} trực tiếp: shape JSON
 * của {@code Page} không ổn định giữa các bản Spring Boot (đổi ở 3.3+), và nó làm bẩn hợp đồng
 * OpenAPI với ~10 property nội bộ (pageable, sort, ...) không ai dùng.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
