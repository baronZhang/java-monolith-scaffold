package com.example.monolith.user.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.persistence.DbTime;
import com.example.monolith.shared.persistence.LikeEscaper;
import com.example.monolith.user.domain.User;
import com.example.monolith.user.domain.UserRepository;
import com.example.monolith.user.domain.UserSearchCriteria;
import com.example.monolith.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 用户仓储的 MyBatis-Plus 实现（适配器）。
 *
 * <p>Wrapper（MyBatis-Plus 的条件构造器）只允许出现在 infrastructure 层，ArchUnit 测试会检查。
 * 否则查询条件会散落到 Service 甚至 Controller 里，换 ORM 或者排查慢 SQL 时到处都要改。
 */
@Repository
class MybatisUserRepository implements UserRepository {

    private static final long NOT_DELETED = 0L;

    private final UserMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;

    MybatisUserRepository(UserMapper userMapper, RolePermissionMapper rolePermissionMapper) {
        this.userMapper = userMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(userMapper.selectOne(alive().eq(UserPO::getId, id)))
                .map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(alive().eq(UserPO::getUsername, username)))
                .map(this::toDomain);
    }

    @Override
    public PageResult<User> search(UserSearchCriteria criteria) {
        LambdaQueryWrapper<UserPO> query = alive();
        if (StringUtils.hasText(criteria.keyword())) {
            String keyword = LikeEscaper.escape(criteria.keyword());
            // 用 and(...) 把两个 or 条件括起来，否则会和前面的 deleted = 0 组合错
            query.and(w -> w.like(UserPO::getUsername, keyword).or().like(UserPO::getNickname, keyword));
        }
        if (criteria.status() != null) {
            query.eq(UserPO::getStatus, criteria.status().value());
        }
        query.orderByAsc(UserPO::getId);
        // 分页插件先执行 count，再追加 LIMIT；count 为 0 时不再查数据
        Page<UserPO> page = userMapper.selectPage(Page.of(criteria.page(), criteria.pageSize()), query);
        List<User> list = page.getRecords().stream().map(this::toDomain).toList();
        return new PageResult<>(list, page.getTotal(), criteria.page(), criteria.pageSize());
    }

    @Override
    public void updateStatus(User user, Instant updatedAt) {
        userMapper.updateStatus(user.id(), user.status().value(), DbTime.toDb(updatedAt));
    }

    @Override
    public boolean markDeleted(long id, Instant updatedAt) {
        return userMapper.markDeleted(id, DbTime.toDb(updatedAt)) == 1;
    }

    @Override
    public List<String> findPermissions(String role) {
        return rolePermissionMapper.findPermissions(role);
    }

    private static LambdaQueryWrapper<UserPO> alive() {
        return Wrappers.lambdaQuery(UserPO.class).eq(UserPO::getDeleted, NOT_DELETED);
    }

    private User toDomain(UserPO po) {
        return new User(
                po.getId(),
                po.getUsername(),
                po.getNickname(),
                po.getEmail(),
                po.getRole(),
                UserStatus.fromValue(po.getStatus()),
                po.getPasswordHash(),
                DbTime.fromDb(po.getCreatedAt()));
    }
}
