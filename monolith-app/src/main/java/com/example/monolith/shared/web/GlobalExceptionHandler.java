package com.example.monolith.shared.web;

import com.example.monolith.shared.api.Result;
import com.example.monolith.shared.error.BusinessException;
import com.example.monolith.shared.error.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各种异常统一翻译成 {@code {code, message, data}}，Controller 里不写 try-catch。
 *
 * <p>三条原则：
 * <ol>
 *   <li>预期内的错误（业务规则、参数校验）：返回明确的提示，日志只记一行，不打堆栈；</li>
 *   <li>预期外的错误：对外只说"服务器开小差了"，细节（堆栈、SQL）只写进日志——异常信息里可能有表名、SQL、内部地址；</li>
 *   <li>安全相关的异常必须单独处理：如果被最后那个兜底的 Exception 处理器接住，403 会变成 500。</li>
 * </ol>
 *
 * <p>为什么不用 Spring 自带的 ProblemDetail（RFC 9457）：前后端已经约定了 {code, message, data} 结构，
 * 前端的请求层按它拆包。新项目没有历史包袱的话，ProblemDetail 是更标准的选择。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：HTTP 状态码由错误码决定（认证 401、权限 403、不存在 404，其余业务失败 200）。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.errorCode();
        log.info("业务异常 code={} message={}", errorCode.code(), ex.getMessage());
        return respond(errorCode, ex.getMessage());
    }

    /** @RequestBody 上的 @Valid 校验失败：把每个字段的提示拼起来。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        return badRequest(message);
    }

    /** 方法参数（@RequestParam、@PathVariable、@RequestHeader）上的约束校验失败：Spring 6.1 起框架内置。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        return badRequest(message);
    }

    /** 在应用层等非 Web 位置用 @Validated 做的校验。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return badRequest("请求体不是合法的 JSON");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return badRequest("缺少请求头 " + ex.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("缺少参数 " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数 " + ex.getName() + " 的格式不正确");
    }

    /** 没有匹配的接口：Spring 6.1 起由静态资源处理器抛出这个异常。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, "接口不存在：" + ex.getHttpMethod() + " /" + ex.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "不支持 " + ex.getMethod() + " 请求");
    }

    /**
     * 方法级权限（@RequiresPermission）拒绝时抛出的异常。
     * 它发生在 Controller 方法调用时，会先到达这里而不是 Spring Security 的 AccessDeniedHandler，
     * 所以必须在这里处理，否则会掉进下面的兜底处理器变成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    /** 兜底：未预期的异常。对外不暴露细节，日志里留完整堆栈（带 traceId，方便按响应头里的 X-Trace-Id 查）。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未处理的异常", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "服务器开小差了，请稍后重试");
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        log.info("参数校验失败：{}", message);
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.fail(errorCode, message));
    }
}
