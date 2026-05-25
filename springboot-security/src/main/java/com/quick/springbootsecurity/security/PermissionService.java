package com.quick.springbootsecurity.security;

import com.quick.springbootsecurity.model.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 权限校验服务 —— 业务层细粒度权限判断。
 * <p>
 * 可在 Service 层直接注入使用，避免在业务代码中硬编码权限字符串：
 * <pre>{@code
 * if (permissionService.hasPermission("order:delete")) {
 *     orderService.delete(orderId);
 * }
 * }</pre>
 */
@Service
public class PermissionService {

    private final SecurityContextUtil securityUtil;

    public PermissionService(SecurityContextUtil securityUtil) {
        this.securityUtil = securityUtil;
    }

    /** 当前用户是否拥有指定权限 */
    public boolean hasPermission(String permission) {
        LoginUser user = securityUtil.getCurrentUser();
        return user != null && user.hasPermission(permission);
    }

    /** 当前用户是否拥有任一权限（OR） */
    public boolean hasAnyPermission(String... permissions) {
        LoginUser user = securityUtil.getCurrentUser();
        if (user == null) return false;
        for (String perm : permissions) {
            if (user.hasPermission(perm)) return true;
        }
        return false;
    }

    /** 当前用户是否拥有全部权限（AND） */
    public boolean hasAllPermissions(String... permissions) {
        LoginUser user = securityUtil.getCurrentUser();
        if (user == null) return false;
        for (String perm : permissions) {
            if (!user.hasPermission(perm)) return false;
        }
        return true;
    }

    /** 当前用户是否拥有指定角色 */
    public boolean hasRole(String role) {
        LoginUser user = securityUtil.getCurrentUser();
        return user != null && user.hasRole(role);
    }

    /** 当前用户是否拥有任一角色（OR） */
    public boolean hasAnyRole(String... roles) {
        LoginUser user = securityUtil.getCurrentUser();
        if (user == null) return false;
        for (String role : roles) {
            if (user.hasRole(role)) return true;
        }
        return false;
    }

    /** 当前用户是否拥有全部角色（AND） */
    public boolean hasAllRoles(String... roles) {
        LoginUser user = securityUtil.getCurrentUser();
        if (user == null) return false;
        for (String role : roles) {
            if (!user.hasRole(role)) return false;
        }
        return true;
    }
}
