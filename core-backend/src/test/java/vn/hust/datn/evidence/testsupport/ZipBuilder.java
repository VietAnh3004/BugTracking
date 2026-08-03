package vn.hust.datn.evidence.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builder fluent để dựng nội dung zip trong bộ nhớ cho test — {@code ZipExtractorTest} và
 * {@code SnapshotUploadControllerTest} đều cần tạo zip tuỳ biến (entry hợp lệ, entry zip-slip,
 * entry rác cần lọc...) mà không phải chạm filesystem.
 */
public final class ZipBuilder {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ZipOutputStream zip = new ZipOutputStream(buffer);

    public ZipBuilder file(String path, String content) {
        return file(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public ZipBuilder file(String path, byte[] content) {
        try {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content);
            zip.closeEntry();
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Entry thư mục tường minh — không phổ biến trong zip thật (đa số công cụ chỉ ghi file) nhưng hợp lệ. */
    public ZipBuilder directory(String path) {
        try {
            String normalized = path.endsWith("/") ? path : path + "/";
            zip.putNextEntry(new ZipEntry(normalized));
            zip.closeEntry();
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] build() {
        try {
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
