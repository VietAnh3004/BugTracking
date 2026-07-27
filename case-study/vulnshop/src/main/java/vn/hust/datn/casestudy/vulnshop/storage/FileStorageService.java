package vn.hust.datn.casestudy.vulnshop.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;

/**
 * Lưu và đọc tệp người dùng tải lên.
 *
 * <p>Phần lớn lớp này KHÔNG được test phủ — có chủ đích, để sinh ra các cảnh báo không có bằng
 * chứng kiểm thử đi kèm.
 */
@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService() {
        this(Paths.get("uploads"));
    }

    public FileStorageService(Path uploadRoot) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
    }

    /**
     * SEEDED — lỗi thật: path traversal.
     *
     * <p>{@code filename} đến từ tham số HTTP và được ghép thẳng vào đường dẫn. Truyền
     * {@code ../../etc/passwd} sẽ đọc được tệp ngoài thư mục upload.
     *
     * <p>Kỳ vọng Semgrep và find-sec-bugs cùng bắt (PATH_TRAVERSAL_IN).
     */
    public String readUserFile(String filename) throws IOException {
        File target = new File(uploadRoot.toString() + File.separator + filename);
        return Files.readString(target.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * SEEDED — false positive: ghép đường dẫn nhưng đã kiểm tra biên đúng cách.
     *
     * <p>Công cụ nhiều khả năng báo path traversal ở đây vì thấy đường dẫn được ghép từ dữ liệu
     * bên ngoài. Nhưng đường dẫn đã được {@code normalize()} rồi kiểm tra {@code startsWith} với
     * thư mục gốc — {@code ../} bị triệt tiêu trước khi so sánh, nên không thoát ra ngoài được.
     *
     * <p>Đây là cách phòng chống path traversal đúng chuẩn. Cảnh báo tại đây là dương tính giả,
     * và muốn kết luận được thì phải đọc cả ba dòng lệnh chứ không chỉ nhìn dòng bị đánh dấu.
     */
    public String readUserFileSafely(String filename) throws IOException {
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new SecurityException("Duong dan nam ngoai thu muc upload: " + filename);
        }
        return Files.readString(resolved, StandardCharsets.UTF_8);
    }

    /**
     * SEEDED — lỗi thật: rò rỉ tài nguyên.
     *
     * <p>{@code InputStream} không nằm trong try-with-resources và không được đóng ở nhánh nào.
     * Gọi nhiều lần sẽ cạn file descriptor.
     *
     * <p>Kỳ vọng SpotBugs bắt (OBL_UNSATISFIED_OBLIGATION hoặc OS_OPEN_STREAM).
     */
    public int readFirstByte(String filename) throws IOException {
        InputStream input = new FileInputStream(uploadRoot.resolve(filename).toFile());
        int first = input.read();
        return first;
    }

    /** Ghi tệp, có kiểm tra biên. Không có lỗi cài đặt. */
    public void store(String filename, byte[] content) throws IOException {
        Path resolved = uploadRoot.resolve(filename).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new SecurityException("Duong dan nam ngoai thu muc upload: " + filename);
        }
        Files.createDirectories(resolved.getParent());
        Files.write(resolved, content);
    }
}
