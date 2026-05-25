package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;

/**
 * 权限不足异常 —— 当前用户没有操作权限时 throw。
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ResultCode.FORBIDDEN, message);
    }
}
