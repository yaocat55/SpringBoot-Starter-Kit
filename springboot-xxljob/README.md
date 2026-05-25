# springboot-xxljob

SpringBoot XXL-JOB 分布式任务调度接入模板，开箱即用的执行器示例。

## 快速接入

### 1. 启动调度中心（Admin）

```bash
# 从 GitHub 拉取 xxl-job
git clone https://github.com/xuxueli/xxl-job.git
cd xxl-job/xxl-job-admin

# 修改 application.properties 中的数据库连接
# spring.datasource.url=jdbc:mysql://localhost:3306/xxl_job?useUnicode=true&...

# 在 MySQL 中执行 doc/db/tables_xxl_job.sql 建表

# 启动 Admin
mvn spring-boot:run
# 访问 http://localhost:8080/xxl-job-admin
# 默认账号: admin / 123456
```

### 2. Admin 后台注册执行器

1. 登录 Admin → 执行器管理 → 新增
2. 填写：
   - **AppName**: `springboot-xxljob-executor`（必须和 yml 里的 `xxl.job.executor.appname` 一致）
   - **名称**: SpringBoot 示例执行器
   - **注册方式**: 自动注册

### 3. 配置并启动本模块

```yml
# application.yml
xxl:
  job:
    enabled: true
    admin:
      addresses: http://localhost:8080/xxl-job-admin
    executor:
      appname: springboot-xxljob-executor
      port: 9999
    access-token: default_token
```

```bash
cd springboot-xxljob
mvn spring-boot:run
```

启动后查看控制台，出现 `XXL-JOB 执行器初始化` 表示注册成功。同时 Admin 后台「执行器管理」页面会看到节点上线。

### 4. Admin 后台创建任务

1. 任务管理 → 新增
2. 填写：
   - **执行器**: springboot-xxljob-executor
   - **运行模式**: BEAN
   - **JobHandler**: `simpleJob`（对应 `@XxlJob("simpleJob")`）
   - **Cron**: `0/30 * * * * ?`（每 30 秒执行一次）
   - **路由策略**: 第一个
3. 保存后点击「启动」，每 30 秒就能在控制台看到执行日志

---

## 项目结构

```
src/main/java/com/quick/springbootxxljob/
├── SpringbootXxljobApplication.java   # 启动类
├── config/
│   └── XxlJobConfig.java              # 创建 XxlJobSpringExecutor，注册到 Admin
├── handler/
│   └── SampleJobHandler.java          # 7 个示例 @XxlJob Handler
└── controller/
    └── JobController.java             # 不依赖 Admin，REST 接口手动触发 Job（本地调试用）
```

---

## 本地调试：绕过 Admin 直接触发 Job

**不需要部署调度中心**，启动本模块后直接调 REST 接口即可手动触发所有示例任务，效果和 Admin 调度一致。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/jobs/simple` | 触发简单任务 |
| POST | `/api/jobs/sharding` | 触发分片广播任务 |
| POST | `/api/jobs/param?type=daily_report` | 触发带参数任务 |
| POST | `/api/jobs/parent-child` | 触发父子任务链 |
| POST | `/api/jobs/long-running` | 触发长时间任务（10 秒） |
| POST | `/api/jobs/fail-retry` | 触发失败重试任务 |
| POST | `/api/jobs/broadcast` | 触发广播任务 |
| POST | `/api/jobs/all` | 一键触发全部 8 个任务 |

```bash
# 示例：手动触发简单任务
curl -X POST http://localhost:8082/api/jobs/simple

# 返回: {"success":true, "job":"simpleJob", "time":"2025-01-15T10:30:00"}

# 一键跑通所有任务
curl -X POST http://localhost:8082/api/jobs/all
```

**注意**：直接调接口时 `XxlJobHelper.getJobParam()` 返回空字符串（因为没有经过 Admin 的 HTTP 调度链路）。真正使用参数时需要在 Admin 后台配置并调度。

---

## 示例 JobHandler 说明

| JobHandler | 演示特性 | 路由策略 | 说明 |
|------------|---------|---------|------|
| `simpleJob` | 基础任务 | 第一个 | 最简单的定时任务，每隔 Cron 表达式执行一次 |
| `shardingJob` | 分片广播 | 分片广播 | 多节点并行处理大数据量，每节点处理一段 |
| `paramJob` | 动态参数 | 第一个 | 通过 Admin 后台传参，不重启改行为 |
| `parentJob` → `childJob` | 子任务链 | 串行 | 父任务完成后自动触发子任务，编排任务流 |
| `longRunningJob` | 超时告警 | 第一个 | 模拟长时间任务，配合 Admin 超时告警 |
| `failRetryJob` | 失败重试 | 第一个 | 模拟失败，配合 Admin 失败重试（最多 3 次） |
| `broadcastJob` | 广播执行 | 广播 | 所有在线节点同时执行，适用清缓存/刷配置 |

---

## 后台可以做什么

登录 Admin 后台 `http://localhost:8080/xxl-job-admin` 后，你可以：

