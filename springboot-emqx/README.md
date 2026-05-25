# springboot-emqx

SpringBoot EMQX 开箱即用接入模板，基于 MQTT 5.0 协议 + Eclipse Paho 客户端。

## 快速接入

### 1. 启动 EMQX

```bash
# Docker 方式（推荐）
docker run -d --name emqx \
  -p 1883:1883 -p 8083:8083 -p 18083:18083 \
  emqx/emqx:latest

# 访问 Dashboard: http://localhost:18083
# 默认账号: admin / public
```

### 2. 配置并启动

```yml
# application.yml
emqx:
  enabled: true                            # 启用 EMQX 连接
  broker-url: tcp://localhost:1883         # EMQX TCP 端口
  client-id: springboot-emqx-demo          # 客户端唯一 ID
  username: admin                          # EMQX 内置认证用户名
  password: public                         # 认证密码
```

```bash
cd springboot-emqx
mvn spring-boot:run
```

### 3. 验证

```bash
# 发一条消息
curl -X POST "http://localhost:8081/api/emqx/publish?topic=/test/hello&payload=HelloEMQX"

# 查看状态
curl http://localhost:8081/api/emqx/status

# 发送设备遥测
curl -X POST "http://localhost:8081/api/emqx/device/demo/telemetry" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"demo","dataType":"temperature","value":25.5,"unit":"celsius"}'

# 动态订阅
curl -X POST "http://localhost:8081/api/emqx/subscribe?topicFilter=/test/+&qos=1"

# 订阅列表
curl http://localhost:8081/api/emqx/subscriptions

# 健康检查
curl http://localhost:8081/actuator/health
```

### 4. 关键设计：emqx.enabled = false

模块默认不连接 EMQX，设 `false` 时整个 MQTT 子系统不会被创建（`@ConditionalOnProperty`）。这意味着即使没有 EMQX 环境，服务也能正常启动，不会因为连不上 Broker 而报错。

---

## 项目结构

```
src/main/java/com/quick/springbootemqx/
├── SpringbootEmqxApplication.java        # 启动类
├── config/
│   ├── EmqxConfig.java                   # MQTT 5.0 客户端配置 + 连接
│   ├── EmqxConnectionStateListener.java  # 连接/断连/重连事件监听
│   └── EmqxHealthIndicator.java          # Actuator 健康检查 (/actuator/health)
├── publisher/
│   └── EmqxPublisher.java               # 消息发布 + 离线缓冲 + 断连重发
├── subscriber/
│   └── EmqxSubscriber.java              # Topic 订阅 + 通配符 + 动态管理
├── controller/
│   └── EmqxController.java              # REST 测试接口 (发布/订阅/状态)
├── model/
│   ├── EmqxMessageWrapper.java          # 消息信封（统一包装格式）
│   └── DeviceMessage.java               # 设备消息示例
└── util/
    └── EmqxUtil.java                    # 同步/异步发送工具
```

---

## 类调用链（谁调用了谁）

### Demo 模式（默认，无需 EMQX）

```
SpringbootEmqxApplication
  └─ demoRunner()                          ← CommandLineRunner，启动时自动执行
       └─ EmqxDemoService.runStartupDemo() ← 内存 Pub/Sub 演示
            ├─ subscribe()                  ← 注册 Topic 监听
            ├─ publish()                    ← 发送消息 → 触发订阅回调
            └─ publishDeviceTelemetry()     ← 包装 JSON → 发布

EmqxDemoController                          ← REST 接口（/api/emqx/*）
  └─ EmqxDemoService                       ← 内存 Pub/Sub 引擎
       ├─ publish()                         ← 消息写入 messageLog + 分发给匹配订阅者
       ├─ subscribe()                       ← 注册 handler 到 ConcurrentHashMap
       ├─ getStatus()                       ← 返回连接态 + 消息统计
       └─ getMessageLog()                   ← 返回最近 200 条消息记录
```

### 真实 EMQX 模式（emqx.enabled = true）

