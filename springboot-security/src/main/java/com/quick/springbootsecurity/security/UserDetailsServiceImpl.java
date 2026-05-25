package com.quick.springbootsecurity.security;

import com.quick.springbootsecurity.model.LoginUser;
import com.quick.springbootsecurity.model.UserDetailsImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Spring Security {@link UserDetailsService} 实现 —— 根据用户名加载用户信息。
 * <p>
 * 当前为示例实现（硬编码演示用户）。
 * 集成到真实项目时需要替换为动态数据源（数据库 / 远程服务 / LDAP）。
 * <p>
 * 替换方式：注入 UserMapper / UserRepository，将 hardcoded 逻辑替换为数据库查询。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ========== 示例数据（实际项目替换为数据库查询） ==========
        LoginUser loginUser;
        if ("admin".equals(username)) {
            loginUser = LoginUser.builder()
                    .userId(1L)
                    .username("admin")
                    .nickname("管理员")
                    .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
                    .permissions(Set.of("user:read", "user:write", "order:read", "order:write", "order:delete"))
                    .build();
        } else if ("user".equals(username)) {
            loginUser = LoginUser.builder()
                    .userId(2L)
                    .username("user")
                    .nickname("普通用户")
                    .roles(Set.of("ROLE_USER"))
                    .permissions(Set.of("user:read", "order:read"))
                    .build();
        } else {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        return new UserDetailsImpl(loginUser);
    }
}
