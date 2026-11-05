package com.example.monolith.auth.interfaces;

import com.example.monolith.auth.application.AuthService;
import com.example.monolith.auth.application.TokenPair;
import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.security.CurrentUser;
import com.example.monolith.user.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口，路径和行为与 Vue 项目 {@code mock/routes/auth.ts} 一致。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录（公开接口）。用户名或密码错误：HTTP 200 + 业务码 10001。 */
    @PostMapping("/login")
    public Result<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request.username(), request.password()));
    }

    /** 用 refresh token 换一对新 token（公开接口）。失败：HTTP 401 + 业务码 401。 */
    @PostMapping("/refresh")
    public Result<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    /** 退出登录：作废当前会话。只有认证模块知道登录态是 JWT，所以这里可以直接拿 Jwt 对象。 */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt.getClaimAsString("sid"), jwt.getId(), jwt.getExpiresAt());
        return Result.ok();
    }

    @GetMapping("/profile")
    public Result<UserProfile> profile() {
        return Result.ok(authService.profile(CurrentUser.id()));
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名不能超过 64 个字符")
            String username,

            @NotBlank(message = "密码不能为空") @Size(max = 72, message = "密码不能超过 72 个字符")
            String password) {

        /** 不在 toString 里输出密码：校验失败时框架可能会把请求对象打进日志 */
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    public record RefreshRequest(
            @NotBlank(message = "refreshToken 不能为空") String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[]";
        }
    }
}
