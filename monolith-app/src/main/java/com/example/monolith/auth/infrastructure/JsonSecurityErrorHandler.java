package com.example.monolith.auth.infrastructure;

import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 过滤器链里的 401 / 403 统一输出成 {@code {code, message, data}}。
 *
 * <p>这些错误发生在 DispatcherServlet 之前，@RestControllerAdvice 管不到，必须在 Spring Security 这一层处理。
 * 401 仍然先交给官方的 BearerTokenAuthenticationEntryPoint 写 WWW-Authenticate 响应头（RFC 6750 规定的），
 * 再补上 JSON 响应体：既符合标准，前端也能按统一结构解析。
 */
@Component
class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final JsonMapper jsonMapper;

    JsonSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 未登录或 token 无效 → 401 */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        write(response, ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    /** 已登录但没有权限 → 403 */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }

    private void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.fail(errorCode, message)));
    }
}