### 任务管理

| 操作 | 说明 |
|------|------|
| **新增任务** | 配置 JobHandler、Cron 表达式、路由策略、阻塞策略 |
| **启动/停止** | 一键启停任务调度 |
| **执行一次** | 手动触发单次执行（不等 Cron），用于测试或临时补数据 |
| **修改参数** | 修改任务参数后下次执行自动生效，无需重启 |
| **终止运行** | 强制终止正在执行的任务 |
| **删除任务** | 下线不再需要的任务 |

### 调度日志

| 操作 | 说明 |
|------|------|
| **查看日志** | 每次执行的触发时间、调度结果、执行耗时 |
| **查看执行日志** | 点击「执行日志」按钮，看 JobHandler 里 `log.info()` 的完整输出 |
| **失败重试** | 失败的任务可以手动重试一次 |
| **终止执行** | 终止卡住的运行中任务 |
| **日志清理** | 定期清理过期日志 |

### 执行器管理

| 操作 | 说明 |
|------|------|
| **注册执行器** | 配置 AppName + 注册方式（自动/手动） |
| **查看在线节点** | 看到每个执行器集群有多少节点在线 |
| **剔除节点** | 手动下线故障节点 |

### 报表

| 操作 | 说明 |
|------|------|
| **调度报表** | 按天/小时统计调度次数、成功率、失败率 |

### 高级功能

| 功能 | 说明 |
|------|------|
| **路由策略** | 第一个、轮询、随机、最不经常使用、故障转移、分片广播等 |
| **阻塞策略** | 单机串行 / 丢弃后续调度 / 覆盖之前调度 |
| **子任务** | 本任务执行成功后自动触发子任务，可串联多个任务形成 DAG |
| **任务超时** | 设置超时时间，超时后标记失败 |
| **失败重试** | 失败后自动重试 N 次 |
| **任务依赖** | 通过子任务链实现 A → B → C 的串行依赖 |
| **告警** | 失败时自动发送告警邮件 |
| **GLUE 模式** | 直接在 Admin 后台编写/修改任务代码，热部署（支持 Java/Shell/Python/PHP 等） |
| **分片广播** | 一份数据分给多个节点并行处理，适用于大数据量批处理 |

---

## XXL-JOB 原理

### 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     XXL-JOB 调度中心 (Admin)                       │
│                                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐    │
│  │ 任务管理  │  │ 调度日志  │  │ 执行器管理│  │ 调度报表      │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘    │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                    调度线程池                               │    │
│  │  ┌────────┐  ┌────────┐  ┌────────┐                      │    │
│  │  │快慢线程│  │时间轮  │  │Cron解析│  → 生成 Trigger 任务   │    │
│  │  │池分离  │  │调度    │  │        │                      │    │
│  │  └────────┘  └────────┘  └────────┘                      │    │
│  └──────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                        HTTP 回调调度                               │
│                    (POST /run 接口)                                │
└──────────────────────────┼────────────────────────────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐ ┌───▼──────┐ ┌───▼──────┐
        │ 执行器节点1│ │ 执行器节点2│ │ 执行器节点3│    ← 你的 SpringBoot 应用
        │  port:9999 │ │  port:9999│ │  port:9999│
        │ @XxlJob   │ │ @XxlJob  │ │ @XxlJob   │
        └───────────┘ └──────────┘ └──────────┘
```

### 执行器启动流程

```
SpringBoot 启动
     │
     ▼
XxlJobConfig 创建 XxlJobSpringExecutor
     │
     ├── 1. 扫描所有 @XxlJob 注解方法，注册为 JobHandler
     │      存入 ConcurrentHashMap<String, IJobHandler>
     │
     ├── 2. 启动内嵌 Netty HTTP Server (端口 9999)
     │      暴露 /beat  (心跳检测)
     │      暴露 /idleBeat  (空闲检测)
     │      暴露 /run   (触发任务执行)
     │      暴露 /kill  (强制终止)
     │      暴露 /log   (读取执行日志)
     │
     └── 3. 启动注册线程，每 30 秒向 Admin POST 注册请求
            Admin 收到注册 → 更新执行器节点列表 → 维持心跳
