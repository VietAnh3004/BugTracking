package vn.hust.datn.evidence.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;
import vn.hust.datn.evidence.storage.ObjectStorageException;

/**
 * Đọc nội dung một file trong snapshot cho các tiến trình chạy NGOÀI phiên người dùng.
 *
 * <h2>Vì sao không dùng {@code SnapshotQueryService.readFile}</h2>
 *
 * Cái đó phục vụ giao diện: nó đòi {@code ownerId} để phân quyền, và cắt trần 2000/5000 dòng để
 * một request không kéo về cả file khổng lồ. Cả hai đều sai cho pipeline phân tích — nó chạy bất
 * đồng bộ trong sandbox executor, không có phiên người dùng nào, và JavaParser cần **toàn văn**
 * chứ không phải một lát cắt (parse nửa file cho ra một AST vô nghĩa).
 *
 * <p>Phân quyền cho đường này xảy ra ở entry point: người gọi đã phải sở hữu snapshot mới tạo
 * được {@code analysis_run} trên nó.
 *
 * <h2>Trả {@code Optional} chứ không ném</h2>
 *
 * Mã nguồn người dùng là dữ liệu KHÔNG TIN CẬY. Một file thiếu, quá lớn nên không lưu nội dung,
 * hay là nhị phân đều là chuyện bình thường trong một repo bất kỳ — chúng không được phép làm hỏng
 * cả lần phân tích. Người gọi có nhiệm vụ ĐẾM số lần rỗng và báo ra, chứ không bỏ qua im lặng.
 */
@Service
@Transactional(readOnly = true)
public class SnapshotSourceReader {

    private static final Logger log = LoggerFactory.getLogger(SnapshotSourceReader.class);

    private final SnapshotFileRepository snapshotFileRepository;
    private final ObjectStorageClient storageClient;

    public SnapshotSourceReader(SnapshotFileRepository snapshotFileRepository, ObjectStorageClient storageClient) {
        this.snapshotFileRepository = snapshotFileRepository;
        this.storageClient = storageClient;
    }

    /**
     * Toàn văn của một file, giải mã UTF-8. Rỗng khi không đọc được vì bất kỳ lý do hợp lệ nào.
     *
     * @param path đường dẫn ĐÃ chuẩn hoá về {@code snapshot_file.path} (tra khớp chính xác nhờ
     *     {@code UNIQUE (snapshot_id, path)}) — người gọi phải đưa qua {@code SnapshotPathResolver}
     *     trước, ở đây không đoán thêm lần nữa
     */
    public Optional<String> readText(UUID snapshotId, String path) {
        Optional<SnapshotFile> found = snapshotFileRepository.findBySnapshotIdAndPath(snapshotId, path);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SnapshotFile file = found.get();
        if (file.getEntryType() != SnapshotEntryType.FILE || file.isBinary() || !file.isStored()) {
            return Optional.empty();
        }

        byte[] content;
        try {
            content = storageClient.getObject(file.getStorageKey()).content();
        } catch (ObjectStorageException e) {
            // Chỉ mục nói có nội dung nhưng object storage không có. Không ném lên: một file trôi
            // mất không được phép biến cả lần phân tích thành HTTP 500 kèm stack trace. Nếu MinIO
            // hỏng toàn bộ thì mọi file đều rỗng, và AnalysisRunService.decide() bắt được điều đó
            // rồi ghi run FAILED kèm lý do người vận hành đọc được — đúng khuôn xử lý lỗi của
            // pipeline này.
            log.warn("Snapshot {}: khong lay duoc object cua '{}' ({}) — bo qua", snapshotId, path, e.getMessage());
            return Optional.empty();
        }

        // Nội dung lệch với băm ghi lúc upload nghĩa là object storage đã trôi khỏi chỉ mục. Với
        // CODE_CONTEXT điều đó nguy hiểm hơn một file thiếu: mọi số dòng suy ra sẽ trỏ sai chỗ mà
        // vẫn trông hợp lệ. Bỏ file đó và nói ra, đừng dựng bằng chứng trên nền không tin được.
        if (file.getSha256() != null) {
            String actual = sha256Hex(content);
            if (!actual.equals(file.getSha256())) {
                log.warn(
                        "Snapshot {}: noi dung '{}' khong khop sha256 ghi luc upload (cho {}, doc duoc {}) — bo qua",
                        snapshotId,
                        path,
                        file.getSha256(),
                        actual);
                return Optional.empty();
            }
        }

        return Optional.of(new String(content, StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM khong co SHA-256", e);
        }
    }
}
