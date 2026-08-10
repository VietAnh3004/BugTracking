package vn.hust.datn.evidence.exception;

/**
 * Quyết định gửi lên không hợp lệ về mặt nghiệp vụ.
 *
 * <p>Ca duy nhất tới lúc này: {@code messageId} không phải một câu trả lời mà chính người review
 * đọc được trong đúng run đang xét. Ném ra 400 kèm lý do chứ KHÔNG lặng lẽ ghi {@code NULL} —
 * ghi NULL sẽ đẩy một người có dùng Agent vào nhóm đối chứng của user study, và không gì báo.
 */
public class InvalidReviewException extends RuntimeException {

    public InvalidReviewException(String message) {
        super(message);
    }
}
