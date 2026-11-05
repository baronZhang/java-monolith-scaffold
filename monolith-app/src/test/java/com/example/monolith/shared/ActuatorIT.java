package com.example.monolith.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 运维端点：健康检查、存活/就绪探针公开，指标和模块结构只有管理员能看。 */
@IntegrationTest
class ActuatorIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @Test
    void 健康检查和探针不需要登录() throws Exception {
        ApiClient.Response health = api.getJson("/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.json().get("status").asString()).isEqualTo("UP");
        // 数据库是健康检查的组成部分；默认 profile 不用 Redis，它的检查是关掉的
        assertThat(health.json().get("components").has("db")).isTrue();
        assertThat(health.json().get("components").has("redis")).isFalse();

        assertThat(api.getJson("/actuator/health/liveness", null).status()).isEqualTo(200);
        assertThat(api.getJson("/actuator/health/readiness", null).status()).isEqualTo(200);
    }

    @Test
    void 指标和模块结构需要管理员() throws Exception {
        assertThat(api.getJson("/actuator/prometheus", null).status()).isEqualTo(401);

        String adminToken = api.accessToken(DemoAccounts.ADMIN);
        ApiClient.Response prometheus = api.getJson("/actuator/prometheus", adminToken);
        assertThat(prometheus.status()).isEqualTo(200);
        // 刷新次数是一个真实的业务指标，Prometheus 可以直接抓
        assertThat(prometheus.raw().getResponse().getContentAsString()).contains("auth_refresh_requests_total");

        // 模块结构端点：只检查原始响应体（它在 MockMvc 下返回的 JSON 尾部多出一段，见"单体 7.3 踩坑记录"）
        String modulith = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/modulith")
                                .header("Authorization", "Bearer " + adminToken))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(modulith).contains("\"order\"").contains("\"inventory\"");
    }
}
