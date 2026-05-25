package com.quick.springbootsecurity.controller;

import com.quick.springbootsecurity.model.LoginRequest;
import com.quick.springbootsecurity.model.RegisterRequest;
import com.quick.springbootsecurity.model.UserDetailsImpl;
import com.quick.springbootsecurity.security.JwtTokenProvider;
import com.quick.springbootsecurity.security.UserDetailsServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 —— 登录、注册、刷新 Token、登出。
 * <p>
 * 这是整个认证流程的入口。所有接口路径在 {@code /api/auth/**} 下，
 * 其中 login / register / refresh 在白名单中（无需 Token 即可访问）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * 登录 —— 返回 Access Token + Refresh Token。
     * <pre>
     * POST /api/auth/login
     * Body: { "username": "admin", "password": "123456" }
     * </pre>
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 1. Spring Security 认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // 2. 获取用户信息
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            // 3. 生成 Token
            String accessToken = jwtTokenProvider.generateAccessToken(userDetails.getLoginUser());
            String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getLoginUser());

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "登录成功",
                    "data", Map.of(
                            "accessToken", accessToken,
                            "refreshToken", refreshToken,
                            "expiresIn", jwtTokenProvider.getRemainingTime(accessToken),
                            "userInfo", Map.of(
                                    "userId", userDetails.getLoginUser().getUserId(),
                                    "username", userDetails.getLoginUser().getUsername(),
                                    "roles", userDetails.getLoginUser().getRoles(),
                                    "permissions", userDetails.getLoginUser().getPermissions()
                            )
                    )
            ));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "用户名或密码错误"
            ));
        }
    }

    /**
     * 注册 —— 仅做示例，实际项目需完善（验证码、邮箱校验等）。
     * <pre>
     * POST /api/auth/register
     * Body: { "username": "zhangsan", "password": "123456", "nickname": "张三" }
     * </pre>
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // 实际项目：检查用户名/邮箱是否已存在 → 加密密码 → 插入数据库
        // 此处返回示例响应
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "注册成功（示例），请接驳数据库后完善"
        ));
    }

    /**
     * 刷新 Access Token —— 用 Refresh Token 换取新的 Access Token。
     * <pre>
     * POST /api/auth/refresh
     * Body: { "refreshToken": "xxx" }
     * </pre>
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "refreshToken 不能为空"
            ));
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "Refresh Token 无效或已过期，请重新登录"
            ));
        }

        try {
            Long userId = jwtTokenProvider.getUserId(refreshToken);
            String username = jwtTokenProvider.getUsername(refreshToken);

            // 重新加载用户（确保权限是最新的）
            UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(username);

            String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails.getLoginUser());

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "Token 刷新成功",
                    "data", Map.of(
                            "accessToken", newAccessToken,
                            "expiresIn", jwtTokenProvider.getRemainingTime(newAccessToken)
                    )
            ));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "用户不存在"
            ));
        }
    }
}
