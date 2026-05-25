package com.quick.springbootapiresult.exception;

/**
 * 数据重复异常（唯一键冲突等）。
 *
 * <pre>{@code
 * throw new DuplicateException("邮箱已存在: test@example.com");
 * // → {"code":1002, "msg":"数据重复: 邮箱已存在: test@example.com"}
 * }</pre>
 */
@BizErrorCode(code = 1002, message = "数据重复")
public class DuplicateException extends BizException {
    public DuplicateException(String detail) {
        super(detail);
    }
}
