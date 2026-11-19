package vn.hust.datn.evidence.exception;

/** Chọn model Premium trong dự án Free. 403 — đã đăng nhập, nhưng dự án này không có quyền đó. */
public class PremiumModelNotAllowedException extends RuntimeException {

    public PremiumModelNotAllowedException() {
        super("Dự án này chưa được nâng lên Premium nên chỉ dùng được model miễn phí");
    }
}
