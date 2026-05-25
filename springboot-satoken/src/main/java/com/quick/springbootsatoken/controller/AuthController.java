package com.quick.springbootsatoken.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.quick.springbootsatoken.model.LoginRequest;
import com.quick.springbootsatoken.model.LoginUser;
import com.quick.springbootsatoken.model.RegisterRequest;
import com.quick.springbootsatoken.service.UserService;
import com.quick.springbootsatoken.util.SaTokenUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口 —— 登录、注册、登出、用户信息。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ==================== 登录 ====================

    /**
     * 用户名 + 密码登录。
     * <p>
     * 返回 satoken 给前端，前端后续请求在 Header 中携带：satoken: <tokenValue>
     */
    @PostMapping("/login")
    public SaResult login(@Valid @RequestBody LoginRequest request) {
        LoginUser user = userService.login(request.getUsername(), request.getPassword());
        return SaResult.ok("登录成功")
                .set("token", StpUtil.getTokenValue())
                .set("user", user);
    }

    /**
     * 查询当前 Token 是否有效（根据 Token 自动登录场景）。
     */
    @GetMapping("/check")
    public SaResult check() {
        if (StpUtil.isLogin()) {
            return SaResult.ok("已登录")
                    .set("user", SaTokenUtil.getLoginUser());
        }
        return SaResult.error("未登录");
    }

    // ==================== 注册 ====================

    /**
     * 注册新用户（演示接口，生产环境需加密密码）。
     */
    @PostMapping("/register")
    public SaResult register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return SaResult.ok("注册成功");
    }

    // ==================== 登出 ====================

    /**
     * 退出登录，使当前 Token 失效。
     */
    @PostMapping("/logout")
    public SaResult logout() {
        StpUtil.logout();
        return SaResult.ok("已登出");
    }

    // ==================== 用户信息 ====================

    /**
     * 获取当前登录用户信息（需登录）。
     */
    @SaCheckLogin
    @GetMapping("/userinfo")
    public SaResult userinfo() {
        LoginUser user = SaTokenUtil.getLoginUser();
        return SaResult.ok("获取成功").set("user", user);
    }

    // ==================== 权限测试接口 ====================

    /**
     * 仅 admin 角色可访问。
     */
    @SaCheckRole("admin")
    @GetMapping("/admin/test")
    public SaResult adminOnly() {
        return SaResult.ok("你是管理员，有权访问此接口");
    }

    /**
     * 需要 user:write 权限。
     */
    @SaCheckPermission("user:write")
    @GetMapping("/user/write")
    public SaResult writeOnly() {
        return SaResult.ok("你有写权限");
    }

    /**
     * 仅登录即可访问。
     */
    @SaCheckLogin
    @GetMapping("/dashboard")
    public SaResult dashboard() {
        return SaResult.ok("欢迎回来，" + SaTokenUtil.getNickname());
    }
}
