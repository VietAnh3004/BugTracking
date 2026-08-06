package vn.hust.datn.evidence.service.linker;

import java.util.UUID;

import vn.hust.datn.evidence.domain.TestCaseStatus;

/**
 * Một test case của run, dạng phẳng cho EvidenceLinker.
 *
 * @param evidenceRecordId evidence loại {@code TEST_RESULT}/{@code FAILED_TEST} — ĐÂY mới là thứ
 *     người dùng gọi là "test liên quan" và là đầu {@code from} của mọi liên kết test → cảnh báo
 * @param stackTraceEvidenceId evidence {@code STACK_TRACE} của chính test này; NULL khi test không
 *     hỏng. Đây là provenance CẤU TRÚC có sẵn từ tuần 8-9 — nhờ nó tầng 3 quy được một frame về
 *     test chứa nó mà không cần thêm liên kết ngữ nghĩa nào
 */
public record TestCaseRow(
        UUID evidenceRecordId, String className, String methodName, TestCaseStatus status, UUID stackTraceEvidenceId) {}
