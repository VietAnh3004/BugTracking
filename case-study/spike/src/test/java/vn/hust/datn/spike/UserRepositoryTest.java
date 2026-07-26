package vn.hust.datn.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserRepositoryTest {

    @Test
    void findEmailDomain_nguoiDungTonTai_traVeDomain() {
        UserRepository repo = new UserRepository();
        repo.addUser("anh", "anh@hust.edu.vn");

        assertEquals("hust.edu.vn", repo.findEmailDomain("anh"));
    }

    /**
     * Test này CỐ TÌNH hỏng.
     *
     * <p>Nó tạo ra một FAILED_TEST kèm STACK_TRACE trỏ về dòng lỗi trong UserRepository — đúng
     * loại bằng chứng mạnh nhất để khẳng định một cảnh báo là true positive. Spike kiểm tra xem
     * vị trí trong stack trace có khớp vị trí cảnh báo của SpotBugs hay không.
     */
    @Test
    void findEmailDomain_khongTonTai_traVeRong() {
        UserRepository repo = new UserRepository();

        assertEquals("", repo.findEmailDomain("khong-ton-tai"));
    }

    @Test
    void countUsersWithDomain_demDungSoLuong() {
        UserRepository repo = new UserRepository();
        repo.addUser("a", "a@hust.edu.vn");
        repo.addUser("b", "b@gmail.com");

        assertEquals(1, repo.countUsersWithDomain("hust.edu.vn"));
    }
}
