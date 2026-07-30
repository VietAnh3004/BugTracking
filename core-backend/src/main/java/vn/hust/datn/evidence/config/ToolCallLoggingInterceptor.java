package vn.hust.datn.evidence.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ghi lại từng tool call trên {@code /api/v1/tools/**}: tên tool, thời lượng, mã trạng thái.
 *
 * <p>Bảng {@code llm_trace} thật là tuần 12-13 (khi có agent loop), nhưng dữ liệu này KHÔNG tái
 * tạo được về sau nếu không log từ ngày đầu — biến luận điểm "5 tool là ranh giới kiến trúc thật,
 * dễ log/đo" (kế hoạch §1) từ khẩu hiệu thành số liệu thật.
 */
public class ToolCallLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("vn.hust.datn.evidence.tool-call");
    private static final String START_TIME_ATTR = "toolCallStartNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startNanos = (Long) request.getAttribute(START_TIME_ATTR);
        long durationMs = startNanos == null ? -1 : (System.nanoTime() - startNanos) / 1_000_000;
        String toolName = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);

        log.info(
                "tool_call tool_name={} status={} duration_ms={} outcome={}",
                toolName,
                response.getStatus(),
                durationMs,
                response.getStatus() < 400 ? "success" : "error");
    }
}
