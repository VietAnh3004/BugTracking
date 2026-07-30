package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hust.datn.evidence.domain.EvidenceLink;

public interface EvidenceLinkRepository extends JpaRepository<EvidenceLink, UUID> {

    // EvidenceLink lưu fromEvidenceId/toEvidenceId là UUID trần, không có @ManyToOne sang
    // EvidenceRecord -> không join JPQL được. Lấy link trước, gom id phía đối diện, rồi
    // findAllById(...) ở service (hai bước) — không sửa entity (tránh thêm bẫy LAZY), không
    // native SQL (giữ ddl-auto: validate che lưng).
    List<EvidenceLink> findByFromEvidenceIdOrToEvidenceId(java.util.UUID fromEvidenceId, java.util.UUID toEvidenceId);
}
