package vn.hust.datn.evidence.service.analysis.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.domain.SnapshotFile;
import vn.hust.datn.evidence.exception.UnsupportedSourceTreeException;
import vn.hust.datn.evidence.repository.SnapshotFileRepository;
import vn.hust.datn.evidence.storage.ObjectStorageClient;

/**
 * Dựng lại cây mã nguồn của một snapshot từ object storage thành một tar để đẩy vào container.
 *
 * <h2>Không {@code @Transactional}</h2>
 *
 * Cùng lý do {@code SnapshotService} không có: vòng lặp ở đây gọi MinIO hàng trăm tới hàng nghìn
 * lần, bọc transaction quanh nó sẽ giữ một connection Postgres suốt thời gian đó và cạn pool.
 * Truy vấn chỉ mục là MỘT lời gọi repository (tự mở transaction riêng, đóng ngay), phần còn lại
 * hoàn toàn ngoài DB.
 *
 * <h2>uid/gid trong tar là bắt buộc, không phải chi tiết</h2>
 *
 * Container analyzer chạy non-root (uid 10001). Nếu tar không mang uid đó, file bung ra thuộc về
 * root và Maven trong container chết với {@code Cannot create resource output directory} — một lỗi
 * <i>về trình biên dịch</i>, không hề nhắc tới quyền, nên rất dễ đi sửa nhầm chỗ. Đã vấp thật khi
 * dựng image, xem {@code infra/analyzer/README.md} cạm bẫy #1.
 *
 * <h2>Bốn kiểm tra bắt buộc, tất cả đều từ chối TƯỜNG MINH</h2>
 *
 * Điểm chung của cả bốn: thứ chúng chặn đều dẫn tới một lần phân tích "thành công" với 0 cảnh báo.
 * Một repo Gradle chạy qua sandbox Maven cho ra 0 class, 0 cảnh báo, exit 0 — trông y hệt một dự
 * án sạch.
 */
@Service
public class SourceTreeBuilder {

    private static final Logger log = LoggerFactory.getLogger(SourceTreeBuilder.class);

    /**
     * Tiền tố trong tar. Container copy tar vào {@code /work} nên cây nằm ở {@code /work/repo}.
     *
     * <p>{@code repo} chứ KHÔNG phải {@code src}: hầu hết dự án Java đã có sẵn thư mục {@code src/}
     * ở gốc, nên tiền tố {@code src} cho ra những đường dẫn kiểu {@code /work/src/src/main/java/...}
     * trong mọi log và thông điệp lỗi — đọc rất dễ nhầm là lặp do bug.
     */
    private static final String TAR_PREFIX = "repo";

    /** Khớp user {@code analyzer} trong {@code infra/analyzer/java/Dockerfile}. */
    private static final int ANALYZER_UID = 10001;

    private static final String POM_FILE_NAME = "pom.xml";

    private final SnapshotFileRepository snapshotFileRepository;
    private final ObjectStorageClient storageClient;
    private final UploadProperties limits;

    public SourceTreeBuilder(
            SnapshotFileRepository snapshotFileRepository, ObjectStorageClient storageClient, UploadProperties limits) {
        this.snapshotFileRepository = snapshotFileRepository;
        this.storageClient = storageClient;
        this.limits = limits;
    }

    public SourceTree build(UUID snapshotId) {
        List<SnapshotFile> files = snapshotFileRepository.findBySnapshotIdOrderByPathAsc(snapshotId).stream()
                .filter(file -> file.getEntryType() == vn.hust.datn.evidence.domain.SnapshotEntryType.FILE)
                .toList();
        if (files.isEmpty()) {
            throw new UnsupportedSourceTreeException("Snapshot khong co file nao");
        }

        String sourceRootDir = locateSourceRoot(files);
        requireJavaSources(files, sourceRootDir);

        return packAsTar(files, sourceRootDir);
    }

