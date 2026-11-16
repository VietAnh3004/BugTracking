package vn.hust.datn.evidence.domain;

/** Mục đích của một {@link AccountToken}. Khớp CHECK constraint {@code account_token.purpose}. */
public enum AccountTokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
