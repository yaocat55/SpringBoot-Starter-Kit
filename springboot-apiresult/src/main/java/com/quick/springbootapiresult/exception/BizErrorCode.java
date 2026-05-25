package com.quick.springbootapiresult.exception;

import java.lang.annotation.*;

/**
 * 业务错误码注解 —— 贴在 BizException 子类上，声明该异常对应的 code 和 message 前缀。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * @BizErrorCode(code = 1001, message = "数据不存在")
 * public class NotFoundException extends BizException {
 *     public NotFoundException(String detail) { super(detail); }
 * }
 * }</pre>
 *
 * <p>GlobalExceptionHandler 会读取该注解，自动拼装 ApiResult：
 * <pre>{@code
 * throw new NotFoundException("id=" + id);
 * // → {"code":1001, "msg":"数据不存在: id=1"}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface BizErrorCode {

    /** 错误码 */
    int code();

    /** 错误信息前缀，与异常的 detail 用 ": " 拼接 */
    String message();
}
