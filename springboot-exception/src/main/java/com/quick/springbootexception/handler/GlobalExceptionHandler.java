package com.quick.springbootexception.handler;

import com.quick.springbootexception.common.Result;
import com.quick.springbootexception.common.ResultCode;
import com.quick.springbootexception.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 整个项目的异常统一在这兜底。
 * <p>
 * {@code @RestControllerAdvice} = @ControllerAdvice + @ResponseBody，
 * 拦截所有 Controller 抛出的异常，转成统一的 Result JSON 返回。
 * <p>
 * <b>直接复制到任何 SpringBoot 项目即可工作</b>，无需额外配置。
 * 新增业务异常只需：
 * <ol>
 *   <li>继承 BusinessException，传入对应的 ResultCode</li>
 *   <li>在业务代码中 throw new XxxException(...)</li>
 *   <li>本类自动捕获并返回统一格式</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常（你自定义的） ====================

    /**
     * 捕获所有 BusinessException 子类 —— NotFound / BadRequest / Forbidden / ...
     * HTTP 状态码从 ResultCode 推断：4xx → 4xx，其他 → 500。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)  // 业务异常一律返回 200，用 code 字段区分错误类型
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} | {} | {}",
                e.getResultCode().getCode(), e.getMessage(),
                request.getMethod(), request.getRequestURI());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    // ==================== 参数校验失败（@Valid / @Validated 抛的） ====================

    /**
     * 处理 @Valid 校验失败 —— 把多字段错误拼成一条消息返回。
     * 例如：新增用户时 name 为空、age 不合法，返回 "name: 不能为空; age: 必须大于0"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ResultCode.PARAM_VALID_FAILED, detail);
    }

    /**
     * 缺少必填的 @RequestParam 参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.fail(ResultCode.BAD_REQUEST, "缺少参数: " + e.getParameterName());
    }

    /**
     * 参数类型转换错误（比如 ID 传了字符串 "abc"）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = String.format("参数 %s 类型错误，期望 %s，收到 %s",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?",
                e.getValue());
        log.warn("参数类型错误: {}", msg);
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    // ==================== Spring MVC 层面的异常 ====================

    /**
     * 404 —— 访问了不存在的 URL（需要 yml 里配 spring.mvc.throw-exception-if-no-handler-found=true）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("404 路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 405 —— GET 接口用 POST 方式调了。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("405 方法不支持: {} 支持 {}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED, "请求方法 " + e.getMethod() + " 不被支持");
    }

    /**
     * 请求体为空或 JSON 格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体为空或格式错误: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "请求体不能为空且须为合法 JSON");
    }

    // ==================== 兜底（未知异常） ====================

    /**
     * 所有未被上面捕获的异常，统一兜底返回 500。
     * <p>
     * 生产环境不要返回 e.getMessage() 给前端（可能泄露堆栈信息），
     * 这里为了方便调试返回了，生产请改成固定文案如 "服务器内部错误"。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{}] {} | {} {}",
                e.getClass().getSimpleName(), e.getMessage(),
                request.getMethod(), request.getRequestURI(), e);
        return Result.fail(ResultCode.INTERNAL_ERROR,
                "服务器内部错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
}
