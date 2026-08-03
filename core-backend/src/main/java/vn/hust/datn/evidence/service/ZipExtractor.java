package vn.hust.datn.evidence.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.exception.ArchiveLimitExceededException;
import vn.hust.datn.evidence.exception.InvalidArchiveException;

/**
 * Giải nén zip streaming, an toàn — không bao giờ ghi ra filesystem, không bao giờ tin dữ liệu do
 * kẻ tấn công khai báo (header {@code ZipEntry.getSize()}, độ sâu, số lượng entry).
 *
 * <p><b>Mô hình đe doạ cho zip-slip ở đây khác ghi file thật</b>: ta không bao giờ gọi
 * {@code new File(baseDir, entry.getName())}, nên không có "thoát khỏi thư mục gốc" theo nghĩa cổ
 * điển. Nhưng {@code path} chuẩn hoá sẽ trở thành một phần key object storage
 * ({@code snapshots/{id}/{path}}) — key S3 là chuỗi opaque, không tự chuẩn hoá {@code ../}, nên
 * {@code snapshots/A/../B/x.txt} được MinIO lưu NGUYÊN VĂN như vậy và phá vỡ giả định "prefix
 * {@code snapshots/{id}/} cô lập từng snapshot", nền tảng của cơ chế dọn rác theo prefix
 * ({@link vn.hust.datn.evidence.storage.ObjectStorageClient#deleteObjectsByPrefix}) và của phân
 * quyền (một snapshot chỉ đọc được object trong prefix của chính nó). Vì vậy path phải được từ
 * chối, không chỉ "làm sạch", ngay khi phát hiện segment nguy hiểm.
 */
@Component
public class ZipExtractor {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_PATH_DEPTH = 30;
    private static final int BINARY_SNIFF_BYTES = 8000;

    private static final Set<String> JUNK_DIR_NAMES =
            Set.of(".git", ".svn", "node_modules", "target", "build", "dist", ".idea", ".gradle", "__pycache__", ".venv", "__MACOSX");
    private static final Set<String> JUNK_FILE_NAMES = Set.of(".DS_Store");
    private static final Set<String> JUNK_FILE_SUFFIXES = Set.of(".class", ".jar");

