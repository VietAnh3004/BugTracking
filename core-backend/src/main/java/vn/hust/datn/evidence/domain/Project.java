package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity đầu tiên của dự án, map vào bảng {@code project} đã có sẵn từ V1__baseline.sql.
 *
 * <p>Cố tình tách thành bước riêng (tuần 3, bước 1) trước khi đụng tới schema V2 mới: đây là lần
 * đầu {@code ddl-auto: validate} chạy thật, nên cô lập câu hỏi "mapping UUID/TIMESTAMPTZ có đúng
 * không" khỏi câu hỏi "schema V2 mới viết có đúng không" — 5 cột dễ gỡ lỗi hơn 60 cột.
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
