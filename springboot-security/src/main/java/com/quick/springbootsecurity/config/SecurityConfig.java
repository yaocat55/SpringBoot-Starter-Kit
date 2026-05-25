package com.quick.springbootsecurity.config;

import com.quick.springbootsecurity.handler.AccessDeniedHandlerImpl;
import com.quick.springbootsecurity.handler.AuthenticationEntryPointImpl;
import com.quick.springbootsecurity.handler.LogoutSuccessHandlerImpl;
import com.quick.springbootsecurity.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Spring Security 核心配置。
 * <p>
 * 安全策略：
 * <ul>
 *   <li>基于 JWT 的无状态认证（SessionCreationPolicy.STATELESS）</li>
 *   <li>白名单路径跳过认证，其余全部需要认证</li>
 *   <li>通过 {@link EnableMethodSecurity} 开启方法级权限控制</li>
 *   <li>CSRF 关闭（API 服务通常关闭，前后端分离场景靠 Token 防 CSRF）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)  // 开启 @PreAuthorize / @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;
    private final LogoutSuccessHandlerImpl logoutSuccessHandler;

    @Value("${security.whitelist}")
    private List<String> whitelist = List.of();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF —— API 服务关闭（前端 Token 天然防 CSRF）
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 无状态 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 请求授权
                .authorizeHttpRequests(auth -> {
                    // 白名单 —— 无需认证
                    auth.requestMatchers(whitelist.toArray(new String[0])).permitAll();
                    // OPTIONS 预检请求放行
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // POST /api/auth/register 放行
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll();
                    // 其余全部需要认证
                    auth.anyRequest().authenticated();
                })

                // 4. 异常处理
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401
                        .accessDeniedHandler(accessDeniedHandler)             // 403
                )

                // 5. 登出
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                )

                // 6. JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 7. 禁用默认登录页 / 表单登录 / HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /** BCrypt 密码编码器 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** AuthenticationManager —— 手动注入，供 AuthController 使用 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
