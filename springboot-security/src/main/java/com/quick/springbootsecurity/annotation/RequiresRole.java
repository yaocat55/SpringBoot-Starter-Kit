package com.quick.springbootsecurity.annotation;

import java.lang.annotation.*;

/**
 * 角色校验注解 —— 标注在 Controller 方法上，检查当前用户是否拥有指定角色。
 * <p>
 * 角色名会自动添加 "ROLE_" 前缀（若未手动添加）。
 *
 * <pre>{@code
 * // 仅 ADMIN 或 MANAGER 角色可访问
 * @RequiresRole({"ADMIN", "MANAGER"})
 *
 * // 必须同时是 ADMIN 和 VIP
 * @RequiresRole(value = {"ADMIN", "VIP"}, requireAll = true)
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /** 角色名称数组 */
    String[] value();

    /** 是否需要同时拥有全部角色（默认 false，即 OR 逻辑） */
    boolean requireAll() default false;

    /** 未授权时的提示消息 */
    String message() default "角色不满足要求";
}
