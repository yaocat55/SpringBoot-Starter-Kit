# Spring Security 开箱即用模板 —— JWT + RBAC 全覆盖

> 每次开新项目都要从零搭建认证授权框架？这个模块提供了一套**生产就绪**的 Spring Security 模板代码，基于 **JWT 无状态认证 + RBAC 权限模型**，覆盖登录注册、Token 刷新、方法级权限控制、统一异常处理等全部场景。拷走即用，按需扩展。

## 目录

- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [文件清单与职责](#文件清单与职责)
- [核心流程](#核心流程)
  - [登录流程](#登录流程)
  - [请求认证流程](#请求认证流程)
  - [Token 刷新流程](#token-刷新流程)
- [使用指南](#使用指南)
  - [获取当前用户](#获取当前用户)
  - [权限控制（四种方式）](#权限控制四种方式)
  - [添加白名单路径](#添加白名单路径)
  - [接入真实数据库](#接入真实数据库)
- [常见场景示例](#常见场景示例)
- [扩展指南](#扩展指南)
- [常见问题](#常见问题)

---

## 架构概览

```
                    ┌──────────────────────────────────────┐
                    │           SecurityFilterChain        │
                    │                                      │
   Request ────────▶│  1. JwtAuthenticationFilter          │
                    │     (从 Header 提取 Token → 校验      │
                    │      → 解析用户信息 → 设置上下文)      │
                    │                                      │
                    │  2. SecurityContextHolder            │
                    │     (存储 Authentication)            │
                    │                                      │
                    │  3. Method Security / AOP            │
                    │     (@PreAuthorize / @RequiresPerm)  │
                    │                                      │
                    │  4. Exception Handling               │
                    │     (401 → EntryPoint / 403 → Denied)│
                    └──────────────────────────────────────┘
```

**技术选型**：

| 组件 | 方案 | 原因 |
|------|------|------|
| 认证方式 | JWT (jjwt 0.12.x) | 无状态、适合分布式、移动端友好 |
| 密码加密 | BCryptPasswordEncoder | Spring Security 默认推荐 |
| 权限模型 | RBAC（角色 + 权限标识） | 最常见的权限模型 |
| 会话管理 | STATELESS | 不依赖服务端 Session |
| 序列化 | Jackson | 与 Spring Boot 默认一致 |

---

## 快速开始

### 1. 依赖

将 `pom.xml` 中的关键依赖拷贝到自己的项目：

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- Validation & AOP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 2. 配置

在 `application.yml` 中配置 JWT 密钥和白名单路径：

```yaml
jwt:
  # 生产环境用环境变量注入，不要硬编码！
  secret: ${JWT_SECRET:your-256-bit-secret-key-here-min-32-chars}
  access-token-expiration: 7200000      # Access Token 2 小时
  refresh-token-expiration: 1209600000  # Refresh Token 14 天

security:
  whitelist:
    - /api/auth/login
    - /api/auth/register
    - /api/auth/refresh
    - /api/public/**
    - /swagger-ui/**
    - /v3/api-docs/**
```

> 生成一个安全的 JWT secret：运行 `SecurityUtil.generateJwtSecret()` 或在线生成 256-bit Base64 字符串。

### 3. 拷贝文件

将以下文件/目录整体拷贝到自己的项目，调整包路径即可：

```
src/main/java/com/quick/springbootsecurity/
├── config/
│   ├── SecurityConfig.java          # ★ Security 核心配置
│   └── CorsConfig.java              # ★ 跨域配置
├── security/
│   ├── JwtTokenProvider.java        # ★ JWT 生成/解析/验证
│   ├── JwtAuthenticationFilter.java # ★ JWT 认证过滤器
│   ├── UserDetailsServiceImpl.java  # ★ 用户加载（需接入数据库）
│   ├── SecurityContextUtil.java     # ★ 获取当前用户
│   └── PermissionService.java       # 权限校验服务
├── annotation/
│   ├── RequiresPermission.java      # 权限注解
│   └── RequiresRole.java            # 角色注解
├── aspect/
│   └── PermissionAspect.java        # 权限 AOP 切面
├── handler/
│   ├── AccessDeniedHandlerImpl.java       # 403 处理器
│   ├── AuthenticationEntryPointImpl.java  # 401 处理器
│   └── LogoutSuccessHandlerImpl.java      # 登出处理器
├── model/
│   ├── LoginUser.java               # 业务用户主体
│   ├── UserDetailsImpl.java         # Security UserDetails 实现
│   ├── LoginRequest.java            # 登录请求 DTO
│   └── RegisterRequest.java         # 注册请求 DTO
├── controller/
│   └── AuthController.java          # ★ 登录/注册/刷新Token
└── util/
    └── SecurityUtil.java            # 加密和随机字符串工具
```

> ★ 号为每次新项目必改的文件，其余大多可直接复用。

### 4. 修改包路径

全局搜索替换 `com.quick.springbootsecurity` 为自己的基础包路径，调整 `SecurityConfig` 中 `@ComponentScan` 扫描范围（如有需要）。

### 5. 启动测试

```bash
mvn spring-boot:run
```

用内置的示例用户测试登录（详见 [接入真实数据库](#接入真实数据库) 前的测试数据）：

```bash
# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 响应：
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userInfo": { "userId": 1, "username": "admin", "roles": ["ROLE_ADMIN"] }
  }
}

# 携带 Token 访问受保护接口
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

---

## 配置说明

### application.yml 完整配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `jwt.secret` | 无（必填） | JWT 签名密钥，至少 256 bit（32 字符）。**生产环境必须通过环境变量注入** |
| `jwt.access-token-expiration` | 7200000 (2h) | Access Token 过期时间（毫秒） |
| `jwt.refresh-token-expiration` | 1209600000 (14d) | Refresh Token 过期时间（毫秒） |
| `jwt.token-prefix` | Bearer | Token 类型前缀 |
| `jwt.header` | Authorization | 携带 Token 的 HTTP Header 名称 |
| `security.whitelist` | 见配置 | 无需认证的路径列表，支持 Ant 风格通配符 |

### SecurityConfig 关键决策说明

```java
// 1. CSRF 关闭 —— API 服务前后端分离，Token 天然防 CSRF
.csrf(AbstractHttpConfigurer::disable)

// 2. 无状态 Session —— JWT 不依赖服务端 Session
.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

// 3. 方法级安全 —— 开启 @PreAuthorize / @Secured / @RolesAllowed
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)

// 4. 禁用默认登录页 —— API 服务不需要
.formLogin(AbstractHttpConfigurer::disable)
.httpBasic(AbstractHttpConfigurer::disable)
```

---

## 文件清单与职责

### config/ —— 配置层

| 文件 | 职责 | 是否需要改 |
|------|------|-----------|
| `SecurityConfig.java` | SecurityFilterChain 配置、PasswordEncoder Bean、AuthenticationManager Bean | 白名单路径、跨域策略 |
| `CorsConfig.java` | CORS 跨域过滤器 | 生产环境改为具体域名 |

### security/ —— 安全核心层

| 文件 | 职责 | 是否需要改 |
|------|------|-----------|
| `JwtTokenProvider.java` | JWT 生成（Access + Refresh）、解析 Claims、验证有效性 | 一般不需要 |
| `JwtAuthenticationFilter.java` | OncePerRequestFilter，从 Header 提取 Token → 校验 → 设置 SecurityContext | **须检查 Claims 解析逻辑** |
| `UserDetailsServiceImpl.java` | 根据用户名加载用户、角色、权限 | **★ 必须改为查数据库** |
| `SecurityContextUtil.java` | 在任意层获取当前登录用户 ID/用户名/角色/权限 | 一般不需要 |
| `PermissionService.java` | 业务层编程式权限校验（`hasPermission` / `hasRole`） | 一般不需要 |

### annotation/ + aspect/ —— 声明式权限

| 文件 | 职责 |
|------|------|
| `RequiresPermission.java` | 权限注解，支持 OR / AND 逻辑 |
| `RequiresRole.java` | 角色注解，支持 OR / AND 逻辑 |
| `PermissionAspect.java` | AOP 切面，拦截注解并调用 PermissionService 校验 |

### handler/ —— 异常处理

| 文件 | 职责 | HTTP 状态码 |
|------|------|------------|
| `AuthenticationEntryPointImpl.java` | 未登录 / Token 失效 | 401 |
| `AccessDeniedHandlerImpl.java` | 已登录但权限不足 | 403 |
| `LogoutSuccessHandlerImpl.java` | 登出成功响应 | 200 |

### model/ —— 数据模型

| 文件 | 职责 |
|------|------|
| `LoginUser.java` | 业务层用户主体，携带 userId / roles / permissions |
| `UserDetailsImpl.java` | Spring Security `UserDetails` 适配器，将 LoginUser 转为框架可用格式 |
| `LoginRequest.java` | 登录请求体（支持用户名+密码 / 短信验证码） |
| `RegisterRequest.java` | 注册请求体（带 Validation 校验） |

### controller/ + util/

| 文件 | 职责 |
|------|------|
| `AuthController.java` | 登录 / 注册 / 刷新 Token / 登出 四个 REST 接口 |
| `SecurityUtil.java` | BCrypt 密码加密 / 随机字符串 / UUID 工具 |

---

## 核心流程

### 登录流程

```
Client                    AuthController              Spring Security           JwtTokenProvider
  │                            │                             │                        │
  │  POST /api/auth/login      │                             │                        │
  │  {username, password}      │                             │                        │
  │ ─────────────────────────▶ │                             │                        │
  │                            │  AuthenticationManager      │                        │
  │                            │  .authenticate()            │                        │
  │                            │ ──────────────────────────▶ │                        │
  │                            │                             │  UserDetailsService     │
  │                            │                             │  .loadUserByUsername()  │
  │                            │                             │ ──────────────────────▶ │
  │                            │                             │ ◀─────────────────────  │
  │                            │                             │  返回 UserDetails       │
  │                            │                             │  校验密码               │
  │                            │ ◀────────────────────────── │                        │
  │                            │  返回 Authentication        │                        │
  │                            │                             │                        │
  │                            │  jwtTokenProvider           │                        │
  │                            │  .generateAccessToken()     │                        │
  │                            │ ──────────────────────────────────────────────────▶ │
  │                            │ ◀──────────────────────────────────────────────────  │
  │                            │  jwtTokenProvider                                   │
  │                            │  .generateRefreshToken()                            │
  │                            │ ──────────────────────────────────────────────────▶ │
  │                            │ ◀──────────────────────────────────────────────────  │
  │                            │                             │                        │
  │ ◀───────────────────────── │                             │                        │
  │  {accessToken,             │                             │                        │
  │   refreshToken, userInfo}  │                             │                        │
```

### 请求认证流程

```
Client                    JwtAuthFilter               SecurityContext           Controller
  │                            │                             │                        │
  │  GET /api/users/me         │                             │                        │
  │  Authorization: Bearer xx  │                             │                        │
  │ ─────────────────────────▶ │                             │                        │
  │                            │  1. extractToken()          │                        │
  │                            │  2. validateToken()         │                        │
  │                            │  3. parseToken() → userId   │                        │
  │                            │     username, roles, perms  │                        │
  │                            │  4. 构建 Authentication     │                        │
  │                            │  5. SecurityContextHolder   │                        │
  │                            │     .setAuthentication()    │                        │
  │                            │ ──────────────────────────▶ │                        │
  │                            │                             │                        │
  │                            │                             │  SecurityContextUtil   │
  │                            │                             │  .getCurrentUserId()   │
  │                            │                             │ ─────────────────────▶ │
  │                            │                             │ ◀────────────────────  │
  │                            │                             │                        │
  │ ◀────────────────────────────────────────────────────────────────────────────── │
  │  200 OK                    │                             │                        │
```

### Token 刷新流程

```
Client                    AuthController              JwtTokenProvider       UserDetailsService
  │                            │                             │                      │
  │  POST /api/auth/refresh    │                             │                      │
  │  {refreshToken}            │                             │                      │
  │ ─────────────────────────▶ │                             │                      │
  │                            │  validateToken()            │                      │
  │                            │ ──────────────────────────▶ │                      │
  │                            │ ◀────────────────────────── │                      │
  │                            │  getUserId() → getUsername()│                      │
  │                            │                             │                      │
  │                            │  loadUserByUsername()       │                      │
  │                            │ ─────────────────────────────────────────────────▶ │
  │                            │ ◀─────────────────────────────────────────────────  │
  │                            │  (重新加载权限，确保最新)     │                      │
  │                            │                             │                      │
  │                            │  generateAccessToken()      │                      │
  │                            │ ──────────────────────────▶ │                      │
  │                            │ ◀────────────────────────── │                      │
  │                            │                             │                      │
  │ ◀───────────────────────── │                             │                      │
  │  {accessToken, expiresIn}  │                             │                      │
```

---

## 使用指南

### 获取当前用户

在任何 Controller / Service 中，通过 `SecurityContextUtil` 获取当前登录用户：

```java
@Autowired
private SecurityContextUtil securityUtil;

public void doSomething() {
    // 获取完整用户信息
    LoginUser user = securityUtil.getCurrentUser();
    Long userId = securityUtil.getCurrentUserId();
    String username = securityUtil.getCurrentUsername();

    // 判断登录状态
    if (securityUtil.isAuthenticated()) { ... }

    // 判断权限
    if (securityUtil.hasPermission("order:delete")) { ... }
    if (securityUtil.hasRole("ADMIN")) { ... }
}
```

### 权限控制（四种方式）

#### 方式一：@PreAuthorize（Spring Security 原生，推荐）

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public Result dashboard() { ... }

    @PreAuthorize("hasAuthority('user:write')")
    @PutMapping("/users/{id}")
    public Result updateUser(@PathVariable Long id) { ... }

    // 多条件
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('order:delete')")
    @DeleteMapping("/orders/{id}")
    public Result deleteOrder(@PathVariable Long id) { ... }
}
```

#### 方式二：@RequiresPermission / @RequiresRole（自定义注解，AOP）

```java
// OR 逻辑：有任一权限即可
@RequiresPermission({"user:read", "user:write"})
@GetMapping("/users")
public Result listUsers() { ... }

// AND 逻辑：必须同时拥有
@RequiresPermission(value = {"order:read", "order:delete"}, requireAll = true)
@DeleteMapping("/orders/{id}")
public Result deleteOrder(@PathVariable Long id) { ... }

// 角色校验
@RequiresRole({"ADMIN", "MANAGER"})
@GetMapping("/settings")
public Result getSettings() { ... }
```

#### 方式三：编程式（Service 层使用）

```java
@Service
public class OrderService {

    @Autowired
    private PermissionService permissionService;

    public void deleteOrder(Long orderId) {
        if (!permissionService.hasPermission("order:delete")) {
            throw new BusinessException("无权限删除订单");
        }
        // 执行删除
    }
}
```

#### 方式四：SecurityFilterChain URL 级别

```java
// 在 SecurityConfig.java 中
.authorizeHttpRequests(auth -> {
    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.DELETE, "/api/orders/**").hasAuthority("order:delete");
    auth.requestMatchers("/api/users/**").hasAnyRole("ADMIN", "MANAGER");
})
```

### 添加白名单路径

在 `application.yml` 或 `SecurityConfig` 中添加无需认证的路径：

```yaml
# application.yml
security:
  whitelist:
    - /api/auth/login
    - /api/auth/register
    - /api/public/**
    - /swagger-ui/**
    - /error          # Spring Boot 默认错误页
```

或在 `SecurityConfig.java` 中直接添加：

```java
auth.requestMatchers("/api/health", "/api/version").permitAll();
```

### 接入真实数据库

`UserDetailsServiceImpl.java` 当前是硬编码的示例数据，接入数据库只需三步：

**步骤 1**：创建用户/角色/权限表（以 RBAC 五张表为例）：

```sql
-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(32),
    status TINYINT DEFAULT 1
);

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) UNIQUE NOT NULL,
    role_name VARCHAR(32) NOT NULL
);

-- 权限表
CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    perm_code VARCHAR(64) UNIQUE NOT NULL,
    perm_name VARCHAR(32) NOT NULL
);

-- 用户-角色关联
CREATE TABLE sys_user_role (
    user_id BIGINT, role_id BIGINT,
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限关联
CREATE TABLE sys_role_permission (
    role_id BIGINT, perm_id BIGINT,
    PRIMARY KEY (role_id, perm_id)
);
```

**步骤 2**：修改 `UserDetailsServiceImpl`：

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private SysUserMapper userMapper;       // MyBatis-Plus / JPA
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private SysPermissionMapper permMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 查用户
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 2. 查角色
        Set<String> roles = roleMapper.selectCodesByUserId(user.getId());

        // 3. 查权限
        Set<String> perms = permMapper.selectCodesByUserId(user.getId());

        // 4. 构建 LoginUser
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roles)
                .permissions(perms)
                .build();

        // 5. 密码传给 Spring Security 做校验
        return new UserDetailsImpl(loginUser, user.getPassword());
    }
}
```

**步骤 3**：修改 `UserDetailsImpl` 支持密码（当前密码返回 null，因为 JWT 模式下密码不存内存）：

```java
// 在 UserDetailsImpl 中添加 password 字段
private final String password;

public UserDetailsImpl(LoginUser loginUser, String password) {
    this.loginUser = loginUser;
    this.password = password;
    // ... authorities 构建
}

@Override
public String getPassword() {
    return password;
}
```

---

## 常见场景示例

### 场景 1：从 Token 中获取用户信息（免查数据库）

JWT 模式下，`JwtAuthenticationFilter` 直接从 Token Claims 中读取角色和权限，不查数据库。适合高性能场景：

```java
// JwtAuthenticationFilter.java 中
Set<String> roles = Optional.ofNullable(
    jwtTokenProvider.parseToken(token).get("roles", Set.class)
).orElse(Collections.emptySet());

Set<String> permissions = Optional.ofNullable(
    jwtTokenProvider.parseToken(token).get("permissions", Set.class)
).orElse(Collections.emptySet());
```

**优点**：不需要每次请求查数据库。
**缺点**：Token 签发后的权限变更不会立即生效（需等待 Token 过期或用户重新登录）。

### 场景 2：每次请求实时查权限（强实时性）

如果需要权限变更即时生效，修改 `JwtAuthenticationFilter` 的 `doFilterInternal`：

```java
// 替换从 Token 读取权限的逻辑，改为查库
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    String username = jwtTokenProvider.getUsername(token);
    // 每次都从数据库加载最新权限
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

**优点**：权限实时生效。
**缺点**：每次请求多一次数据库查询（可用 Redis 缓存优化）。

### 场景 3：短信验证码登录

`LoginRequest` 已预留 `smsCode` 字段：

```java
// AuthController 中添加
@PostMapping("/login/sms")
public ResponseEntity<?> loginBySms(@RequestBody LoginRequest request) {
    // 1. 校验验证码
    String cachedCode = redisUtil.get("sms:" + request.getUsername());
    if (cachedCode == null || !cachedCode.equals(request.getSmsCode())) {
        return ResponseEntity.status(401).body(Map.of("code", 401, "message", "验证码错误"));
    }

    // 2. 查用户（用户不存在则自动注册）
    LoginUser loginUser = userService.getOrCreateByPhone(request.getUsername());

    // 3. 删验证码 + 发 Token
    redisUtil.delete("sms:" + request.getUsername());
    String accessToken = jwtTokenProvider.generateAccessToken(loginUser);
    String refreshToken = jwtTokenProvider.generateRefreshToken(loginUser);
    // ...
}
```

### 场景 4：Token 黑名单（服务端主动踢人）

JWT 无法服务端主动失效，需要配合 Redis 黑名单：

```java
// 登出时将 Token 加入黑名单
redisUtil.set("token:blacklist:" + tokenId, "1", Duration.ofHours(2));

// 在 JwtAuthenticationFilter 中检查
if (redisUtil.hasKey("token:blacklist:" + jwtTokenProvider.parseToken(token).getId())) {
    // Token 已被拉黑，拒绝请求
    filterChain.doFilter(request, response);
    return;
}
```

### 场景 5：多租户数据隔离

```java
@PreAuthorize("hasRole('TENANT_ADMIN') and #tenantId == authentication.principal.loginUser.tenantId")
@GetMapping("/tenant/{tenantId}/data")
public Result getTenantData(@PathVariable Long tenantId) {
    // 只能访问自己租户的数据
}
```

### 场景 6：接口防刷（结合 Redis）

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest req) {
    String clientIp = req.getRemoteAddr();
    String rateKey = "login:rate:" + clientIp;

    // 1 分钟内最多尝试 5 次
    Long attempts = redisUtil.incr(rateKey);
    if (attempts == 1) redisUtil.expire(rateKey, 1, TimeUnit.MINUTES);
    if (attempts > 5) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "操作频繁"));
    }

    // ... 正常登录逻辑
}
```

---

## 扩展指南

### 添加 OAuth2 / OIDC 支持

Spring Security 5/6 原生支持 OAuth2。在 `SecurityConfig` 中添加：

```java
.oauth2Login(oauth2 -> oauth2
    .loginPage("/oauth2/authorization/google")
    .defaultSuccessUrl("/oauth2/success")
)
```

需要的依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

### 集成 Swagger / Knife4j

白名单中添加 Swagger 路径：

```yaml
security:
  whitelist:
    - /swagger-ui/**
    - /swagger-resources/**
    - /v3/api-docs/**
    - /doc.html          # Knife4j
    - /webjars/**
```

Swagger 中配置全局 Token 参数：

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .components(new Components()
            .addSecuritySchemes("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("Bearer"));
}
```

### 增加图形验证码

```java
@GetMapping("/captcha")
public void getCaptcha(HttpServletResponse response) {
    // 生成验证码图片
    String captchaText = generateCaptchaText();
    String captchaKey = SecurityUtil.uuid();
    redisUtil.set("captcha:" + captchaKey, captchaText, Duration.ofMinutes(2));
    // 输出图片 + 返回 captchaKey
}
```

### 增加记住我功能

```java
// application.yml
jwt:
  remember-me-expiration: 2592000000  # 30 天

// AuthController 登录接口
if (request.getRememberMe()) {
    refreshToken = jwtTokenProvider.generateLongRefreshToken(loginUser);
}
```

---

## 常见问题

### Q1：为什么不用 spring-boot-starter-oauth2-resource-server？

`spring-boot-starter-oauth2-resource-server` 适合需要对接独立授权服务器的场景（如 Keycloak / Auth0）。本模板面向的是**自包含认证**的常见业务系统，JWT 签发和校验都在应用内完成，简单直接。如需对接 OAuth2，参考上面的[扩展指南](#添加-oauth2--oidc-支持)。

### Q2：Token 过期了怎么办？

1. 客户端收到 401 → 用 Refresh Token 调 `/api/auth/refresh` → 获取新 Access Token → 重试原请求
2. Refresh Token 也过期了 → 跳转登录页

推荐前端封装 axios 拦截器自动完成静默刷新。

### Q3：JWT 真的安全吗？

JWT 的核心风险是**密钥泄露**和**Token 泄漏**。做好以下措施：

- **密钥**：通过环境变量/K8s Secret/Vault 注入，永远不要提交到代码仓库
- **HTTPS**：生产环境必须全站 HTTPS，防止 Token 被中间人截获
- **过期时间**：Access Token 不要超过 2 小时
- **不在 Token 中存敏感信息**（如密码哈希），Claims 可以 base64 解码查看

### Q4：为什么 CSRF 要关闭？

前后端分离架构中，Token 存储在 Header 而非 Cookie，CSRF 攻击者无法获取 Header 中的 Token，天然免疫 CSRF。关闭 CSRF 是标准做法。

如果你的项目是服务端渲染（Thymeleaf/JSP），需要保留 CSRF 保护。

### Q5：@PreAuthorize 和 @RequiresPermission 有什么不同？

| 维度 | @PreAuthorize | @RequiresPermission |
|------|--------------|-------------------|
| 来源 | Spring Security 内置 | 本模块自定义 |
| 表达式 | SpEL 表达式，功能强大 | 固定逻辑（OR/AND），简单直观 |
| 异常 | 返回 AccessDeniedException | 返回 AccessDeniedException |
| 推荐 | 简单场景首选 | 需要自定义校验逻辑时使用 |

两者可以混用。

### Q6：权限标识的命名规范？

推荐 `资源:操作` 格式：

```
user:read    user:write    user:delete
order:read   order:write   order:delete   order:export
system:config:read   system:config:write
```

### Q7：怎么在 Gateway 中校验 JWT？

如果用了 Spring Cloud Gateway，建议将 JWT 校验上提到网关层，下游服务不再重复校验：

```java
@Bean
public GlobalFilter jwtAuthFilter() {
    return (exchange, chain) -> {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        // 校验 JWT，提取用户信息，写入 Header 传给下游
        exchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", roles)
                        .build())
                .build();
        return chain.filter(exchange);
    };
}
```

### Q8：忘记了 JWT secret 导致所有 Token 失效怎么办？

所有用户的 Token 会立即失效。更新密钥后，已签发的 Token 将无法通过校验。在密钥轮换期间可以配置多密钥验证（jjwt 支持多个 SigningKey）。

---

## 文件清单总览

```
springboot-security/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/quick/springbootsecurity/
    │   ├── SpringbootSecurityApplication.java
    │   ├── config/
    │   │   ├── SecurityConfig.java
    │   │   └── CorsConfig.java
    │   ├── security/
    │   │   ├── JwtTokenProvider.java
    │   │   ├── JwtAuthenticationFilter.java
    │   │   ├── UserDetailsServiceImpl.java
    │   │   ├── SecurityContextUtil.java
    │   │   └── PermissionService.java
    │   ├── annotation/
    │   │   ├── RequiresPermission.java
    │   │   └── RequiresRole.java
    │   ├── aspect/
    │   │   └── PermissionAspect.java
    │   ├── handler/
    │   │   ├── AccessDeniedHandlerImpl.java
    │   │   ├── AuthenticationEntryPointImpl.java
    │   │   └── LogoutSuccessHandlerImpl.java
    │   ├── model/
    │   │   ├── LoginUser.java
    │   │   ├── UserDetailsImpl.java
    │   │   ├── LoginRequest.java
    │   │   └── RegisterRequest.java
    │   ├── controller/
    │   │   └── AuthController.java
    │   └── util/
    │       └── SecurityUtil.java
    └── resources/
        └── application.yml
```

---

## 版本兼容

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Spring Security | 6.5.x（随 Boot 管理） |
| jjwt | 0.12.6 |
| Java | 17+ |
