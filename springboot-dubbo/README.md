# springboot-dubbo

SpringBoot Apache Dubbo 3.x RPC 调用接入模板，一个应用同时演示**提供者（上游）**和**消费者（下游）**两个角色，开箱即用。

## 目录

- [是什么](#是什么)
- [为什么需要 Dubbo](#为什么需要-dubbo)
- [核心概念——5 分钟理解 Dubbo](#核心概念--5-分钟理解-dubbo)
- [快速接入](#快速接入)
- [项目结构](#项目结构)
- [调用链](#调用链)
- [核心 API 说明](#核心-api-说明)
- [代码导读——从 0 到 1 写一个 Dubbo 服务](#代码导读--从-0-到-1-写一个-dubbo-服务)
- [生产环境接入指南](#生产环境接入指南)
- [常见问题](#常见问题)

## 是什么

**Apache Dubbo** 是阿里巴巴开源的高性能 Java RPC 框架，现在是 Apache 顶级项目。

一句话解释：**Dubbo 让你像调用本地方法一样调用远程方法。**

```
┌─────────────────────────┐        ┌─────────────────────────┐
│     消费者 Consumer       │        │     提供者 Provider       │
│                          │  RPC   │                          │
│  greetingService         │───────→│  GreetingServiceImpl     │
│    .sayHello("Tom")      │ 网络调用 │    .sayHello("Tom")      │
│                          │←───────│                          │
│  "Hello Tom!"            │  返回   │  返回 "Hello Tom!"       │
└─────────────────────────┘        └─────────────────────────┘
```

消费者代码里就是一个接口调用，Dubbo 在背后做了：序列化 → 网络传输 → 服务发现 → 负载均衡 → 反序列化。

## 为什么需要 Dubbo

假设你有一个用户服务和一个订单服务，订单服务需要查用户信息。

**不用 Dubbo（HTTP + JSON）：**
```java
// 订单服务里调用户服务
String resp = restTemplate.getForObject("http://user-service/api/user/" + userId, String.class);
User user = objectMapper.readValue(resp, User.class);
// 问题：URL 硬编码、手写序列化、没有服务发现、没有负载均衡
```

**用 Dubbo：**
```java
// 订单服务里调用户服务
@DubboReference
private UserService userService;

User user = userService.getUserById(userId);
// Dubbo 自动处理：服务发现、长连接复用、序列化、负载均衡、失败重试
```

| 对比维度 | HTTP 直连 | Dubbo RPC |
|----------|----------|-----------|
| 调用方式 | 拼接 URL + 手动序列化 | 像本地方法一样调用 |
| 服务发现 | 硬编码 IP:Port | 注册中心自动发现 |
| 负载均衡 | 手写 Ribbon / Nginx | 内置 Random / RoundRobin / LeastActive |
| 失败重试 | 手写 retry 逻辑 | `retries=2` 一行配置 |
| 协议 | HTTP/1.1 短连接 | Dubbo 协议（长连接 + 二进制） |
| 适用场景 | 对外 API / 跨语言 | 内部微服务之间高频调用 |

**一句话：HTTP 适合对外暴露接口，Dubbo 适合内部微服务互相调用。**

## 核心概念 —— 5 分钟理解 Dubbo

### 架构三要素

```
         ┌─────────────┐
         │   注册中心    │  ← Nacos / 组播（本地演示）
         │   Registry   │     存着"谁提供了什么服务"的目录
         └──┬───────┬──┘
            │       │
   ②注册    │       │ ③订阅（发现）
   自己     │       │ 获取提供者地址
            │       │
    ┌───────┴───────┴──────┐
    │                      │
    ▼                      ▼
┌─────────┐  ④ RPC 调用  ┌─────────┐
│ 提供者   │─────────────→│ 消费者   │
│Provider │←─────────────│Consumer │
└─────────┘   ⑤ 返回结果  └─────────┘
```

| 角色 | 干什么的 | 代码体现 |
|------|---------|---------|
| **提供者 Provider** | 实现业务逻辑，把服务"暴露"出去 | `@DubboService` 注解的实现类 |
| **消费者 Consumer** | 调用远程服务，像调本地方法 | `@DubboReference` 注入的接口 |
| **注册中心 Registry** | 存着"谁提供了什么服务"的目录 | yml 中 `dubbo.registry.address` |
| **服务接口 API** | 提供者和消费者之间的契约 | 公共的 `interface`，双方都依赖 |

### 调用流程（一次 RPC 发生了什么）

```
消费者 consumer.callHello("Tom")
    │
    ├── 1. 通过注册中心找到提供者地址列表
    │       └── "GreetingService → [192.168.1.10:20880, 192.168.1.11:20880]"
    │
    ├── 2. 负载均衡选一台机器（如 Random 随机选一台）
    │
    ├── 3. 对方法名 + 参数进行序列化（二进制）
    │
    ├── 4. 通过 Netty 长连接发送请求
    │
    ├── 5. 提供者反序列化 → 反射调用 GreetingServiceImpl.sayHello("Tom")
    │
    ├── 6. 返回值序列化 → 通过同一条连接返回给消费者
    │
    └── 7. 消费者收到 "Hello Tom!" → 返回给调用方
```

### 本示例简化说明

为了做到"零外部依赖、开箱即用"，本示例使用**组播（multicast）**作为注册中心。提供者和消费者在同一个 JVM 里启动，但 Dubbo 的 RPC 调用链路（代理 → 序列化 → 协议栈）完全真实。部署到不同机器时，只需把注册中心换成 Nacos，**业务代码一行不改**。

## 快速接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-spring-boot-starter</artifactId>
    <version>3.3.6</version>  <!-- Spring Boot 3.x 兼容 -->
</dependency>
```

### 2. 配置 application.yml

```yml
dubbo:
  application:
    name: my-app
  registry:
    address: multicast://224.5.6.7:12345   # 本地演示用组播，生产换 Nacos/ZK
  protocol:
    name: dubbo
    port: 20880
  consumer:
    check: false    # 启动时不检查提供者是否存在
```

### 3. 定义服务接口（契约）

```java
public interface GreetingService {
    String sayHello(String name);
}
```

### 4. 编写提供者（上游）

```java
@DubboService(version = "1.0.0")     // ← 这一行暴露为 Dubbo 服务
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String sayHello(String name) {
        return "Hello " + name + "!";
    }
}
```

### 5. 编写消费者（下游）

```java
@Component
public class GreetingConsumer {
    @DubboReference(version = "1.0.0")  // ← 这一行注入远程代理
    private GreetingService greetingService;

    public void call() {
        String result = greetingService.sayHello("Tom");  // 像调本地方法！
    }
}
```

### 6. 启动验证

```bash
cd springboot-dubbo
mvn spring-boot:run
```

看到 Dubbo banner 即接入成功。访问以下接口验证：

```bash
# Consumer 方式调用
curl "http://localhost:8084/api/dubbo/consumer/hello?name=Dubbo"

# @DubboReference 直接注入方式调用
curl "http://localhost:8084/api/dubbo/direct/hello?name=Dubbo"

# 查看提供者信息
curl "http://localhost:8084/api/dubbo/consumer/info"

# 对比两种调用方式
curl "http://localhost:8084/api/dubbo/compare?name=Dubbo"
```

## 项目结构

```
springboot-dubbo/
├── pom.xml                                     # dubbo-spring-boot-starter:3.3.6
├── README.md
└── src/main/
    ├── java/com/quick/springbootdubbo/
    │   ├── SpringbootDubboApplication.java     # @EnableDubbo + 启动类
    │   ├── api/
    │   │   └── GreetingService.java            # 【接口】上下游的契约
    │   ├── provider/
    │   │   └── GreetingServiceImpl.java        # 【提供者】@DubboService 暴露服务
    │   ├── consumer/
    │   │   └── GreetingConsumer.java           # 【消费者】@DubboReference 注入代理
    │   └── controller/
    │       └── GreetingController.java         # REST 接口，验证 Dubbo 调用链路
    └── resources/
        └── application.yml                     # Dubbo 协议 + 注册中心 + 消费者/提供者配置
```

## 调用链

### 请求链路全景图

```
浏览器 / curl
    │
    ▼
GreetingController                  ← REST 入口 @ /api/dubbo/*
    │
    ├── GET /consumer/hello
    │       │
    │       ▼
    │   GreetingConsumer.callSayHello()          ← @Component，封装调用逻辑
    │       │
    │       ▼
    │   greetingService.sayHello(name)           ← @DubboReference 注入的代理对象
    │       │                                     （此时 greetingService 是 Dubbo 生成的代理）
    │       │
    │       ├── Dubbo 代理拦截调用
    │       ├── 通过注册中心找到 GreetingService 的提供者
    │       ├── 序列化方法名 + 参数
    │       ├── 通过 dubbo://192.168.x.x:20880 发送请求
    │       │       │
    │       │       ▼
    │       │   GreetingServiceImpl.sayHello()   ← @DubboService 暴露的提供者
    │       │       │
    │       │       └── 返回 "Hello Dubbo, from Dubbo Provider!"
    │       │
    │       └── 消费者收到响应 → 返回给 Controller → 返回给浏览器
    │
    ├── GET /direct/hello
    │       │
    │       └── Controller 内直接 @DubboReference 调用
    │           （跳过了 GreetingConsumer 这一层封装）
    │
    └── GET /compare
            │
            └── 同时走两条路径，验证结果一致
```

### 上下游对应关系

```
┌─────────────────────────────────────────────────────────────────┐
│                        本模块（同 JVM）                           │
│                                                                 │
│   下游（消费者）                          上游（提供者）           │
│                                                                 │
│   GreetingController                    GreetingServiceImpl     │
│        │                                       ▲                │
│        ├── @DubboReference ──→ RPC 代理 ──→ @DubboService       │
│        │                                                         │
│   GreetingConsumer                                               │
│        │                                                         │
│        └── @DubboReference ──→ RPC 代理 ──→ (同上)               │
│                                                                 │
│   注：同 JVM 时 Dubbo 自动走 injvm 调用，不经过网络               │
│       分开部署后自动切换为远程 dubbo:// 协议调用                   │
└─────────────────────────────────────────────────────────────────┘
```

## 核心 API 说明

### REST 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/dubbo/consumer/hello?name=xxx` | 通过 Consumer 封装调用 |
| `GET` | `/api/dubbo/consumer/info` | 获取提供者线程与时间 |
| `GET` | `/api/dubbo/direct/hello?name=xxx` | 直接 @DubboReference 调用 |
| `GET` | `/api/dubbo/direct/info` | 直接注入方式获取信息 |
| `GET` | `/api/dubbo/compare?name=xxx` | 对比两种调用方式 |

### 关键注解

| 注解 | 放在哪 | 作用 |
|------|--------|------|
| `@EnableDubbo` | 启动类 | 开启 Dubbo 组件扫描 |
| `@DubboService(version="1.0.0")` | 提供者实现类 | 暴露为 Dubbo 服务，注册到注册中心 |
| `@DubboReference(version="1.0.0")` | 消费者字段 | 注入远程服务代理，调用像本地方法 |

### 配置速查

```yml
dubbo:
  application.name: my-app            # 应用名，注册中心的标识
  registry.address: multicast://...   # 注册中心地址
  protocol.name: dubbo                # 协议：dubbo / triple / rest
  protocol.port: 20880                # 协议端口
  consumer.check: false               # 启动时不检查提供者
  consumer.timeout: 3000              # 调用超时（毫秒）
  consumer.retries: 1                 # 失败重试次数
```

## 代码导读 —— 从 0 到 1 写一个 Dubbo 服务

如果你从没接触过 Dubbo，跟着下面 5 步走就能跑通：

### 第 1 步：写接口（定义契约）

```java
// api/GreetingService.java
// 这就是上下游之间的"合同"。提供者按合同实现，消费者按合同调用。
public interface GreetingService {
    String sayHello(String name);
    String getServerInfo();
}
```

### 第 2 步：实现接口（提供者——上游）

```java
// provider/GreetingServiceImpl.java
@DubboService(version = "1.0.0")   // version 用来区分同一接口的不同版本
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String sayHello(String name) {
        return "Hello " + name + ", from Dubbo Provider!";
    }
    // ...
}
```

### 第 3 步：注入远程代理（消费者——下游）

```java
// consumer/GreetingConsumer.java
@Component
public class GreetingConsumer {
    @DubboReference(version = "1.0.0")  // version 必须匹配
    private GreetingService greetingService;  // 这看起来是接口，实际是 Dubbo 生成的代理

    public String callSayHello(String name) {
        return greetingService.sayHello(name);  // 远程调用！
    }
}
```

### 第 4 步：暴露 REST 接口验证

```java
// controller/GreetingController.java
@RestController
public class GreetingController {
    @DubboReference(version = "1.0.0")
    private GreetingService greetingService;

    @GetMapping("/hello")
    public String hello(@RequestParam String name) {
        return greetingService.sayHello(name);
    }
}
```

### 第 5 步：配置并启动

```yml
# application.yml
dubbo:
  application.name: springboot-dubbo
  registry.address: multicast://224.5.6.7:12345
  protocol.name: dubbo
  protocol.port: 20880
```

```bash
mvn spring-boot:run
```

## 生产环境接入指南

### 1. 换成 Nacos 注册中心

组播只在本地开发演示用，生产环境统一使用 Nacos：

```xml
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-registry-nacos</artifactId>
    <version>3.3.6</version>
</dependency>
```

```yml
dubbo:
  registry:
    address: nacos://127.0.0.1:8848
  metadata-report:
    address: nacos://127.0.0.1:8848  # 元数据中心（3.x 必须配）
```

### 2. 拆分提供者和消费者

生产环境通常提供者和消费者分开部署：

```
springboot-order-service/       （提供者——暴露订单服务）
    └── provider/
        └── OrderServiceImpl.java   @DubboService

springboot-user-service/        （消费者——调用订单服务）
    └── consumer/
        └── UserConsumer.java       @DubboReference
```

拆分后，公共接口通常抽到一个独立的 jar 包（如 `common-api`），上下游共同依赖。

### 3. 调优参数建议

| 参数 | 建议值 | 原因 |
|------|--------|------|
| `consumer.timeout` | 3000 | 超时太短容易误报，太长占线程 |
| `consumer.retries` | 1 | 幂等接口可多设，非幂等设 0 |
| `protocol.threads` | 200 | 默认 200，按 QPS 调整 |
| `consumer.check` | false | 避免启动顺序依赖 |

### 4. Dubbo 3.x 协议选择

| 协议 | 适用场景 |
|------|---------|
| `dubbo://` | 内部微服务之间，长连接 + 二进制，高性能 |
| `triple://` | 需要 gRPC 兼容 / 穿透网关 / 跨语言 |
| `rest://` | 对外暴露 HTTP RESTful 接口 |

### 5. 版本管理与灰度发布

```java
// 同一个接口，不同版本
@DubboService(version = "1.0.0")   // 老版本
@DubboService(version = "2.0.0")   // 新版本

// 消费者灰度：只调 2.0.0
@DubboReference(version = "2.0.0")
```

利用 version + 注册中心的权重调整，可以实现平滑的灰度发布。

## 常见问题

**Q: 为什么同一个应用里同时有提供者和消费者？**
为了演示方便。在一个 JVM 里同时起两个角色，代码结构上 `provider/` 和 `consumer/` 是分开的，你可以随时把它们拆到不同的 Spring Boot 应用中。

**Q: 启动时看到 "Failed register interface application mapping" 错误？**
这是 Dubbo 3.x 在组播模式下元数据注册不成功，**不影响调用**（测试通过）。换成 Nacos 后会自动消失。

**Q: @DubboService 和 @Service 有什么区别？**
`@Service` 是 Spring 的注解，让类成为 Spring Bean。`@DubboService` 是 Dubbo 的注解，不仅成为 Spring Bean，还会把服务注册到 Dubbo 注册中心。Dubbo 3.x 必须用 `@DubboService`，老版本（2.x）用的是 `@Service`（容易和 Spring 的混淆）。

**Q: 同 JVM 时真的是 RPC 调用吗？**
Dubbo 会检测到提供者和消费者在同一个 JVM，自动走 `injvm` 协议（不走网络），但**整个调用链路和远程完全一致**——代理拦截、序列化、filter 链都经过。分开部署后自动切换为远程协议。

**Q: 怎么验证 RPC 调用确实走了 Dubbo？**
1. 看日志：Dubbo 的 filter 会打印调用链
2. 改端口：把 `dubbo.protocol.port` 改成不存在的端口，如果调用失败说明走了 Dubbo
3. 分开部署：把提供者和消费者部署到不同 JVM

**Q: 和 Spring Cloud 有什么区别？**
Dubbo 是 RPC 框架（专注于服务间调用），Spring Cloud 是微服务全家桶（包含网关、配置中心、熔断等）。实际项目中二者可以混用：Dubbo 负责内部高性能 RPC，Spring Cloud Gateway 负责对外 API 网关。

**Q: 和 gRPC 有什么区别？**
两者都是高性能 RPC 框架。Dubbo 是 Java 生态的，对 Java 开发者最友好（接口定义就是 Java interface）。gRPC 用 Protobuf 定义接口，跨语言更强。如果你的系统主要是 Java，Dubbo 开发体验更好。