    /**
     * Gốc dự án = thư mục của {@code pom.xml} NÔNG NHẤT.
     *
     * <p>Hai trường hợp bị từ chối thẳng thay vì đoán, vì đoán sai ở đây cho ra một lần phân tích
     * trông bình thường mà quét nhầm chỗ: nhiều {@code pom.xml} cùng độ sâu (không có căn cứ nào
     * để chọn), và dự án multi-module (sandbox chỉ chạy một lần {@code mvn} ở một thư mục, sẽ bỏ
     * sót toàn bộ module con mà vẫn "thành công").
     */
    private String locateSourceRoot(List<SnapshotFile> files) {
        List<SnapshotFile> poms = files.stream()
                .filter(file -> POM_FILE_NAME.equals(file.getName()))
                .sorted(Comparator.comparingInt(file -> depthOf(file.getPath())))
                .toList();

        if (poms.isEmpty()) {
            throw new UnsupportedSourceTreeException(
                    "Khong tim thay pom.xml nao. Sandbox hien chi ho tro du an Maven mot module; "
                            + "du an Gradle chua ho tro.");
        }

        int shallowest = depthOf(poms.get(0).getPath());
        List<SnapshotFile> candidates =
                poms.stream().filter(pom -> depthOf(pom.getPath()) == shallowest).toList();
        if (candidates.size() > 1) {
            throw new UnsupportedSourceTreeException(
                    "Co %d file pom.xml cung o do sau nong nhat (%s) — khong co can cu de chon du an nao."
                            .formatted(
                                    candidates.size(),
                                    candidates.stream()
                                            .map(SnapshotFile::getPath)
                                            .toList()));
        }

        SnapshotFile rootPom = candidates.get(0);
        requireSingleModule(rootPom);
        return parentOf(rootPom.getPath());
    }

    private void requireSingleModule(SnapshotFile rootPom) {
        if (!rootPom.isStored()) {
            throw new UnsupportedSourceTreeException(
                    "Khong doc duoc noi dung '%s' (file khong duoc luu vi qua lon)".formatted(rootPom.getPath()));
        }
        String content = new String(
                storageClient.getObject(rootPom.getStorageKey()).content(), StandardCharsets.UTF_8);
        // Kiểm bằng chuỗi, không parse XML: ta chỉ cần biết CÓ khai module hay không, và một pom
        // hỏng cú pháp thì Maven trong container sẽ báo rõ hơn bất kỳ thông điệp nào ở đây.
        if (content.contains("<modules>")) {
            throw new UnsupportedSourceTreeException(
                    "'%s' khai <modules> — du an multi-module chua ho tro. Sandbox chi chay mot lan mvn o mot "
                                    .formatted(rootPom.getPath())
                            + "thu muc, se bo sot toan bo module con ma van bao thanh cong.");
        }
    }

    /**
     * Phải có ít nhất một {@code .java} dưới gốc dự án, và mọi {@code .java} đều phải đọc được
     * nội dung. Một file nguồn {@code stored=false} (quá lớn nên không đẩy lên object storage) mà
     * bỏ qua im lặng thì analyzer quét thiếu đúng file đó và không ai biết.
     */
    private void requireJavaSources(List<SnapshotFile> files, String sourceRootDir) {
        List<SnapshotFile> javaFiles = files.stream()
                .filter(file -> isUnder(file.getPath(), sourceRootDir))
                .filter(file -> file.getPath().endsWith(".java"))
                .toList();

        if (javaFiles.isEmpty()) {
            throw new UnsupportedSourceTreeException(
                    "Khong co file .java nao duoi '%s'".formatted(sourceRootDir.isEmpty() ? "goc repo" : sourceRootDir));
        }

        List<String> khongDocDuoc = javaFiles.stream()
                .filter(file -> !file.isStored())
                .map(SnapshotFile::getPath)
                .toList();
        if (!khongDocDuoc.isEmpty()) {
            throw new UnsupportedSourceTreeException(
                    "%d file .java khong co noi dung de gui vao sandbox (qua lon luc upload): %s"
                            .formatted(khongDocDuoc.size(), khongDocDuoc.stream().limit(5).toList()));
        }
    }

