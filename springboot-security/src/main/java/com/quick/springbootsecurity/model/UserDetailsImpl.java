package com.quick.springbootsecurity.model;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security {@link UserDetails} 实现。
 * <p>
 * 包装 {@link LoginUser}，将业务角色/权限转为 Spring Security 的 GrantedAuthority。
 * 角色自动添加 "ROLE_" 前缀，权限保持原样。
 */
public class UserDetailsImpl implements UserDetails {

    @Getter
    private final LoginUser loginUser;
    private final Set<GrantedAuthority> authorities;

    public UserDetailsImpl(LoginUser loginUser) {
        this.loginUser = loginUser;

        // 角色 + 权限统一转为 GrantedAuthority
        Set<GrantedAuthority> authSet = loginUser.getRoles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        loginUser.getPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authSet::add);

        this.authorities = Set.copyOf(authSet);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;  // JWT 模式下密码无需保存在内存中
    }

    @Override
    public String getUsername() {
        return loginUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
