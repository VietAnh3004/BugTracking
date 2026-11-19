package vn.hust.datn.evidence.exception;

/**
 * Thao tác quản trị bị từ chối vì chính nó nguy hiểm: admin tự xoá mình, hoặc xoá tài khoản ADMIN.
 * 409 Conflict — yêu cầu hợp lệ, nhưng trạng thái hiện tại không cho phép.
 */
public class AdminActionNotAllowedException extends RuntimeException {

    public AdminActionNotAllowedException(String message) {
        super(message);
    }
}
