package com.quick.springbootsecurity.security;

import com.quick.springbootsecurity.model.LoginUser;
import com.quick.springbootsecurity.model.UserDetailsImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 安全上下文工具类 —— 在 Controller / Service 层快捷获取当前登录用户信息。
 * <p>
 * 使用方式：
 * <pre>{@code
 * @Autowired
 * private SecurityContextUtil securityUtil;
 *
 * public void doSomething() {
 *     Long userId = securityUtil.getCurrentUserId();
 *     String username = securityUtil.getCurrentUsername();
 *     LoginUser user = securityUtil.getCurrentUser();
 * }
 * }</pre>
 */
@Component
public class SecurityContextUtil {

    /** 获取当前登录用户完整信息 */
    public LoginUser getCurrentUser() {
        return Optional.ofNullable(getAuthentication())
                .map(auth -> (UserDetailsImpl) auth.getPrincipal())
                .map(UserDetailsImpl::getLoginUser)
                .orElse(null);
    }

    /** 获取当前用户 ID */
    public Long getCurrentUserId() {
        return Optional.ofNullable(getCurrentUser())
                .map(LoginUser::getUserId)
                .orElse(null);
    }

    /** 获取当前用户名 */
    public String getCurrentUsername() {
        return Optional.ofNullable(getCurrentUser())
                .map(LoginUser::getUsername)
                .orElse(null);
    }

    /** 判断当前用户是否已登录 */
    public boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** 判断当前用户是否拥有指定角色 */
    public boolean hasRole(String role) {
        return Optional.ofNullable(getCurrentUser())
                .map(u -> u.hasRole(role))
                .orElse(false);
    }

    /** 判断当前用户是否拥有指定权限 */
    public boolean hasPermission(String permission) {
        return Optional.ofNullable(getCurrentUser())
                .map(u -> u.hasPermission(permission))
                .orElse(false);
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
