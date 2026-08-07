package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Lọc theo {@code ownerId} NGAY TRONG truy vấn, không "tìm rồi so sánh trong Java" — tài
     * nguyên không thuộc về bạn phải ra 404 chứ không phải 403 (quy ước phân quyền của repo).
     */
    Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Conversation> findByAnalysisRunIdAndOwnerIdOrderByUpdatedAtDesc(UUID analysisRunId, UUID ownerId);
}
