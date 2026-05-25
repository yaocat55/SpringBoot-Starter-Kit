package com.quick.springbootapiresult.common;

import lombok.Getter;

/**
 * 统一错误码 —— 所有返回码都从这里取，禁止在代码里硬编码数字。
 *
 * <h3>规范</h3>
 * <ul>
 *   <li>0 / 200        — 成功</li>
 *   <li>400-499        — 客户端错误（参数、权限、资源不存在）</li>
 *   <li>500-599        — 服务端错误（代码 Bug、第三方挂了）</li>
 *   <li>1000-1999      — 用户模块业务错误</li>
 *   <li>2000-2999      — 订单模块业务错误</li>
 *   <li>... 以此类推，按模块划分</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙"),

    /** 业务异常起始码 —— 你项目的业务错误从 1001 开始定义 */
    BIZ_ERROR_START(1001, "业务异常起始码"),
    ;

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
