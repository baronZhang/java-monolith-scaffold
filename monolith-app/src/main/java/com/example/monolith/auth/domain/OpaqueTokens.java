package com.example.monolith.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 不透明 token（refresh token）的生成和哈希。只用 JDK，领域层可以直接用。
 *
 * <p>refresh token 故意不用 JWT：它需要服务端状态（用过没有、属于哪个会话）才能做轮换和重放检测，
 * 做成 JWT 也得查库，自包含的好处用不上，反而更长、更容易被误当成 access token 使用。
 */
public final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {}

    /** 32 字节（256 位）安全随机数，Base64URL 编码，不带填充。 */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // 每个 JDK 都必须支持 SHA-256，走到这里说明运行环境有问题
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }
}
