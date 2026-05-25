package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;

/**
 * 数据冲突异常 —— 唯一键重复、并发冲突等。
 */
public class DuplicateKeyException extends BusinessException {

    public DuplicateKeyException(String message) {
        super(ResultCode.CONFLICT, message);
    }
}
