package com.example.monolith.user;

import java.util.Optional;

/**
 * 用户模块对外提供的接口（进程内 API）。
 *
 * <p>其他模块只能通过它访问用户数据，不能直接查 sys_user 表、不能注入用户模块的 Mapper。
 * 将来把用户模块拆成独立服务时，调用方改成依赖一个实现了同样接口的 HTTP / RPC 客户端即可。
 */
public interface UserApi {

    /** 按用户名查账号（登录用）；用户不存在或已删除时返回空。 */
    Optional<UserAccount> findAccountByUsername(String username);

    /** 按 id 查账号（刷新 token 时确认用户仍然有效、权限是否变化）。 */
    Optional<UserAccount> findAccount(long userId);

    /** 当前用户的资料和权限，对应前端的 UserProfile。 */
    Optional<UserProfile> findProfile(long userId);
}
