package vn.hust.datn.evidence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    // So khớp không phân biệt hoa/thường, khớp UNIQUE INDEX ux_app_user_email_lower ở V3 —
    // tránh đăng ký trùng kiểu 'A@x.com' / 'a@x.com'.
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
