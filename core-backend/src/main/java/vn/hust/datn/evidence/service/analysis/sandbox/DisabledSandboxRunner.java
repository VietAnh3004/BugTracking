package vn.hust.datn.evidence.service.analysis.sandbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Bean mặc định khi {@code app.sandbox.runner} không phải {@code docker}.
 *
 * <p>Tồn tại để ứng dụng KHỞI ĐỘNG ĐƯỢC trên môi trường không có Docker daemon. Không có nó thì
 * {@code AnalysisRunService} sẽ thiếu một bean bắt buộc và cả ứng dụng không lên — tức là một
 * tính năng phụ (sandbox) kéo sập một hệ thống mà 90% chức năng không dùng tới nó.
 *
 * <p>{@link #run} ném thay vì trả về rỗng: một lần chạy sandbox trả về "0 artifact" trông y hệt
 * một lần chạy thật không tìm thấy gì.
 *
 * <p>Điều kiện nạp bean khai TƯỜNG MINH theo giá trị, không dùng {@code @ConditionalOnMissingBean}:
 * annotation đó chỉ đáng tin trong auto-configuration, còn với component scan thì thứ tự đánh giá
 * không xác định và có thể nạp cả hai bean. {@code SandboxProperties} ép {@code runner} chỉ nhận
 * đúng hai giá trị nên hai điều kiện dưới đây phủ kín.
 */
@Service
@ConditionalOnProperty(name = "app.sandbox.runner", havingValue = "disabled", matchIfMissing = true)
public class DisabledSandboxRunner implements SandboxRunner {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public SandboxResult run(SandboxRequest request) {
        throw new SandboxException(
                "Sandbox dang TAT (app.sandbox.runner=disabled). Bat bang ANALYSIS_RUNNER=docker, hoac nap "
                        + "artifact thu cong qua POST /api/v1/snapshots/{id}/analysis-runs.");
    }
}
