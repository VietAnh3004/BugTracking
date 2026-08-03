package vn.hust.datn.evidence.service;

/**
 * Một file đã giải nén thành công từ archive upload, sẵn sàng để lưu object storage + lập chỉ mục.
 * KHÔNG map trực tiếp từ {@code ZipEntry} — {@code path} đã được chuẩn hoá/kiểm an toàn, và
 * {@code sizeBytes} là byte thực đã đọc, không phải {@code ZipEntry.getSize()} do kẻ tấn công khai.
 *
 * <p>{@code stored=false} khi {@code sizeBytes} vượt {@code maxStoredFileBytes}: file vẫn xuất
 * hiện trong cây (cây phải trung thực với những gì có trong zip) nhưng không có object trên MinIO
 * để đọc — {@code content}/{@code sha256} khi đó là {@code null}.
 */
public record ExtractedEntry(
        String path, byte[] content, long sizeBytes, boolean binary, Integer lineCount, String sha256, boolean stored) {}
