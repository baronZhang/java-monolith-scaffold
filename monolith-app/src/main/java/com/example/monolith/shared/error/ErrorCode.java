package com.example.monolith.shared.error;

/**
 * 业务码约定，和 Vue 前端 {@code src/types/api.ts} 里的 {@code ErrorCode} 一致，并做了少量扩展。
 *
 * <p>HTTP 状态码和业务码分工：
 * <ul>
 *   <li>认证（401）、权限（403）、资源不存在（404）、参数错误（400）用对应的 HTTP 状态码，网关、监控、浏览器都能识别；</li>
 *   <li>普通业务失败（密码错、库存不足……）返回 HTTP 200 + 非 0 业务码：请求本身是成功处理了的，只是业务上不允许。</li>
 * </ul>
 */
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, 200),
    /** 参数不合法：Bean Validation 校验失败、请求体不是合法 JSON 等（Vue Mock 对非法 JSON 也是返回 400 / 400） */
    BAD_REQUEST(400, 400),
    /** 未登录或 token 失效 */
    UNAUTHORIZED(401, 401),
    /** 没有权限 */
    FORBIDDEN(403, 403),
    /** 资源不存在 */
    NOT_FOUND(404, 404),
    /** 请求方法不支持，比如对只读接口发了 DELETE */
    METHOD_NOT_ALLOWED(405, 405),
    /** 未预期的服务端错误：只给出笼统的提示，细节写进日志，不暴露给调用方 */
    INTERNAL_ERROR(500, 500),
    /** 用户名或密码错误 */
    LOGIN_FAILED(10001, 200),
    /** 业务规则不允许，比如不能删除自己 */
    BUSINESS_RULE(10002, 200),
    /** 库存不足（和微服务版约定的扩展码） */
    STOCK_INSUFFICIENT(20001, 200),
    /** 订单当前状态不允许该操作（和微服务版约定的扩展码） */
    ORDER_STATE_INVALID(20002, 200);

    private final int code;
    private final int httpStatus;

    ErrorCode(int code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
