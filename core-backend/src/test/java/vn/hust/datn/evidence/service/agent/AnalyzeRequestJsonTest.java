package vn.hust.datn.evidence.service.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hust.datn.evidence.domain.ExperimentConfig;

/**
 * Hình dạng JSON THẬT của {@code POST /analyze}, không phải của record Java.
 *
 * <p>{@code AgentClient} dựng {@code RestClient} riêng nên KHÔNG dùng ObjectMapper của Spring Boot
 * (nơi khai {@code non_null}); ObjectMapper trơn ở đây mô phỏng đúng điều đó. agent-service đọc
 * {@code config} để quyết định chạy cấu hình thực nghiệm nào — sai tên trường là cả ba cấu hình
 * lặng lẽ chạy thành agent thường.
 */
class AnalyzeRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void oThucNghiemGuiConfigDungTenVaModelFree() throws Exception {
        String json = mapper.writeValueAsString(
                AgentClient.AnalyzeRequest.forExperiment(UUID.randomUUID(), "EV-0001", ExperimentConfig.LLM_STATIC));

        assertThat(json)
                .contains("\"config\":\"llm_static\"")
                .contains("\"focus_short_id\":\"EV-0001\"")
                .contains("\"model_tier\":\"free\"");
    }

    @Test
    void hoiDapThuongKhongGuiTruongConfig() throws Exception {
        String json =
                mapper.writeValueAsString(AgentClient.AnalyzeRequest.of(UUID.randomUUID(), "hoi", "EV-0001", List.of()));

        assertThat(json).doesNotContain("config");
    }
}
