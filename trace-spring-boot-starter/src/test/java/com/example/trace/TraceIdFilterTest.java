package com.example.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceProperties properties = new TraceProperties();
    private final TraceIdFilter filter = new TraceIdFilter(properties, () -> "generated-0001");

    @Test
    void 请求头里带了合法的traceId就沿用() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "upstream-trace-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain.set(MDC.get("traceId"));
            }
        });

        assertThat(seenInChain.get()).isEqualTo("upstream-trace-42");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("upstream-trace-42");
        // 请求结束后必须清理，否则线程复用时会串号
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 非法的traceId被丢弃并重新生成_防止日志注入() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Trace-Id", "abc\n2026-10-27 INFO 伪造的日志行");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("generated-0001");
    }

    @Test
    void 装饰后的任务能带上提交线程的MDC() throws Exception {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> seenInWorker = new AtomicReference<>();
        MDC.put("traceId", "trace-in-caller");
        try {
            Runnable decorated = decorator.decorate(() -> seenInWorker.set(MDC.get("traceId")));
            // 换一个线程执行：没有装饰的话这里拿到的是 null
            CompletableFuture.runAsync(decorated).get();
        } finally {
            MDC.clear();
        }
        assertThat(seenInWorker.get()).isEqualTo("trace-in-caller");
    }

    @Test
    void 默认生成器输出32位十六进制() {
        String traceId = new RandomTraceIdGenerator().generate();
        assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
    }
}
