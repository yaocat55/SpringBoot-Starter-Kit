# Spring Boot 阿里云 MQTT 接入指南

Spring Boot 3.5.x + Eclipse Paho 接入阿里云微消息队列 MQTT 版，覆盖 QoS 0/1/2、持久会话、遗嘱消息、通配符订阅、断线缓冲、健康检查。**复制即用，5 分钟接入。**

---

## 目录

- [接入指南](#接入指南)
  - [Step 1：开通阿里云 MQTT 服务](#step-1开通阿里云-mqtt-服务)
  - [Step 2：引入依赖](#step-2引入依赖)
  - [Step 3：配置文件](#step-3配置文件)
  - [Step 4：拷贝代码](#step-4拷贝代码)
  - [Step 5：启动验证](#step-5启动验证)
  - [Step 6：对接生产](#step-6对接生产)
- [核心 API 速查](#核心-api-速查)
- [MQTT 核心概念](#mqtt-核心概念)
  - [Topic 与通配符](#1-topic-与通配符)
  - [QoS 三级质量](#2-qos-三级质量)
  - [持久会话（Persistent Session）](#3-持久会话persistent-session)
  - [遗嘱消息（LWT）](#4-遗嘱消息lwt)
  - [保留消息（Retained Message）](#5-保留消息retained-message)
- [阿里云认证详解](#阿里云认证详解)
  - [认证流程](#认证流程)
  - [签名算法](#签名算法)
  - [GroupID 与 ClientID 命名规范](#groupid-与-clientid-命名规范)
  - [权限最小化](#权限最小化)
- [配置项速查](#配置项速查)
- [断线缓冲机制](#断线缓冲机制)
- [常见场景](#常见场景)
- [FAQ](#faq)
- [文件职责一览](#文件职责一览)

---

## 接入指南

### Step 1：开通阿里云 MQTT 服务

1. 登录 [阿里云控制台](https://iot.console.aliyun.com/mqtt) → **微消息队列 MQTT 版**
2. 创建实例，记下两个关键信息：
   - **接入点**：`tcp://post-cn-xxxx.mqtt.aliyuncs.com:1883`（TCP）或 `ssl://post-cn-xxxx.mqtt.aliyuncs.com:8883`（TLS）
   - **实例 ID**：`post-cn-xxxx`
3. 在实例下创建 **Group ID**（如 `GID_SMART_HOME`）和 **Client ID**（如 `device-001`）
4. RAM 控制台创建 AccessKey，授予 `AliyunMQFullAccess` 权限

### Step 2：引入依赖

```xml
<!-- Eclipse Paho MQTT 客户端 -->
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
</dependency>

<!-- 健康检查（可选，对接监控用） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Step 3：配置文件

```yaml
aliyun:
  mqtt:
    # ---- 开关：开发环境 false，需要 MQTT 时改为 true ----
    enabled: true

    # ---- 连接参数（从 Step 1 拿到） ----
    broker-url: tcp://post-cn-xxxx.mqtt.aliyuncs.com:1883
    client-id: GID_SMART_HOME@@@device-001        # GroupID@@@ClientID
    instance-id: post-cn-xxxx
    access-key: ${ALIYUN_ACCESS_KEY}              # 环境变量注入，不要硬编码
    secret-key: ${ALIYUN_SECRET_KEY}

    # ---- 会话参数 ----
    clean-session: false          # false=持久会话，离线消息不丢
    connection-timeout: 30
    keep-alive-interval: 60

    # ---- 自动重连 ----
    auto-reconnect: true
    reconnect-interval-ms: 5000

    # ---- 遗嘱消息（生产必备，设备异常断线时自动通知） ----
    enable-lwt: true
    lwt-topic: device/offline
    lwt-message: "{\"deviceId\":\"device-001\",\"status\":\"offline\"}"
    lwt-qos: 1

# Actuator 健康端点（可选）
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

> **不要硬编码 AccessKey**，用环境变量：
> ```bash
> export ALIYUN_ACCESS_KEY=LTAI5txxxxxxxx
> export ALIYUN_SECRET_KEY=xxxxxxxxxxxxxxxx
> ```

### Step 4：拷贝代码

把 `springboot-aliyun-mqtt/src/main/java/com/quick/springbootaliyunmqtt/` 下所有文件拷贝到你的项目，按需改包名。核心文件：

| 文件 | 职责 | 是否需要改 |
|------|------|-----------|
| `config/MqttConfig.java` | MQTT 客户端配置 + 阿里云签名 | 不需要 |
| `config/MqttConnectionStateListener.java` | 连接/断线/重连事件 | 不需要 |
| `config/MqttHealthIndicator.java` | Actuator 健康检查 | 不需要 |
| `publisher/MqttPublisher.java` | 发布消息 + 断线缓冲 | 不需要 |
| `subscriber/MqttSubscriber.java` | 订阅消息 | **需要改订阅的 Topic** |
| `model/MqttMessageWrapper.java` | 通用消息包装器 | 不需要 |
| `model/OrderMessage.java` | 演示业务模型 | **替换成你的业务模型** |
| `controller/MqttController.java` | REST 测试接口 | 可以删掉 |
| `util/MqttUtil.java` | 同步/异步发送工具 | 不需要 |

最小接入只需 3 个类：**MqttConfig**、**MqttPublisher**、**MqttSubscriber**。

#### 发送消息示例

```java
@Component
@RequiredArgsConstructor
public class YourService {

    private final MqttPublisher mqttPublisher;

    public void sendCommand(String deviceId, String command) {
        String topic = "/device/" + deviceId + "/command";

        // QoS 1：至少送达一次，适合控制指令
        mqttPublisher.publish(topic, "{\"command\":\"" + command + "\"}", 1, false);

        // 或发送 Java 对象，自动序列化为 JSON
        MqttMessageWrapper<YourData> wrapper = MqttMessageWrapper.of("COMMAND", yourData);
        mqttPublisher.publishAsJson(topic, wrapper);
    }
}
```

#### 订阅消息示例

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class YourSubscriber {

    private final MqttSubscriber mqttSubscriber;

    @PostConstruct
    public void init() {
        // 订阅所有设备的 report 消息
        mqttSubscriber.subscribe("/device/+/report", 1, (topic, message) -> {
            String deviceId = topic.split("/")[2];
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("收到设备上报: deviceId={}, data={}", deviceId, payload);
            // 你的业务处理 ...
        });

        // 订阅设备离线通知（配合 LWT 遗嘱消息）
        mqttSubscriber.subscribe("device/offline", 1, (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.warn("设备离线: {}", payload);
            // 更新设备状态 + 触发告警
        });
    }
}
```

### Step 5：启动验证

```bash
mvn spring-boot:run
```

看到以下日志说明接入成功：
```
MQTT 首次连接成功: serverURI=tcp://post-cn-xxxx.mqtt.aliyuncs.com:1883
MQTT 订阅初始化完成: topics=[/device/+/report, device/offline]
```

验证：
```bash
# 健康检查
curl http://localhost:8080/actuator/health
# → mqtt.status: UP

# 连接状态 + 缓冲统计
curl http://localhost:8080/api/mqtt/status
# → connected: true
```

### Step 6：对接生产

上线前检查：

**1. 鉴权收窄** — 用 RAM 子账号 + 最小权限，不要用主账号 AK：

```json
{
  "Statement": [{
    "Action": ["mq:Pub", "mq:Sub"],
    "Resource": "acs:mq:*:*:post-cn-xxxx/GID_SMART_HOME/*",
    "Effect": "Allow"
  }]
}
```

**2. 启用遗嘱消息** — `enable-lwt: true`，设备异常断线时自动通知

**3. 健康检查接入监控** — Prometheus / Zabbix 采 `/actuator/health`，判断 `mqtt.status == UP`

**4. 监控缓冲积压**：

```java
@Scheduled(fixedDelay = 60000)
public void checkBuffer() {
    long pending = mqttPublisher.getPendingCount();
    if (pending > 100) log.warn("MQTT 缓冲积压: {} 条", pending);
}
```

**5. Topic 设计规范**：

```
/product/{deviceId}/event      设备事件（QoS 0，高频上报）
/product/{deviceId}/status     设备状态（QoS 1 + Retained）
/product/{deviceId}/command    下发指令（QoS 1）
/product/{deviceId}/ota        固件升级（QoS 2）
```

---

## 核心 API 速查

### MqttPublisher —— 发送

```java
// QoS 0：最多一次，无确认，适合高频传感器
publisher.publish(topic, payload, 0, false);

// QoS 1：至少一次，有确认，适合控制指令（默认）
publisher.publish(topic, payload);                              // 默认 QoS=1
publisher.publish(topic, payload, 1, false);

// QoS 2：恰好一次，四次握手，适合计费/支付
publisher.publish(topic, payload, 2, false);

// 保留消息：新订阅者立即可收到最后一条
publisher.publish(topic, payload, 1, true);

// 发送 Java 对象，自动 JSON 序列化
publisher.publishAsJson(topic, yourObject);
publisher.publishAsJson(topic, yourObject, 1, false);

// 缓冲状态查询
long pending = publisher.getPendingCount();   // 待补发消息数
long dropped = publisher.getDroppedCount();   // 缓冲满丢弃数
long flushed = publisher.getFlushedCount();   // 累计补发成功数
```

### MqttSubscriber —— 订阅

```java
// 精确订阅
subscriber.subscribe("/device/demo/event", 1, (topic, msg) -> { ... });

// 单层通配符 "+"：匹配所有设备的 event
subscriber.subscribe("/device/+/event", 1, (topic, msg) -> { ... });

// 多层通配符 "#"：匹配 /device 下所有消息
subscriber.subscribe("/device/#", 1, (topic, msg) -> { ... });

// 动态取消订阅
subscriber.unsubscribe("/device/+/event");

// 查看当前订阅列表
Set<String> topics = subscriber.getSubscribedTopics();
```

### MqttUtil —— 工具

```java
// 同步发送，阻塞等待 Broker 确认（10 秒超时）
util.publishAndWait(topic, payload, 1, false, 10, TimeUnit.SECONDS);

// 异步发送，返回 CompletableFuture
util.publishAsync(topic, payload, 1, false)
    .thenAccept(msgId -> log.info("发送成功: msgId={}", msgId));

// 连接状态
boolean connected = util.isConnected();
int pendingTokens = util.getPendingDeliveryTokens();   // 积压消息数
```

---

## MQTT 核心概念

> 没有 MQTT 基础的新同学看这里。如果已经了解 MQTT，可以跳到[阿里云认证详解](#阿里云认证详解)。

### 1. Topic 与通配符

MQTT 的 Topic 是一个用 `/` 分隔的层级字符串，不像 Kafka/RabbitMQ 那样需要提前创建，发布者直接往 Topic 发消息即可。

```
Topic 示例:
  /device/sensor-001/temperature
  /device/sensor-001/humidity
  /device/sensor-002/temperature

通配符订阅:
  "+" — 匹配单层
    /device/+/temperature  → 匹配 sensor-001 和 sensor-002 的 temperature

  "#" — 匹配多层（只能放在末尾）
    /device/#  → 匹配 /device 下的所有 Topic
```

> 阿里云 MQTT 的 Topic 必须以 `/` 开头。

### 2. QoS 三级质量

这是 MQTT 区别于 Kafka/RabbitMQ 的核心特性——每条消息可以单独选择可靠性级别。

```
QoS 0 — 最多一次（At most once）
  Publisher ──PUBLISH──▶ Broker ──PUBLISH──▶ Subscriber
  无确认，消息可能丢失
  适用：传感器高频上报（温度/湿度/GPS），丢了也有下一条

QoS 1 — 至少一次（At least once）
  Publisher ──PUBLISH──▶ Broker ──PUBLISH──▶ Subscriber
             ◀──PUBACK──           ◀──PUBACK──
  有确认，可能重复（重传导致）
  适用：控制指令（开关/阀门/继电器），必须送达

QoS 2 — 恰好一次（Exactly once）
  PUBLISH → PUBREC → PUBREL → PUBCOMP（四次握手）
  无重复无丢失
  适用：计费/支付/OTA 升级，一条都不能丢不能重
```

**QoS 降级规则：** 消息最终的 QoS 取"发布者指定的 QoS"和"订阅者指定的 QoS"中的**较小值**。比如发布者用 QoS 2、订阅者用 QoS 1，则实际投递为 QoS 1。

### 3. 持久会话（Persistent Session）

用 `clean-session` 控制：

```
clean-session = false（持久会话）:
  客户端离线时 Broker 会保留：
    1. 该客户端的所有订阅关系
    2. 所有 QoS ≥ 1 且尚未确认的消息
  客户端重连后：
    1. 订阅关系自动恢复 → 不用重新 subscribe
    2. 离线期间的消息批量推送 → 一条不漏

clean-session = true（新会话）:
  客户端断开 → Broker 清空所有订阅和消息
  重连 → 从头开始，收不到离线期间的消息
```

### 4. 遗嘱消息（LWT — Last Will and Testament）

设备异常断线时，Broker 自动发一条你预先设定好的消息，告诉其他人"这个设备下线了"。

```
设备正常连接时 → 向 Broker 注册 LWT：
  Topic: device/offline
  Payload: {"deviceId":"sensor-001","status":"offline"}

设备异常断线（网络中断/断电/进程 crash）→ Broker 检测心跳超时
  → Broker 自动发布 device/offline
    → 管理后台收到 → 更新设备状态为"离线" → 触发告警
```

这就是 IoT 场景下"设备离线感知"的标准做法，不用自己写心跳逻辑。

### 5. 保留消息（Retained Message）

Broker 会为每个 Topic 存储**最后一条** `retained=true` 的消息。新订阅者订阅时，Broker 立即把这条消息推给它，不需要等发布者下次发布。

```
场景:
  设备 sensor-001 上线时发一条 retained 消息:
    publish("/device/sensor-001/status", "online", retain=true)

  新接入的监控端订阅:
    subscribe("/device/+/status") → 立即收到 "online"（不用等设备下次上报）
```

---

## 阿里云认证详解

### 认证流程

```
┌──────────┐     ┌─────────────────┐     ┌──────────┐
│  客户端   │     │   阿里云 MQTT    │     │  RAM 服务 │
└────┬─────┘     └───────┬─────────┘     └────┬─────┘
     │                   │                    │
     │  1. CONNECT       │                    │
     │  userName = Signature|{AK}|{InstanceId}│
     │  password = Base64(HMAC-SHA1(ClientId, SecretKey))
     │──────────────────▶│                    │
     │                   │  2. 验证签名       │
     │                   │───────────────────▶│
     │                   │  3. 通过           │
     │                   │◀───────────────────│
     │  4. CONNACK       │                    │
     │◀──────────────────│                    │
     │                   │                    │
     │  5. PUBLISH / SUBSCRIBE（后续正常通信） │
     │◀─────────────────▶│                    │
```

### 签名算法

密码不是直接填 SecretKey，而是对 ClientId 做 HMAC-SHA1 签名后 Base64 编码：

```java
public static String generatePassword(String clientId, String secretKey) {
    Mac mac = Mac.getInstance("HmacSHA1");
    SecretKeySpec spec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
    mac.init(spec);
    byte[] raw = mac.doFinal(clientId.getBytes());
    return Base64.getEncoder().encodeToString(raw);
}
```

可以在本地验证签名是否正确：
```java
String pwd = MqttConfig.generatePassword("GID_DEFAULT@@@demo-device-001", "your-secret-key");
System.out.println(pwd);  // 对比阿里云控制台生成的密码
```

### GroupID 与 ClientID 命名规范

```
GroupID:  GID_<业务标识>
  例: GID_SMART_HOME, GID_VEHICLE, GID_MONITOR

ClientID: <GroupID>@@@<设备唯一ID>
  例: GID_SMART_HOME@@@thermometer-001
      GID_VEHICLE@@@VIN-ABC123
      GID_MONITOR@@@server-192-168-1-100
```

### 权限最小化

不要用主账号 AK，在 RAM 创建子账号，按需授权：

```json
{
  "Statement": [{
    "Action": ["mq:Pub", "mq:Sub"],
    "Resource": "acs:mq:*:*:post-cn-xxxx/GID_MY_GROUP/*",
    "Effect": "Allow"
  }]
}
```

---

## 配置项速查

`application.yml` 中 `aliyun.mqtt` 下的全部配置：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 总开关 |
| `broker-url` | String | — | 接入点，TCP `1883` / TLS `8883` |
| `client-id` | String | — | `GID_<组>@@@<设备ID>` |
| `instance-id` | String | — | 阿里云实例 ID |
| `access-key` | String | — | RAM AccessKey（支持 `${ENV}` 占位） |
| `secret-key` | String | — | RAM SecretKey |
| `clean-session` | boolean | `true` | `false`=持久会话，保留离线消息 |
| `connection-timeout` | int | `30` | 连接超时（秒） |
| `keep-alive-interval` | int | `60` | 心跳间隔（秒） |
| `auto-reconnect` | boolean | `true` | 断线自动重连 |
| `reconnect-interval-ms` | long | `5000` | 首次重连等待（毫秒） |
| `enable-lwt` | boolean | `false` | 是否开启遗嘱消息 |
| `lwt-topic` | String | `device/offline` | 遗嘱 Topic |
| `lwt-message` | String | — | 遗嘱消息体 |
| `lwt-qos` | int | `1` | 遗嘱 QoS |
| `lwt-retained` | boolean | `false` | 遗嘱是否保留 |

---

## 断线缓冲机制

```
正常时:  publish() → 直接发 Broker
断线时:  publish() → 消息入本地内存缓冲队列（容量 10000）
重连后:  自动清空缓冲 → 逐条补发
缓冲满:  丢弃最旧消息 → droppedCount + 1
```

缓冲状态通过 `GET /api/mqtt/status` 查看，或代码中调用：

```java
long pending = mqttPublisher.getPendingCount();   // 当前积压
long dropped = mqttPublisher.getDroppedCount();   // 累计丢弃
long flushed = mqttPublisher.getFlushedCount();   // 累计补发成功
```

---

## 常见场景

### 智能家居：App 控制设备

```java
// App 端发指令，QoS 1 保证送达
mqttPublisher.publish("/home/living-room/light", "{\"state\":\"on\"}", 1, false);

// 设备端订阅指令，通配符监听所有房间
mqttSubscriber.subscribe("/home/+/light", 1, (topic, msg) -> {
    String room = topic.split("/")[2];
    execute(room, new String(msg.getPayload()));
});
```

### 车联网：GPS 轨迹上报

```java
// 每 3 秒报一次，QoS 0 追求低延迟（允许偶尔丢一条）
scheduler.scheduleAtFixedRate(() -> {
    GpsData gps = new GpsData(lng, lat, speed);
    mqttPublisher.publish("/vehicle/" + vin + "/gps", gps.toJson(), 0, false);
}, 0, 3, TimeUnit.SECONDS);
```

### OTA 固件升级

```java
// QoS 2 不丢不重，cleanSession=false 保证离线设备上线后自动收到
mqttPublisher.publish("/device/" + deviceId + "/ota", otaPackage.toJson(), 2, false);
```

### 设备状态同步

```java
// 设备上线时发一条 retained 消息
mqttPublisher.publish("/device/sensor-001/status", "{\"online\":true}", 1, true);

// 新接入的监控端订阅后立即收到当前状态，不用等下次上报
mqttSubscriber.subscribe("/device/+/status", 1, (topic, msg) -> updateDashboard(topic, msg));
```

---

## FAQ

### 1. 断线期间的消息会丢吗？

不会。`MqttPublisher` 内置断线缓冲：断线时消息暂存内存队列，重连后自动补发。容量默认 10000 条，存满后丢弃最旧消息。

### 2. 怎么监控 MQTT 连接？

```bash
curl http://localhost:8080/actuator/health
# 返回 mqtt.status: UP 或 DOWN
```
接入 Prometheus / Zabbix 直接采这个端点。

### 3. 连接频繁断开重连？

- Keep Alive 太小 → 调大 `keep-alive-interval`（120-300s）
- 防火墙空闲断开 → 心跳需小于网络设备超时
- Client ID 冲突 → 同一 ID 被另一个客户端踢下线

### 4. 多实例部署怎么消费？

MQTT 默认是**广播模型**（所有订阅者都收到），需要负载均衡用共享订阅：

```java
// 同一共享组的客户端间负载均衡
mqttSubscriber.subscribe("$share/my-group/device/+/order", 1, callback);
```

### 5. MQTT 能和 Kafka/RocketMQ 一起用吗？

推荐组合：MQTT 负责设备 ↔ 云端，Kafka/RocketMQ 负责微服务间流转。

```
设备 ──MQTT──▶ 阿里云 MQTT ──规则引擎──▶ Kafka ──▶ 微服务
```

### 6. 认证失败 "Bad username or password"？

- `userName` 格式必须是 `Signature|{AK}|{InstanceId}`（竖线分隔，注意大小写）
- `password` 是对 `ClientId` 做 HMAC-SHA1 的 Base64 值，不是 SecretKey 本身
- 确认 RAM 授权包含 `mq:Pub` 和 `mq:Sub`

---

## 文件职责一览

```
springboot-aliyun-mqtt/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/quick/springbootaliyunmqtt/
    │   ├── SpringbootAliyunMqttApplication.java
    │   ├── config/
    │   │   ├── MqttConfig.java                      # 客户端配置 + 阿里云 HMAC-SHA1 签名
    │   │   ├── MqttConnectionStateListener.java     # 连接/断线/重连事件监听
    │   │   └── MqttHealthIndicator.java             # Actuator 健康检查（/actuator/health）
    │   ├── publisher/
    │   │   └── MqttPublisher.java                   # 消息发布 + 断线缓冲自动补发
    │   ├── subscriber/
    │   │   └── MqttSubscriber.java                   # 消息订阅 + 通配符 + 消息分发
    │   ├── controller/
    │   │   └── MqttController.java                   # REST 测试接口（可删除）
    │   ├── model/
    │   │   ├── MqttMessageWrapper.java               # 通用消息包装器
    │   │   └── OrderMessage.java                     # 演示业务模型（替换成你的）
    │   └── util/
    │       └── MqttUtil.java                         # 同步/异步发送工具方法
    └── resources/
        └── application.yml
```

## 版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Java | 17+ |
| Eclipse Paho | 1.2.5（MQTT 3.1.1） |
| 阿里云 MQTT | 3.x / 5.x |
