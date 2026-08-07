package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.LlmTrace;

public interface LlmTraceRepository extends JpaRepository<LlmTrace, UUID> {

    List<LlmTrace> findByConversationIdOrderByCreatedAt(UUID conversationId);
}
