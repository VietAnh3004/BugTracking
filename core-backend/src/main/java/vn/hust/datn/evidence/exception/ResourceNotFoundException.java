package vn.hust.datn.evidence.exception;

/** Không tìm thấy tài nguyên được yêu cầu. Ánh xạ sang HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("Không tìm thấy %s với định danh %s".formatted(resource, id));
    }
}
