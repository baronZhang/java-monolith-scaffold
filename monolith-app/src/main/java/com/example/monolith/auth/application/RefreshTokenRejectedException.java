package com.example.monolith.auth.application;

import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;

/**
 * refresh token 不能用了：不存在、过期、已用过、会话已作废。统一返回 HTTP 401，前端据此跳转登录页。
 *
 * <p>对外的提示故意不区分具体原因：告诉调用方"这个 token 已经用过了"，等于告诉攻击者他偷到的 token 被发现了。
 */
public class RefreshTokenRejectedException extends BusinessException {

    public RefreshTokenRejectedException() {
        super(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }
}
