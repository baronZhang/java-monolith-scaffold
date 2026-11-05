package com.example.monolith.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * 统一异常处理、参数校验、traceId 的集成测试：各种异常最终都变成 {code, message, data}，并且不泄露内部细节。
 */
@IntegrationTest
@Import(ErrorHandlingIT.BrokenController.class)
class ErrorHandlingIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    ApiClient api;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, jsonMapper);
        adminToken = api.accessToken(DemoAccounts.ADMIN);
    }

    @Test
    void 请求体不是合法JSON返回400() throws Exception {
        ApiClient.Response response = perform(put("/api/users/5/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{status: oops"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("请求体不是合法的 JSON");
    }

    @Test
    void 枚举值不合法返回400和字段提示() throws Exception {
        ApiClient.Response response = perform(put("/api/users/5/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"frozen\"}"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("status 只能是 active 或 disabled");
    }

    @Test
    void 路径参数类型不对返回400() throws Exception {
        assertThat(perform(get("/api/users/abc")).message()).isEqualTo("参数 id 的格式不正确");
    }

    @Test
    void 不存在的接口返回404_不支持的方法返回405() throws Exception {
        ApiClient.Response notFound = perform(get("/api/nothing-here"));
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(notFound.message()).isEqualTo("接口不存在：GET /api/nothing-here");

        ApiClient.Response notAllowed = perform(delete("/api/products"));
        assertThat(notAllowed.status()).isEqualTo(405);
    }

    @Test
    void 未预期的异常只返回笼统提示_不泄露内部信息() throws Exception {
        ApiClient.Response response = perform(get("/api/test/broken"));

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.code()).isEqualTo(500);
        assertThat(response.message()).isEqualTo("服务器开小差了，请稍后重试");
        assertThat(response.body()).doesNotContain("jdbc:").doesNotContain("password");
    }

    @Test
    void 每个响应都带traceId_请求里带了合法的就沿用() throws Exception {
        ApiClient.Response generated = perform(get("/api/products"));
        assertThat(generated.header("X-Trace-Id")).matches("[0-9a-f]{32}");

        ApiClient.Response propagated = perform(get("/api/products").header("X-Trace-Id", "from-gateway-123"));
        assertThat(propagated.header("X-Trace-Id")).isEqualTo("from-gateway-123");
    }

    private ApiClient.Response perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return api.toResponse(mockMvc.perform(request.header("Authorization", "Bearer " + adminToken))
                .andReturn());
    }

    /** 测试专用：模拟一个出了意外的接口，异常信息里带着敏感内容 */
    @TestConfiguration
    @RestController
    static class BrokenController {

        @GetMapping("/api/test/broken")
        String broken() {
            throw new IllegalStateException("连接 jdbc:mysql://10.0.0.8:3306 失败，password=secret");
        }
    }
}
