package vn.hust.datn.casestudy.vulnshop.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.hust.datn.casestudy.vulnshop.domain.User;

/**
 * Test tich hop tren H2 that.
 *
 * <p>Muc dich chinh: phu duoc dong co SQL injection trong findByUsername, de canh bao tai do CO
 * bang chung kiem thu di kem thay vi roi vao trang thai khong du bang chung.
 */
@JdbcTest
@Import(UserRepository.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("DELETE FROM users");
        userRepository.insert(new User(1, "anh", "anh@hust.edu.vn", "hash1", "ADMIN"));
        userRepository.insert(new User(2, "binh", "binh@hust.edu.vn", "hash2", "USER"));
    }

    /**
     * Test XANH tren doan ma CO LO HONG.
     *
     * <p>Day la truong hop dang chu y nhat cua case study. Duong di binh thuong chay dung, nen
     * test mau xanh - nhung ham nay van dinh SQL injection. Neu AI Agent ket luan "co test phu va
     * test xanh nen canh bao la duong tinh gia" thi no da sai.
     *
     * <p>Bang chung kiem thu o day KHONG bac bo duoc canh bao; no chi noi rang happy path chua vo.
     */
    @Test
    @DisplayName("Dau vao binh thuong: tim dung nguoi dung")
    void findByUsername_dauVaoBinhThuong_timDungNguoiDung() {
        List<User> found = userRepository.findByUsername("anh");

        assertEquals(1, found.size());
        assertEquals("anh@hust.edu.vn", found.get(0).email());
    }

    /**
     * Test XANH chung minh lo hong CO THAT.
     *
     * <p>Chuoi ' OR '1'='1 lam menh de WHERE luon dung, tra ve toan bo bang thay vi mot ban ghi.
     * Test nay xanh chinh vi lo hong ton tai - neu ai do va loi bang tham so rang buoc, no se do.
     *
     * <p>Day la loai bang chung manh nhat cho verdict LIKELY_TRUE_POSITIVE: mot TEST_RESULT dang
     * pass nhung noi dung cua no XAC NHAN canh bao tinh la dung. He thong phai phan biet duoc
     * "test xanh" voi "test xanh chung minh dieu gi".
     */
    @Test
    @DisplayName("SQL injection: dau nhay don lam WHERE luon dung, lo toan bo bang")
    void findByUsername_sqlInjection_traVeToanBoBang() {
        List<User> leaked = userRepository.findByUsername("' OR '1'='1");

        assertEquals(2, leaked.size(), "Lo hong SQL injection cho phep doc toan bo bang users");
    }

    @Test
    @DisplayName("Truy van an toan: tham so rang buoc khong bi injection")
    void findByRole_thamSoRangBuoc_khongBiInjection() {
        List<User> result = userRepository.findByRole("' OR '1'='1");

        assertTrue(result.isEmpty(), "Tham so rang buoc coi ca chuoi la gia tri, khong phai cu phap SQL");
    }
}
