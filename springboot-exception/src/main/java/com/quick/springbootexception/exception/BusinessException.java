package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常基类 —— 所有自定义异常都继承这个。
 * <p>
 * 带上 ResultCode 后，全局异常处理器能找到对应的 HTTP 状态码和错误消息，
 * 统一返回给前端，不需要每个 Controller 自己 try-catch。
 * <p>
 * 子类只要继承它，一行代码都不用写：
 * <pre>{@code
 * public class UserNotFoundException extends BusinessException {
 *     public UserNotFoundException(Long id) {
 *         super(ResultCode.USER_NOT_FOUND, "用户ID: " + id);
 *     }
 * }
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }
}
