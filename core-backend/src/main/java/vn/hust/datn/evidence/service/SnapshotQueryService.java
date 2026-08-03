package vn.hust.datn.evidence.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hust.datn.evidence.domain.RepositorySnapshot;
import vn.hust.datn.evidence.domain.SnapshotEntryType;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.domain.SnapshotStatus;
import vn.hust.datn.evidence.dto.FileContentResponse;
import vn.hust.datn.evidence.exception.BinaryFileException;
import vn.hust.datn.evidence.exception.FileContentNotStoredException;
import vn.hust.datn.evidence.exception.InvalidLineRangeException;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.exception.SnapshotNotReadyException;
import vn.hust.datn.evidence.repository.RepositorySnapshotRepository;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;

/**
 * Đường ĐỌC của snapshot — tách khỏi {@link SnapshotService} (đường GHI) vì hai bên có ràng buộc
 * hoàn toàn khác nhau: ghi phải tránh transaction bao quanh vòng lặp mạng, đọc thì
 * {@code readOnly = true} và cắt lát ở server.
 *
 * <p>Quy tắc authorization giống hệt {@code ProjectService}: lọc {@code ownerId} NGAY TRONG câu
 * truy vấn ({@code findByIdAndOwnerId}), snapshot của người khác ⇒ 404 chứ không phải 403.
 */