    private SourceTree packAsTar(List<SnapshotFile> files, String sourceRootDir) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int packed = 0;
        long totalBytes = 0;
        Set<String> writtenDirs = new HashSet<>();

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(buffer)) {
            // PAX cho tên dài: ustar cắt tên ở 100 ký tự và cắt ÂM THẦM, mà đường dẫn kiểu
            // src/main/java/vn/hust/datn/casestudy/vulnshop/... vượt mốc đó rất dễ.
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            for (SnapshotFile file : files) {
                if (!file.isStored()) {
                    // File không có nội dung: bỏ qua được vì mọi .java đã bị requireJavaSources
                    // chặn từ trước. Ở đây chỉ còn file to không phải mã nguồn (ảnh, jar mẫu...).
                    continue;
                }
                byte[] content = storageClient.getObject(file.getStorageKey()).content();
                verifySha256(file, content);

                totalBytes += content.length;
                if (totalBytes > limits.maxUncompressedBytes()) {
                    throw new UnsupportedSourceTreeException(
                            "Cay ma nguon vuot qua %d byte — qua lon de gui vao sandbox"
                                    .formatted(limits.maxUncompressedBytes()));
                }

                String entryName = TAR_PREFIX + "/" + file.getPath();
                writeParentDirs(tar, entryName, writtenDirs);

                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(content.length);
                entry.setMode(0_644);
                // uid/gid CỦA USER TRONG IMAGE — xem Javadoc lớp.
                entry.setUserId(ANALYZER_UID);
                entry.setGroupId(ANALYZER_UID);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
                packed++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Khong dung duoc tar cay ma nguon", e);
        }

        String sourceRoot = sourceRootDir.isEmpty() ? TAR_PREFIX : TAR_PREFIX + "/" + sourceRootDir;
        log.info("Dong goi {} file ({} byte) cho sandbox, goc du an = {}", packed, totalBytes, sourceRoot);
        return new SourceTree(buffer.toByteArray(), sourceRoot, packed, totalBytes);
    }

    /**
     * Ghi entry THƯ MỤC cho mọi cấp cha, với đúng uid/gid của user trong image.
     *
     * <p>Bắt buộc, không phải cho gọn: tar chỉ có entry file thì {@code copyArchiveToContainer}
     * để Docker tự tạo các thư mục trung gian, và Docker tạo chúng thuộc về <b>root</b>. Container
     * non-root sau đó không tạo được {@code target/} và Maven chết với
     * {@code Cannot create resource output directory} — một lỗi <i>về trình biên dịch</i>.
     *
     * <p>Đây là biến thể thứ hai của cùng cạm bẫy đã vấp ở D2, và <b>unit test không bắt được</b>:
     * kiểm tra danh sách entry trong tar vẫn xanh vì các file đều có mặt. Chỉ test chạy container
     * thật mới lộ ra.
     */
    private void writeParentDirs(TarArchiveOutputStream tar, String entryName, Set<String> written) throws IOException {
        int slash = entryName.indexOf('/');
        while (slash >= 0) {
            String dir = entryName.substring(0, slash);
            if (written.add(dir)) {
                TarArchiveEntry entry = new TarArchiveEntry(dir + "/");
                entry.setMode(0_755);
                entry.setUserId(ANALYZER_UID);
                entry.setGroupId(ANALYZER_UID);
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
            slash = entryName.indexOf('/', slash + 1);
        }
    }

    /**
     * Đối chiếu nội dung đọc từ object storage với sha256 ghi lúc upload.
     *
     * <p>Không phải nghi ngờ MinIO: đây là chốt chặn cho việc chỉ mục DB và object storage trôi
     * khỏi nhau (chúng KHÔNG nguyên tử với nhau — xem Javadoc {@code SnapshotService}). Phân tích
     * một cây mã nguồn khác với cây người dùng đã upload là hỏng im lặng ở dạng tệ nhất: mọi
     * Evidence ID vẫn hợp lệ, chỉ là chúng nói về một mã nguồn khác.
     */
    private void verifySha256(SnapshotFile file, byte[] content) {
        if (file.getSha256() == null) {
            return;
        }
        String actual = sha256Hex(content);
        if (!actual.equals(file.getSha256())) {
            throw new UnsupportedSourceTreeException(
                    "Noi dung '%s' khong khop sha256 ghi luc upload (cho %s, doc duoc %s)"
                            .formatted(file.getPath(), file.getSha256(), actual));
        }
    }

    private String sha256Hex(byte[] content) {
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

    private int depthOf(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private String parentOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? "" : path.substring(0, lastSlash);
    }

    private boolean isUnder(String path, String dir) {
        return dir.isEmpty() || path.startsWith(dir + "/");
    }

    /** Chỉ để test dựng danh sách kỳ vọng mà không phải chép hằng số. */
    static List<String> tarEntryNames(List<String> snapshotPaths) {
        List<String> names = new ArrayList<>(snapshotPaths.size());
        snapshotPaths.forEach(path -> names.add(TAR_PREFIX + "/" + path));
        return names;
    }
}
