package com.quick.springbootapiresult.exception;

import com.quick.springbootapiresult.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常基类 —— 优先从子类的 {@link BizErrorCode} 注解取 code/message。
 *
 * <h3>两种用法</h3>
 * <ol>
 *   <li><b>子类 + 注解（推荐）</b>：定义 NotFoundException 等子类，贴 @BizErrorCode</li>
 *   <li><b>直接构造（临时用）</b>：new BizException(1001, "xxx")</li>
 * </ol>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 直接构造，code 优先取子类注解，其次 500 */
    public BizException(String detail) {
        super(detail);
        BizErrorCode ann = this.getClass().getAnnotation(BizErrorCode.class);
        this.code = ann != null ? ann.code() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    /** 直接指定 code */
    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
