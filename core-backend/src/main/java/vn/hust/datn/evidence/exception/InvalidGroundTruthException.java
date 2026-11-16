package vn.hust.datn.evidence.exception;

/**
 * File nhãn ground truth không đọc được hoặc có mục sai. 400 kèm danh sách mục sai — và KHÔNG ghi
 * mục nào: nạp một nửa file thì người dùng không biết DB đang ở trạng thái nào.
 */
public class InvalidGroundTruthException extends RuntimeException {

    public InvalidGroundTruthException(String message) {
        super(message);
    }
}
