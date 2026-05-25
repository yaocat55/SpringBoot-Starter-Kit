# springboot-dynamictp

SpringBoot DynamicTp 动态线程池接入模板，开箱即用，提供线程池监控、运行时调参、告警通知的完整示例。

## 目录

- [是什么](#是什么)
- [为什么需要动态线程池](#为什么需要动态线程池)
- [原理概述](#原理概述)
- [快速接入](#快速接入)
- [项目结构](#项目结构)
- [调用链](#调用链)
- [核心 API 说明](#核心-api-说明)
- [生产环境接入指南](#生产环境接入指南)
- [常见问题](#常见问题)

## 是什么

**DynamicTp**（Dynamic Thread Pool）是一个动态线程池管理框架，由 dromara 开源社区维护。

它的核心能力就一句话：**让你在运行时动态调整线程池参数，不用重启应用。**

传统线程池用 `ThreadPoolExecutor` 硬编码参数写在代码里，一旦上线，核心线程数、最大线程数、队列容量全部写死。流量突增线程打满只能看着，凌晨三点还得爬起来改配置重启。

DynamicTp 把线程池参数外置到配置文件（或配置中心），提供：

- **实时监控**：活跃线程数、队列大小、拒绝次数、任务耗时，一目了然
- **运行时调参**：改 core/max pool size、队列容量，立刻生效，不需要重启
- **多维度告警**：队列容量超阈值、任务拒绝、执行超时、排队超时，自动通知
- **优雅关闭**：应用下线时等待队列中任务执行完再关闭，不丢任务

> 官网：https://dynamictp.cn | GitHub：https://github.com/dromara/dynamic-tp

## 为什么需要动态线程池

假设你维护一个订单系统，线程池配置 `core=5, max=20, queue=200`：

| 场景 | 传统线程池 | DynamicTp |
|------|-----------|-----------|
| 大促流量突增，线程打满 | 大量任务进队列等待，RT 飙高。改代码重启 = 丢任务 + 停服务 | 调大 max pool size，立刻生效，扛过峰值再调回来 |
| 下游变慢，队列积压 | 不知道积压了多少，等用户投诉才发现 | 队列容量告警 → 收到通知 → 排查 |
| 某个业务突然被拒绝 | 日志里看到 RejectedExecutionException，不知道频率 | 拒绝告警告诉你：哪个池、拒绝了多少次、什么时间 |
| 发布时队列还有任务 | 直接 shutdown，队列里几百个任务全丢 | `wait-for-tasks-to-complete-on-shutdown: true`，等任务跑完再关 |

**一句话：把线程池从"黑盒"变成"可观测、可调节"的组件。**

## 原理概述

### 架构分层

```
┌──────────────────────────────────────────────────┐
│                    应用层                          │
│   OrderService / MonitorController               │
│   通过 @Resource 注入 DtpExecutor 使用线程池       │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────┐
│                DynamicTp 层                        │
│                                                   │
│   @EnableDynamicTp  ──→  DtpBeanDefinitionRegistrar │
│   读取 dynamictp.executors 配置                     │
│   为每个线程池创建 DtpExecutor Bean                 │
│   注册到 DtpRegistry（全局注册表）                  │
│                                                   │
│   DtpExecutor extends ThreadPoolExecutor           │
│   重写 execute() 添加监控、告警、队列动态扩容        │
│                                                   │
│   DtpRegistry.getDtpExecutor(name)                 │
│   运行时获取任意线程池，直接调参                     │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────┐
│                  Java 标准层                        │
│   ThreadPoolExecutor (JDK)                        │
│   BlockingQueue (JDK)                             │
└──────────────────────────────────────────────────┘
```

### 关键设计

**DtpExecutor 不是另起炉灶**，它直接继承 `ThreadPoolExecutor`。你在代码里用的 `execute()`、`submit()` 和原生线程池完全一样。DynamicTp 在 execute 方法前后织入了监控逻辑（类似 AOP），收集每次任务执行的耗时、排队时间、拒绝次数等指标。

**没有配置中心也能用**。本例使用本地 yml 配置（`dynamic-tp-spring-boot-starter-common`），线程池参数写死在配置文件中。虽然没有动态调参能力（因为配置是静态文件），但**监控和告警功能完全可用**，运行时仍然可以通过 `/api/dynamictp/adjust` 接口手动调参。接入 Nacos 配置中心后，就可以实现配置变更自动下发、无需手动调用接口。

**Bean 命名规则**：yml 中 `thread-pool-name: order-processor` → Spring 容器中的 Bean name 就是 `order-processor`。你可以用 `@Resource(name = "order-processor")` 注入，也可以用 `DtpRegistry.getDtpExecutor("order-processor")` 动态获取。

## 快速接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.dromara.dynamictp</groupId>
    <artifactId>dynamic-tp-spring-boot-starter-common</artifactId>
    <version>1.2.2-x</version>  <!-- Spring Boot 3.x -->
</dependency>
```

> Spring Boot 2.x 去掉 `-x` 后缀，版本用 `1.2.2`。

### 2. 启动类加注解

```java
@EnableDynamicTp
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 配置文件定义线程池

```yml
dynamictp:
  global-executor-props:
    rejected-handler-type: CallerRunsPolicy
    queue-type: VariableLinkedBlockingQueue
    wait-for-tasks-to-complete-on-shutdown: true
    await-termination-seconds: 5

  executors:
    - thread-pool-name: order-processor     # Bean name，也是后续调参的 key
      core-pool-size: 5
      maximum-pool-size: 20
      queue-capacity: 200
      thread-name-prefix: order-            # 线程名前缀，方便 jstack 排查

    - thread-pool-name: notification-sender
      core-pool-size: 3
      maximum-pool-size: 10
      queue-capacity: 100
      thread-name-prefix: notify-
      rejected-handler-type: DiscardPolicy   # 通知类任务溢出直接丢弃
```

### 4. 注入并使用

```java
@Service
public class OrderService {

    @Resource(name = "order-processor")
    private DtpExecutor orderProcessor;

    public void processOrders(int count) {
        for (int i = 0; i < count; i++) {
            orderProcessor.execute(() -> {
                // 业务逻辑
            });
        }
    }
}
```

### 5. 启动验证

```bash
cd springboot-dynamictp
mvn spring-boot:run
```

看到 DynamicTp 的 banner 输出即表示接入成功。访问 `http://localhost:8083/api/dynamictp/status` 查看所有线程池实时状态。

## 项目结构

```
springboot-dynamictp/
├── pom.xml                                         # 依赖：dynamic-tp-spring-boot-starter-common
├── README.md
└── src/main/
    ├── java/com/quick/springbootdynamictp/
    │   ├── SpringbootDynamictpApplication.java     # 启动类 + @EnableDynamicTp
    │   ├── controller/
    │   │   └── MonitorController.java              # REST 接口：触发任务、查看状态、动态调参
    │   └── service/
    │       └── OrderService.java                   # 业务 Service：注入 DtpExecutor 演示用法
    └── resources/
        └── application.yml                         # DynamicTp 全局配置 + 3 个线程池定义
```

## 调用链

### 请求进来 → 线程池运转 → 监控反馈

```
浏览器 / curl
    │
    ▼
MonitorController                    GET/POST/PUT  @ /api/dynamictp/*
    │
    ├── POST /orders/batch ──→ OrderService.processOrders(count)
    │                               │
    │                               ▼
    │                          orderProcessor.execute(task)     ← DtpExecutor 接管
    │                               │
    │                               ├── 记录排队时间
    │                               ├── 记录执行耗时
    │                               ├── 队列容量超阈值 → 触发告警
    │                               ├── 任务被拒绝 → 触发告警
    │                               └── 执行线程池任务
    │
    ├── POST /notify ──→ OrderService.sendNotification()
    │                         │
    │                         ▼
    │                    notificationSender.execute(task)        ← 另一个 DtpExecutor
    │
    ├── POST /report ──→ OrderService.generateReport()
    │                         │
    │                         ▼
    │                    reportGenerator.execute(task)           ← 又一个 DtpExecutor
    │
    ├── GET /status ──→ OrderService.getPoolStatus()
    │                        │
    │                        ▼
    │                   DtpExecutor.getActiveCount()            ← 实时读取线程池指标
    │                   DtpExecutor.getQueueSize()
    │                   DtpExecutor.getQueueCapacity()
    │
    ├── PUT /adjust ──→ DtpRegistry.getDtpExecutor(name)       ← 从全局注册表获取
    │                        │
    │                        ├── setCorePoolSize(newValue)        ← 立刻生效
    │                        ├── setMaximumPoolSize(newValue)
    │                        └── onRefreshQueueCapacity(newValue)
    │
    └── GET /pools ──→ DtpRegistry.getAllExecutorNames()       ← 列出所有管理的线程池
```

### 线程池生命周期

```
application.yml 配置
    │
    ▼
@EnableDynamicTp 触发 DtpBeanDefinitionRegistrar
    │
    ├── 读取 dynamictp.executors 列表
    ├── 为每个线程池构造 DtpExecutor 实例
    ├── 注册为 Spring Bean（bean name = thread-pool-name）
    └── 注册到 DtpRegistry（全局 map）
            │
            ▼
       应用运行中
            │
            ├── 业务代码通过 @Resource 注入使用
            ├── 监控指标持续采集（活跃线程、队列深度、拒绝次数）
            ├── 告警规则持续评估（capacity > 70%、reject 发生）
            └── 运行时调参通过 /api/dynamictp/adjust 或配置中心下发
            │
            ▼
       应用关闭
            │
            └── wait-for-tasks-to-complete-on-shutdown: true
                 → 等待队列任务执行完（最多等 await-termination-seconds 秒）
                 → 再关闭线程池
```

## 核心 API 说明

### REST 接口（MonitorController）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/dynamictp/orders/batch?count=50` | 批量下单，压测线程池 |
| `POST` | `/api/dynamictp/notify?userId=1&message=hello` | 异步发送通知 |
| `POST` | `/api/dynamictp/report?type=daily` | 异步生成报表 |
| `GET` | `/api/dynamictp/status` | 查看所有线程池实时状态 |
| `PUT` | `/api/dynamictp/adjust?poolName=order-processor&coreSize=10&maxSize=30` | 运行时动态调参 |
| `GET` | `/api/dynamictp/pools` | 列出所有管理的线程池名称 |

### 编程 API（DtpRegistry）

```java
// 获取指定线程池（用来调参）
DtpExecutor executor = DtpRegistry.getDtpExecutor("order-processor");

// 调整参数（立刻生效）
executor.setCorePoolSize(10);
executor.setMaximumPoolSize(30);
executor.onRefreshQueueCapacity(500);

// 获取所有线程池名称
Set<String> names = DtpRegistry.getAllExecutorNames();
```

### 注入方式

```java
// 方式1：@Resource 按名称注入（推荐）
@Resource(name = "order-processor")
private DtpExecutor orderProcessor;

// 方式2：DtpRegistry 动态获取
DtpExecutor executor = DtpRegistry.getDtpExecutor("order-processor");
```

## 生产环境接入指南

### 1. 线程池参数怎么设

没有万能公式，需要根据业务特征来调：

| 参数 | 建议 | 原因 |
|------|------|------|
| `core-pool-size` | 日常 QPS 下 80% 的并发任务数 | 避免频繁创建/销毁线程 |
| `maximum-pool-size` | 峰值 QPS 的 1.2~1.5 倍 | 留一定余量，但不要太大（线程切换也有开销） |
| `queue-capacity` | core pool size × 平均任务耗时(ms) / 1000 | 队列主要用来削峰，不要用来无限缓冲 |
| `rejected-handler-type` | IO 密集型用 `CallerRunsPolicy`，可丢弃任务用 `DiscardPolicy` | CallerRuns 能自然降速，Discard 适合非关键通知 |

### 2. 告警如何配置

本模块使用的是本地 yml 配置模式，告警会输出到日志。在生产环境，建议：

1. **接入 Nacos 配置中心**，配置变更自动下发，线程池参数真正"动态"起来
2. **配置通知平台**，在 notify-items 中设置 `platform-ids` 指向钉钉/企微/飞书机器人
3. **接入 Prometheus + Grafana**，DynamicTp 暴露了 metrics 端点，可以接入监控大盘

### 3. 生产环境依赖推荐

```xml
<!-- Nacos 配置中心：配置变更自动下发，无需手动调参 -->
<dependency>
    <groupId>org.dromara.dynamictp</groupId>
    <artifactId>dynamic-tp-spring-cloud-starter-nacos</artifactId>
    <version>1.2.2-x</version>
</dependency>
```

### 4. 线程池命名规范

- 用 kebab-case：`order-processor`、`notification-sender`
- 线程名前缀短而有意义：`order-`、`notify-`、`report-`
- 这样在 `jstack` 或 Arthas 里一眼就能认出是哪个业务的线程

### 5. 优雅关闭检查清单

```yml
dynamictp:
  global-executor-props:
    wait-for-tasks-to-complete-on-shutdown: true   # 必须开
    await-termination-seconds: 10                  # 根据最长任务耗时设置
```

同时确保 K8s / 发布系统的 `terminationGracePeriodSeconds` > `await-termination-seconds`，否则容器会被强制 kill。

## 常见问题

**Q: 不加 `@EnableDynamicTp` 会怎样？**
线程池不会自动创建，`@Resource` 注入 DtpExecutor 时会报 NoSuchBeanDefinitionException。

**Q: `@Resource` 注入时报 "expected single matching bean but found 3"？**
`@Resource` 默认按字段名匹配 Bean name。你的字段名必须和 yml 中 `thread-pool-name` 一致（都是 kebab-case），或者显式指定 `@Resource(name = "order-processor")`。

**Q: 不用配置中心有什么限制？**
yml 中的参数在启动时读取，改配置文件需要重启。但运行时仍然可以通过 API `/api/dynamictp/adjust` 手动调参，监控告警也不受影响。

**Q: 怎么看到线程池的实时状态？**
1. GET `/api/dynamictp/status` — 文本格式展示
2. GET `/api/dynamictp/pools` — 列出所有线程池名称
3. 日志中也会输出线程池变更和告警信息
4. 接入 Prometheus 后有 /actuator/prometheus 端点

**Q: 和 Hippo4j 有什么区别？**
两者都是动态线程池框架，功能高度重叠。DynamicTp 是 dromara 社区的项目，Hippo4j 也有独立的控制台 UI。选择上：如果你的团队已经用了 dromara 生态（如 Forest、Hutool、LiteFlow），DynamicTp 风格一致；如果你需要自带 Web 控制台管理线程池，Hippo4j 的 Server 模式更直观。
