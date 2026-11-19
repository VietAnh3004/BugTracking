package vn.hust.datn.evidence.domain;

/**
 * Model người dùng chọn cho MỘT câu hỏi. {@link #PREMIUM} chỉ hợp lệ trong dự án
 * {@link ProjectTier#PREMIUM}; backend kiểm ở mỗi câu, không tin giao diện.
 */
public enum AgentModel {
    FREE,
    PREMIUM;

    /** Giá trị agent-service nhận ở trường {@code model_tier}. */
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
