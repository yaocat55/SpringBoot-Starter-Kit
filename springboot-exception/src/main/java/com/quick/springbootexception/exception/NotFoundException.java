package com.quick.springbootexception.exception;

import com.quick.springbootexception.common.ResultCode;

/**
 * 资源不存在异常 —— 查数据库返回空、ID 不存在等场景。
 * <p>
 * Controller / Service 中直接 throw，全局异常处理会转成 404 返回。
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String resource, Object id) {
        super(ResultCode.NOT_FOUND, resource + " 不存在: " + id);
    }

    public NotFoundException(String message) {
        super(ResultCode.NOT_FOUND, message);
    }
}
