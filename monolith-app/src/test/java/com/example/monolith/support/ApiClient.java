package com.example.monolith.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 集成测试里调接口的小工具：登录、刷新、带 token 发请求，返回解析好的 JSON。
 * 走的是完整的过滤器链和 Controller（MockMvc），和真实 HTTP 请求的处理路径一样，只是不经过网络。
 */
public class ApiClient {

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    public ApiClient(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    /** 登录成功后返回 data 部分：accessToken、refreshToken、expiresIn */
    public JsonNode login(String username) throws Exception {
        Response response = postJson(
                "/api/auth/login",
                null,
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, DemoAccounts.PASSWORD));
        if (response.status() != 200 || response.code() != 0) {
            throw new IllegalStateException("登录失败：" + response.body());
        }
        return response.data();
    }

    public String accessToken(String username) throws Exception {
        return login(username).get("accessToken").asString();
    }

    public Response refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", null, "{\"refreshToken\":\"%s\"}".formatted(refreshToken));
    }

    public Response getJson(String path, String accessToken) throws Exception {
        var request = get(path);
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return toResponse(mockMvc.perform(request).andReturn());
    }

    public Response postJson(String path, String accessToken, String body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return toResponse(mockMvc.perform(request).andReturn());
    }

    public Response toResponse(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String contentType = result.getResponse().getContentType();
        // 只解析 JSON 响应：/actuator/prometheus 返回的是纯文本
        boolean isJson = contentType != null && contentType.contains("json");
        JsonNode json = null;
        if (!body.isBlank() && isJson) {
            try {
                json = jsonMapper.readTree(body);
            } catch (tools.jackson.core.JacksonException ex) {
                throw new IllegalStateException("响应不是合法 JSON（" + contentType + "）：" + body, ex);
            }
        }
        return new Response(result.getResponse().getStatus(), json, body, result);
    }

    /** HTTP 状态码 + 解析后的 {code, message, data} */
    public record Response(int status, JsonNode json, String body, MvcResult raw) {

        public int code() {
            return json == null ? -1 : json.get("code").asInt();
        }

        public String message() {
            return json == null ? null : json.get("message").asString();
        }

        public JsonNode data() {
            return json == null ? null : json.get("data");
        }

        public String header(String name) {
            return raw.getResponse().getHeader(name);
        }
    }
}
