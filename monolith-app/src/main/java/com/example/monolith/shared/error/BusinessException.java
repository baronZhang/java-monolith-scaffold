package com.example.monolith.shared.error;

/**
 * 业务异常：表示"请求不满足业务规则"，由全局异常处理器统一翻译成 {@code {code, message, data}}。
 *
 * <p>它是预期之内的分支，不是程序缺陷，所以：
 * <ul>
 *   <li>不收集堆栈（writableStackTrace = false）：省掉填充堆栈的开销，日志里也不会刷出一大段无用的堆栈；</li>
 *   <li>继承 RuntimeException：在 @Transactional 方法里抛出会触发回滚（默认只对 RuntimeException 和 Error 回滚）。</li>
 * </ul>
 *
 * <p>这个类只依赖 JDK，领域层可以放心使用。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException rule(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE, message);
    }
}
