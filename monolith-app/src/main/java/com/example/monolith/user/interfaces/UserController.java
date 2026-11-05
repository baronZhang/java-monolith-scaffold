package com.example.monolith.user.interfaces;

import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.security.CurrentUser;
import com.example.monolith.shared.security.RequiresPermission;
import com.example.monolith.user.application.UserCommandService;
import com.example.monolith.user.application.UserQueryService;
import com.example.monolith.user.application.UserView;
import com.example.monolith.user.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，路径和行为与 Vue 项目 {@code mock/routes/user.ts} 一致。
 *
 * <p>接口层只做三件事：把 HTTP 请求转换成用例的入参（含当前用户 id）、声明需要的权限、把结果包装成 Result。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService queries;
    private final UserCommandService commands;

    public UserController(UserQueryService queries, UserCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    /** 分页搜索：keyword 按用户名或昵称模糊匹配，status 为空表示全部。 */
    @GetMapping
    @RequiresPermission("user:list")
    public Result<PageResult<UserView>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize) {
        return Result.ok(queries.search(keyword, status, page, pageSize));
    }

    @GetMapping("/{id}")
    @RequiresPermission("user:list")
    public Result<UserView> get(@PathVariable long id) {
        return Result.ok(queries.get(id));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("user:edit")
    public Result<UserView> changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return Result.ok(commands.changeStatus(id, UserStatus.fromValue(request.status()), CurrentUser.id()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("user:delete")
    public Result<Void> delete(@PathVariable long id) {
        commands.delete(id, CurrentUser.id());
        return Result.ok();
    }

    /** 启用 / 禁用的请求体：{"status": "active" | "disabled"} */
    public record StatusRequest(
            @NotNull(message = "status 不能为空")
            @Pattern(regexp = "active|disabled", message = "status 只能是 active 或 disabled")
            String status) {}
}