@Service
public class SnapshotQueryService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotQueryService.class);

    /** Không truyền khoảng dòng: đủ để xem hầu hết file mà không gửi cả file 12.000 dòng qua mạng. */
    private static final int DEFAULT_MAX_LINES = 2000;

    /** Có truyền khoảng dòng: rộng hơn vì người dùng chủ động yêu cầu, nhưng vẫn phải có trần. */
    private static final int RANGE_MAX_LINES = 5000;

    private final ProjectService projectService;
    private final RepositorySnapshotRepository snapshotRepository;
    private final SnapshotFileRepository snapshotFileRepository;
    private final ObjectStorageClient storageClient;

    public SnapshotQueryService(
            ProjectService projectService,
            RepositorySnapshotRepository snapshotRepository,
            SnapshotFileRepository snapshotFileRepository,
            ObjectStorageClient storageClient) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.snapshotFileRepository = snapshotFileRepository;
        this.storageClient = storageClient;
    }

    @Transactional(readOnly = true)
    public Page<RepositorySnapshot> listByProject(UUID projectId, UUID ownerId, Pageable pageable) {
        projectService.getOwned(projectId, ownerId);
        return snapshotRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
    }

    /**
     * Metadata một snapshot — KHÔNG đòi {@code READY} có chủ đích: đây chính là endpoint để FE đọc
     * {@code status} và {@code errorMessage} của một snapshot {@code FAILED}.
     */
    @Transactional(readOnly = true)
    public RepositorySnapshot get(UUID snapshotId, UUID ownerId) {
        return snapshotRepository
                .findByIdAndOwnerId(snapshotId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("snapshot", snapshotId));
    }

    @Transactional(readOnly = true)
    public List<SnapshotFile> listFiles(UUID snapshotId, UUID ownerId) {
        requireReady(snapshotId, ownerId);
        return snapshotFileRepository.findBySnapshotIdOrderByPathAsc(snapshotId);
    }

    /**
     * Đọc nội dung file theo khoảng dòng 1-based (khớp {@code evidence_record.start_line}, để
     * {@code #L42} trên URL trỏ đúng dòng mà evidence tham chiếu).
     *
     * <p>{@code @Transactional(readOnly = true)} chỉ bao phần truy vấn chỉ mục; lời gọi object
     * storage nằm trong đó là chấp nhận được vì đây là ĐÚNG MỘT object cho một request người dùng
     * — khác hẳn vòng lặp hàng nghìn object của đường upload (xem Javadoc {@link SnapshotService}).
     */
    @Transactional(readOnly = true)
    public FileContentResponse readFile(UUID snapshotId, UUID ownerId, String path, Integer fromLine, Integer toLine) {
        requireReady(snapshotId, ownerId);

        SnapshotFile file = snapshotFileRepository
                .findBySnapshotIdAndPath(snapshotId, path)
                .orElseThrow(() -> new ResourceNotFoundException("file trong snapshot", path));

        if (file.getEntryType() == SnapshotEntryType.DIRECTORY) {
            throw new FileContentNotStoredException(path, "đây là thư mục, không phải file");
        }
        if (file.isBinary()) {
            throw new BinaryFileException(path);
        }
        if (!file.isStored()) {
            throw new FileContentNotStoredException(path, "file vượt ngưỡng lưu trữ nên chỉ được lập chỉ mục");
        }

        int totalLines = file.getLineCount() == null ? 0 : file.getLineCount();
        LineRange range = resolveRange(fromLine, toLine);

        String text = new String(storageClient.getObject(file.getStorageKey()).content(), StandardCharsets.UTF_8);
        List<String> allLines = splitLines(text);
        if (allLines.size() != totalLines) {
            // Không ném lỗi (đây là đường đọc, người dùng vẫn nên xem được file) nhưng KHÔNG im lặng:
            // chỉ mục và nội dung thật lệch nhau nghĩa là số dòng lưu lúc upload sai, và source
            // viewer sẽ highlight lệch dòng ở mọi evidence trỏ vào file này.
            log.warn(
                    "Chỉ mục lệch nội dung: snapshot={} path={} line_count={} nhưng đọc được {} dòng",
                    snapshotId,
                    path,
                    totalLines,
                    allLines.size());
        }

        int startIndex = Math.min(range.start() - 1, allLines.size());
        int endIndexExclusive = Math.min(range.end(), allLines.size());
        List<String> slice = startIndex >= endIndexExclusive ? List.of() : List.copyOf(allLines.subList(startIndex, endIndexExclusive));

        // toLine = start + số dòng trả về - 1: lát rỗng tự nhiên cho ra toLine = start - 1
        // (file rỗng với start = 1 ⇒ toLine = 0), không cần nhánh đặc biệt nào.
        int actualToLine = range.start() + slice.size() - 1;
        boolean truncated = endIndexExclusive < allLines.size();
        return new FileContentResponse(path, range.start(), actualToLine, totalLines, truncated, slice);
    }

    private RepositorySnapshot requireReady(UUID snapshotId, UUID ownerId) {
        RepositorySnapshot snapshot = get(snapshotId, ownerId);
        if (snapshot.getStatus() != SnapshotStatus.READY) {
            throw new SnapshotNotReadyException(snapshotId, snapshot.getStatus());
        }
        return snapshot;
    }

    private LineRange resolveRange(Integer fromLine, Integer toLine) {
        int start = fromLine == null ? 1 : fromLine;
        if (start < 1) {
            throw new InvalidLineRangeException("fromLine phải >= 1 (đánh số dòng bắt đầu từ 1), nhận được %d".formatted(start));
        }

        boolean explicitRange = fromLine != null || toLine != null;
        if (toLine == null) {
            int maxLines = explicitRange ? RANGE_MAX_LINES : DEFAULT_MAX_LINES;
            return new LineRange(start, start + maxLines - 1);
        }

        if (toLine < start) {
            throw new InvalidLineRangeException("toLine (%d) phải >= fromLine (%d)".formatted(toLine, start));
        }
        int requested = toLine - start + 1;
        if (requested > RANGE_MAX_LINES) {
            throw new InvalidLineRangeException(
                    "Khoảng dòng yêu cầu %d dòng, vượt tối đa %d dòng mỗi lần đọc".formatted(requested, RANGE_MAX_LINES));
        }
        return new LineRange(start, toLine);
    }

    /**
     * Tách dòng CHỈ theo {@code '\n'} — phải khớp từng chi tiết với cách
     * {@code ZipExtractor.countLines} đếm lúc upload, nếu không {@code totalLines} (lấy từ chỉ mục)
     * và số dòng thực trả về sẽ lệch nhau. Cố tình KHÔNG dùng {@code String.lines()}: nó còn tách
     * theo {@code '\r'} đơn lẻ, cho ra số dòng khác với bộ đếm lúc upload trên file dùng CR cũ.
     *
     * <p>{@code '\r'} cuối dòng (file CRLF) bị cắt bỏ vì đó là ký tự phân tách, không phải nội
     * dung — giữ lại sẽ hiện thành ký tự lạ ở cuối mỗi dòng trong source viewer.
     */
    private List<String> splitLines(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(stripTrailingCarriageReturn(text.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(stripTrailingCarriageReturn(text.substring(start)));
        }
        return lines;
    }

    private String stripTrailingCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** Khoảng dòng đã chuẩn hoá, 1-based, {@code end} bao gồm. */
    private record LineRange(int start, int end) {}
}
