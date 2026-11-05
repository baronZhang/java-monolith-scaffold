package com.example.monolith.auth;

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

/**
 * 认证接口的集成测试：完整的过滤器链 + 真实的 JWT 签发和校验 + H2 数据库。
 * 断言的 HTTP 状态码和业务码，就是 Vue 前端请求层依赖的契约。
 */
@IntegrationTest
class AuthFlowIT {

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
    void 密码错误返回HTTP200加业务码10001() throws Exception {
        ApiClient.Response response =
                api.postJson("/api/auth/login", null, "{\"username\":\"admin\",\"password\":\"wrong\"}");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.code()).isEqualTo(10001);
        assertThat(response.message()).isEqualTo("用户名或密码错误");
        assertThat(response.data().isNull()).isTrue();
    }

    @Test
    void 参数校验失败返回400() throws Exception {
        ApiClient.Response response = api.postJson("/api/auth/login", null, "{\"username\":\"\",\"password\":\"\"}");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.message()).contains("用户名不能为空").contains("密码不能为空");
    }

    @Test
    void 不带token访问返回401_同时带WWW_Authenticate头() throws Exception {
        ApiClient.Response response = api.getJson("/api/auth/profile", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo(401);
        assertThat(response.message()).isEqualTo("登录已过期，请重新登录");
        assertThat(response.header("WWW-Authenticate")).startsWith("Bearer");
    }

    @Test
    void 伪造或篡改的token返回401() throws Exception {
        String token = api.accessToken(DemoAccounts.GUEST);
        // 改掉签名的最后几个字符：签名校验不通过
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(api.getJson("/api/auth/profile", tampered).status()).isEqualTo(401);
    }

    @Test
    void 刷新后旧refresh_token作废_再用就整个会话下线() throws Exception {
        JsonNode first = api.login(DemoAccounts.EDITOR);
        String firstRefresh = first.get("refreshToken").asString();

        ApiClient.Response refreshed = api.refresh(firstRefresh);
        assertThat(refreshed.code()).isZero();
        String secondAccess = refreshed.data().get("accessToken").asString();
        String secondRefresh = refreshed.data().get("refreshToken").asString();
        assertThat(api.getJson("/api/auth/profile", secondAccess).status()).isEqualTo(200);

        // 旧 token 再来一次：401，并且被判定为重放
        ApiClient.Response replay = api.refresh(firstRefresh);
        assertThat(replay.status()).isEqualTo(401);
        assertThat(replay.code()).isEqualTo(401);

        // 整个会话作废：新的 refresh token、新的 access token 都不能用了
        assertThat(api.refresh(secondRefresh).status()).isEqualTo(401);
        assertThat(api.getJson("/api/auth/profile", secondAccess).status()).isEqualTo(401);
    }

    @Test
    void 退出登录后access_token立即失效() throws Exception {
        JsonNode tokens = api.login(DemoAccounts.EDITOR);
        String accessToken = tokens.get("accessToken").asString();

        assertThat(api.postJson("/api/auth/logout", accessToken, null).code()).isZero();

        assertThat(api.getJson("/api/auth/profile", accessToken).status()).isEqualTo(401);
        assertThat(api.refresh(tokens.get("refreshToken").asString()).status()).isEqualTo(401);
    }

    @Test
    void demo接口_让token过期后刷新一次就能继续用_并且计数加一() throws Exception {
        JsonNode tokens = api.login(DemoAccounts.ADMIN);
        String accessToken = tokens.get("accessToken").asString();
        long before =
                api.getJson("/api/debug/stats", null).data().get("refreshCount").asLong();

        assertThat(api.postJson("/api/debug/expire-token", accessToken, null).code())
                .isZero();
        assertThat(api.getJson("/api/auth/profile", accessToken).status()).isEqualTo(401);

        ApiClient.Response refreshed = api.refresh(tokens.get("refreshToken").asString());
        String newAccess = refreshed.data().get("accessToken").asString();
        assertThat(api.getJson("/api/auth/profile", newAccess)
                        .data()
                        .get("username")
                        .asString())
                .isEqualTo("admin");

        long after =
                api.getJson("/api/debug/stats", null).data().get("refreshCount").asLong();
        assertThat(after - before).isEqualTo(1);
    }

    @Test
    void 当前用户信息和Mock的结构一致() throws Exception {
        JsonNode profile = api.getJson("/api/auth/profile", api.accessToken(DemoAccounts.EDITOR))
                .data();

        assertThat(profile.get("id").asLong()).isEqualTo(DemoAccounts.EDITOR_ID);
        assertThat(profile.get("nickname").asString()).isEqualTo("运营小王");
        assertThat(profile.get("roles").get(0).asString()).isEqualTo("editor");
        assertThat(profile.get("permissions").toString()).isEqualTo("[\"user:edit\",\"user:list\"]");
    }
}
