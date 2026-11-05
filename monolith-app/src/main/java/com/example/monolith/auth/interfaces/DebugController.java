package com.example.monolith.auth.interfaces;

import com.example.monolith.auth.application.AuthService;
import com.example.monolith.shared.api.Result;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示辅助接口，给 Vue 的"Token 无感刷新"演示页用，和 Vue 项目 {@code mock/routes/debug.ts} 一致。
 *
 * <p>{@code @Profile("demo")}：只有激活 demo profile 时这个 Bean 才存在，其他环境里这两个路径根本没有处理器（404）。
 * 用 profile 控制而不是在代码里写 if：生产包里即使带着这个类，也不会被注册。
 */
@Profile("demo")
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final AuthService authService;

    public DebugController(AuthService authService) {
        this.authService = authService;
    }

    /** 让当前请求带的 access token 立即失效，下一个请求就会收到 401。 */
    @PostMapping("/expire-token")
    public Result<Void> expireToken(@AuthenticationPrincipal Jwt jwt) {
        authService.expireAccessToken(jwt.getId(), jwt.getExpiresAt());
        return Result.ok();
    }

    /** 累计收到的刷新请求数。公开接口：避免它自己触发刷新、干扰统计（和 Mock 的做法一样）。 */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(Map.of("refreshCount", authService.refreshRequestCount()));
    }
}
