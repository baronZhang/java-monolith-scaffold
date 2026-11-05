package com.example.monolith.user.application;

import com.example.monolith.shared.api.PageResult;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.user.domain.UserRepository;
import com.example.monolith.user.domain.UserSearchCriteria;
import com.example.monolith.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户查询用例。只读事务：告诉连接池和数据库这是只读操作，MySQL 可以省掉一些事务开销。 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    /**
     * 分页搜索。
     *
     * @param status 空字符串或 null 表示全部（和前端约定一致）
     */
    public PageResult<UserView> search(String keyword, String status, Long page, Long pageSize) {
        UserSearchCriteria criteria = new UserSearchCriteria(
                keyword == null ? null : keyword.trim(),
                status == null || status.isBlank() ? null : UserStatus.fromValue(status),
                PageResult.normalizePage(page),
                PageResult.normalizePageSize(pageSize));
        return users.search(criteria).map(UserView::from);
    }

    public UserView get(long id) {
        return users.findById(id)
                .map(UserView::from)
                .orElseThrow(() -> BusinessException.notFound("用户 " + id + " 不存在"));
    }
}
