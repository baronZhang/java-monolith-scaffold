package com.example.monolith.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 会话仓储（端口）。 */
public interface AuthSessionRepository {

    void insert(AuthSession session);

    /**
     * 加锁读取（SELECT ... FOR UPDATE）：同一个会话的刷新、吊销在这里排队，一个事务提交后下一个才能拿到锁。
     * 必须在事务里调用，锁随事务结束释放。
     */
    Optional<AuthSession> lockById(String id);

    void updateStatus(AuthSession session, Instant updatedAt);

    List<String> findActiveSessionIds(long userId);
}
