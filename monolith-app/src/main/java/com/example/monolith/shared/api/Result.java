package com.example.monolith.shared.api;

import com.example.monolith.shared.error.ErrorCode;

/**
 * 统一响应结构，和 Vue 前端 {@code src/types/api.ts} 里的 {@code ApiResult<T>} 一一对应。
 *
 * <p>约定：code = 0 表示成功；认证、权限类错误同时返回 HTTP 401 / 403，普通业务失败返回 HTTP 200 + 非 0 业务码。
 *
 * @param code 业务码，含义见 {@link ErrorCode}
 * @param message 提示信息，失败时前端可以直接展示
 * @param data 业务数据，失败时为 null
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.code(), "ok", data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static Result<Void> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }
}
