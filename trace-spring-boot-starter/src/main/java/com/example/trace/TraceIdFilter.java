package com.example.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 给每个请求分配 traceId：放进 MDC（日志里自动带上）、写回响应头（前端报错时可以把它发给后端排查）。
 *
 * <p>注册顺序是最高优先级，排在 Spring Security 过滤器链前面，所以 401 / 403 的日志里也有 traceId。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    /**
     * 只接受 8~64 位的字母、数字、横线、下划线。
     * 请求头是外部输入：如果原样写进日志，攻击者可以在里面塞换行符，伪造出一整行假日志（日志注入）。
     */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final TraceProperties properties;
    private final TraceIdGenerator generator;

    public TraceIdFilter(TraceProperties properties, TraceIdGenerator generator) {
        this.properties = properties;
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(properties.getHeaderName()));
        MDC.put(properties.getMdcKey(), traceId);
        // 在响应提交之前写头；放到 finally 里就可能太晚了
        response.setHeader(properties.getHeaderName(), traceId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (properties.isAccessLog()) {
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                ACCESS_LOG.info(
                        "{} {} -> {} ({} ms)",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMillis);
            }
            // 线程会被线程池复用：不清理的话，下一个请求会带着上一个请求的 traceId
            MDC.remove(properties.getMdcKey());
        }
    }

    private String resolveTraceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return generator.generate();
    }
}
