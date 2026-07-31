package vn.hust.datn.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokensResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserResponse user) {}
