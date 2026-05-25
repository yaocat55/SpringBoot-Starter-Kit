package com.quick.springbootapiresult.exception;

/**
 * 参数错误异常。
 *
 * <pre>{@code
 * throw new BadRequestException("pageSize 不能超过 100");
 * // → {"code":400, "msg":"参数错误: pageSize 不能超过 100"}
 * }</pre>
 */
@BizErrorCode(code = 400, message = "参数错误")
public class BadRequestException extends BizException {
    public BadRequestException(String detail) {
        super(detail);
    }
}
