package com.quick.springbootsecurity.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解 —— 标注在 Controller 方法上，检查当前用户是否拥有指定权限。
 * <p>
 * 逻辑关系：
 * <ul>
 *   <li>单个值时，用户拥有任一权限即通过（OR 逻辑）</li>
 *   <li>{@link #requireAll()} = true 时，用户必须拥有全部权限（AND 逻辑）</li>
 * </ul>
 *
 * <pre>{@code
 * // 拥有 user:read 或 user:write 任一即可
 * @RequiresPermission({"user:read", "user:write"})
 *
 * // 必须同时拥有 user:read 和 user:write
 * @RequiresPermission(value = {"user:read", "user:write"}, requireAll = true)
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 权限标识数组 */
    String[] value();

    /** 是否需要同时拥有全部权限（默认 false，即 OR 逻辑） */
    boolean requireAll() default false;

    /** 未授权时的提示消息 */
    String message() default "权限不足";
}
