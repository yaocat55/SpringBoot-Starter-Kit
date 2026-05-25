package com.quick.springbootsatoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理 —— 统一返回 SaResult 格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 未登录 */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public SaResult handleNotLogin(NotLoginException e) {
        log.warn("未登录访问: {}", e.getMessage());
        return SaResult.error("请先登录").set("code", 401);
    }

    /** 无角色 */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public SaResult handleNotRole(NotRoleException e) {
        log.warn("角色不足: {}", e.getMessage());
        return SaResult.error("权限不足，需要角色: " + e.getRole()).set("code", 403);
    }

    /** 无权限 */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public SaResult handleNotPermission(NotPermissionException e) {
        log.warn("权限不足: {}", e.getMessage());
        return SaResult.error("权限不足，需要权限: " + e.getPermission()).set("code", 403);
    }

    /** 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SaResult handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return SaResult.error(msg).set("code", 400);
    }

    /** 业务异常 */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public SaResult handleRuntime(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return SaResult.error(e.getMessage()).set("code", 400);
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public SaResult handleException(Exception e) {
        log.error("系统异常", e);
        return SaResult.error("服务器内部错误").set("code", 500);
    }
}
