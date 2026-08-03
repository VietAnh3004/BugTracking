package vn.hust.datn.evidence.exception;

/**
 * Mục tồn tại trong chỉ mục nhưng không có nội dung để đọc — thư mục, hoặc file vượt ngưỡng
 * {@code app.upload.max-stored-file-bytes} nên chỉ được ghi chỉ mục chứ không đẩy lên object
 * storage ({@code snapshot_file.stored = false}). 409 Conflict.
 *
 * <p>Khác {@link ResourceNotFoundException} (404) một cách có chủ đích: đường dẫn CÓ tồn tại trong
 * snapshot và vẫn hiện trong cây file — trả 404 sẽ khiến FE tưởng cây file sai.
 */
public class FileContentNotStoredException extends RuntimeException {

    public FileContentNotStoredException(String path, String reason) {
        super("Không có nội dung để đọc cho '%s': %s".formatted(path, reason));
    }
}
