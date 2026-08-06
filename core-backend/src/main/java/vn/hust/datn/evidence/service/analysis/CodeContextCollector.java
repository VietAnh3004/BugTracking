package vn.hust.datn.evidence.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.hust.datn.evidence.service.SnapshotSourceReader;

/**
 * Cầu nối giữa {@link CodeContextExtractor} (thuần hàm) và snapshot trên object storage.
 *
 * <p>Chạy TRƯỚC {@code EvidenceMaterializer} và nối kết quả vào chính danh sách draft, vì
 * materializer là <b>nơi duy nhất cấp {@code seq_in_run} và là nơi duy nhất ghi evidence</b>. Xem
 * Javadoc {@link CodeContextDraft} để biết vì sao CODE_CONTEXT không được sinh ở bước liên kết.
 */
@Service
public class CodeContextCollector {

    private static final Logger log = LoggerFactory.getLogger(CodeContextCollector.class);

    /**
     * @param drafts context dựng được
     * @param failureCount số vị trí KHÔNG dựng được context — phải hiện thành số, xem
     *     {@link CodeContextExtractor.Result}
     * @param fileCount số file nguồn đã thật sự đọc và parse
     */
    public record Result(List<CodeContextDraft> drafts, int failureCount, int fileCount) {}

    private final SnapshotSourceReader sourceReader;
    private final CodeContextExtractor extractor;

    public CodeContextCollector(SnapshotSourceReader sourceReader, CodeContextExtractor extractor) {
        this.sourceReader = sourceReader;
        this.extractor = extractor;
    }

    /**
     * Dựng CODE_CONTEXT cho mọi vị trí cảnh báo đối chiếu được với snapshot.
     *
     * <p>Chỉ đi theo {@link WarningDraft}: đó là những vị trí mà người dùng sẽ mở ra xem. Test và
     * độ phủ không cần ngữ cảnh mã bao quanh — bản thân chúng đã là bằng chứng hoàn chỉnh.
     *
     * <p>Duyệt <b>từng vị trí</b> trong {@code WarningDraft.locations()} chứ không chỉ vị trí
     * chính: 3/28 cảnh báo thật của vulnshop có nhiều vị trí, một cái trỏ tới 3 file khác nhau.
     * Chỉ lấy vị trí đầu là lặng lẽ bỏ 2/3 bằng chứng.
     */
    public Result collect(UUID snapshotId, List<EvidenceDraft> drafts, SnapshotPathResolver pathResolver) {
        // Gom theo file TRƯỚC khi đọc: 33 cảnh báo của vulnshop rơi vào ~15 file, đọc theo từng
        // cảnh báo là 33 lượt gọi MinIO cho cùng chừng ấy nội dung.
        Map<String, SortedSet<Integer>> linesByFile = new LinkedHashMap<>();
        for (EvidenceDraft draft : drafts) {
            if (!(draft instanceof WarningDraft warning)) {
                continue;
            }
            for (WarningLocationDraft location : warning.locations()) {
                if (location.startLine() == null || location.pathRaw() == null) {
                    continue;
                }
                ResolvedPath resolved = pathResolver.resolve(location.pathRaw());
                if (!resolved.resolved()) {
                    // Không đối chiếu được đường dẫn thì cũng không đọc được file. Thất bại này đã
                    // được đếm ở unresolved_location_count, không đếm lần thứ hai ở đây.
                    continue;
                }
                linesByFile
                        .computeIfAbsent(resolved.filePath(), key -> new TreeSet<>())
                        .add(location.startLine());
            }
        }

        List<CodeContextDraft> collected = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int fileCount = 0;

        for (Map.Entry<String, SortedSet<Integer>> entry : linesByFile.entrySet()) {
            String path = entry.getKey();
            Optional<String> source = sourceReader.readText(snapshotId, path);
            if (source.isEmpty()) {
                failures.add("%s: khong doc duoc noi dung".formatted(path));
                continue;
            }
            fileCount++;
            CodeContextExtractor.Result result = extractor.extract(path, source.get(), entry.getValue());
            collected.addAll(result.drafts());
            failures.addAll(result.failures());
        }

        if (!failures.isEmpty()) {
            // WARN chứ không ném: mã nguồn người dùng có thể không biên dịch được, và một file
            // hỏng không được phép làm hỏng cả lần phân tích. Nhưng phải NÓI RA — quyết định
            // "hỏng tới mức nào thì coi là lỗi" nằm ở AnalysisRunService.decide().
            log.warn(
                    "Snapshot {}: {} vi tri khong dung duoc ngu canh ma nguon. Vi du: {}",
                    snapshotId,
                    failures.size(),
                    failures.subList(0, Math.min(3, failures.size())));
        }
        return new Result(List.copyOf(collected), failures.size(), fileCount);
    }
}
