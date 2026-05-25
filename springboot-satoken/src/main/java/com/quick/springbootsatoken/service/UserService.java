package com.quick.springbootsatoken.service;

import cn.dev33.satoken.stp.StpUtil;
import com.quick.springbootsatoken.model.LoginUser;
import com.quick.springbootsatoken.model.RegisterRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务 —— 演示用内存数据，生产环境替换为数据库查询。
 * <p>
 * Sa-Token 的登录流程：
 * <pre>
 * 1. 前端提交 username + password
 * 2. 后端验证用户名密码（本类负责）
 * 3. 验证通过 → 调用 {@code StpUtil.login(userId)} 生成 Token
 * 4. 将 Token 返回给前端
 * 5. 前端后续请求在 Header 中携带 Token
 * </pre>
 * <p>
 * 生产环境替换步骤：
 * <ol>
 * <li>接入真实数据库（MySQL / PostgreSQL）</li>
 * <li>密码使用 BCrypt 加密存储</li>
 * <li>用户/角色/权限改为数据库查询</li>
 * </ol>
 */
@Slf4j
@Service
public class UserService {

    private final Map<String, DemoUser> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1000);

    /**
     * 演示用户数据。
     */
    @PostConstruct
    public void init() {
        // 管理员：拥有全部权限
        DemoUser admin = new DemoUser();
        admin.setUserId(idGenerator.incrementAndGet());
        admin.setUsername("admin");
        admin.setPassword("admin123");
        admin.setNickname("系统管理员");
        admin.setRoles(Set.of("admin", "user"));
        admin.setPermissions(Set.of("user:read", "user:write", "user:delete",
                "order:read", "order:write", "order:delete",
                "system:config", "system:monitor"));
        users.put("admin", admin);

        // 普通用户：只有读权限
        DemoUser user = new DemoUser();
        user.setUserId(idGenerator.incrementAndGet());
        user.setUsername("user");
        user.setPassword("user123");
        user.setNickname("普通用户");
        user.setRoles(Set.of("user"));
        user.setPermissions(Set.of("user:read", "order:read"));
        users.put("user", user);

        // 只读用户：仅读
        DemoUser viewer = new DemoUser();
        viewer.setUserId(idGenerator.incrementAndGet());
        viewer.setUsername("viewer");
        viewer.setPassword("viewer123");
        viewer.setNickname("只读用户");
        viewer.setRoles(Set.of("viewer"));
        viewer.setPermissions(Set.of("user:read"));
        users.put("viewer", viewer);

        log.info("演示用户初始化完成: 共 {} 个用户", users.size());
    }

    // ==================== 登录验证 ====================

    /**
     * 验证用户名密码，验证通过后调用 {@code StpUtil.login()} 生成 Token。
     *
     * @return 登录成功的用户信息
     * @throws RuntimeException 用户名或密码错误
     */
    public LoginUser login(String username, String password) {
        DemoUser user = users.get(username);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("用户名或密码错误");
        }

        // Sa-Token 核心：登录，传入 userId
        StpUtil.login(user.getUserId());

        // 将用户信息存入 Token Session（登录后可随时取出）
        LoginUser loginUser = toLoginUser(user);
        StpUtil.getSession().set("loginUser", loginUser);

        log.info("用户登录成功: username={}, userId={}, roles={}",
                username, user.getUserId(), user.getRoles());

        return loginUser;
    }

    // ==================== 用户查询 ====================

    /**
     * 根据 userId 获取用户信息。
     */
    public LoginUser getByUserId(Long userId) {
        return users.values().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .map(this::toLoginUser)
                .orElse(null);
    }

    /**
     * 根据 username 获取用户信息。
     */
    public LoginUser getByUsername(String username) {
        DemoUser user = users.get(username);
        return user != null ? toLoginUser(user) : null;
    }

    // ==================== 注册 ====================

    /**
     * 注册新用户（演示实现，生产环境需 BCrypt 加密 + 数据库存储）。
     *
     * @throws RuntimeException 用户名已存在
     */
    public void register(RegisterRequest request) {
        if (users.containsKey(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        DemoUser demoUser = new DemoUser();
        demoUser.setUserId(idGenerator.incrementAndGet());
        demoUser.setUsername(request.getUsername());
        demoUser.setPassword(request.getPassword());
        demoUser.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        demoUser.setRoles(Set.of("user"));
        demoUser.setPermissions(Set.of("user:read"));
        users.put(request.getUsername(), demoUser);
        log.info("用户注册成功: username={}, userId={}", request.getUsername(), demoUser.getUserId());
    }

    // ==================== 辅助方法 ====================

    private LoginUser toLoginUser(DemoUser user) {
        return LoginUser.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .build();
    }

    // ==================== 内部类 ====================

    @Data
    static class DemoUser {
        private Long userId;
        private String username;
        private String password;
        private String nickname;
        private Set<String> roles;
        private Set<String> permissions;
    }
}
