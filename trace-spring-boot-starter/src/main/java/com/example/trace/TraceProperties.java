package com.example.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * traceId 相关配置，前缀 {@code scaffold.trace}。
 *
 * <p>字段上的 Javadoc 会被 spring-boot-configuration-processor 收集进
 * {@code META-INF/spring-configuration-metadata.json}，使用方在 IDE 里写 application.yml 时能看到这些说明。
 */
@ConfigurationProperties(prefix = "scaffold.trace")
public class TraceProperties {

    /** 是否启用 traceId 过滤器。关掉后整个自动配置都不生效。 */
    private boolean enabled = true;

    /** 透传 traceId 用的 HTTP 头：请求里带了就沿用（网关、上游服务传下来的），没带就新生成，并写回响应头。 */
    private String headerName = "X-Trace-Id";

    /** 放进 MDC 的键，日志格式里用 %X{traceId} 取出。 */
    private String mdcKey = "traceId";

    /** 是否打印访问日志：每个请求结束时一行，包含方法、路径、状态码和耗时。 */
    private boolean accessLog = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getMdcKey() {
        return mdcKey;
    }

    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    public boolean isAccessLog() {
        return accessLog;
    }

    public void setAccessLog(boolean accessLog) {
        this.accessLog = accessLog;
    }
}
