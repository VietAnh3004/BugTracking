package vn.hust.datn.evidence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.hust.datn.evidence.domain.TestCase;
import vn.hust.datn.evidence.service.linker.TestCaseRow;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    /**
     * Mọi test case của một run, dạng phẳng.
     *
     * <p>Cột {@code stackTraceEvidenceId} là thứ tầng 3 cần: nó quy một frame về TEST chứa frame
     * đó. Không có nó thì liên kết sẽ trỏ từ evidence {@code STACK_TRACE} sang cảnh báo, và giao
     * diện "test liên quan" hiện ra một stack trace thay vì một cái tên test.
     */
    @Query(
            """
            select new vn.hust.datn.evidence.service.linker.TestCaseRow(
                tc.evidenceRecordId, tc.className, tc.methodName, tc.status, tc.stackTraceEvidenceId)
            from TestCase tc, EvidenceRecord e
            where tc.evidenceRecordId = e.id
              and e.analysisRunId = :runId
            order by e.seqInRun
            """)
    List<TestCaseRow> findRowsByRun(@Param("runId") UUID runId);
}
