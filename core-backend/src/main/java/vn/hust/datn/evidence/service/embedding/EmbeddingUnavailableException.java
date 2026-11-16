package vn.hust.datn.evidence.service.embedding;

/**
 * Không sinh được vector — embedding chưa bật, nhà cung cấp hết quota, hay mất mạng. Không bao
 * giờ làm hỏng một lần phân tích hay một lượt tìm kiếm: người gọi lùi về so chữ và GHI LẠI lý do.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }
}
