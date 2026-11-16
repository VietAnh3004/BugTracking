package vn.hust.datn.evidence.service.review;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

import vn.hust.datn.evidence.domain.GroundTruthLabel;
import vn.hust.datn.evidence.domain.GroundTruthLabelId;
import vn.hust.datn.evidence.domain.GroundTruthSource;
import vn.hust.datn.evidence.domain.GroundTruthVerdict;
import vn.hust.datn.evidence.domain.ProjectRole;
import vn.hust.datn.evidence.dto.GroundTruthImportResponse;
import vn.hust.datn.evidence.dto.GroundTruthSheetResponse;
import vn.hust.datn.evidence.dto.SaveGroundTruthRequest;
import vn.hust.datn.evidence.dto.WarningView;
import vn.hust.datn.evidence.exception.InvalidGroundTruthException;
import vn.hust.datn.evidence.repository.GroundTruthLabelRepository;
import vn.hust.datn.evidence.repository.UserRepository;
import vn.hust.datn.evidence.repository.WarningRepository;
import vn.hust.datn.evidence.service.EvidenceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Gán nhãn ground truth: bảng gán nhãn theo vai trò, ghi/xoá nhãn, và xuất/nạp file YAML.
 *
 * <h2>Nguồn nhãn do VAI TRÒ quyết định, không do client khai</h2>
 *
 * Chủ dự án ghi {@code MANUAL}, thành viên ghi {@code BLIND_SECOND_RATER}. Thành viên không bao
 * giờ nhận về nhãn MANUAL hay gợi ý SEEDED — tính "mù" là điều kiện để Cohen's kappa đo đúng thứ
 * nó đo (hai người đọc mã ĐỘC LẬP), nên nó phải được server bảo đảm.
 *
 * <h2>File YAML nhận diện cảnh báo bằng VỊ TRÍ, không bằng Evidence ID</h2>
 *
 * Mỗi lần phân tích lại, cảnh báo nhận Evidence ID mới. File nhãn khoá theo
 * {@code file + dòng} (kèm {@code tool}/{@code rule} nếu có) nên nạp được vào run mới, kể cả của
 * dự án khác — đó là lý do chọn "theo từng lần phân tích + nạp/xuất file" (chốt 16/11/2026).
 * Mục bỏ trống {@code tool}/{@code rule} khớp MỌI cảnh báo ở vị trí đó: một lỗi gieo bị cả SpotBugs
 * lẫn Semgrep bắt chỉ cần khai một lần.
 */
@Service
@RequiredArgsConstructor
public class GroundTruthService {

    /** Đủ cho mọi run thật (vulnshop: 34 cảnh báo); một trang duy nhất giữ code đơn giản. */
    private static final int MAX_WARNINGS = 10_000;

    private final EvidenceQueryService evidenceQueryService;
    private final WarningRepository warningRepository;
    private final GroundTruthLabelRepository labelRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public GroundTruthSheetResponse sheet(UUID runId, UUID userId) {
        ProjectRole role = evidenceQueryService.requireRunReadableBy(runId, userId);
        evidenceQueryService.ensureRunQueryable(runId);
        GroundTruthSource source = sourceFor(role);

        Map<UUID, GroundTruthLabel> mine = byEvidence(labelRepository.findByRunAndSource(runId, source));
        // Gợi ý SEEDED chỉ cho chủ dự án — thành viên KHÔNG được thấy, kể cả "chỉ là gợi ý".
        Map<UUID, GroundTruthLabel> hints = role == ProjectRole.OWNER
                ? byEvidence(labelRepository.findByRunAndSource(runId, GroundTruthSource.SEEDED))
                : Map.of();

        List<GroundTruthSheetResponse.GroundTruthRow> rows = warningsOf(runId).stream()
                .map(w -> new GroundTruthSheetResponse.GroundTruthRow(
                        w.shortId(),
                        w.tool(),
                        w.ruleIdNormalized(),
                        w.severityNormalized(),
                        w.filePath(),
                        w.startLine(),
                        w.summary(),
                        viewOf(mine.get(w.evidenceId())),
                        viewOf(hints.get(w.evidenceId()))))
                .toList();
        return new GroundTruthSheetResponse(runId, role, source, rows);
    }

    @Transactional
    public GroundTruthSheetResponse.GroundTruthLabelView save(UUID runId, String shortId, UUID userId, SaveGroundTruthRequest request) {
        ProjectRole role = evidenceQueryService.requireRunReadableBy(runId, userId);
        // Ném 4xx phân loại nếu shortId không tồn tại, thuộc run khác, hoặc không phải cảnh báo.
        WarningView warning = evidenceQueryService.getWarning(runId, shortId);

        GroundTruthLabel label = upsert(
                warning.evidenceId(), sourceFor(role), request.label(), request.rationale().trim(), displayNameOf(userId));
        return GroundTruthSheetResponse.GroundTruthLabelView.from(label);
    }

