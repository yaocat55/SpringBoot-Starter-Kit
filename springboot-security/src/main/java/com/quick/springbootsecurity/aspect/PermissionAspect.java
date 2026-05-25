package com.quick.springbootsecurity.aspect;

import com.quick.springbootsecurity.annotation.RequiresPermission;
import com.quick.springbootsecurity.annotation.RequiresRole;
import com.quick.springbootsecurity.security.PermissionService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 权限注解 AOP 切面 —— 拦截 {@link RequiresPermission} 和 {@link RequiresRole} 注解，
 * 在方法执行前校验权限。
 * <p>
 * 注意：Spring Security 的 {@code @PreAuthorize} 已提供类似能力，
 * 此切面适合需要自定义校验逻辑或统一异常格式的场景。
 */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 拦截 @RequiresPermission 注解 */
    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        String[] permissions = requiresPermission.value();
        String message = requiresPermission.message();
        boolean passed;

        if (requiresPermission.requireAll()) {
            passed = permissionService.hasAllPermissions(permissions);
        } else {
            passed = permissionService.hasAnyPermission(permissions);
        }

        if (!passed) {
            throw new AccessDeniedException(message);
        }
    }

    /** 拦截 @RequiresRole 注解 */
    @Before("@annotation(requiresRole)")
    public void checkRole(JoinPoint joinPoint, RequiresRole requiresRole) {
        String[] roles = requiresRole.value();
        String message = requiresRole.message();
        boolean passed;

        if (requiresRole.requireAll()) {
            passed = permissionService.hasAllRoles(roles);
        } else {
            passed = permissionService.hasAnyRole(roles);
        }

        if (!passed) {
            throw new AccessDeniedException(message);
        }
    }
}
