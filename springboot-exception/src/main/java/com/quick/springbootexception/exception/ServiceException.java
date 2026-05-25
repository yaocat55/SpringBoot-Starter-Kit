package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;

/**
 * 服务端异常 —— 代码 Bug、DB 连接失败、第三方接口挂了等。
 */
public class ServiceException extends BusinessException {

    public ServiceException(String message) {
        super(ResultCode.INTERNAL_ERROR, message);
    }

    public ServiceException(String message, Throwable cause) {
        super(ResultCode.INTERNAL_ERROR, message, cause);
    }
}
