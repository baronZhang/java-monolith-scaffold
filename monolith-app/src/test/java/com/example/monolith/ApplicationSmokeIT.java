package com.example.monolith;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 冒烟测试：整个应用能启动，最核心的一条链路（登录 → 带 token 访问）是通的。 */
@IntegrationTest
class ApplicationSmokeIT {

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
    void 应用启动后可以登录并获取当前用户信息() throws Exception {
        JsonNode tokens = api.login(DemoAccounts.ADMIN);
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(300);

        ApiClient.Response profile =
                api.getJson("/api/auth/profile", tokens.get("accessToken").asString());

        assertThat(profile.status()).isEqualTo(200);
        assertThat(profile.code()).isZero();
        assertThat(profile.data().get("username").asString()).isEqualTo("admin");
        assertThat(profile.data().get("permissions").get(0).asString()).isEqualTo("*");
        // 手写 starter 生效：响应头里有 traceId
        assertThat(profile.header("X-Trace-Id")).isNotBlank();
    }
}
