# Spring Boot RabbitMQ 开箱即用模板 —— 消息队列全场景覆盖

> 每次新项目都要从零配 RabbitMQ？这个模块提供了 **Direct / Fanout / Topic 三种交换机 + 死信队列 + 延迟消息 + 手动 ACK** 的完整封装，所有代码可以直接拷贝到项目中使用。

## 目录

- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [配置详解](#配置详解)
- [四种交换机详解](#四种交换机详解)
- [文件清单与职责](#文件清单与职责)
- [核心概念](#核心概念)
  - [消息流转过程](#消息流转过程)
  - [ACK 机制](#ack-机制)
  - [死信队列](#死信队列)
  - [延迟消息](#延迟消息)
- [使用指南](#使用指南)
  - [如何发送消息](#如何发送消息)
  - [如何消费消息](#如何消费消息)
  - [完整示例（订单创建 → 异步处理）](#完整示例订单创建--异步处理)
- [常见场景示例](#常见场景示例)
- [常见问题](#常见问题)

---

## 架构概览

```
 ┌──────────────────────────┐
 │      Producer            │
 │  ┌─────────────────┐     │
 │  │ MessagePublisher │     │   convertAndSend(exchange, routingKey, message)
 │  └────────┬─────────┘     │
 └───────────┼───────────────┘
             │
             ▼
      ┌──────────────┐
      │   Exchange   │  ← Direct / Fanout / Topic（按 routingKey 路由）
      └──────┬───────┘
             │
      ┌──────┴──────┐
      ▼              ▼
  ┌────────┐    ┌────────┐
  │ Queue1 │    │ Queue2 │  ← 消息持久化在队列中等待消费
  └───┬────┘    └───┬────┘
      │              │
      ▼              ▼
  ┌────────┐    ┌────────┐
  │Consumer│    │Consumer│  ← @RabbitListener + 手动 ACK
  └────────┘    └────────┘
      │ (消费失败)
      ▼
  ┌──────────────┐
  │ 死信队列      │  ← 失败消息统一处理 / 延迟消息的中转站
  └──────────────┘
```

**技术选型**：

| 组件 | 方案 | 说明 |
|------|------|------|
| 消息协议 | AMQP 0-9-1 | RabbitMQ 原生协议 |
| 客户端 | Spring AMQP (`spring-boot-starter-amqp`) | 底层自动选择 RabbitMQ Java Client |
| 序列化 | Jackson JSON (`Jackson2JsonMessageConverter`) | 对象自动序列化，消息可视化 |
| ACK 模式 | 手动 ACK (MANUAL) | 业务处理完成后显式确认，避免消息丢失 |
| 发送确认 | Publisher Confirm + Return | 发送方知道消息是否到达 Exchange / 队列 |

---

## 快速开始

### 1. RabbitMQ 环境准备

```bash
# Docker 快速启动（最简单）
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3.13-management

# 访问管理界面：http://localhost:15672  (guest/guest)
```

### 2. 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- Jackson (通常已有) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-json</artifactId>
</dependency>
```

### 3. 配置

最小配置（只需连上 RabbitMQ）：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated  # 发送确认
    publisher-returns: true             # 路由失败回调
    listener:
      simple:
        acknowledge-mode: manual        # 手动 ACK
        concurrency: 4
        max-concurrency: 8
        prefetch: 50
```

> 更多配置项见[配置详解](#配置详解)。

### 4. 拷贝文件

将以下文件拷贝到自己的项目（调整包路径）：

```
src/main/java/com/quick/springrabbitmq/
├── config/
│   ├── RabbitMQConfig.java          # ★ 核心配置（Template + Converter + Confirm/Return）
│   └── RabbitQueueConfig.java       # ★ 所有队列/交换机/绑定声明
├── producer/
│   └── MessagePublisher.java        # ★ 消息发布器（全部发送模式）
├── consumer/
│   ├── DirectQueueConsumer.java     # Direct 模式消费者示例
│   ├── FanoutQueueConsumer.java     # Fanout 模式消费者示例
│   ├── TopicQueueConsumer.java      # Topic 模式消费者示例
│   └── DeadLetterConsumer.java      # 死信 + 延迟消息消费者
├── model/
│   ├── MessageWrapper.java          # ★ 统一消息包装体
│   └── OrderMessage.java            # 业务消息示例
├── controller/
│   └── RabbitMQController.java      # REST 演示接口
└── util/
    └── RabbitUtil.java              # RabbitMQ 管理工具
```

> ★ 号文件每次新项目必拷贝。Consumer 和 Controller 是示例代码，请按需参考修改。

### 5. 启动测试

```bash
mvn spring-boot:run
```

用 curl 测试各个模式：

```bash
# 1. Direct 模式 —— 精确路由
curl -X POST http://localhost:8080/api/rabbit/direct

# 2. Fanout 模式 —— 广播
curl -X POST http://localhost:8080/api/rabbit/fanout

# 3. Topic 模式 —— info 消息（仅 LogQueue 消费）
curl -X POST http://localhost:8080/api/rabbit/topic/log \
  -H "Content-Type: application/json" \
  -d '{"routingKey":"log.info","content":"系统启动完成"}'

# 4. Topic 模式 —— error 消息（两个队列都消费）
curl -X POST http://localhost:8080/api/rabbit/topic/log \
  -H "Content-Type: application/json" \
  -d '{"routingKey":"log.error","content":"数据库连接失败!"}'

# 5. 延迟消息 —— 5 秒后消费
curl -X POST "http://localhost:8080/api/rabbit/delay?seconds=5"
```

观察控制台日志可以看到消费者的输出。

---

## 配置详解

### application.yml 完整配置

```yaml
spring:
  rabbitmq:
    # ─── 连接 ───
    host: localhost              # RabbitMQ 地址
    port: 5672                   # AMQP 端口（非管理界面 15672）
    username: guest
    password: guest
    virtual-host: /              # 虚拟主机（多租户隔离用）
    connection-timeout: 3000ms   # 连接超时
    requested-heartbeat: 60s     # 心跳（防止连接被中间网络设备断开）

    # ─── 多节点（addresses 和 host+port 二选一） ───
    # addresses: 10.0.0.1:5672,10.0.0.2:5672,10.0.0.3:5672

    # ─── 发送方确认 ───
    publisher-confirm-type: correlated  # SIMPLE | CORRELATED | NONE
    publisher-returns: true             # 消息无法路由到队列时触发 ReturnCallback

    # ─── 消费方 ───
    listener:
      type: simple
      simple:
        acknowledge-mode: manual        # NONE(自动) | AUTO(异常时nack) | MANUAL(手动)
        concurrency: 4                  # 最小消费者线程数
        max-concurrency: 8              # 最大消费者线程数（积压时动态扩展）
        prefetch: 50                    # 每次从队列拉取的消息数（调大提升吞吐，调小分配更公平）
        retry:
          enabled: true
          max-attempts: 5               # 最大投递次数（含首次）
          initial-interval: 1000ms      # 首次重试间隔
          multiplier: 2                 # 间隔倍增：1s → 2s → 4s → 8s
          max-interval: 10000ms         # 最大重试间隔
        default-requeue-rejected: false # 重试耗尽后不重新入队（进死信）
```

### 配置要点说明

| 配置项 | 推荐值 | 原因 |
|--------|--------|------|
| `acknowledge-mode: manual` | 必选 | 手动 ACK 保证业务处理完成后再确认，防止消息丢失 |
| `prefetch: 50` | 按需调整 | 太小 → 吞吐量低；太大 → 消息分配不均（快的消费者分更多） |
| `concurrency: 4-8` | 按 CPU 核数 | 消费者是 CPU 密集型则少开，IO 密集型多开 |
| `default-requeue-rejected: false` | 推荐 false | true 会导致失败消息无限重试死循环 |
| `publisher-confirm-type: correlated` | 推荐 | 异步确认，发送方感知消息是否到达 Exchange |

---

## 四种交换机详解

### Direct Exchange —— 精确匹配（最常用）

```
                 direct.exchange
                      │
         routingKey ──┴── routingKey
         "order"           "payment"
              │                  │
              ▼                  ▼
        direct.queue       (另一个队列)
```

**规则**：Routing Key 完全匹配才路由。**最常用**。

```java
// 发送（指定 routingKey）
publisher.sendDirect("order.create", orderMessage);

// 消费（绑定时指定 routingKey）
@RabbitListener(queues = "direct.queue")
public void handle(MessageWrapper<?> msg) { ... }
```

### Fanout Exchange —— 广播

```
                 fanout.exchange
                 /      |      \
                ▼       ▼       ▼
           queue1   queue2   queue3
```

**规则**：忽略 Routing Key，消息发送到**所有**绑定的队列。

**典型场景**：缓存刷新通知、配置变更广播。

```java
// 发送
publisher.sendFanout(Map.of("event", "cache.refresh"));

// 多个消费者各自监听自己的队列，都会收到同一条消息
```

### Topic Exchange —— 通配符过滤

```
                    topic.exchange
                    /           \
         "log.#"    /             \   "log.error"
                   ▼               ▼
          topic.queue.log   topic.queue.error
          (全部日志)         (仅错误日志)
```

**规则**：Routing Key 按 `.` 分段匹配。
- `*` 匹配**刚好一个**词（如 `order.*` 匹配 `order.create`、`order.pay`，不匹配 `order.create.sync`）
- `#` 匹配**零个或多个**词（如 `log.#` 匹配 `log`、`log.info`、`log.error.db`）

**典型场景**：日志分级处理、按地域/类型路由 IoT 消息。

```java
// 发送 log.error → 两个队列都收到
publisher.sendTopic("log.error", errorData);

// 发送 log.info → 只有 logQueue 收到
publisher.sendTopic("log.info", infoData);
```

### Headers Exchange（模板未演示）

根据消息 Header 键值对匹配，极少使用。适用于需要非字符串路由条件的高级场景。

---

## 文件清单与职责

### config/ —— 配置层

| 文件 | 职责 | 关键配置 |
|------|------|---------|
| `RabbitMQConfig.java` | RabbitTemplate 配置 + MessageConverter + Confirm/Return 回调 | Jackson JSON 序列化、Confirm 异步确认 |
| `RabbitQueueConfig.java` | 所有队列/交换机/绑定声明、死信队列、延迟队列 | Direct / Fanout / Topic / Dead Letter / Delay 完整定义 |

### producer/ —— 消息发布

| 文件 | 职责 |
|------|------|
| `MessagePublisher.java` | 封装所有发送模式：Direct / Fanout / Topic / Delay / Priority / TTL / RPC / General |

### consumer/ —— 消息消费

| 文件 | 职责 |
|------|------|
| `DirectQueueConsumer.java` | Direct 模式消费者 + 手动 ACK/NACK 三种处理演示 |
| `FanoutQueueConsumer.java` | Fanout 广播消费者（两个队列各自监听） |
| `TopicQueueConsumer.java` | Topic 通配符消费者（log.all / log.error） |
| `DeadLetterConsumer.java` | 死信队列消费者 + 延迟消息消费者 |

### model/ —— 数据模型

| 文件 | 职责 |
|------|------|
| `MessageWrapper.java` | 统一消息信封（messageId + timestamp + source + messageType + payload） |
| `OrderMessage.java` | 业务消息 POJO 示例 |

### controller/ + util/

| 文件 | 职责 |
|------|------|
| `RabbitMQController.java` | REST 测试接口，演示所有发送模式 |
| `RabbitUtil.java` | RabbitMQ 管理工具（运行时动态声明/删除队列、查询队列状态） |

---

## 核心概念

### 消息流转过程

```
Producer                          RabbitMQ Broker                         Consumer
   │                                    │                                    │
   │ 1. convertAndSend(exchange,        │                                    │
   │    routingKey, message)            │                                    │
   │ ──────────────────────────────────▶│                                    │
   │                                    │ 2. Exchange 按 routingKey 匹配    │
   │                                    │    Binding，路由到目标队列          │
   │                                    │                                    │
   │ 3. ConfirmCallback(ack=true)       │                                    │
   │ ◀──────────────────────────────────│                                    │
   │    (消息已到达 Exchange)            │                                    │
   │                                    │                                    │
   │                                    │ 4. 消息持久化到磁盘                  │
   │                                    │                                    │
   │                                    │ 5. Push 消息给 Consumer             │
   │                                    │ ──────────────────────────────────▶│
   │                                    │                                    │ 6. 处理业务逻辑
   │                                    │                                    │
   │                                    │ 7. basicAck(deliveryTag)           │
   │                                    │ ◀──────────────────────────────────│
   │                                    │    (消息从队列中删除)                │
```

### ACK 机制

**为什么必须手动 ACK？**

自动 ACK 模式下，消息从队列取出的一瞬间就被删除了。如果消费者处理过程中崩溃，消息就丢了。

手动 ACK 只有三种操作：

```
basicAck(deliveryTag, false)
  → 确认成功，消息从队列删除
  → 场景：业务处理正常完成

basicNack(deliveryTag, false, requeue=false)
  → 消费失败，不重新入队 → 消息删除或进死信
  → 场景：业务异常（金额不合法、数据校验失败），没必要重试

basicNack(deliveryTag, false, requeue=true)
  → 消费失败，消息回到队列等待重新投递
  → 场景：临时故障（网络超时、DB 短暂不可用），重试可能成功
  → ⚠️ 注意：容易死循环，务必设置最大重试次数！
```

**本模块的实现**（见 `DirectQueueConsumer.java`）：

```java
@RabbitListener(queues = RabbitQueueConfig.DIRECT_QUEUE, ackMode = "MANUAL")
public void handle(MessageWrapper<?> wrapper, Message message, Channel channel) {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
        processBusinessLogic(wrapper);
        channel.basicAck(deliveryTag, false);          // 成功 → ACK
    } catch (BusinessException e) {
        channel.basicNack(deliveryTag, false, false);  // 业务异常 → 不重试
    } catch (Exception e) {
        channel.basicNack(deliveryTag, false, true);   // 系统异常 → 重试
    }
}
```

### 死信队列

消息在以下三种情况下会变成"死信"并进入死信队列：

| 触发条件 | 说明 |
|---------|------|
| `basicNack(requeue=false)` 或 `basicReject(requeue=false)` | 消费者主动拒绝且不重新入队 |
| 消息 TTL 过期 | 消息在队列中存活时间超过了 TTL |
| 队列长度超限 | 队列消息数达到 `x-max-length` 上限，溢出的最老消息 |

**死信处理策略**（见 `DeadLetterConsumer.java`）：

```java
@RabbitListener(queues = "dead.letter.queue")
public void handleDeadLetter(MessageWrapper<?> wrapper, Message message, Channel channel) {
    // 1. 获取原始消息的元信息（原始队列、路由键、死亡原因）
    String origQueue = message.getMessageProperties().getHeader("x-first-death-queue");
    String deathReason = message.getMessageProperties().getHeader("x-first-death-reason");

    // 2. 记录到数据库
    // deadLetterRepository.save(...);

    // 3. 发送告警
    // alertService.send("RabbitMQ 死信", ...);

    channel.basicAck(deliveryTag, false);
}
```

### 延迟消息

本模块使用 **TTL + 死信** 实现延迟消息（不依赖 RabbitMQ 插件）：

```
发送消息带 5s TTL
      │
      ▼
┌─────────────┐   TTL 到期    ┌──────────────────┐
│ delay.queue │ ─────────────▶│ delay.process.queue│
│ (无消费者)   │  成为死信      │ (真正消费)         │
└─────────────┘               └──────────────────┘
```

```java
// 发送：5 秒后消费
publisher.sendWithDelay(Map.of("task", "remindOrder"), 5000);

// 消费：监听 delay.process.queue
@RabbitListener(queues = RabbitQueueConfig.DELAY_PROCESS_QUEUE)
public void handleDelayedMessage(...) {
    // 5 秒后触发
}
```

**注意**：此实现支持不同消息有不同延迟时间（通过 `expiration` 设单条消息 TTL）。如果所有消息延迟时间一致，建议直接在队列声明时设置 `x-message-ttl`，性能更好。

> 如果安装了 `rabbitmq_delayed_message_exchange` 插件，可以用官方的延迟交换机，更优雅。本模块的 TTL+死信 方案不依赖插件，兼容性更好。

---

## 使用指南

### 如何发送消息

```java
@Service
public class OrderService {

    @Autowired
    private MessagePublisher publisher;

    // 方式 1：通用发送（最灵活）
    public void createOrder(OrderMessage order) {
        publisher.send("direct.exchange", "order.create", order);
    }

    // 方式 2：Direct 快捷发送
    public void payOrder(OrderMessage order) {
        publisher.sendDirect("order.pay", order);
    }

    // 方式 3：广播消息
    public void notifyCacheRefresh() {
        publisher.sendFanout(Map.of("event", "cache.refresh", "time", Instant.now()));
    }

    // 方式 4：延迟消息（30 分钟后检查订单是否支付）
    public void scheduleOrderCheck(Long orderId) {
        publisher.sendWithDelay(Map.of("orderId", orderId), 30 * 60 * 1000);
    }

    // 方式 5：优先级消息（VIP 用户优先处理）
    public void vipOrder(OrderMessage order) {
        publisher.sendWithPriority("direct.exchange", "order.create", order, 10);
    }
}
```

### 如何消费消息

```java
@Slf4j
@Component
public class OrderConsumer {

    @RabbitListener(queues = "direct.queue", ackMode = "MANUAL")
    public void handleOrder(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // 1. 从 wrapper 中取实际业务对象
            OrderMessage order = (OrderMessage) wrapper.getPayload();

            // 2. 业务处理
            orderBusinessLogic(order);

            // 3. 确认消费
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("订单处理失败: {}", wrapper.getMessageId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

### 完整示例（订单创建 → 异步处理）

**步骤 1**：定义业务消息

```java
@Data
@Builder
public class OrderMessage implements Serializable {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
}
```

**步骤 2**：发送方

```java
@RestController
public class OrderController {

    @Autowired
    private MessagePublisher publisher;

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        // 1. 保存订单到数据库（状态 = CREATED）
        Order order = orderService.create(request);

        // 2. 构建消息
        OrderMessage msg = OrderMessage.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .build();

        // 3. 发送到 MQ，异步处理（减库存、发短信、记日志...）
        publisher.sendDirect("order.create", msg);

        return ResponseEntity.ok(Map.of("orderId", order.getId()));
    }
}
```

**步骤 3**：消费方

```java
@Component
public class OrderAsyncProcessor {

    @RabbitListener(queues = "direct.queue", ackMode = "MANUAL")
    public void processOrder(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        OrderMessage order = (OrderMessage) wrapper.getPayload();

        try {
            // 异步处理：减库存、发积分、发短信...
            inventoryService.deduct(order);
            pointsService.addPoints(order.getUserId(), order.getAmount());
            smsService.sendOrderConfirm(order.getUserId(), order.getOrderNo());

            channel.basicAck(deliveryTag, false);
        } catch (InsufficientInventoryException e) {
            // 库存不足 → 不需要重试
            orderService.markFailed(order.getOrderId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            // 其他异常 → 重回队列
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

---

## 常见场景示例

### 场景 1：订单超时取消（延迟消息）

```java
// 创建订单时，发送 30 分钟延迟消息
@PostMapping("/orders")
public Result createOrder(@RequestBody OrderRequest req) {
    Order order = orderService.create(req);

    // 30 分钟后检查
    publisher.sendWithDelay(Map.of("orderId", order.getId()), 30 * 60 * 1000);

    return Result.ok(order.getId());
}

// DelayConsumer 收到后检查支付状态
@RabbitListener(queues = RabbitQueueConfig.DELAY_PROCESS_QUEUE, ackMode = "MANUAL")
public void handleDelay(MessageWrapper<?> wrapper, Message msg, Channel channel) throws Exception {
    Long orderId = (Long) ((Map) wrapper.getPayload()).get("orderId");
    Order order = orderService.getById(orderId);
    if ("CREATED".equals(order.getStatus())) {
        orderService.cancel(orderId);  // 超时未支付，取消
    }
    channel.basicAck(msg.getMessageProperties().getDeliveryTag(), false);
}
```

### 场景 2：幂等消费（防止重复处理）

```java
@Component
public class IdempotentConsumer {

    @Autowired
    private RedisUtil redisUtil;  // 使用 springboot-redis 模块

    @RabbitListener(queues = "direct.queue", ackMode = "MANUAL")
    public void handle(MessageWrapper<?> wrapper, Message msg, Channel channel) throws Exception {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        String idempotentKey = "mq:idempotent:" + wrapper.getMessageId();

        // Redis SETNX 保证幂等
        Boolean firstTime = redisUtil.setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        if (!firstTime) {
            log.warn("重复消息，跳过: {}", wrapper.getMessageId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            processBusiness(wrapper);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 处理失败，删除幂等标记，允许重试
            redisUtil.delete(idempotentKey);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

### 场景 3：消息发送失败补偿

```java
// RabbitMQConfig 中注册的 ConfirmCallback 已经做了日志记录。
// 需要补偿重发时，配合数据库做定时任务：

@Component
public class MessageCompensationTask {

    @Autowired
    private MessagePublisher publisher;
    @Autowired
    private MessageLogMapper messageLogMapper;

    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void retryFailedMessages() {
        // 查询 confirm 失败的消息记录
        List<MessageLog> failed = messageLogMapper.selectFailedMessages();
        for (MessageLog log : failed) {
            if (log.getRetryCount() >= 10) {
                // 超过最大重试，标记为最终失败，人工处理
                messageLogMapper.markAsFinalFailed(log.getId());
                continue;
            }
            // 重发
            publisher.send(log.getExchange(), log.getRoutingKey(), log.getPayload());
            messageLogMapper.incrementRetryCount(log.getId());
        }
    }
}
```

### 场景 4：消息积压监控

```java
@Component
public class QueueMonitor {

    @Autowired
    private RabbitUtil rabbitUtil;

    @Scheduled(fixedDelay = 30000)
    public void monitor() {
        int count = rabbitUtil.getQueueMessageCount("direct.queue");
        if (count > 10000) {
            // 发送告警：队列积压严重
            log.warn("⚠️ direct.queue 消息积压: {} 条", count);
            // alertService.send("RabbitMQ 积压告警", "direct.queue 积压 " + count + " 条");
        }
    }
}
```

### 场景 5：并发消费与动态扩展

```yaml
# application.yml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 2              # 启动时 2 个消费者
        max-concurrency: 10         # 高峰期自动扩展到 10 个
        prefetch: 100               # 每次预取 100 条
```

当队列消息积压时，RabbitMQ Listener Container 会自动增加消费者线程（从 2 → 10），处理速度线性提升。积压消除后线程会自动回收。

---

## 常见问题

### Q1：消息丢失了怎么排查？

消息丢失有三个地方：

| 丢失环节 | 原因 | 解决方法 |
|---------|------|---------|
| **发送方** | 发完不管 | `publisher-confirm-type: correlated` + ConfirmCallback |
| **Broker** | 队列非持久化 | 队列和消息都设置 `durable=true` |
| **消费方** | 自动 ACK | `acknowledge-mode: manual`，业务处理完再 ACK |

本模板三个环节都已覆盖。

### Q2：什么时候用 Direct，什么时候用 Topic？

- **Direct**：一对一精确路由，大多数场景用这个就够了
- **Topic**：一对多按规则分发，比如日志按级别分流到不同消费者
- **Fanout**：一对多广播，所有绑定队列都收到

简单场景用 Direct + 一个队列 + routing key 区分消息类型即可，不必一上来就搞复杂路由。

### Q3：消息消费失败了会一直重试吗？

不会。本模板配置了重试次数上限（`max-attempts: 5`），且 `default-requeue-rejected: false`。重试耗尽后：
1. 消息被 Reject（不重新入队）
2. 如果队列绑定了死信交换机，消息进入死信队列
3. `DeadLetterConsumer` 处理死信（入库 + 告警）

### Q4：怎么保证消息的顺序性？

RabbitMQ 单队列内消息是 FIFO 有序的。如果要求严格顺序：
- 同一业务对象（如同一个订单）的消息发到同一个队列
- 单队列只用单个消费者（`concurrency: 1`）
- 关闭 prefetch 或不批量消费

但**不建议强依赖 MQ 的消息顺序**，用数据库版本号或状态机来控制更可靠。

### Q5：延迟消息的精度如何？

TTL + 死信方案：精度取决于 RabbitMQ 的消息 TTL 检查机制，大约有几十毫秒到几秒的误差。如果对精度要求高（如秒杀倒计时），建议用 Redis + Scheduled + MQ 结合的方式。

### Q6：生产环境 RabbitMQ 怎么搭建？

- **单机**：Docker 启动，适合开发 / 小项目
- **集群**：3 节点，使用镜像队列（Mirrored Queue）或 Quorum Queue
- **运维**：管理界面 http://ip:15672，配置 Grafana + Prometheus 监控

### Q7：为什么不能用 `system.out` 观察 Consumer 是否收到消息？

Spring AMQP 默认在非 Web 线程中执行 `@RabbitListener` 方法。某些 IDE 会吞掉非主线程的 stdout。建议用 `log.info()` 并查看日志文件，或者在 RabbitMQ 管理界面查看队列的消费速率。

### Q8：消息体大小限制是多少？

RabbitMQ 单条消息默认最大约 128MB（实际受内存限制）。**实践建议单条不超过 10MB**，大文件走 OSS 然后传 URL。

---

## 版本兼容

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Spring AMQP | 3.2.x（随 Boot 管理） |
| RabbitMQ Server | 3.12 / 3.13 / 4.0 |
| Java | 17+ |