```
SpringbootEmqxApplication
  └─ (无 CommandLineRunner，等待外部请求)

EmqxConfig                                   ← @Configuration，启动时执行
  ├─ emqxProperties()                        ← 绑定 application.yml 的 emqx.* 配置
  └─ mqttClient()                            ← 创建 MqttClient → connect() → 返回 Bean
       └─ buildConnectionOptions()           ← 组装 MqttConnectionOptions (认证/会话/遗嘱)

EmqxConnectionStateListener                  ← @PostConstruct 注册全局 MqttCallback
  ├─ connectComplete()                       ← 首次连接/重连成功 → 标记 connected=true
  │    └─ onReconnectCallback.run()          ← 触发 EmqxPublisher.flushBuffer()
  └─ disconnected()                          ← 断连 → 标记 connected=false
       └─ onDisconnectCallback.run()         ← 通知外部

EmqxHealthIndicator                          ← 被 Actuator 调用
  └─ health()                                ← GET /actuator/health → "emqx": UP/DOWN

EmqxController  ──────────────────────────┐
  ├─ publish()          ─→ EmqxPublisher  │   所有端点都受 @ConditionalOnBean(MqttClient) 保护
  ├─ publishTelemetry()  ─→ EmqxPublisher  │   仅在 emqx.enabled=true 时注册
  ├─ publishBatch()      ─→ EmqxPublisher  │
  ├─ subscribe()         ─→ EmqxSubscriber │
  ├─ unsubscribe()       ─→ EmqxSubscriber │
  └─ status()            ─→ MqttClient     │
                             + EmqxPublisher│
                             + EmqxSubscriber
                                          ┘
EmqxPublisher                               ← 消息发布引擎
  ├─ init()                                 ← @PostConstruct：注册 onReconnectCallback
  ├─ publish(topic, payload)                ← 已连接 → doSend() / 未连接 → bufferMessage()
  ├─ publishAsJson(topic, object)           ← Jackson 序列化 → publish()
  └─ flushBuffer()                          ← 重连后自动调用：逐条重发 + 清空队列

EmqxSubscriber                              ← 消息订阅引擎
  ├─ init()                                 ← @PostConstruct：订阅默认 Topic
  └─ subscribe(topicFilter, qos, handler)   ← 注册到 MqttClient + 本地记录

EmqxUtil                                    ← 工具类，被业务代码调用
  ├─ publish(topic, payload, qos, retained) ← 同步发送
  └─ publishAsync(topic, payload, qos)      ← 异步发送（CompletableFuture）
```

### 切换机制

```
emqx.enabled = false (默认)            emqx.enabled = true
─────────────────────────────         ─────────────────────────
EmqxDemoService   ← 激活              EmqxDemoService   ← 被 @ConditionalOnMissingBean 移除
EmqxDemoController ← 激活             EmqxDemoController ← 被 @ConditionalOnMissingBean 移除
MqttClient        ← 不创建            MqttClient        ← 创建
EmqxController    ← 不创建            EmqxController    ← 激活
EmqxPublisher     ← 不创建            EmqxPublisher     ← 激活
EmqxSubscriber    ← 不创建            EmqxSubscriber    ← 激活
EmqxUtil          ← 不创建            EmqxUtil          ← 激活
EmqxHealthIndicator ← 不创建          EmqxHealthIndicator ← 激活
EmqxConnectionStateListener ← 不创建  EmqxConnectionStateListener ← 激活
```

---

## 生产接入指南

### 你要改哪些文件

| 优先级 | 文件 | 改什么 |
|--------|------|--------|
| **必改** | `application.yml` | `emqx.enabled: true`，填真实的 `broker-url` / `username` / `password` |
| **必改** | `EmqxSubscriber.java` | 改 `init()` 里的默认订阅 Topic，换成你自己的业务 Topic |
| **必改** | `EmqxSubscriber.java` | 改每个订阅的 `handler` 回调，写你自己的业务处理逻辑（入库、推送给前端、触发告警等） |
| **建议** | `DeviceMessage.java` | 替换成你自己的设备数据模型 |
| **建议** | `EmqxController.java` | 删掉测试用的 `/publish/batch` 端点，改 `/device/telemetry` 的路径前缀 |
| **可选** | `EmqxMessageWrapper.java` | 消息信封字段够用就保留，不够就加业务字段 |
| **可选** | `EmqxConfig.EmqxProperties` | 默认值不符就改 |
| **不动** | `EmqxPublisher.java` | 开箱即用，无需改动 |
| **不动** | `EmqxConnectionStateListener.java` | 开箱即用，无需改动 |
| **不动** | `EmqxHealthIndicator.java` | 开箱即用，接入 Prometheus/Grafana 直接读 `/actuator/health` |

### 典型接入流程

**第一步：设备上报数据（设备 → EMQX → 你的服务）**

