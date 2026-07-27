package vn.hust.datn.casestudy.vulnshop.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hust.datn.casestudy.vulnshop.domain.User;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService() {
        return new UserService(userRepository);
    }

    @Test
    void emailDomainOf_nguoiDungTonTai_traVeDomain() {
        when(userRepository.findByUsername("anh"))
                .thenReturn(List.of(new User(1, "anh", "anh@hust.edu.vn", "hash", "USER")));

        assertEquals("hust.edu.vn", userService().emailDomainOf("anh"));
    }

    /**
     * TEST NAY CO TINH HONG.
     *
     * <p>Khong tim thay nguoi dung -> findFirst tra ve null -> goi email() tren null -> NPE.
     *
     * <p>Day la bang chung quan trong nhat cua case study: SpotBugs KHONG bat duoc dang NPE nay
     * (da xac nhan o spike tuan 1), nhung stack trace cua test hong tro thang ve dong loi. Mot
     * nguon bang chung la khong du - do la luan diem trung tam cua de tai.
     */
    @Test
    @DisplayName("Khong tim thay nguoi dung: phai bao loi ro rang thay vi NPE")
    void emailDomainOf_khongTonTai_khongDuocNemNPE() {
        when(userRepository.findByUsername("khong-ton-tai")).thenReturn(List.of());

        assertEquals("", userService().emailDomainOf("khong-ton-tai"));
    }

    @Test
    void countByRole_demDungSoLuong() {
        when(userRepository.findByRole("ADMIN"))
                .thenReturn(List.of(
                        new User(1, "a", "a@x.vn", "h", "ADMIN"), new User(2, "b", "b@x.vn", "h", "ADMIN")));

        assertEquals(2, userService().countByRole("ADMIN"));
    }

    @Test
    void countByRole_khongCoAi_traVeKhong() {
        when(userRepository.findByRole("SUPERADMIN")).thenReturn(List.of());

        assertEquals(0, userService().countByRole("SUPERADMIN"));
    }
}
