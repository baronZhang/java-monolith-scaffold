package com.example.monolith.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 三个演示账号 × 各个接口的权限矩阵：前端藏按钮、藏菜单只是体验，这里证明接口级的拦截是真实存在的。
 *
 * <p>为了不让写操作互相影响，写接口用的目标用户各不相同（id 40~49），并且都选"不存在 → 404"之外的合法对象。
 */
@IntegrationTest
class PermissionMatrixIT {

    /** 每个账号只登录一次，矩阵里的多行用例复用同一个 token */
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @ParameterizedTest(name = "{0} {1} {2} → HTTP {3}")
    @CsvSource({
        // 账号,   方法,   路径,                   期望状态码
        "admin,  GET,    /api/users,              200",
        "editor, GET,    /api/users,              200",
        "guest,  GET,    /api/users,              403",
        "admin,  GET,    /api/users/4,            200",
        "guest,  GET,    /api/users/4,            403",
        "admin,  PUT,    /api/users/41/status,    200",
        "editor, PUT,    /api/users/42/status,    200",
        "guest,  PUT,    /api/users/43/status,    403",
        "admin,  DELETE, /api/users/44,           200",
        // editor 能看、能启停，但没有 user:delete：前端看不到"删除"按钮，直接调接口也是 403
        "editor, DELETE, /api/users/45,           403",
        "guest,  DELETE, /api/users/46,           403",
        // 商品、订单登录即可；改价、调库存只有 '*'
        "guest,  GET,    /api/products,           200",
        "editor, PUT,    /api/products/1/price,   403",
        "guest,  PUT,    /api/inventory/1,        403",
        "admin,  GET,    /actuator/metrics,       200",
        "editor, GET,    /actuator/metrics,       403",
    })
    void 权限矩阵(String account, String method, String path, int expectedStatus) throws Exception {
        String token = TOKENS.computeIfAbsent(account, this::login);
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get(path);
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content(bodyFor(path));
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        request.header("Authorization", "Bearer " + token);

        ApiClient.Response response = api.toResponse(mockMvc.perform(request).andReturn());

        assertThat(response.status()).as(response.body()).isEqualTo(expectedStatus);
        if (expectedStatus == 403) {
            assertThat(response.code()).isEqualTo(403);
            assertThat(response.message()).isEqualTo("没有权限执行该操作");
        }
    }

    private String login(String account) {
        try {
            return api.accessToken(account.equals("admin") ? DemoAccounts.ADMIN : account);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String bodyFor(String path) {
        if (path.endsWith("/status")) {
            return "{\"status\":\"disabled\"}";
        }
        if (path.endsWith("/price")) {
            return "{\"price\":399.00}";
        }
        return "{\"available\":100}";
    }
}
