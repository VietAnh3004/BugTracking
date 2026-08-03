package vn.hust.datn.evidence.exception;

import java.util.UUID;

import vn.hust.datn.evidence.domain.SnapshotStatus;

/**
 * Snapshot chưa {@code READY} (đang giải nén, hoặc đã {@code FAILED}). 409 Conflict.
 *
 * <p>Cùng nguyên tắc với {@link RunNotQueryableException}: phân biệt "cây file rỗng vì chưa giải
 * nén xong" với "rỗng vì đúng là rỗng". Trả 200 kèm danh sách rỗng ở đây sẽ khiến FE vẽ một
 * snapshot trống rỗng trông như upload thành công nhưng không có file nào — đúng loại hỏng im lặng
 * mà dự án này chống.
 */
public class SnapshotNotReadyException extends RuntimeException {

    public SnapshotNotReadyException(UUID snapshotId, SnapshotStatus status) {
        super("repository_snapshot %s đang ở trạng thái %s, chưa thể duyệt file".formatted(snapshotId, status));
    }
}
