package vn.hust.datn.casestudy.vulnshop.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chi phuong thuc readUserFileSafely va store duoc test.
 *
 * <p>readUserFile (path traversal) va readFirstByte (ro ri tai nguyen) CO TINH khong duoc test phu.
 * Canh bao tai hai cho do se khong co bang chung kiem thu di kem - tao ra truong hop
 * INSUFFICIENT_EVIDENCE cho he thong danh gia. Neu moi dong deu duoc phu thi verdict do khong bao
 * gio xuat hien duoc trong demo.
 */
class FileStorageServiceTest {

    @Test
    @DisplayName("Doc tep hop le trong thu muc upload")
    void readUserFileSafely_tepHopLe_docDuocNoiDung(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("ghichu.txt"), "xin chao", StandardCharsets.UTF_8);
        FileStorageService service = new FileStorageService(tempDir);

        assertEquals("xin chao", service.readUserFileSafely("ghichu.txt"));
    }

    /**
     * Bang chung MAU THUAN voi canh bao path traversal tai readUserFileSafely.
     *
     * <p>Test nay XANH: duong dan vuot ra ngoai bi chan dung nhu thiet ke. Neu cong cu bao path
     * traversal o day thi day chinh la bang chung phan bac - loai quan he CONTRADICTS trong
     * Evidence Model.
     */
    @Test
    @DisplayName("Duong dan vuot ra ngoai thu muc bi chan")
    void readUserFileSafely_duongDanVuotRaNgoai_biChan(@TempDir Path tempDir) {
        FileStorageService service = new FileStorageService(tempDir);

        assertThrows(SecurityException.class, () -> service.readUserFileSafely("../../etc/passwd"));
    }

    @Test
    void store_ghiRoiDocLai_duocNoiDungGiongNhau(@TempDir Path tempDir) throws IOException {
        FileStorageService service = new FileStorageService(tempDir);
        service.store("a/b/ghichu.txt", "noi dung".getBytes(StandardCharsets.UTF_8));

        assertEquals("noi dung", service.readUserFileSafely("a/b/ghichu.txt"));
    }

    @Test
    void store_duongDanVuotRaNgoai_biChan(@TempDir Path tempDir) {
        FileStorageService service = new FileStorageService(tempDir);

        assertThrows(
                SecurityException.class,
                () -> service.store("../thoat.txt", "x".getBytes(StandardCharsets.UTF_8)));
    }
}
