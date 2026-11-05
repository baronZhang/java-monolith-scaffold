package com.example.monolith.auth.interfaces;

import com.example.monolith.auth.application.AuthService;
import com.example.monolith.auth.domain.RevokeReason;
import com.example.monolith.user.UserDeleted;
import com.example.monolith.user.UserStatusChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 监听用户模块的事件，作废被禁用、被删除用户的会话。
 *
 * <p>{@code @ApplicationModuleListener} = {@code @Async} + {@code @Transactional(REQUIRES_NEW)} +
 * {@code @TransactionalEventListener(AFTER_COMMIT)}：用户模块的事务提交之后，在另一个线程、另一个事务里执行。
 * 好处：禁用用户的接口不用等会话作废完成；作废失败也不会让禁用操作回滚。
 * 代价：两步之间有一个很短的时间窗口（毫秒级），这是"最终一致"。可靠性由事件登记表保证：失败了会重投。
 */
@Component
public class UserEventListener {

    private final AuthService authService;

    public UserEventListener(AuthService authService) {
        this.authService = authService;
    }

    @ApplicationModuleListener
    public void on(UserStatusChanged event) {
        if (event.disabled()) {
            authService.revokeAllSessions(event.userId(), RevokeReason.USER_DISABLED);
        }
    }

    @ApplicationModuleListener
    public void on(UserDeleted event) {
        authService.revokeAllSessions(event.userId(), RevokeReason.USER_DELETED);
    }
}
