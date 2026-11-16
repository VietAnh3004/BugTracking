package vn.hust.datn.evidence.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code app_user} (V3). Tên bảng tránh từ khoá SQL {@code user}.
 *
 * <p>{@code passwordHash} không bao giờ được map ra DTO — chỉ dùng trong {@code service} để so
 * khớp bằng BCrypt.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    /** {@code null} với tài khoản chỉ đăng nhập bằng Google (V13). */
    @Column(name = "password_hash")
    private String passwordHash;

    /** {@code null} = chưa xác thực email: chưa đăng nhập được bằng mật khẩu. */
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    /** Claim {@code sub} của Google ID token — định danh bất biến, không phải email. */
    @Column(name = "google_subject")
    private String googleSubject;

    @Column(name = "google_picture_url")
    private String googlePictureUrl;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
