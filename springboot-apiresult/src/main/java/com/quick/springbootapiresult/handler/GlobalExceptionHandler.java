package com.quick.springbootapiresult.handler;

import com.quick.springbootapiresult.common.ApiResult;
import com.quick.springbootapiresult.common.ErrorCode;
import com.quick.springbootapiresult.exception.BadRequestException;
import com.quick.springbootapiresult.exception.BizErrorCode;
import com.quick.springbootapiresult.exception.BizException;
import com.quick.springbootapiresult.exception.DuplicateException;
import com.quick.springbootapiresult.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 注解驱动，新增异常类型只需建子类 + 贴 {@link BizErrorCode}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常子类（精确匹配，注解自动提供 code/message） ====================

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleNotFound(NotFoundException e) {
        return build(e);
    }

    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleDuplicate(DuplicateException e) {
        return build(e);
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleBadRequest(BadRequestException e) {
        return build(e);
    }

    /** 兜底：未单独定义 handler 的 BizException 子类，走 @BizErrorCode 注解 */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleBizException(BizException e) {
        log.warn("业务异常 [{}] {}", e.getCode(), e.getMessage());
        return ApiResult.error(e.getCode(), buildMessage(e));
    }

    // ==================== 参数 / 校验 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), "参数 " + e.getName() + " 类型错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleMessageNotReadable() {
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), "请求体不能为空且须为合法 JSON");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数非法: {}", e.getMessage());
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    // ==================== 兜底 ====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<?> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{}] | {} {}", e.getClass().getSimpleName(),
                request.getMethod(), request.getRequestURI(), e);
        return ApiResult.error(ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getMsg() + ": " + e.getMessage());
    }

    // ==================== 工具方法 ====================

    /** 从 @BizErrorCode 注解拼装 message */
    private ApiResult<?> build(BizException e) {
        String msg = buildMessage(e);
        log.warn("业务异常 [{}] {}", e.getCode(), msg);
        return ApiResult.error(e.getCode(), msg);
    }

    /** 注解的 message() + ": " + 异常的 detail */
    private String buildMessage(BizException e) {
        BizErrorCode ann = e.getClass().getAnnotation(BizErrorCode.class);
        String prefix = ann != null ? ann.message() : "";
        String detail = e.getMessage() != null ? e.getMessage() : "";
        return prefix.isEmpty() ? detail : prefix + ": " + detail;
    }
}
