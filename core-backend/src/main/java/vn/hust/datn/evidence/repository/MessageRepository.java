package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

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
}
