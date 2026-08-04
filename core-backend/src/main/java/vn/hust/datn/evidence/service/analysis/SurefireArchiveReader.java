package vn.hust.datn.evidence.service.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import vn.hust.datn.evidence.config.properties.UploadProperties;

/**
 * Rút các file {@code TEST-*.xml} ra khỏi một zip báo cáo Surefire.
 *
 * <h2>Vì sao KHÔNG dùng lại {@code ZipExtractor}</h2>
 *
 * {@code ZipExtractor} lọc bỏ mọi entry nằm dưới thư mục tên {@code target} — hợp lý cho mã nguồn
 * người dùng upload, nhưng báo cáo Surefire nằm đúng ở {@code target/surefire-reports/}. Dùng lại
 * nó thì một zip nén từ gốc dự án bị nuốt sạch báo cáo và cho ra "0 test" mà không báo lỗi gì —
 * đúng loại hỏng im lặng mà cả đợt này chống. Bộ lọc rác là chính sách của MỘT luồng nhập, không
 * phải tính chất chung của việc đọc zip.
 *
 * <p>Giới hạn chống zip-bomb thì dùng chung {@link UploadProperties} — không hardcode lại ngưỡng ở
 * đây, và cũng không nới lỏng chúng chỉ vì đây là artifact chứ không phải mã nguồn.
 */
@Component
public class SurefireArchiveReader {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** Số tên file liệt kê trong thông điệp lỗi — đủ để chẩn đoán, không đủ để làm ngập log. */
    private static final int MAX_NAMES_IN_ERROR = 10;

    private final UploadProperties limits;

    public SurefireArchiveReader(UploadProperties limits) {
        this.limits = limits;
    }

    /** Nhận dạng bằng magic number, KHÔNG bằng {@code Content-Type} hay đuôi file. */
    public boolean looksLikeArchive(byte[] rawContent) {
        if (rawContent == null || rawContent.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (rawContent[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return các báo cáo đã sắp theo đường dẫn trong zip — thứ tự ổn định là điều kiện để
     *     {@code seq_in_run} không đổi giữa hai lần nạp cùng một zip
     * @throws InvalidArtifactException khi zip hỏng, vượt giới hạn, hoặc KHÔNG có báo cáo nào
     */
    public List<NamedArtifact> read(byte[] zipBytes) {
        List<NamedArtifact> reports = new ArrayList<>();
        List<String> boQua = new ArrayList<>();
        long totalUncompressed = 0;
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                fileCount++;
                if (fileCount > limits.maxFiles()) {
                    throw new InvalidArtifactException(
                            "Zip bao cao vuot qua so file toi da cho phep (%d)".formatted(limits.maxFiles()));
                }

                String path = entry.getName().replace('\\', '/');
                if (!isSurefireReport(path)) {
                    boQua.add(path);
                    zis.closeEntry();
                    continue;
                }

                // KHONG dung entry.getSize(): do la con so trong header do nguoi tao zip tu khai.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = zis.read(chunk)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > limits.maxUncompressedBytes()) {
                        throw new InvalidArtifactException("Zip bao cao vuot qua tong dung luong giai nen toi da");
                    }
                    buffer.write(chunk, 0, read);
                }
                zis.closeEntry();
                reports.add(new NamedArtifact(path, buffer.toByteArray()));
            }
        } catch (IOException e) {
            throw new InvalidArtifactException("Zip bao cao Surefire khong doc duoc hoac da hong", e);
        }

        if (zipBytes.length > 0 && totalUncompressed / (double) zipBytes.length > limits.maxCompressionRatio()) {
            throw new InvalidArtifactException("Zip bao cao vuot qua ty le nen toi da cho phep (%d)"
                    .formatted(limits.maxCompressionRatio()));
        }
        if (reports.isEmpty()) {
            // Liet ke thu tim thay gi: nguyen nhan thuong gap la nguoi dung nen nham thu muc, va
            // mot loi "khong co bao cao nao" tran trui khong giup ho sua duoc.
            throw new InvalidArtifactException(
                    "Zip khong chua bao cao Surefire nao (can file ten TEST-*.xml). Tim thay %d entry: %s"
                            .formatted(
                                    boQua.size(),
                                    boQua.stream().limit(MAX_NAMES_IN_ERROR).toList()));
        }

        reports.sort(Comparator.comparing(NamedArtifact::name));
        return reports;
    }

    /**
     * Surefire sinh cả {@code TEST-*.xml} (JUnit XML, thứ ta cần) lẫn {@code *.txt} tóm tắt và
     * {@code *-output.txt}. Chỉ tiền tố {@code TEST-} mới là báo cáo có cấu trúc.
     */
    private boolean isSurefireReport(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.startsWith("TEST-") && fileName.endsWith(".xml");
    }
}
