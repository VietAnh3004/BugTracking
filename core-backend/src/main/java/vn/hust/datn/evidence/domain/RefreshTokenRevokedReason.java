package vn.hust.datn.evidence.domain;

/** Lý do thu hồi refresh token. Khớp CHECK constraint refresh_token.revoked_reason. */
public enum RefreshTokenRevokedReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    EXPIRED
}
