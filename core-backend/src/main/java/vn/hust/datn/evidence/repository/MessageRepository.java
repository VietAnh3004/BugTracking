package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.hust.datn.evidence.domain.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByOrdinal(UUID conversationId);

    /**
     * Số thứ tự lượt kế tiếp. Trả về 0 cho hội thoại rỗng nhờ {@code COALESCE} — nếu để
     * {@code MAX} trả {@code null} thì lượt đầu tiên của mọi hội thoại sẽ ném NPE.
     */
    @Query("SELECT COALESCE(MAX(m.ordinal), -1) + 1 FROM Message m WHERE m.conversationId = :conversationId")
    int nextOrdinal(UUID conversationId);

    /**
     * Câu trả lời của Agent mà {@code ownerId} đã nhận về đúng một cảnh báo, mới nhất trước.
     *
     * <p>Tính cả phiên đã ẨN ({@code deleted_at}): người đó vẫn đã đọc câu trả lời trước khi ẩn, và
     * biến thực nghiệm "có đọc Agent" nói về việc đã đọc, không về việc phiên còn hiện hay không.
     */
    @Query("""
            SELECT m FROM Message m, Conversation c
             WHERE c.id = m.conversationId
               AND c.ownerId = :ownerId
               AND c.analysisRunId = :analysisRunId
               AND c.focusShortId = :focusShortId
               AND m.role = vn.hust.datn.evidence.domain.MessageRole.ASSISTANT
             ORDER BY m.createdAt DESC, m.ordinal DESC
            """)
    List<Message> findAgentAnswersForWarning(UUID ownerId, UUID analysisRunId, String focusShortId, Pageable pageable);
}
