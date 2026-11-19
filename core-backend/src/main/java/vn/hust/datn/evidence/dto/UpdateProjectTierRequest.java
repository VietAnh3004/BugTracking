package vn.hust.datn.evidence.dto;

import jakarta.validation.constraints.NotNull;

import vn.hust.datn.evidence.domain.ProjectTier;

import io.swagger.v3.oas.annotations.media.Schema;

/** Đổi hạng một dự án (admin). */
public record UpdateProjectTierRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ProjectTier tier) {}
