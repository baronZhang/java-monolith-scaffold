package com.example.monolith.user.application;

import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.user.UserDeleted;
import com.example.monolith.user.UserStatusChanged;
import com.example.monolith.user.domain.User;
import com.example.monolith.user.domain.UserRepository;
import com.example.monolith.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户的写操作用例：启用 / 禁用、删除。
 *
 * <p>应用层的职责是"编排"：取出聚合 → 调用领域对象上的业务方法（规则在领域层）→ 保存 → 发布事件。
 * 它本身不写业务规则。
 *
 * <p>事件在事务里发布，但 Spring Modulith 会先把它记进 event_publication 表（和业务数据同一个事务），
 * 等事务提交后才异步通知监听器。事务回滚了，事件也就不存在——不会出现"数据没改成，通知却发出去了"。
 */
@Service
public class UserCommandService {

    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserCommandService(UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UserView changeStatus(long id, UserStatus target, long operatorId) {
        User user = findOrThrow(id);
        if (user.changeStatus(target, operatorId)) {
            Instant now = clock.instant();
            users.updateStatus(user, now);
            events.publishEvent(new UserStatusChanged(id, target.value(), now));
        }
        return UserView.from(user);
    }

    @Transactional
    public void delete(long id, long operatorId) {
        User user = findOrThrow(id);
        user.assertDeletableBy(operatorId);
        Instant now = clock.instant();
        if (users.markDeleted(id, now)) {
            events.publishEvent(new UserDeleted(id, now));
        }
    }

    private User findOrThrow(long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("用户 " + id + " 不存在"));
    }
}
