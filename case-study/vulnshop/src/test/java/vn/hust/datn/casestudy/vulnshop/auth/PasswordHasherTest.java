package vn.hust.datn.casestudy.vulnshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ca hai phuong thuc bang MD5 deu duoc test phu va deu XANH.
 *
 * <p>Day la chu dich. Canh bao "weak hashing algorithm" tai hashPassword la loi that, con tai
 * checksumForDeduplication la duong tinh gia - nhung do phu kiem thu KHONG phan biet duoc hai
 * truong hop nay, vi ca hai deu chay dung theo dac ta cua chung.
 *
 * <p>Muon phan biet phai doc xem gia tri bam duoc dung vao viec gi. Do la ly do he thong can
 * CODE_CONTEXT chu khong chi can TEST_RESULT.
 */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashPassword_cungMatKhau_choCungGiaTri() {
        assertEquals(hasher.hashPassword("matkhau123"), hasher.hashPassword("matkhau123"));
    }

    @Test
    void hashPassword_khacMatKhau_choKhacGiaTri() {
        assertNotEquals(hasher.hashPassword("matkhau123"), hasher.hashPassword("matkhau124"));
    }

    @Test
    void matches_dungMatKhau_traVeDung() {
        String hash = hasher.hashPassword("matkhau123");

        assertTrue(hasher.matches("matkhau123", hash));
    }

    @Test
    @DisplayName("Checksum: cung noi dung cho cung ma bam")
    void checksum_cungNoiDung_choCungMaBam() {
        byte[] content = "noi dung tep".getBytes(StandardCharsets.UTF_8);

        assertEquals(hasher.checksumForDeduplication(content), hasher.checksumForDeduplication(content));
    }

    @Test
    void checksum_khacNoiDung_choKhacMaBam() {
        assertNotEquals(
                hasher.checksumForDeduplication("tep A".getBytes(StandardCharsets.UTF_8)),
                hasher.checksumForDeduplication("tep B".getBytes(StandardCharsets.UTF_8)));
    }
}
