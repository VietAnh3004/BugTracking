package vn.hust.datn.evidence.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.exception.InvalidArchiveException;
import vn.hust.datn.evidence.storage.ObjectStorageClient;

/**
 * Luồng upload đồng bộ: giải nén → ghi từng object lên MinIO → MỘT transaction cuối lưu chỉ mục.
 *
 * <p><b>Ranh giới transaction — MinIO không có transaction, nên không cố làm nguyên tử, làm cho
 * đối chiếu lại được:</b> (1) DB là nguồn sự thật, MinIO được phép có rác mồ côi; (2) thứ tự cố
 * định là OBJECT TRƯỚC, HÀNG DB SAU trong vòng lặp — vì hàng DB trỏ tới object không tồn tại khiến
 * người dùng thấy lỗi khi đọc file (xấu hơn), còn object không có hàng DB chỉ tốn dung lượng lưu
 * trữ (chấp nhận được, dọn được bằng job quét theo snapshot FAILED); (3) rác đặt tên theo
 * {@code snapshotId} không bao giờ tái sử dụng (UUID mới mỗi lần upload), nên không có nguy cơ
 * đụng độ với snapshot khác.
 *
 * <p><b>Tuyệt đối không bọc transaction quanh vòng lặp upload:</b> hàng nghìn lượt gọi mạng tới
 * MinIO bên trong một transaction sẽ giữ một connection Postgres suốt thời gian đó, cạn
 * {@code hikari.maximum-pool-size: 10} và treo toàn bộ ứng dụng cho các request khác. Vì vậy class
 * này hoàn toàn không mang {@code @Transactional} — mọi thao tác DB đi qua
 * {@link SnapshotPersistenceService}, mỗi thao tác một transaction ngắn, riêng biệt.
 */
@Service
public class SnapshotService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final ProjectService projectService;
    private final SnapshotPersistenceService persistenceService;
    private final SnapshotSourceIngestor ingestor;
    private final ObjectStorageClient storageClient;
    private final UploadProperties uploadProperties;

    public SnapshotService(
            ProjectService projectService,
            SnapshotPersistenceService persistenceService,
            SnapshotSourceIngestor ingestor,
            ObjectStorageClient storageClient,
            UploadProperties uploadProperties) {
        this.projectService = projectService;
        this.persistenceService = persistenceService;
        this.ingestor = ingestor;
        this.storageClient = storageClient;
        this.uploadProperties = uploadProperties;
    }

    public RepositorySnapshot upload(UUID projectId, UUID ownerId, MultipartFile file) {
        // Tài nguyên không thuộc về bạn ⇒ 404 (ResourceNotFoundException từ ProjectService), không
        // tạo hàng snapshot nào cho một project không xác nhận được quyền sở hữu.
        projectService.getOwned(projectId, ownerId);

        byte[] rawContent = readBytes(file);
        // Kiểm rẻ TRƯỚC khi tạo hàng PENDING - không để lại bia mộ cho mọi lần thử tải file sai
        // định dạng rõ ràng (vd người dùng lỡ chọn nhầm ảnh thay vì zip).
        ingestor.validateFormat(rawContent);

        RepositorySnapshot pending = persistenceService.createPending(projectId, file.getOriginalFilename(), ingestor.supportedType());
        UUID snapshotId = pending.getId();
        String storagePrefix = pending.getStorageKey();

        try {
            List<ExtractedEntry> entries = ingestor.extractEntries(rawContent, uploadProperties);

            long totalSizeBytes = 0;
            for (ExtractedEntry entry : entries) {
                if (entry.stored()) {
                    storageClient.putObject(storagePrefix + entry.path(), entry.content(), contentTypeFor(entry));
                }
                totalSizeBytes += entry.sizeBytes();
            }

            List<SnapshotFile> rows = buildRows(snapshotId, entries, storagePrefix);
            // Trả về bản đã cập nhật (status=READY, fileCount, totalSizeBytes) — KHÔNG trả `pending`,
            // nó vẫn giữ trạng thái PENDING vì được nạp/sửa trong một transaction khác đã commit
            // trước đó và không tự động phản chiếu thay đổi từ transaction này.
            return persistenceService.finalizeReady(snapshotId, rows, entries.size(), totalSizeBytes);
        } catch (RuntimeException e) {
            persistenceService.markFailed(snapshotId, errorMessageFor(e));
            throw e;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidArchiveException("Không đọc được nội dung file tải lên", e);
        }
    }

    private String contentTypeFor(ExtractedEntry entry) {
        return entry.binary() ? "application/octet-stream" : "text/plain; charset=utf-8";
    }

    /**
     * Dựng danh sách hàng {@code snapshot_file} cho cả file lẫn thư mục cha còn thiếu — cây phải
     * đầy đủ vì {@code idx_snapshot_file_dir} liệt kê nội dung một thư mục bằng
     * {@code parent_path}, không tự suy ra thư mục từ path của file con.
     */
    private List<SnapshotFile> buildRows(UUID snapshotId, List<ExtractedEntry> entries, String storagePrefix) {
        Map<String, SnapshotFile> byPath = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (ExtractedEntry entry : entries) {
            addDirectoryAncestors(byPath, snapshotId, entry.path(), now);
            byPath.put(entry.path(), fileRow(snapshotId, entry, storagePrefix, now));
        }
        return new ArrayList<>(byPath.values());
    }

    private void addDirectoryAncestors(Map<String, SnapshotFile> byPath, UUID snapshotId, String path, OffsetDateTime now) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return;
        }
        String dirPath = path.substring(0, lastSlash);
        if (byPath.containsKey(dirPath)) {
            return;
        }
        addDirectoryAncestors(byPath, snapshotId, dirPath, now);
        byPath.put(dirPath, directoryRow(snapshotId, dirPath, now));
    }

    private SnapshotFile directoryRow(UUID snapshotId, String path, OffsetDateTime now) {
        SnapshotFile row = new SnapshotFile();
        row.setSnapshotId(snapshotId);
        row.setPath(path);
        row.setParentPath(parentPathOf(path));
        row.setName(nameOf(path));
        row.setEntryType(SnapshotEntryType.DIRECTORY);
        row.setSizeBytes(0);
        row.setBinary(false);
        row.setStored(false);
        row.setCreatedAt(now);
        return row;
    }

    private SnapshotFile fileRow(UUID snapshotId, ExtractedEntry entry, String storagePrefix, OffsetDateTime now) {
        SnapshotFile row = new SnapshotFile();
        row.setSnapshotId(snapshotId);
        row.setPath(entry.path());
        row.setParentPath(parentPathOf(entry.path()));
        row.setName(nameOf(entry.path()));
        row.setEntryType(SnapshotEntryType.FILE);
        row.setExtension(extensionOf(entry.path()));
        row.setSizeBytes(entry.sizeBytes());
        row.setLineCount(entry.lineCount());
        row.setSha256(entry.sha256());
        row.setBinary(entry.binary());
        row.setStored(entry.stored());
        row.setStorageKey(entry.stored() ? storagePrefix + entry.path() : null);
        row.setCreatedAt(now);
        return row;
    }

    private String parentPathOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? "" : path.substring(0, lastSlash);
    }

    private String nameOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? path : path.substring(lastSlash + 1);
    }

    private String extensionOf(String path) {
        String name = nameOf(path);
        int lastDot = name.lastIndexOf('.');
        return lastDot <= 0 ? null : name.substring(lastDot + 1);
    }

    private String errorMessageFor(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
    }
}
