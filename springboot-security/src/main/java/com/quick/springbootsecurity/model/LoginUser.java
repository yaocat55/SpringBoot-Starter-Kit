package com.quick.springbootsecurity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 登录成功后缓存的用户主体信息（存入 SecurityContext）。
 * <p>
 * 区别于 {@link UserDetailsImpl}（Spring Security 框架内部使用），
 * 本类承载业务层面的用户信息，便于在 Controller/Service 层获取当前用户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 昵称 */
    private String nickname;
    /** 角色集合（如 ["ROLE_ADMIN", "ROLE_USER"]） */
    private Set<String> roles;
    /** 权限集合（如 ["user:read", "user:write", "order:delete"]） */
    private Set<String> permissions;
    /** 登录 IP */
    private String loginIp;
    /** 登录时间 */
    private Long loginTime;

    /** 判断是否拥有某个角色 */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /** 判断是否拥有某个权限 */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /** 获取角色列表（只读） */
    public Set<String> getRoles() {
        return roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
    }

    /** 获取权限列表（只读） */
    public Set<String> getPermissions() {
        return permissions != null ? Collections.unmodifiableSet(permissions) : Collections.emptySet();
    }
}