    /** Xoá nhãn CỦA NGƯỜI GỌI trên một cảnh báo. Chưa có nhãn thì không làm gì. */
    @Transactional
    public void delete(UUID runId, String shortId, UUID userId) {
        ProjectRole role = evidenceQueryService.requireRunReadableBy(runId, userId);
        WarningView warning = evidenceQueryService.getWarning(runId, shortId);
        labelRepository.deleteById(new GroundTruthLabelId(warning.evidenceId(), sourceFor(role)));
    }

    /**
     * Xuất MỌI nhãn của run (cả ba nguồn) ra YAML. Chỉ chủ dự án — file chứa nhãn MANUAL, đưa cho
     * thành viên là phá tính mù.
     */
    @Transactional(readOnly = true)
    public byte[] exportYaml(UUID runId, UUID userId) {
        evidenceQueryService.requireRunOwnedBy(runId, userId);
        evidenceQueryService.ensureRunQueryable(runId);
        Map<UUID, WarningView> warnings =
                warningsOf(runId).stream().collect(Collectors.toMap(WarningView::evidenceId, Function.identity()));

        List<Map<String, Object>> entries = new ArrayList<>();
        for (GroundTruthSource source : GroundTruthSource.values()) {
            for (GroundTruthLabel label : labelRepository.findByRunAndSource(runId, source)) {
                WarningView w = warnings.get(label.getEvidenceRecordId());
                if (w == null || w.filePath() == null || w.startLine() == null) {
                    // Không vị trí ⇒ không nạp lại được vào run khác; bỏ khỏi file thay vì xuất
                    // một mục chắc chắn không bao giờ khớp.
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("tool", w.tool().name());
                entry.put("rule", w.ruleIdNormalized());
                entry.put("file", w.filePath());
                entry.put("line", w.startLine());
                entry.put("source", source.name());
                entry.put("label", label.getLabel().name());
                entry.put("rationale", label.getRationale());
                entry.put("labeler", label.getLabeler());
                entries.add(entry);
            }
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", 1);
        document.put("labels", entries);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        options.setWidth(120);
        String header =
                """
                # Nhan ground truth — xuat tu run %s.
                # Nap lai vao run khac bang nut "Nap file" o trang Gan nhan: moi muc khop canh bao theo
                # file + line (va tool/rule neu co), KHONG theo Evidence ID.
                """
                        .formatted(runId);
        return (header + new Yaml(new Representer(options), options).dump(document)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Nạp nhãn từ YAML. Chỉ chủ dự án. KIỂM TOÀN BỘ FILE TRƯỚC, rồi mới ghi trong một transaction:
     * một file sai ở mục thứ 20 mà đã ghi 19 mục đầu thì người dùng không biết DB đang ở trạng thái
     * nào.
     */
    @Transactional
    public GroundTruthImportResponse importYaml(UUID runId, UUID userId, byte[] content) {
        evidenceQueryService.requireRunOwnedBy(runId, userId);
        evidenceQueryService.ensureRunQueryable(runId);

        List<ImportEntry> entries = parse(content);
        List<WarningView> warnings = warningsOf(runId);
        String fallbackLabeler = displayNameOf(userId);

        int written = 0;
        int matchedEntries = 0;
        List<String> unmatched = new ArrayList<>();
        for (ImportEntry entry : entries) {
            List<WarningView> matches = warnings.stream().filter(entry::matches).toList();
            if (matches.isEmpty()) {
                unmatched.add(entry.describe());
                continue;
            }
            matchedEntries++;
            for (WarningView w : matches) {
                upsert(
                        w.evidenceId(),
                        entry.source(),
                        entry.label(),
                        entry.rationale(),
                        entry.labeler() == null ? fallbackLabeler : entry.labeler());
                written++;
            }
        }
        return new GroundTruthImportResponse(written, matchedEntries, unmatched);
    }

    // ------------------------------------------------------------------------------------------

    private static GroundTruthSource sourceFor(ProjectRole role) {
        return role == ProjectRole.OWNER ? GroundTruthSource.MANUAL : GroundTruthSource.BLIND_SECOND_RATER;
    }

    private List<WarningView> warningsOf(UUID runId) {
        return warningRepository
                .findForUi(runId, null, null, null, PageRequest.of(0, MAX_WARNINGS))
                .getContent();
    }

    private GroundTruthLabel upsert(
            UUID evidenceRecordId, GroundTruthSource source, GroundTruthVerdict verdict, String rationale, String labeler) {
        GroundTruthLabel label = labelRepository
                .findById(new GroundTruthLabelId(evidenceRecordId, source))
                .orElseGet(() -> {
                    GroundTruthLabel fresh = new GroundTruthLabel();
                    fresh.setEvidenceRecordId(evidenceRecordId);
                    fresh.setSource(source);
                    return fresh;
                });
        label.setLabel(verdict);
        label.setRationale(rationale);
        label.setLabeler(labeler);
        // UTC + microsecond: giá trị trả về ngay trùng khít giá trị đọc lại từ DB (xem
        // HumanReviewService — cùng cạm bẫy timestamptz).
        label.setLabeledAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        return labelRepository.save(label);
    }

    private String displayNameOf(UUID userId) {
        return userRepository.findById(userId).map(u -> u.getDisplayName()).orElse(userId.toString());
    }

    private static Map<UUID, GroundTruthLabel> byEvidence(List<GroundTruthLabel> labels) {
        return labels.stream().collect(Collectors.toMap(GroundTruthLabel::getEvidenceRecordId, Function.identity()));
    }

    private static GroundTruthSheetResponse.GroundTruthLabelView viewOf(GroundTruthLabel label) {
        return label == null ? null : GroundTruthSheetResponse.GroundTruthLabelView.from(label);
    }

    // ------------------------------------------------------------------------------------------
    // Đọc file YAML

    /** Package-private để test được trực tiếp trên chuỗi YAML. */
    static List<ImportEntry> parse(byte[] content) {
        LoaderOptions options = new LoaderOptions();
        // File do người dùng tải lên: không cho alias lồng nhau (chống "billion laughs") và không
        // cho khoá trùng (một mục khai `label` hai lần là lỗi, không phải "lấy cái sau").
        options.setMaxAliasesForCollections(10);
        options.setAllowDuplicateKeys(false);
        Object root;
        try {
            root = new Yaml(new SafeConstructor(options)).load(new String(content, StandardCharsets.UTF_8));
        } catch (YAMLException e) {
            throw new InvalidGroundTruthException("File khong phai YAML hop le: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> document) || !(document.get("labels") instanceof List<?> labels)) {
            throw new InvalidGroundTruthException("Thieu danh sach 'labels' o cap ngoai cung cua file.");
        }

        List<ImportEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            try {
                entries.add(ImportEntry.of(labels.get(i)));
            } catch (IllegalArgumentException e) {
                errors.add("muc #%d: %s".formatted(i + 1, e.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidGroundTruthException("File co %d muc sai, chua ghi gi: %s"
                    .formatted(errors.size(), String.join("; ", errors.subList(0, Math.min(5, errors.size())))));
        }
        return entries;
    }

    /** Một mục trong file nhãn. {@code tool}/{@code rule}/{@code labeler} được phép bỏ trống. */
    record ImportEntry(
            String tool,
            String rule,
            String file,
            int line,
            GroundTruthSource source,
            GroundTruthVerdict label,
            String rationale,
            String labeler) {

        static ImportEntry of(Object raw) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("moi muc phai la mot map");
            }
            String file = text(map, "file", true);
            Object line = map.get("line");
            if (!(line instanceof Integer lineNumber) || lineNumber < 1) {
                throw new IllegalArgumentException("'line' phai la so nguyen >= 1");
            }
            String rationale = text(map, "rationale", true);
            return new ImportEntry(
                    text(map, "tool", false),
                    text(map, "rule", false),
                    file,
                    lineNumber,
                    enumOf(GroundTruthSource.class, text(map, "source", true), "source"),
                    enumOf(GroundTruthVerdict.class, text(map, "label", true), "label"),
                    rationale,
                    text(map, "labeler", false));
        }

        boolean matches(WarningView warning) {
            return file.equals(warning.filePath())
                    && Integer.valueOf(line).equals(warning.startLine())
                    && (tool == null || tool.equalsIgnoreCase(warning.tool().name()))
                    && (rule == null || rule.equals(warning.ruleIdNormalized()) || rule.equals(warning.ruleId()));
        }

        String describe() {
            return "%s/%s@%s:%d".formatted(tool == null ? "*" : tool, rule == null ? "*" : rule, file, line);
        }

        private static String text(Map<?, ?> map, String key, boolean required) {
            Object value = map.get(key);
            if (value == null || value.toString().isBlank()) {
                if (required) {
                    throw new IllegalArgumentException("thieu '" + key + "'");
                }
                return null;
            }
            return value.toString().trim();
        }

        private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String key) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'%s' khong hop le: %s".formatted(key, value));
            }
        }
    }
}
