package com.example.monolith.user.application;

import com.example.monolith.user.UserAccount;
import com.example.monolith.user.UserApi;
import com.example.monolith.user.UserProfile;
import com.example.monolith.user.domain.User;
import com.example.monolith.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块对外 API 的实现。类是包级私有的：其他模块只能看到 {@link UserApi} 接口，看不到实现。
 *
 * <p>其他模块调用本模块的入口有两类：HTTP、定时任务、事件这类"技术入口"放在 interfaces 层；
 * 进程内的 API 调用不需要协议转换，实现直接放在应用层。
 */
@Service
@Transactional(readOnly = true)
class UserApiImpl implements UserApi {

    private final UserRepository users;

    UserApiImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserAccount> findAccountByUsername(String username) {
        return users.findByUsername(username).map(this::toAccount);
    }

    @Override
    public Optional<UserAccount> findAccount(long userId) {
        return users.findById(userId).map(this::toAccount);
    }

    @Override
    public Optional<UserProfile> findProfile(long userId) {
        return users.findById(userId)
                .map(user -> new UserProfile(
                        user.id(),
                        user.username(),
                        user.nickname(),
                        List.of(user.role()),
                        users.findPermissions(user.role())));
    }

    private UserAccount toAccount(User user) {
        return new UserAccount(
                user.id(), user.username(), user.passwordHash(), user.isEnabled(), users.findPermissions(user.role()));
    }
}
