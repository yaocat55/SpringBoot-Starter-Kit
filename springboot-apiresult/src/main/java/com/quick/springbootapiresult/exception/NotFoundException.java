package com.quick.springbootapiresult.exception;

/**
 * 数据不存在异常。
 *
 * <pre>{@code
 * throw new NotFoundException("id=" + id);
 * // → {"code":1001, "msg":"数据不存在: id=1"}
 * }</pre>
 */
@BizErrorCode(code = 1001, message = "数据不存在")
public class NotFoundException extends BizException {
    public NotFoundException(String detail) {
        super(detail);
    }
}
