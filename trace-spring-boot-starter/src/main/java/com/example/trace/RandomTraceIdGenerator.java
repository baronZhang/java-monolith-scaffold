package com.example.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认的 traceId：128 位随机数，写成 32 位小写十六进制。
 *
 * <p>和 W3C Trace Context（traceparent 头）里 trace-id 的格式一致，以后接入 OpenTelemetry 时可以直接沿用。
 * traceId 只用来关联日志，不是安全凭证，所以用 ThreadLocalRandom 就够了，不需要 SecureRandom 的开销。
 */
public class RandomTraceIdGenerator implements TraceIdGenerator {

    private static final HexFormat HEX = HexFormat.of();

    @Override
    public String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return HEX.toHexDigits(random.nextLong()) + HEX.toHexDigits(random.nextLong());
    }
}
