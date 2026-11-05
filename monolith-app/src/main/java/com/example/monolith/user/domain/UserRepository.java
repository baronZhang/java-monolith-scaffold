package com.example.monolith.user.domain;

import com.example.monolith.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储（端口）：领域层定义"需要什么"，基础设施层（MyBatis-Plus）负责"怎么做"。
 *
 * <p>这就是依赖倒置：领域层不依赖持久化框架，反过来由基础设施层依赖领域层的接口。
 * 换 ORM、加缓存，都只动基础设施层。
 */
public interface UserRepository {

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);

    PageResult<User> search(UserSearchCriteria criteria);

    void updateStatus(User user, Instant updatedAt);

    /** 逻辑删除；返回是否真的删除了（并发删除时只有一个请求能删成功）。 */
    boolean markDeleted(long id, Instant updatedAt);

    /** 角色拥有的权限码。 */
    List<String> findPermissions(String role);
}