```java
// 在 EmqxSubscriber.init() 里订阅设备上报 Topic
subscribe("/device/+/telemetry", 1, (topic, message) -> {
    DeviceTelemetry data = parsePayload(message, DeviceTelemetry.class);
    // 你的业务逻辑：
    //   - 写入时序数据库 (TDengine / InfluxDB)
    //   - 推送 WebSocket 给前端
    //   - 触发告警规则
    //   - 写入 Kafka 给下游消费
    deviceDataService.save(data);
});
```

**第二步：下发指令给设备（你的服务 → EMQX → 设备）**

```java
// 在 Controller / Service 里注入 EmqxPublisher
@Autowired
private EmqxPublisher publisher;

public void sendCommand(String deviceId, String command) {
    publisher.publish("/device/" + deviceId + "/command", command);
}
```

**第三步：监听监控**

```bash
# 健康检查（接入 K8s liveness probe / Prometheus）
curl http://localhost:8081/actuator/health

# 检查连接状态 + 离线缓冲堆积情况
curl http://localhost:8081/api/emqx/status
# → {"connected":true, "pendingBufferSize":0, "droppedCount":0, ...}
#   pendingBufferSize 持续增长 = EMQX 连不上，需要告警
#   droppedCount > 0 = 缓冲区满了丢消息，需要扩容或排查网络
```

### 生产环境 EMQX 配置建议

```yml
emqx:
  enabled: true
  broker-url: tcp://emqx-prod.internal:1883    # 内网地址
  client-id: ${HOSTNAME:my-service}-${random.uuid}  # 每个实例唯一
  username: ${EMQX_USERNAME}                    # 从环境变量注入
  password: ${EMQX_PASSWORD}                    # 从环境变量注入
  session-expiry-interval: 7200                 # 2 小时会话保持
  keep-alive-interval: 30                       # 心跳 30 秒
  auto-reconnect: true
  enable-lwt: true                              # 遗嘱消息：服务挂了自动通知
  lwt-topic: /service/my-service/status
  lwt-payload: "OFFLINE"
  max-buffer-size: 50000                        # 离线缓冲 5 万条
```

---

## 实现原理

### 整体架构

```
┌──────────────────────────────────────────────────────┐
│                   SpringBoot 应用                      │
│                                                       │
│  ┌──────────┐    ┌──────────────┐    ┌─────────────┐ │
│  │ Controller│───>│   Publisher  │───>│             │ │
│  └──────────┘    │ (离线缓冲)    │    │  MqttClient │ │
│                  └──────────────┘    │  (Paho v5)  │ │
│  ┌──────────┐                        │             │ │
│  │Subscriber│<───────────────────────│             │ │
│  └──────────┘                        └──────┬──────┘ │
│                                             │         │
│  ┌──────────────┐                           │         │
│  │HealthIndicator│                          │         │
│  └──────────────┘                           │         │
└─────────────────────────────────────────────┼─────────┘
                                              │ MQTT 5.0
                                         ┌────▼─────┐
                                         │   EMQX    │
                                         │  Broker   │
                                         └───────────┘
```

### 连接生命周期

```
启动 ─→ EmqxConfig 创建 MqttClient ─→ connect() ─→ 订阅默认 Topic
                           │
                           ├── 断连 ─→ 消息进离线缓冲区 ─→ 触发 onDisconnectCallback
                           │
                           └── 重连 ─→ 刷新离线缓冲区 ─→ 触发 onReconnectCallback
                                                     ─→ 恢复订阅
```

### 离线缓冲机制（核心）

```
发布消息
   ├── 已连接 ─→ 直接发送 ─→ ACK / 完成
   │
   └── 未连接 ─→ 写入 LinkedBlockingQueue (有界, 默认 10000)
                    ├── 队列未满 ─→ 成功缓冲
                    └── 队列已满 ─→ poll() 丢弃最旧 ─→ droppedCount++
                          
重连成功 ─→ flushBuffer() ─→ 逐条重发 ─→ flushedCount++
```

这保证弱网或 EMQX 短暂不可用时消息不丢失。

### MQTT 5.0 相较 3.x 的改进

