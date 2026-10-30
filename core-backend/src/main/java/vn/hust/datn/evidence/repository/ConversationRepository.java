package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Lọc theo {@code ownerId} NGAY TRONG truy vấn, không "tìm rồi so sánh trong Java" — tài
     * nguyên không thuộc về bạn phải ra 404 chứ không phải 403 (quy ước phân quyền của repo).
     */
    Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Conversation> findByAnalysisRunIdAndOwnerIdOrderByUpdatedAtDesc(UUID analysisRunId, UUID ownerId);

    /**
     * Bước BẮT BUỘC trước khi xoá project — xem {@code ProjectService.delete}. FK
     * {@code claim_citation.evidence_record_id} là NO ACTION, và trong chuỗi CASCADE từ project,
     * Postgres xoá {@code evidence_record} TRƯỚC {@code conversation} (theo thứ tự tạo ràng buộc:
     * V2 trước V8), nên citation còn trỏ vào evidence đã xoá và cả lệnh bị chặn. Xoá hội thoại
     * trước thì message → claim → claim_citation đi theo CASCADE của chính nó.
     */
    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.analysisRunId IN ("
            + "SELECT r.id FROM AnalysisRun r, RepositorySnapshot s "
            + "WHERE r.snapshotId = s.id AND s.projectId = :projectId)")
    int deleteByProjectId(@Param("projectId") UUID projectId);
}
