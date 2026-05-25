package com.quick.springbootexception.common;

import lombok.Getter;

/**
 * 统一返回码 —— 所有接口返回的 code 都从这里取。
 * <p>
 * 规范：code 的前缀代表错误类型，方便排查问题：
 * <ul>
 *   <li>200        — 成功</li>
 *   <li>4xx        — 客户端错误（参数问题、权限问题、资源不存在）</li>
 *   <li>5xx        — 服务端错误（代码 Bug、DB 挂了、第三方调用失败）</li>
 * </ul>
 * <p>
 * 你可以按业务域扩展，比如：1001=用户不存在，2001=订单已关闭。
 */
@Getter
public enum ResultCode {

    // ==================== 成功 ====================
    SUCCESS(200, "操作成功"),

    // ==================== 客户端错误 4xx ====================
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突（如重复插入）"),
    PARAM_VALID_FAILED(422, "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    // ==================== 服务端错误 5xx ====================
    INTERNAL_ERROR(500, "服务器内部错误，请稍后重试"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用（可能正在发布或已熔断）"),

    // ==================== 业务错误（示例，按你项目扩展） ====================
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_NAME_DUPLICATE(1002, "用户名已存在"),
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_STATUS_ERROR(2002, "订单状态不允许此操作"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