    /** Kiểm rẻ trước khi làm bất kỳ việc gì khác (tạo hàng DB...) — không tin {@code Content-Type} hay đuôi file. */
    public boolean looksLikeZip(byte[] rawContent) {
        if (rawContent.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (rawContent[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public List<ExtractedEntry> extract(byte[] zipBytes, UploadProperties limits) {
        List<ExtractedEntry> result = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalUncompressed = 0;
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String path = normalizePath(entry.getName());
                if (path == null || isJunk(path)) {
                    zis.closeEntry();
                    continue;
                }
                if (!seenPaths.add(path)) {
                    throw new InvalidArchiveException("Archive chứa hai entry trùng đường dẫn: '%s'".formatted(path));
                }

                fileCount++;
                if (fileCount > limits.maxFiles()) {
                    throw new ArchiveLimitExceededException(
                            "Archive vượt quá số file tối đa cho phép (%d)".formatted(limits.maxFiles()));
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                // KHÔNG dùng entry.getSize() để cấp phát hay để dừng sớm — đó là con số kẻ tấn công
                // tự khai trong header zip, không phải byte thực sẽ đọc ra.
                while ((read = zis.read(chunk)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > limits.maxUncompressedBytes()) {
                        throw new ArchiveLimitExceededException("Archive vượt quá tổng dung lượng giải nén tối đa cho phép");
                    }
                    buffer.write(chunk, 0, read);
                }
                zis.closeEntry();

                byte[] content = buffer.toByteArray();
                boolean binary = isBinary(content);
                Integer lineCount = binary ? null : countLines(content);
                boolean stored = content.length <= limits.maxStoredFileBytes();
                result.add(new ExtractedEntry(
                        path, stored ? content : null, content.length, binary, lineCount, stored ? sha256Hex(content) : null, stored));
            }
        } catch (IOException e) {
            throw new InvalidArchiveException("File tải lên không phải archive zip hợp lệ hoặc đã hỏng", e);
        }

        // Tỷ lệ nén: chặn "zip bomb" nén cực cao (vd file toàn số 0) mà vẫn lọt qua giới hạn byte
        // tuyệt đối vì zip gốc rất nhỏ.
        if (zipBytes.length > 0 && totalUncompressed / (double) zipBytes.length > limits.maxCompressionRatio()) {
            throw new ArchiveLimitExceededException(
                    "Archive vượt quá tỷ lệ nén tối đa cho phép (%d)".formatted(limits.maxCompressionRatio()));
        }

        return result;
    }

    /** Chuẩn hoá path, ném {@link InvalidArchiveException} nếu là zip-slip; trả {@code null} nếu nên bỏ qua lặng lẽ. */
    private String normalizePath(String rawPath) {
        String path = rawPath.replace('\\', '/');
        if (path.isEmpty() || path.equals("/")) {
            return null;
        }
        if (path.startsWith("/")) {
            throw new InvalidArchiveException("Đường dẫn tuyệt đối không được phép: '%s'".formatted(rawPath));
        }
        if (path.indexOf('\0') >= 0) {
            throw new InvalidArchiveException("Đường dẫn chứa byte NUL: '%s'".formatted(rawPath));
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new ArchiveLimitExceededException("Đường dẫn vượt quá %d ký tự: '%s'".formatted(MAX_PATH_LENGTH, rawPath));
        }
        if (path.matches("^[A-Za-z]:.*")) {
            throw new InvalidArchiveException("Đường dẫn ổ đĩa Windows không được phép: '%s'".formatted(rawPath));
        }

        String[] segments = path.split("/");
        if (segments.length > MAX_PATH_DEPTH) {
            throw new ArchiveLimitExceededException("Đường dẫn sâu quá %d cấp: '%s'".formatted(MAX_PATH_DEPTH, rawPath));
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                throw new InvalidArchiveException("Đường dẫn chứa segment rỗng hoặc '.': '%s'".formatted(rawPath));
            }
            if (segment.equals("..")) {
                throw new InvalidArchiveException("Đường dẫn chứa segment zip-slip '..': '%s'".formatted(rawPath));
            }
        }

        // Kiểm dư thừa: chuẩn hoá lại bằng java.nio.file.Path và xác nhận vẫn nằm trong gốc giả
        // định — lớp phòng thủ thứ hai, độc lập với vòng lặp segment ở trên.
        java.nio.file.Path base = java.nio.file.Path.of("/snapshot-root").normalize();
        java.nio.file.Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(base)) {
            throw new InvalidArchiveException("Đường dẫn thoát khỏi gốc snapshot: '%s'".formatted(rawPath));
        }

        return path;
    }

    private boolean isJunk(String path) {
        String[] segments = path.split("/");
        String fileName = segments[segments.length - 1];
        if (JUNK_FILE_NAMES.contains(fileName)) {
            return true;
        }
        for (String suffix : JUNK_FILE_SUFFIXES) {
            if (fileName.endsWith(suffix)) {
                return true;
            }
        }
        for (int i = 0; i < segments.length - 1; i++) {
            if (JUNK_DIR_NAMES.contains(segments[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean isBinary(byte[] content) {
        int limit = Math.min(content.length, BINARY_SNIFF_BYTES);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private int countLines(byte[] content) {
        if (content.length == 0) {
            return 0;
        }
        int lines = 0;
        for (byte b : content) {
            if (b == '\n') {
                lines++;
            }
        }
        if (content[content.length - 1] != '\n') {
            lines++;
        }
        return lines;
    }

    private String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc của mọi JVM tuân thủ chuẩn (JLS) - không thể xảy ra thật.
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }
}
