package com.example.monolith.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 用户管理接口：分页搜索、详情、启停、删除，行为和 Vue 的 Mock 一致；
 * 以及跨模块的事件链路——禁用 / 删除用户（用户模块）→ 事件 → 作废会话（认证模块）。
 */
@IntegrationTest
class UserManagementIT {

    static final String TEST_PASSWORD = "it-password-1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    ApiClient api;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, jsonMapper);
        adminToken = api.accessToken(DemoAccounts.ADMIN);
    }

    @Test
    void 分页搜索_关键字匹配用户名或昵称() throws Exception {
        JsonNode page = api.getJson("/api/users?keyword=user05&page=1&pageSize=5", adminToken)
                .data();

        // 和 Mock 一样：user050 ~ user059 共 10 个，第一页 5 个，按 id 升序
        assertThat(page.get("total").asLong()).isEqualTo(10);
        assertThat(page.get("list")).hasSize(5);
        assertThat(page.get("list").get(0).get("username").asString()).isEqualTo("user050");
        assertThat(page.get("list").get(0).get("createdAt").asString()).isEqualTo("2026-03-17T01:00:00Z");
        assertThat(page.get("page").asLong()).isEqualTo(1);
        assertThat(page.get("pageSize").asLong()).isEqualTo(5);
    }

    @Test
    void 关键字里的下划线和百分号按普通字符匹配() throws Exception {
        // 如果不转义，"_" 匹配任意单个字符："r_0" 会匹配到 user001~user009 等一批用户；"%" 会匹配全部用户
        assertThat(api.getJson("/api/users?keyword=r_0", adminToken)
                        .data()
                        .get("total")
                        .asLong())
                .isZero();
        assertThat(api.getJson("/api/users?keyword=%25", adminToken)
                        .data()
                        .get("total")
                        .asLong())
                .isZero();
    }

    @Test
    void 按状态筛选_非法状态返回400() throws Exception {
        JsonNode disabled = api.getJson("/api/users?status=disabled&pageSize=100", adminToken)
                .data();
        disabled.get("list")
                .forEach(user -> assertThat(user.get("status").asString()).isEqualTo("disabled"));

        assertThat(api.getJson("/api/users?status=unknown", adminToken).status())
                .isEqualTo(400);
    }

    @Test
    void 用户不存在返回HTTP404加业务码404() throws Exception {
        ApiClient.Response response = api.getJson("/api/users/9999", adminToken);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("用户 9999 不存在");
    }

    @Test
    void 不能禁用和删除自己_HTTP200加业务码10002() throws Exception {
        ApiClient.Response disableSelf = changeStatus(DemoAccounts.ADMIN_ID, "disabled");
        assertThat(disableSelf.status()).isEqualTo(200);
        assertThat(disableSelf.code()).isEqualTo(10002);
        assertThat(disableSelf.message()).isEqualTo("不能禁用当前登录的账号");

        ApiClient.Response deleteSelf = api.toResponse(mockMvc.perform(
                        delete("/api/users/" + DemoAccounts.ADMIN_ID).header("Authorization", "Bearer " + adminToken))
                .andReturn());
        assertThat(deleteSelf.code()).isEqualTo(10002);
        assertThat(deleteSelf.message()).isEqualTo("不能删除当前登录的账号");
    }

    @Test
    void 禁用用户后_他的会话通过事件被作废() throws Exception {
        insertLoginableUser(1000, "it_disable");
        JsonNode tokens = login("it_disable");
        String accessToken = tokens.get("accessToken").asString();
        assertThat(api.getJson("/api/auth/profile", accessToken).status()).isEqualTo(200);

        // 用户模块提交事务 → 事件 → 认证模块在另一个线程里作废会话：异步的，所以要等一下
        assertThat(changeStatus(1000, "disabled").data().get("status").asString())
                .isEqualTo("disabled");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                                api.getJson("/api/auth/profile", accessToken).status())
                        .isEqualTo(401));

        assertThat(api.refresh(tokens.get("refreshToken").asString()).status()).isEqualTo(401);
        ApiClient.Response relogin = api.postJson(
                "/api/auth/login", null, "{\"username\":\"it_disable\",\"password\":\"%s\"}".formatted(TEST_PASSWORD));
        assertThat(relogin.code()).isEqualTo(10002);
        assertThat(relogin.message()).isEqualTo("账号已被禁用，请联系管理员");
    }

    @Test
    void 删除用户是逻辑删除_之后查不到_会话被作废() throws Exception {
        insertLoginableUser(1001, "it_delete");
        String accessToken = login("it_delete").get("accessToken").asString();

        ApiClient.Response deleted = api.toResponse(
                mockMvc.perform(delete("/api/users/1001").header("Authorization", "Bearer " + adminToken))
                        .andReturn());
        assertThat(deleted.code()).isZero();

        assertThat(api.getJson("/api/users/1001", adminToken).status()).isEqualTo(404);
        // 数据还在，只是 deleted 被写成了自己的 id
        assertThat(jdbc.queryForObject("SELECT deleted FROM sys_user WHERE id = 1001", Long.class))
                .isEqualTo(1001L);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                                api.getJson("/api/auth/profile", accessToken).status())
                        .isEqualTo(401));
    }

    private ApiClient.Response changeStatus(long userId, String status) throws Exception {
        return api.toResponse(mockMvc.perform(put("/api/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andReturn());
    }

    private JsonNode login(String username) throws Exception {
        ApiClient.Response response = api.postJson(
                "/api/auth/login",
                null,
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD));
        assertThat(response.code()).as(response.body()).isZero();
        return response.data();
    }

    /** 种子数据里只有三个演示账号能登录；这里插一个专用账号，免得禁用、删除操作影响其他测试 */
    private void insertLoginableUser(long id, String username) {
        jdbc.update(
                "INSERT INTO sys_user (id, username, nickname, email, role, status, password_hash, deleted,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, 'guest', 'active', ?, 0,"
                        + " '2026-10-01 00:00:00', '2026-10-01 00:00:00')",
                id,
                username,
                "测试用户",
                username + "@example.com",
                passwordEncoder.encode(TEST_PASSWORD));
    }
}