```

### 一次任务调度的完整链路

```
Admin 调度线程
     │
     ├── 1. 时间轮到点 → 从 DB 查出待调度任务列表
     │      (SQL: SELECT * FROM xxl_job_info WHERE trigger_next_time <= now())
     │
     ├── 2. 根据路由策略选择一个执行器节点
     │      路由策略: 第一个 / 轮询 / 随机 / LFU / LRU / 故障转移 / 分片广播 / ...
     │
     ├── 3. 发送 HTTP POST → http://执行器IP:9999/run
     │      请求体: {jobId, executorHandler, executorParams, ...}
     │
     ├── 4. 执行器收到请求
     │      └→ XxlJobSpringExecutor 查找对应的 JobHandler
     │         └→ 在线程池中执行 JobHandler.execute()
     │            └→ @XxlJob 注解的方法被调用
     │               └→ 业务逻辑执行
     │                  └→ XxlJobHelper.handleSuccess() / handleFail()
     │
     ├── 5. 执行器同步返回执行结果给 Admin
     │      (HTTP Response: 200 成功 / 500 失败)
     │
     └── 6. Admin 记录调度日志 + 更新下次触发时间
            (UPDATE xxl_job_info SET trigger_next_time = ...)
```

### 关键设计决策

**Q: 为什么 Admin 通过 HTTP 回调执行器，而不是执行器轮询？**

HTTP 回调是 Push 模型，Admin 掌握调度节奏。执行器轮询（Pull）会有延迟（取决于轮询间隔），且浪费资源。Push 模型下任务触发延迟 < 50ms。

**Q: 如果执行器挂了怎么办？**

Admin 每 30 秒收不到心跳就标记节点离线。调度时自动跳过离线节点，路由到健康节点。故障转移策略下会自动 try 下一个节点。

**Q: 如何保证任务不重复执行？**

Admin 使用 DB 行锁（`SELECT ... FOR UPDATE`）确保同一任务在多台 Admin 集群部署时只有一台触发调度。执行器侧通过「阻塞策略」控制单机串行/丢弃/覆盖。

**Q: 分片广播怎么工作？**

Admin 拿到所有在线执行器节点，给每个发一条调度请求，请求体里带 `shardIndex` (0, 1, 2, ...) 和 `shardTotal`。执行器侧用 `XxlJobHelper.getShardIndex()` 和 `getShardTotal()` 算出自己处理哪段数据。所有节点并行执行，互不影响。

### 执行器内部结构

```
XxlJobSpringExecutor (Spring 容器感知版本)
│
├── JobHandler 注册表
│   ConcurrentHashMap<String, IJobHandler>
│   ├── "simpleJob"     → SampleJobHandler.simpleJob 方法
│   ├── "shardingJob"   → SampleJobHandler.shardingJob 方法
│   └── "paramJob"      → SampleJobHandler.paramJob 方法
│
├── Netty HTTP Server (端口 9999)
│   ├── EmbedServer.start()
│   └── 处理 Admin 发来的 /run /beat /kill /log 请求
│
├── ExecutorRegistryThread (注册线程)
│   └── 每 30 秒向 Admin 发送注册请求
│
└── JobLogFileCleanThread (日志清理线程)
    └── 每天清理一次过期日志文件
```

---

## 生产接入指南

### 你要改的文件

| 优先级 | 文件 | 改什么 |
|--------|------|--------|
| **必改** | `application.yml` | Admin 地址、执行器 AppName、accessToken |
| **必改** | `handler/SampleJobHandler.java` | 删除示例方法，写你自己的 @XxlJob 方法 |
| **建议** | `handler/` 目录 | 按业务拆多个 Handler 文件（订单、用户、报表等） |
| **不动** | `XxlJobConfig.java` | 开箱即用，无需改动 |
| **不动** | `controller/JobController.java` | 本地调试用，生产可保留或删除 |

### 生产配置建议

```yml
xxl:
  job:
    admin:
      addresses: http://xxl-job-admin.internal:8080/xxl-job-admin  # 内网地址
    executor:
      appname: ${spring.application.name}-executor
      port: 9999
      logpath: /data/logs/xxl-job
    access-token: ${XXL_JOB_TOKEN}  # 从环境变量注入，与 Admin 保持一致
```

### 监控

```bash
# 执行器心跳正常
# Admin 后台 → 执行器管理 → 查看在线节点

# 也可以直接调 Admin 的 REST API
curl http://localhost:8080/xxl-job-admin/api

# 常见 API：
# - /api/registry  (查看执行器注册信息)
# - /api/jobinfo/trigger (手动触发任务)
# - /api/joblog/pageList (查询调度日志)
```
