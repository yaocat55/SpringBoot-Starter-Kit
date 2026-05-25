# Spring Boot Sa-Token 权限认证开箱即用模板

基于 [Sa-Token](https://sa-token.cc/) 1.42.0，提供「登录鉴权 / 角色权限 / Token 会话 / 注解拦截」完整代码示例，**复制即用**。

## 目录

- [1. 快速开始（接入 6 步）](#1-快速开始接入-6-步)
- [2. 核心概念](#2-核心概念)
- [3. 接口清单](#3-接口清单)
- [4. 配置参考](#4-配置参考)
- [5. 代码结构](#5-代码结构)
- [6. 常见场景](#6-常见场景)
- [7. 生产环境替换指南](#7-生产环境替换指南)
- [8. 常见问题 FAQ](#8-常见问题-faq)
- [9. 文件树](#9-文件树)

---

## 1. 快速开始（接入 6 步）

### 步骤 1：启动应用

```bash
cd springboot-satoken
mvn spring-boot:run
```

应用启动在 **8081** 端口。无需 Redis（已配置关闭 Redis 自动装配，Token 存内存）。

### 步骤 2：登录获取 Token

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "b3f8a7c2-1d4e-4f6a-9c3b-7e8f2a1d5c6b",
    "user": {
      "userId": 1001,
      "username": "admin",
      "nickname": "系统管理员",
      "roles": ["admin", "user"],
      "permissions": ["user:read", "user:write", "user:delete", ...]
    }
  }
}
```

### 步骤 3：携带 Token 访问接口

将上一步返回的 `token` 值放入请求 Header：

```bash
TOKEN="b3f8a7c2-1d4e-4f6a-9c3b-7e8f2a1d5c6b"

# 访问需要登录的接口
curl http://localhost:8081/api/auth/userinfo \
  -H "satoken: $TOKEN"
```

### 步骤 4：测试权限控制

内置 3 个演示用户：

| 用户名 | 密码 | 角色 | 权限 | 说明 |
|--------|------|------|------|------|
| admin | admin123 | admin, user | user:*, order:*, system:* | 管理员，全部权限 |
| user | user123 | user | user:read, order:read | 普通用户，只读 |
| viewer | viewer123 | viewer | user:read | 只读用户 |

```bash
# admin 能访问需要 "admin" 角色的接口
curl http://localhost:8081/api/auth/admin/test -H "satoken: $ADMIN_TOKEN"
# → {"code":200, "msg":"你是管理员，有权访问此接口"}

# 普通 user 访问会被拒绝（403）
curl http://localhost:8081/api/auth/admin/test -H "satoken: $USER_TOKEN"
# → {"code":403, "msg":"权限不足，需要角色: admin"}
```

### 步骤 5：注册新用户

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456","nickname":"张三"}'
```

### 步骤 6：退出登录

```bash
curl -X POST http://localhost:8081/api/auth/logout -H "satoken: $TOKEN"
# → Token 即刻失效
```

---

## 2. 核心概念

### 2.1 Sa-Token 是什么

Sa-Token 是一个**轻量级 Java 权限认证框架**，解决登录认证、权限授权、Token 管理、Session 会话等一系列权限相关问题。

与 Spring Security 相比：Sa-Token 更轻量（核心 jar 不到 200KB），API 更简单（一行代码完成登录/鉴权），中文文档齐全。

### 2.2 登录流程（Token 机制）

```
┌──────────┐   ①POST /login    ┌──────────┐   ②验证密码    ┌──────────┐
│  前端    │ ─────────────────→ │ Controller │ ────────────→ │ UserService │
│          │                    │          │               │          │
│          │   ③返回 Token      │          │  ④StpUtil.login(userId)
│          │ ←───────────────── │          │ ←──────────── │          │
└──────────┘                    └──────────┘               └──────────┘
     │                                                         │
     │  ⑤后续请求 Header: satoken=xxx                          │
     │────────────────────────────────────────────────────→   │
     │                              ┌──────────┐               │
     │  ⑥注解/代码鉴权             │ Sa-Token  │ ← ⑦查 Token  │
     │  @SaCheckPermission("x")    │  框架     │   找 userId  │
     │  StpUtil.checkRole("y")     └──────────┘               │
```

核心步骤：
1. 前端提交 username + password
2. 后端 `UserService.login()` 验证用户名密码
3. 调用 `StpUtil.login(userId)` → Sa-Token 生成一个 Token（如 UUID）
4. Token 返回给前端，前端保存（localStorage / cookie）
5. 前端后续每次请求在 Header 里携带 `satoken: <token值>`
6. Sa-Token 自动从 Header 里读取 Token，解析出 userId
7. 进入 Controller 前，注解/代码鉴权生效

### 2.3 注解鉴权 vs 代码鉴权

Sa-Token 提供了两种鉴权方式，可以混用：

**注解鉴权**（推荐，声明式、无侵入）：

```java
@SaCheckLogin                     // 必须登录
@SaCheckRole("admin")             // 必须有 admin 角色
@SaCheckPermission("user:write")  // 必须有 user:write 权限
@SaCheckRoleOr("admin", "super")  // 满足任一角色
@SaCheckPermissionOr("x", "y")    // 满足任一权限
```

**代码鉴权**（灵活，适合复杂逻辑）：

```java
StpUtil.isLogin();                     // 是否登录
StpUtil.checkLogin();                  // 未登录则抛异常
StpUtil.hasRole("admin");              // 是否有角色
StpUtil.checkPermission("user:read");  // 检查权限
```

### 2.4 权限码 vs 角色码

这俩是 Sa-Token 鉴权的两个维度，职责不同：

| 维度 | 是什么 | 例子 | 判读 |
|------|--------|------|------|
| **角色** | 用户的身份标签 | admin、user、viewer | 你是"谁"→ @SaCheckRole |
| **权限** | 能做的具体操作 | user:read、order:delete | 你能做什么 → @SaCheckPermission |

**最佳实践**：接口鉴权优先用权限码。角色码用于粗粒度控制（如管理后台入口），权限码用于细粒度控制（如具体按钮/接口）。

```java
// ✅ 好：用权限码控制具体操作
@SaCheckPermission("order:delete")
public void deleteOrder() { ... }

// ⚠️ 可用但太粗糙：角色跟权限绑定太死，以后要改成"普通用户也能删"就得改代码
@SaCheckRole("admin")
public void deleteOrder() { ... }
```

### 2.5 StpInterface —— 权限数据的来源

Sa-Token 不会自动知道用户有哪些权限和角色。你需要在 `StpInterface` 实现类里告诉它：

```java
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 根据 loginId（即 userId）查询用户的权限列表
        // 可以从数据库查，也可以从缓存查
        return userService.getPermissions(loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userService.getRoles(loginId);
    }
}
```

**每次鉴权时 Sa-Token 都会回调这两个方法**（有缓存策略可配置，见官方文档）。

本项目的实现在 `config/StpInterfaceImpl.java`，它从 `UserService` 的演示内存数据里查询。

### 2.6 Token Session —— 挂载用户信息

`StpUtil.login(userId)` 之后，可以用 Token Session 存储跟这次登录相关的数据：

```java
StpUtil.login(userId);

// 存用户信息到当前 Token 专属的 Session
StpUtil.getSession().set("loginUser", loginUser);

// 后续任意地方取出
LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
```

Token Session 的生命周期跟 Token 一致：登出即销毁。**适合存用户昵称、头像、当前租户等登录态信息**。

与 `HttpSession` 的区别：Token Session 不依赖 Cookie，前后端分离时也能用。

### 2.7 Token 有效期与续期

```yaml
sa-token:
  timeout: 7200        # Token 有效期（秒），默认 30 天，这里设 2 小时
  active-timeout: 1800 # 最低活跃频率（秒），超时未访问自动冻结
```

- `timeout`：Token 最长存活时间，到期强制过期（用户需重新登录）
- `active-timeout`：如果用户在这个时间内没有任何请求，Token 冻结；一旦有访问，时间重置

不设 `active-timeout` 时，Token 就是纯固定有效期。

---

## 3. 接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 无 | 用户名+密码登录 |
| POST | `/api/auth/register` | 无 | 注册新用户 |
| POST | `/api/auth/logout` | 无 | 退出登录（使 Token 失效） |
| GET | `/api/auth/check` | 无 | 检查当前 Token 是否有效 |
| GET | `/api/auth/userinfo` | 登录 | 获取当前用户信息 |
| GET | `/api/auth/dashboard` | 登录 | 登录仪表盘 |
| GET | `/api/auth/admin/test` | admin 角色 | 管理员专属接口 |
| GET | `/api/auth/user/write` | user:write 权限 | 写权限测试 |

---

## 4. 配置参考

```yaml
sa-token:
  token-name: satoken          # Token 在 Header/Cookie 里的名字
  timeout: 7200                # Token 有效期（秒），-1 永不过期
  active-timeout: 1800         # 最低活跃频率（秒），超时冻结
  is-concurrent: true          # 是否允许同一账号多地登录
  is-share: false              # 多人登录共用一个 Token
  token-style: uuid            # Token 生成风格
  is-log: true                 # 是否打印操作日志
  is-read-header: true         # 从 Header 读取 Token
  is-read-cookie: false        # 从 Cookie 读取 Token
  is-read-body: false          # 从请求体读取 Token
```

### Token 风格对照

| 值 | 示例 | 特点 |
|----|------|------|
| `uuid` | `b3f8a7c2-1d4e-4f6a-9c3b-7e8f2a1d5c6b` | 标准 UUID，最常用 |
| `simple-uuid` | `b3f8a7c21d4e4f6a9c3b7e8f2a1d5c6b` | 去掉横线 |
| `random-32` | `aB3xK9mP2qR7sT1v` | 32 位随机字符串 |
| `random-64` | 64 位随机字符串 | 更长更安全 |
| `tik` | `Bf_XXqT2U4x8H2aT` | 特定算法生成 |

---

## 5. 代码结构

### StpInterfaceImpl.java —— 最关键的类

实现 `StpInterface` 接口，**所有鉴权的数据来源**。每当你用 `@SaCheckPermission` 或 `StpUtil.checkRole()` 时，Sa-Token 内部调的就是这个类的两个方法。不实现这个类，所有鉴权都返回 false。

### AuthController.java —— REST 接口

登录/注册/登出/用户信息 + 3 个权限测试接口。`@SaCheckLogin`、`@SaCheckRole("admin")`、`@SaCheckPermission("user:write")` 三种注解用法。

### UserService.java —— 用户业务逻辑

演示用硬编码用户数据，生产环境替换为数据库查询。核心功能：
- `login()` — 验证用户名密码 + `StpUtil.login(userId)` + 存 Token Session
- `register()` — 注册新用户
- `getByUserId()` — 按 userId 查询

### SaTokenUtil.java —— 快捷工具

封装常见操作，一行代码获取用户信息：

```java
SaTokenUtil.getLoginUser()        // 当前登录用户
SaTokenUtil.getUserId()           // 当前用户 ID
SaTokenUtil.hasRole("admin")      // 是否有角色
SaTokenUtil.hasPermission("x")    // 是否有权限
```

### GlobalExceptionHandler.java —— 统一异常处理

拦截 Sa-Token 异常并返回标准化响应：
- `NotLoginException` → 401
- `NotPermissionException` → 403
- `NotRoleException` → 403

---

## 6. 常见场景

### 场景 1：前端如何传 Token

**方式一：Header（推荐，本项目使用）**

```javascript
// axios 请求拦截器
axios.interceptors.request.use(config => {
  config.headers['satoken'] = localStorage.getItem('token')
  return config
})
```

**方式二：Cookie**

修改 `application.yml`：
```yaml
sa-token:
  is-read-cookie: true   # 开启从 Cookie 读取
  is-read-header: false
```

登录成功后 Sa-Token 自动往 Cookie 里写入 Token，浏览器自动携带。

### 场景 2：获取当前登录用户

```java
// 方式一：通过工具类
LoginUser user = SaTokenUtil.getLoginUser();

// 方式二：直接读 Token Session
LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");

// 方式三：只拿 userId
Long userId = StpUtil.getLoginIdAsLong();
```

### 场景 3：踢人下线

```java
// 强制指定 userId 下线
StpUtil.kickout(1001);

// 踢下线后在对方下次请求时抛出 NotLoginException（be = REPLACED）
```

### 场景 4：多端登录互斥

```yaml
sa-token:
  is-concurrent: false   # 不允许同一账号多地登录
  is-share: false        # 每次登录发新 Token
```

设置为 `false` 后，同一账号在新设备登录会把旧设备的 Token 顶掉。

---

## 7. 生产环境替换指南

演示代码用硬编码内存数据，生产环境需要替换以下内容：

### 7.1 接入数据库

```java
@Service
public class UserService {
    // 替换 ConcurrentHashMap → MyBatis / JPA Repository
    // users.get(username) → userMapper.selectByUsername(username)
}
```

### 7.2 密码加密

```java
// 替换明文比较
if (!user.getPassword().equals(password)) {
    throw new RuntimeException("用户名或密码错误");
}

// 替换为 BCrypt
if (!BCrypt.checkpw(password, user.getPasswordHash())) {
    throw new RuntimeException("用户名或密码错误");
}
```

### 7.3 启用 Redis 持久化

去掉 `application.yml` 中的 Redis 排除配置，Token/Session 自动写入 Redis：

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
  # autoconfigure:
  #   exclude: ...  ← 注释掉这行
```

依赖已在 pom.xml 中添加（`sa-token-redis-jackson`）。

### 7.4 自定义 Token 生成策略

```java
@Bean
public SaTokenConfig saTokenConfig() {
    // 实现自定义 Token 风格
}
```

---

## 8. 常见问题 FAQ

### Q1：登录后访问接口返回 "未登录"？

检查请求 Header 名是否正确。本配置里 Token 名为 `satoken`，请求 Header 必须是 `satoken: <token值>`（全小写）。

### Q2：权限/角色检查不生效？

确保实现了 `StpInterface` 接口（本项目的 `StpInterfaceImpl.java`）。不实现这个接口，所有 `@SaCheckPermission` / `@SaCheckRole` 都返回 false。

### Q3：Token 存在哪里？

默认存内存（`ConcurrentHashMap`）。重启应用后所有 Token 丢失，所有用户需重新登录。生产环境请启用 Redis（见 7.3）。

### Q4：怎么区分普通 Token 和临时 Token？

Sa-Token 支持多种 loginType，可以在不同场景用不同 loginType：

```java
StpUtil.login(userId, "user");     // 默认用户登录
StpUtil.login(userId, "temp");     // 临时 Token（如邮箱验证）
StpUtil.setLoginId(userId, "app"); // APP 端登录
```

### Q5：怎么实现"记住我"功能？

```java
// 勾选"记住我"时，设更长过期时间
StpUtil.login(userId, new SaLoginModel().setTimeout(60 * 60 * 24 * 7)); // 7天
```

### Q6：Sa-Token 和 Spring Security 能共存吗？

可以，但没必要。Sa-Token 已经覆盖了 Spring Security 的核心场景（认证+授权），而且 API 更简单。如果要共存，Spring Security 放行 Sa-Token 相关路径即可。

---

## 9. 文件树

```
springboot-satoken/
├── pom.xml                                          # Maven 依赖（Sa-Token + Web + Validation）
├── src/main/java/com/quick/springbootsatoken/
│   ├── SpringbootSatokenApplication.java             # 启动类
│   ├── config/
│   │   └── StpInterfaceImpl.java                     # 【核心】权限/角色数据加载
│   ├── controller/
│   │   └── AuthController.java                       # 登录/注册/登出/用户信息 REST 接口
│   ├── handler/
│   │   └── GlobalExceptionHandler.java               # 全局异常处理（401/403 统一返回）
│   ├── model/
│   │   ├── LoginRequest.java                         # 登录请求 DTO
│   │   ├── LoginUser.java                            # 登录用户模型（返回给前端）
│   │   └── RegisterRequest.java                      # 注册请求 DTO
│   ├── service/
│   │   └── UserService.java                          # 用户服务（演示用内存数据）
│   └── util/
│       └── SaTokenUtil.java                          # 快捷工具类
├── src/main/resources/
│   └── application.yml                               # Sa-Token 配置
└── src/test/java/.../
    └── SpringbootSatokenApplicationTests.java
```

---

**参考链接**
- [Sa-Token 官方文档](https://sa-token.cc/)
- [Sa-Token Gitee](https://gitee.com/dromara/sa-token)
- [Sa-Token GitHub](https://github.com/dromara/sa-token)
