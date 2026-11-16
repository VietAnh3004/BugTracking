package vn.hust.datn.evidence.service.account;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.config.properties.AccountProperties;
import vn.hust.datn.evidence.domain.AccountToken;
import vn.hust.datn.evidence.domain.AccountTokenPurpose;
import vn.hust.datn.evidence.exception.InvalidAccountTokenException;
import vn.hust.datn.evidence.repository.AccountTokenRepository;
import vn.hust.datn.evidence.service.AuthService;
import vn.hust.datn.evidence.service.TokenService;

/** Phát và dùng token một lần trong email ({@code account_token}, V13). */
@Service
public class AccountTokenService {

    private final AccountTokenRepository repository;
    private final TokenService tokenService;
    private final AccountProperties properties;

    public AccountTokenService(
            AccountTokenRepository repository, TokenService tokenService, AccountProperties properties) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * Phát token mới, vô hiệu hoá token cũ cùng mục đích — trừ khi token gần nhất mới phát chưa
     * quá {@code resendCooldown}: khi đó trả rỗng và KHÔNG gửi thư (chống dội email).
     *
     * @return chuỗi token gốc — thứ duy nhất đi vào email; DB chỉ giữ hash
     */
    @Transactional
    public Optional<String> issue(UUID userId, AccountTokenPurpose purpose) {
        OffsetDateTime now = AuthService.now();
        Optional<AccountToken> latest = repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose);
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minus(properties.resendCooldown()))) {
            return Optional.empty();
        }
        repository.invalidatePending(userId, purpose, now);

        String plaintext = tokenService.newOpaqueToken();
        AccountToken token = new AccountToken();
        token.setUserId(userId);
        token.setPurpose(purpose);
        token.setTokenHash(tokenService.sha256Hex(plaintext));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttlOf(purpose)));
        repository.save(token);
        return Optional.of(plaintext);
    }

    /**
     * Dùng token: đúng mục đích, chưa dùng, còn hạn — rồi đánh dấu đã dùng NGUYÊN TỬ.
     *
     * @return id người dùng sở hữu token
     * @throws InvalidAccountTokenException mọi trường hợp còn lại, cùng MỘT thông điệp
     */
    @Transactional
    public UUID consume(String plaintext, AccountTokenPurpose purpose) {
        OffsetDateTime now = AuthService.now();
        AccountToken token = repository
                .findByTokenHash(tokenService.sha256Hex(plaintext))
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt().isAfter(now))
                .orElseThrow(InvalidAccountTokenException::new);
        if (repository.markUsed(token.getId(), now) != 1) {
            throw new InvalidAccountTokenException();
        }
        return token.getUserId();
    }

    /** Vô hiệu mọi token chưa dùng của mục đích này — vd đã đổi mật khẩu thì link đặt lại cũ vô nghĩa. */
    @Transactional
    public void invalidate(UUID userId, AccountTokenPurpose purpose) {
        repository.invalidatePending(userId, purpose, AuthService.now());
    }

    public Duration ttlOf(AccountTokenPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> properties.verifyEmailTtl();
            case RESET_PASSWORD -> properties.resetPasswordTtl();
        };
    }
}
