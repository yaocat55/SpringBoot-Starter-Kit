package com.quick.springbootapiresult.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一 API 返回体 —— 所有 Controller 的返回值都用这个包装，或通过 ResponseAdvice 自动包装。
 *
 * <h3>使用方式一：静态方法（推荐，简洁）</h3>
 * <pre>{@code
 * return ApiResult.success(user);                          // 200, 带数据
 * return ApiResult.success("操作成功", user);               // 200, 自定义消息
 * return ApiResult.success();                              // 200, 无数据
 * return ApiResult.error("用户不存在");                     // 500, 自定义消息
 * return ApiResult.error(404, "用户不存在");                // 自定义 code + 消息
 * }</pre>
 *
 * <h3>使用方式二：链式调用（灵活）</h3>
 * <pre>{@code
 * return new ApiResult<User>()
 *     .code(200)
 *     .msg("ok")
 *     .data(user);
 * }</pre>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    private int code;
    private String msg;
    private T data;
    private long timestamp;

    public ApiResult() {
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 链式调用 ====================

    public ApiResult<T> code(int code) {
        this.code = code;
        return this;
    }

    public ApiResult<T> msg(String msg) {
        this.msg = msg;
        return this;
    }

    public ApiResult<T> data(T data) {
        this.data = data;
        return this;
    }

    // ==================== 静态工厂 ====================

    /** 成功，无数据 */
    public static <T> ApiResult<T> success() {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.msg = "success";
        return r;
    }

    /** 成功，带数据 */
    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    /** 成功，自定义消息 + 数据 */
    public static <T> ApiResult<T> success(String msg, T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.msg = msg;
        r.data = data;
        return r;
    }

    /** 失败，默认 500 */
    public static <T> ApiResult<T> error(String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 500;
        r.msg = msg;
        return r;
    }

    /** 失败，自定义 code + 消息 */
    public static <T> ApiResult<T> error(int code, String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.msg = msg;
        return r;
    }


}
