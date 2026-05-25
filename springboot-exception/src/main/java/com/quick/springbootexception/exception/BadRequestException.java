package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;

/**
 * 参数错误异常 —— 前端传的参数不合法时直接 throw。
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(ResultCode.BAD_REQUEST, message);
    }
}
