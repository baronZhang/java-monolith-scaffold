package com.example.monolith.user.domain;

import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;

/** 用户状态。value 是前后端约定的字符串（active / disabled），也是数据库里存的值。 */
public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static UserStatus fromValue(String value) {
        for (UserStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "status 只能是 active 或 disabled");
    }
}
