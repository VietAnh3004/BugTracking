package vn.hust.datn.evidence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Không bao giờ trả entity JPA ra API — DTO record riêng cho mọi response (§1bis.2 kế hoạch). */
public record ProjectResponse(UUID id, String name, String description, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