| 特性 | MQTT 3.1.1 | MQTT 5.0 |
|------|-----------|----------|
| 会话管理 | Clean Session (布尔) | Session Expiry Interval (秒级精细控制) |
| 错误信息 | 纯数字 Reason Code | Reason String (人类可读的错误字符串) |
| 消息过期 | 不支持 | Message Expiry Interval (过期自动丢弃) |
| 载荷格式 | 不支持 | Payload Format Indicator (标记是 UTF-8 还是二进制) |
| 用户属性 | 不支持 | User Properties (自定义键值对元数据) |
| 共享订阅 | EMQX 扩展 | 标准协议内置 `$share/group/topic` |
| 增强认证 | 不支持 | AUTH 包支持 OAuth/JWT/SCRAM 等 |

本模块使用 **Eclipse Paho MQTT v5 客户端**，充分利用 MQTT 5.0 的 Reason String、Session Expiry 等特性。

---

## EMQX 相比其他 MQTT 中间件的优势

### vs 阿里云 MQTT / 华为云 IoT

| 维度 | EMQX | 云厂商 MQTT |
|------|------|------------|
| 部署方式 | 自建 / 私有云 / 混合云 | 仅公有云 |
| 供应商锁定 | 无，开源协议 | 强绑定云厂商生态 |
| 认证方式 | 用户名密码 / JWT / 客户端证书 / RBAC | 复杂的密钥签名（如阿里云 HMAC-SHA1） |
| 协议版本 | MQTT 5.0 + 3.1.1 双协议 | 多为 3.1.1 |
| 成本 | 开源免费 | 按连接数/消息量计费 |
| 数据安全 | 数据不出自己的网络 | 数据经云厂商 |
| 规则引擎 | 内置 40+ 数据桥接 | 需配合云产品 |
| 离线环境 | 支持（内网/断网部署） | 不支持 |

**适用场景**：私有化部署、数据合规、大规模自建 IoT 平台。

### vs Mosquitto

| 维度 | EMQX | Mosquitto |
|------|------|-----------|
| 架构 | 分布式集群（多节点水平扩展） | 单机（无原生集群） |
| 性能 | 单节点百万级并发连接 | 数万级 |
| 管理界面 | Web Dashboard + REST API | 无 |
| 规则引擎 | 内置（Kafka/Redis/PG 等 40+ 种） | 无 |
| MQTT 5.0 | 完整支持 | 部分支持 |
| 企业功能 | RBAC / 审计 / 速率限制 / 黑名单 | 无 |
| 社区 | 全球最大 MQTT 开源社区 | 轻量级实现 |

**适用场景**：EMQX 适合生产级 IoT 平台，Mosquitto 适合嵌入式、边缘单机场景。

### vs HiveMQ

| 维度 | EMQX | HiveMQ |
|------|------|--------|
| 开源 | Apache 2.0 开源 | 商业授权（Community 版受限） |
| 中文社区 | 活跃（EMQX 团队在杭州） | 较弱 |
| 文档 | 中英文完整 | 英文为主 |
| 扩展机制 | 插件 + Hooks + 规则引擎 | Extension SDK (Java) |
| 性能 | 相近（均为 Erlang/Java 顶级实现） | 相近 |

**适用场景**：国内团队优先 EMQX，海外/已购买 HiveMQ 许可的场景选 HiveMQ。

### 一张表总结

```
                    开源     集群     MQTT5    规则引擎    中文支持    适合规模
EMQX                ✅       ✅       ✅        ✅ (40+)     ✅        大/中/小
阿里云 MQTT          ❌       ✅       ❌        ⚠️ (云联动)   ✅        任意
Mosquitto           ✅       ❌       ⚠️        ❌           ❌         小
HiveMQ              ⚠️       ✅       ✅        ⚠️           ❌         大/中
ActiveMQ/RabbitMQ   ✅       ✅       ❌        ❌           ⚠️         (非专业 IoT)
```

**结论**：如果你在选型 MQTT 中间件，自建场景首选 EMQX —— 开源免费、性能强悍、生态完整、中文友好。

---

## REST API 速查表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/emqx/publish?topic=&payload=&qos=&retained=` | 发布字符串消息 |
| POST | `/api/emqx/device/{deviceId}/telemetry` | 发布设备遥测（JSON） |
| POST | `/api/emqx/publish/batch?topic=&count=` | 批量发布（压测） |
| POST | `/api/emqx/subscribe?topicFilter=&qos=` | 动态订阅 |
| DELETE | `/api/emqx/subscribe/{topicFilter}` | 取消订阅 |
| GET | `/api/emqx/subscriptions` | 当前订阅列表 |
| GET | `/api/emqx/status` | 连接状态 + 缓冲区统计 |
| GET | `/actuator/health` | 健康检查（含 EMQX 状态） |
