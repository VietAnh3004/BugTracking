package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import vn.hust.datn.evidence.config.properties.UploadProperties;
import vn.hust.datn.evidence.exception.ArchiveLimitExceededException;
import vn.hust.datn.evidence.exception.InvalidArchiveException;
import vn.hust.datn.evidence.testsupport.ZipBuilder;

/**
 * JUnit thuần, không Spring — {@link ZipExtractor} không phụ thuộc gì ngoài
 * {@link UploadProperties}, nên test chạy trong mili-giây và không cần context Spring nào.
 *
 * <p>Mỗi test dùng {@code limits()} với ngưỡng nhỏ (không phải giá trị production thật) để bài
 * test tự tạo được zip vượt ngưỡng mà không phải dựng file khổng lồ thật.
 */
class ZipExtractorTest {

    private final ZipExtractor extractor = new ZipExtractor();

    private UploadProperties limits() {
        return new UploadProperties(20_000, 500L * 1024 * 1024, 200, 2L * 1024 * 1024);
    }

    @Test
    void tuChoiSegmentZipSlipHaiCapThuMuc() {
        byte[] zip = new ZipBuilder().file("../../etc/passwd", "x").build();

        assertThatThrownBy(() -> extractor.extract(zip, limits())).isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void tuChoiDuongDanTuyetDoi() {
        byte[] zip = new ZipBuilder().file("/etc/passwd", "x").build();

        assertThatThrownBy(() -> extractor.extract(zip, limits())).isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void tuChoiZipSlipKieuWindows() {
        byte[] zip = new ZipBuilder().file("..\\..\\x", "noi dung").build();

        assertThatThrownBy(() -> extractor.extract(zip, limits())).isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void tuChoiODiaWindows() {
        byte[] zip = new ZipBuilder().file("C:/Windows/system32/x.dll", "x").build();

        assertThatThrownBy(() -> extractor.extract(zip, limits())).isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void vuotSoFileToiDaNemArchiveLimitExceeded() {
        ZipBuilder builder = new ZipBuilder();
        for (int i = 0; i < 5; i++) {
            builder.file("file-" + i + ".txt", "noi dung");
        }
        byte[] zip = builder.build();
        UploadProperties strictLimits = new UploadProperties(3, 500L * 1024 * 1024, 200, 2L * 1024 * 1024);

        assertThatThrownBy(() -> extractor.extract(zip, strictLimits)).isInstanceOf(ArchiveLimitExceededException.class);
    }

    @Test
    void vuotTongDungLuongGiaiNenNemArchiveLimitExceeded() {
        byte[] zip = new ZipBuilder().file("big.txt", "a".repeat(10_000)).build();
        UploadProperties strictLimits = new UploadProperties(20_000, 1_000, 200, 2L * 1024 * 1024);

        assertThatThrownBy(() -> extractor.extract(zip, strictLimits)).isInstanceOf(ArchiveLimitExceededException.class);
    }

    @Test
    void khongTinKichThuocKhaiTrongHeaderZipMaDemByteThucDaDoc() throws Exception {
        // Entry STORED (không nén) phải khai đúng size/crc thật theo đặc tả ZIP, nhưng ta không
        // bao giờ ĐỌC entry.getSize() ở phía extractor - test này xác nhận nội dung thực tế lớn
        // hơn hẳn một entry rỗng vẫn được đếm đúng và đọc đúng, không tin bất kỳ con số nào từ
        // header thay vì đo trực tiếp bằng vòng lặp đọc byte.
        byte[] content = "noi dung dai hon nhieu so voi mot chuoi rong".repeat(50).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(raw)) {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(content);
            ZipEntry entry = new ZipEntry("real-content.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }

        List<ExtractedEntry> entries = extractor.extract(raw.toByteArray(), limits());

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).content()).isEqualTo(content);
        assertThat(entries.get(0).sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void locBoThuMucRacKhongDuaVaoKetQua() {
        byte[] zip = new ZipBuilder()
                .file(".git/HEAD", "ref: refs/heads/main")
                .file("node_modules/pkg/index.js", "module.exports = {}")
                .file("target/classes/App.class", "binary")
                .file("src/main/App.java", "class App {}")
                .build();

        List<ExtractedEntry> entries = extractor.extract(zip, limits());

        assertThat(entries).extracting(ExtractedEntry::path).containsExactly("src/main/App.java");
    }

    @Test
    void demDongDungKhiFileKhongKetThucBangNewline() {
        byte[] zip = new ZipBuilder().file("no-trailing-newline.txt", "dong1\ndong2\ndong3").build();

        List<ExtractedEntry> entries = extractor.extract(zip, limits());

        assertThat(entries.get(0).lineCount()).isEqualTo(3);
    }

    @Test
    void demDongDungKhiFileKetThucBangNewline() {
        byte[] zip = new ZipBuilder().file("trailing-newline.txt", "dong1\ndong2\n").build();

        List<ExtractedEntry> entries = extractor.extract(zip, limits());

        assertThat(entries.get(0).lineCount()).isEqualTo(2);
    }

    @Test
    void fileNhiPhanDuocDanhDauVaKhongCoLineCount() {
        byte[] binaryContent = {0x50, 0x4B, 0x00, 0x01, 0x02, 0x03};
        byte[] zip = new ZipBuilder().file("app.bin", binaryContent).build();

        List<ExtractedEntry> entries = extractor.extract(zip, limits());

        assertThat(entries.get(0).binary()).isTrue();
        assertThat(entries.get(0).lineCount()).isNull();
    }

    @Test
    void haiEntryTrungDuongDanSauChuanHoaNemInvalidArchive() {
        // java.util.zip.ZipOutputStream tự chặn hai entry TRÙNG Y HỆT tên (ZipException lúc build,
        // không tới lượt extractor) - kịch bản thật đáng lo là hai entry KHÁC NHAU theo byte thô
        // nhưng CÙNG QUY VỀ một path sau chuẩn hoá của ta (ở đây: dấu phân tách '\' so với '/').
        byte[] zip = new ZipBuilder().file("dir/a.txt", "phien ban 1").file("dir\\a.txt", "phien ban 2").build();

        assertThatThrownBy(() -> extractor.extract(zip, limits())).isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void fileKhongPhaiZipNemInvalidArchive() {
        byte[] notAZip = "day khong phai la file zip".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.looksLikeZip(notAZip)).isFalse();
    }

    @Test
    void fileVuotNguongLuuTruVanXuatHienTrongChiMucNhungKhongCoNoiDungDuocLuu() {
        byte[] zip = new ZipBuilder().file("large.bin", "x".repeat(5000)).build();
        UploadProperties strictStoreLimit = new UploadProperties(20_000, 500L * 1024 * 1024, 200, 100);

        List<ExtractedEntry> entries = extractor.extract(zip, strictStoreLimit);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).stored()).isFalse();
        assertThat(entries.get(0).content()).isNull();
        assertThat(entries.get(0).sha256()).isNull();
        assertThat(entries.get(0).sizeBytes()).isEqualTo(5000);
    }
}
