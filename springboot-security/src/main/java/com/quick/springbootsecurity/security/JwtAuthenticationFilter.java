package com.quick.springbootsecurity.security;

import com.quick.springbootsecurity.model.LoginUser;
import com.quick.springbootsecurity.model.UserDetailsImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * JWT 认证过滤器 —— 每次请求时从 Header 提取 Token 并设置认证上下文。
 * <p>
 * 继承 {@link OncePerRequestFilter} 保证单个请求只执行一次。
 * 执行流程：
 * <ol>
 *   <li>从 Authorization Header 中提取 Bearer Token</li>
 *   <li>校验 Token 有效性</li>
 *   <li>解析出用户信息，构造 Authentication 存入 SecurityContext</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    @Value("${jwt.header:Authorization}")
    private String header;
    @Value("${jwt.token-prefix:Bearer}")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 提取 Token
        String token = extractToken(request);

        // 2. 校验 Token
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // 3. 解析用户信息
            Long userId = jwtTokenProvider.getUserId(token);
            String username = jwtTokenProvider.getUsername(token);

            // 4. 从 Token Claims 恢复角色和权限（免查数据库，适合高性能场景）
            Set<String> roles = Optional.ofNullable(
                    jwtTokenProvider.parseToken(token).get("roles", Set.class)
            ).orElse(Collections.emptySet());

            Set<String> permissions = Optional.ofNullable(
                    jwtTokenProvider.parseToken(token).get("permissions", Set.class)
            ).orElse(Collections.emptySet());

            // 5. 构建 LoginUser → UserDetails → Authentication
            LoginUser loginUser = LoginUser.builder()
                    .userId(userId)
                    .username(username)
                    .roles(roles)
                    .permissions(permissions)
                    .build();

            UserDetailsImpl userDetails = new UserDetailsImpl(loginUser);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 6. 设置到 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /** 从请求头提取 Bearer Token */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(header);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenPrefix)) {
            return bearerToken.substring(tokenPrefix.length()).trim();
        }
        return null;
    }
}
