package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(@NotBlank(message = "Tên dự án không được để trống") String name, String description) {}
