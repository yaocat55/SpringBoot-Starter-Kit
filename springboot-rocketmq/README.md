# Spring Boot RocketMQ 开箱即用模板 —— 消息队列全场景覆盖

> 每次新项目都要从零接入 RocketMQ？这个模块提供了**同步 / 异步 / 单向 / 顺序 / 延迟 / 事务**六种发送模式 + 集群消费 / 广播消费 + Tag 过滤的完整封装，所有代码可以直接拷到项目中使用。

## 目录

- [RocketMQ vs RabbitMQ vs Kafka 对比](#rocketmq-vs-rabbitmq-vs-kafka-对比)
- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [配置详解](#配置详解)
- [核心概念](#核心概念)
  - [消息模型](#消息模型)
  - [消费模式](#消费模式)
  - [发送模式](#发送模式)
  - [延迟消息](#延迟消息)
  - [事务消息](#事务消息)
  - [顺序消息](#顺序消息)
- [文件清单与职责](#文件清单与职责)
- [使用指南](#使用指南)
  - [如何发送消息](#如何发送消息)
  - [如何消费消息](#如何消费消息)
  - [完整示例（订单创建 → 异步处理）](#完整示例订单创建--异步处理)
- [常见场景示例](#常见场景示例)
- [常见问题](#常见问题)

---

## RocketMQ vs RabbitMQ vs Kafka 对比

| 维度 | RocketMQ | RabbitMQ | Kafka |
|------|----------|----------|-------|
| 开发语言 | Java | Erlang | Java/Scala |
| 消息吞吐 | 十万级/秒 | 万级/秒 | 百万级/秒 |
| 延迟消息 | **内置 18 级 / 5.x 任意秒数** | 需插件或 TTL+死信 | 不支持 |
| 事务消息 | **原生支持 Half-Message** | 不支持 | 支持（幂等写入） |
| 顺序消息 | **原生支持（同队列 FIFO）** | 单队列有序 | 单分区有序 |
| 消息回溯 | 按时间/偏移量 | 不支持 | 按偏移量 |
| 消费模式 | 集群 / 广播 | 工作队列 / 发布订阅 | 消费者组 |
| Tag 过滤 | **原生支持** | routing key ≈ tag | 无（需业务层处理） |
| 适用场景 | 电商交易、金融、IoT | 企业集成、微服务 | 大数据、日志、流处理 |

**选型建议**：电商/交易/支付/需要事务消息 → RocketMQ；企业系统/普通业务 → RabbitMQ；大数据/日志/流处理 → Kafka。

---

## 架构概览

```
 ┌──────────────────────────────────────────────────────┐
 │                     NameServer                       │
 │              (注册中心，无状态，多个组成集群)            │
 └──────┬──────────────────────────┬────────────────────┘
        │                          │
        ▼                          ▼
 ┌──────────────┐          ┌──────────────┐
 │   Broker-A   │          │   Broker-B   │    ← 消息存储 + 投递
 │  Master/Slave│          │  Master/Slave│
 └──────┬───────┘          └──────┬───────┘
        │                         │
        ▼                         ▼
 ┌──────────────────────────────────────────────────────┐
 │                    Producer                          │
 │  ┌──────────────────────────┐                        │
 │  │   MessageProducer        │  发送: topic + tag     │
 │  │   (6种发送模式)           │  destination="topic:tag"│
 │  └──────────────────────────┘                        │
 └──────────────────────────────────────────────────────┘
        │
        │  MessageQueue (分区, 类似 Kafka Partition)
        ▼
 ┌──────────────────────────────────────────────────────┐
 │                    Consumer                          │
 │  ┌──────────────────────────┐                        │
 │  │ @RocketMQMessageListener │  集群消费 / 广播消费      │
 │  │ (自动注册, Tag 过滤)       │                        │
 │  └──────────────────────────┘                        │
 └──────────────────────────────────────────────────────┘
```

**与 RabbitMQ 的核心区别**：

| 概念 | RabbitMQ | RocketMQ |
|------|----------|----------|
| 注册中心 | 无（直连 Broker） | NameServer |
| 路由机制 | Exchange + Routing Key + Queue | Topic + Tag + MessageQueue |
| 消费者注册 | @RabbitListener + 预先声明 Queue | @RocketMQMessageListener（自动创建） |
| 消息拉取 | Push（Broker 推） | 长轮询 Push（默认） |
| 延迟消息 | TTL + 死信（间接） | 原生内置（直接） |

---

## 快速开始

### 1. RocketMQ 环境准备

```bash
# Docker 快速启动（RocketMQ 5.x）
docker run -d --name rmq-namesrv -p 9876:9876 \
  apache/rocketmq:5.3.1 sh mqnamesrv

docker run -d --name rmq-broker -p 10911:10911 -p 10909:10909 \
  -e "NAMESRV_ADDR=localhost:9876" \
  apache/rocketmq:5.3.1 sh mqbroker \
  -c /home/rocketmq/rocketmq-5.3.1/conf/broker.conf

# 或者用 docker-compose 一键启动更简单
```

### 2. 依赖

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>
```

### 3. 配置

最小配置（只需连上 NameServer）：

```yaml
rocketmq:
  name-server: localhost:9876
  producer:
    group: ${spring.application.name}-producer-group
  consumer:
    group: ${spring.application.name}-consumer-group
```

### 4. 拷贝文件

将以下文件拷贝到自己的项目：

```
src/main/java/com/quick/springbootrocketmq/
├── config/
│   └── RocketMQConfig.java              # Jackson 配置
├── producer/
│   ├── MessageProducer.java             # ★ 消息生产者（6 种发送模式）
│   └── SimpleTransactionListener.java   # 事务消息监听器
├── consumer/
│   ├── OrderMessageListener.java        # 订单消费者示例（集群 + Tag 过滤）
│   └── DelayMessageListener.java        # 延迟消息消费者
├── model/
│   ├── MessageWrapper.java              # ★ 统一消息信封
│   └── OrderMessage.java               # 业务消息示例
├── controller/
│   └── RocketMQController.java         # REST 演示接口
└── util/
    └── RocketMQUtil.java                # 工具类
```

> ★ 号文件是每次新项目必拷贝的基础文件，其余为示例可参考修改。

### 5. 启动测试

```bash
mvn spring-boot:run
```

用 curl 测试：

```bash
# 1. 同步发送
curl -X POST http://localhost:8080/api/rocketmq/order/sync

# 2. 异步发送
curl -X POST http://localhost:8080/api/rocketmq/order/async

# 3. 顺序发送
curl -X POST http://localhost:8080/api/rocketmq/order/orderly

# 4. 延迟发送（5 秒后消费）
curl -X POST "http://localhost:8080/api/rocketmq/delay?seconds=5"

# 5. 事务消息
curl -X POST http://localhost:8080/api/rocketmq/order/transaction
```

---

## 配置详解

### application.yml 完整配置

```yaml
rocketmq:
  name-server: localhost:9876

  producer:
    group: ${spring.application.name}-producer-group
    send-message-timeout: 3000           # 发送超时（毫秒）
    retry-times-when-send-failed: 2      # 同步发送失败重试次数
    retry-times-when-send-async-failed: 2 # 异步发送失败重试次数
    max-message-size: 4194304            # 最大消息大小（4MB）
    check-name-server: true              # 启动时检查 NameServer 可用性
    transaction-listener: transactionListener  # 事务消息监听器 Bean 名

  consumer:
    group: ${spring.application.name}-consumer-group
    message-model: CLUSTERING            # CLUSTERING（集群） | BROADCASTING（广播）
    consume-thread-min: 10               # 最小消费线程
    consume-thread-max: 30               # 最大消费线程
    pull-batch-size: 32                  # 每次拉取消息数
    max-reconsume-times: 16              # 消费失败最大重试次数（超限进死信）
    consume-from-where: CONSUME_FROM_LAST_OFFSET  # 新消费者组从何处开始消费
```

### 消费模式对比

| 模式 | 配置值 | 行为 | 适合场景 |
|------|--------|------|---------|
| 集群消费 | `CLUSTERING`（默认） | 同组内负载均衡，一条消息仅一个消费者处理 | 订单处理、库存扣减 |
| 广播消费 | `BROADCASTING` | 同组内所有消费者都收到同一条消息 | 配置刷新、缓存清除 |

---

## 核心概念

### 消息模型

```
Topic ────────▶ MessageQueue-0 ────▶ Consumer-1  ┐
         │     MessageQueue-1 ────▶ Consumer-2  │ Consumer Group
         │     MessageQueue-2 ────▶ Consumer-3  ┘
         │
         └─── Tag: "order-created"
              Tag: "order-paid"         ← 消费端通过 selectorExpression 过滤
              Tag: "order-cancelled"
```

- **Topic** — 消息主题（一级分类，如 `order-topic`）
- **Tag** — 消息标签（二级分类，如 `order-created`、`order-paid`），消费端可据此过滤
- **MessageQueue** — 分区，类似 Kafka Partition，数量在 Broker 配置
- **Consumer Group** — 消费者组，同组分摊消息

### 消费模式

```java
// 集群消费（默认）—— 一条消息只被组内一个消费者处理
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer",
    messageModel = MessageModel.CLUSTERING
)

// 广播消费 —— 组内所有消费者都收到同一条消息
@RocketMQMessageListener(
    topic = "config-topic",
    consumerGroup = "config-broadcast",
    messageModel = MessageModel.BROADCASTING
)
```

### 发送模式

| 模式 | 方法 | 可靠性 | 吞吐 | 场景 |
|------|------|--------|------|------|
| **同步** | `syncSend()` | ★★★ 最高 | ★★ | 重要通知、交易结果 |
| **异步** | `asyncSend()` | ★★ | ★★★ | 耗时处理想立刻返回 |
| **单向** | `sendOneWay()` | ★ 不保证 | ★★★ 最高 | 日志上报、非关键埋点 |
| **顺序** | `syncSendOrderly()` | ★★★ | ★ | 订单状态流转 |
| **延迟** | `syncSendDelay()` | ★★★ | ★★ | 超时取消、定时提醒 |
| **事务** | `sendMessageInTransaction()` | ★★★ | ★ | 订单+扣库存一致性 |

### 延迟消息

**RocketMQ 5.x（本模板默认）**：支持任意秒数延迟。

```java
// 30 分钟后消费
producer.syncSendDelay("order-topic", "order-check", payload, 30 * 60);
```

**RocketMQ 4.x（兼容模式）**：仅支持 18 个固定等级。

```java
// 延迟等级 16 = 30 分钟
producer.syncSendDelayLevel("order-topic", "order-check", payload, 16);
```

```
等级映射：
1=1s   2=5s   3=10s   4=30s   5=1m    6=2m
7=3m   8=4m   9=5m    10=6m   11=7m   12=8m
13=9m  14=10m 15=20m  16=30m  17=1h   18=2h
```

### 事务消息

RocketMQ 独有特性 —— Half-Message 二阶段提交：

```
Producer                     Broker                      Consumer
   │                            │                            │
   │ 1.发送 Half-Message        │                            │
   │ ─────────────────────────▶ │ (消息暂存，消费者不可见)       │
   │                            │                            │
   │ 2.执行本地事务（操作DB）      │                            │
   │ ─────────                  │                            │
   │                            │                            │
   │ 3.本地事务成功 → COMMIT     │                            │
   │ ─────────────────────────▶ │ (消息可见)                  │
   │                            │ ──────────────────────────▶│
   │                            │   推送消息给消费者             │
   │                            │                            │
   │ 或: 本地事务失败 → ROLLBACK │                            │
   │ ─────────────────────────▶ │ (消息删除)                  │
```

本地事务执行时需要持久化状态到数据库（用于 Broker 回调回查）：

```java
@Component("transactionListener")
public class SimpleTransactionListener implements TransactionListener {

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 执行本地事务（写数据库）
        try {
            orderService.createOrder(order);
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        // Broker 回查：查数据库确认事务是否已提交
        boolean committed = orderService.exists(msg.getTransactionId());
        return committed ? LocalTransactionState.COMMIT_MESSAGE
                         : LocalTransactionState.ROLLBACK_MESSAGE;
    }
}
```

### 顺序消息

RocketMQ 保证**同一 MessageQueue 内消息 FIFO**。通过 `hashKey` 将同一条业务对象的消息路由到同一队列：

```java
// 相同 orderId 的消息发到同一队列，按序消费
producer.syncSendOrderly("order-topic", "order-status", createEvent, String.valueOf(orderId));
producer.syncSendOrderly("order-topic", "order-status", payEvent, String.valueOf(orderId));
producer.syncSendOrderly("order-topic", "order-status", doneEvent, String.valueOf(orderId));
```

消费端需使用顺序消费模式：

```java
@RocketMQMessageListener(
    topic = "order-topic",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费（默认 CONCURRENTLY 并发）
)
```

---

## 文件清单与职责

### config/ —— 配置层

| 文件 | 职责 |
|------|------|
| `RocketMQConfig.java` | 注册带 JavaTimeModule 的 ObjectMapper，供 RocketMQ 消息 JSON 序列化 |

### producer/ —— 生产者

| 文件 | 职责 |
|------|------|
| `MessageProducer.java` | ★ 封装全部发送 API：syncSend / asyncSend / sendOneWay / syncSendOrderly / syncSendDelay / syncSendDelayLevel / sendMessageInTransaction / syncSendBatch |
| `SimpleTransactionListener.java` | 事务消息监听器（executeLocalTransaction + checkLocalTransaction） |

### consumer/ —— 消费者

| 文件 | 职责 |
|------|------|
| `OrderMessageListener.java` | 订单消费者示例（集群消费 + Tag 过滤 + Tag 分流） |
| `DelayMessageListener.java` | 延迟消息消费者 |

### model/ —— 数据模型

| 文件 | 职责 |
|------|------|
| `MessageWrapper.java` | ★ 统一消息信封（messageId + messageKey + timestamp + source + messageType + payload） |
| `OrderMessage.java` | 业务消息 POJO 示例 |

### controller/ + util/

| 文件 | 职责 |
|------|------|
| `RocketMQController.java` | REST 测试接口（7 个端点，覆盖全部发送模式） |
| `RocketMQUtil.java` | RocketMQ 工具类（原生命令查询、消息发送） |

---

## 使用指南

### 如何发送消息

```java
@Service
public class OrderService {

    @Autowired
    private MessageProducer producer;

    // 1. 同步发送（可靠，最常用）
    public void createOrder(OrderMessage order) {
        SendResult result = producer.syncSend("order-topic", "order-created", order);
        log.info("发送成功: msgId={}", result.getMsgId());
    }

    // 2. 异步发送（不阻塞，回调处理结果）
    public void asyncNotify(OrderMessage order) {
        producer.asyncSend("order-topic", "order-created", order,
            result -> log.info("发送成功: {}", result.getMsgId()),
            ex -> log.error("发送失败，需补偿", ex)
        );
    }

    // 3. 单向发送（日志类消息，丢得起）
    public void logEvent(String event) {
        producer.sendOneWay("log-topic", "click", event);
    }

    // 4. 顺序发送（同订单 FIFO）
    public void changeStatus(Long orderId, String status) {
        OrderMessage msg = buildMsg(orderId, status);
        producer.syncSendOrderly("order-topic", "order-status", msg, String.valueOf(orderId));
    }

    // 5. 延迟发送（30 分钟后检查支付）
    public void scheduleCheck(Long orderId) {
        producer.syncSendDelay("order-topic", "order-check",
                Map.of("orderId", orderId), 30 * 60);
    }

    // 6. 事务发送（订单 + 扣库存一致性）
    public void createWithTx(OrderMessage order) {
        TransactionSendResult result = producer.sendMessageInTransaction(
                "order-topic", "order-created", order, "附加参数");
    }
}
```

### 如何消费消息

```java
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-consumer",
        selectorExpression = "order-created||order-paid",  // 只消费这两个 Tag
        consumeMode = ConsumeMode.CONCURRENTLY,             // 并发消费
        messageModel = MessageModel.CLUSTERING              // 集群消费
)
public class OrderConsumer implements RocketMQListener<MessageWrapper<OrderMessage>> {

    @Override
    public void onMessage(MessageWrapper<OrderMessage> wrapper) {
        OrderMessage order = wrapper.getPayload();
        String tag = wrapper.getMessageType();   // Tag 值

        try {
            switch (tag) {
                case "order-created" -> handleCreated(order);
                case "order-paid"    -> handlePaid(order);
            }
            // 返回正常 = ACK

        } catch (Exception e) {
            log.error("消费失败: msgId={}", wrapper.getMessageId(), e);
            // 抛出异常 → RocketMQ 自动重试，达到 maxReconsumeTimes 后进死信
            throw new RuntimeException("处理失败", e);
        }
    }
}
```

### 完整示例（订单创建 → 异步处理）

```java
// ===== 1. Controller —— 接收请求 =====
@RestController
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/orders")
    public Result createOrder(@RequestBody OrderRequest request) {
        // 同步保存订单
        Order order = orderService.save(request);

        // 异步处理（发短信、发优惠券、记日志等）
        OrderMessage msg = OrderMessage.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .build();
        orderService.sendOrderCreated(msg);

        return Result.ok(order.getId());
    }
}

// ===== 2. Service —— 发送消息 =====
@Service
public class OrderService {
    @Autowired
    private MessageProducer producer;

    public void sendOrderCreated(OrderMessage order) {
        producer.send("order-topic", "order-created", order);
    }
}

// ===== 3. Consumer —— 异步处理 =====
@Component
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-async-consumer",
    selectorExpression = "order-created"
)
public class OrderAsyncConsumer implements RocketMQListener<MessageWrapper<OrderMessage>> {
    @Override
    public void onMessage(MessageWrapper<OrderMessage> wrapper) {
        OrderMessage order = wrapper.getPayload();
        smsService.send(order.getUserId(), "订单 " + order.getOrderNo() + " 已创建");
        couponService.grantNewUserCoupon(order.getUserId());
        logService.record(order);
    }
}
```

---

## 常见场景示例

### 场景 1：订单超时取消（延迟消息）

```java
// 下单时发送 30 分钟延迟消息
@PostMapping("/orders")
public Result create(@RequestBody OrderRequest req) {
    Order order = orderService.save(req);
    producer.syncSendDelay("order-topic", "order-check",
            Map.of("orderId", order.getId()), 30 * 60);
    return Result.ok(order.getId());
}

// DelayConsumer 收到后检查支付状态
@Component
@RocketMQMessageListener(
    topic = "order-topic", consumerGroup = "order-check-consumer",
    selectorExpression = "order-check"
)
public class OrderCheckConsumer implements RocketMQListener<MessageWrapper<Map>> {
    @Override
    public void onMessage(MessageWrapper<Map> wrapper) {
        Long orderId = ((Number) wrapper.getPayload().get("orderId")).longValue();
        Order order = orderService.findById(orderId);
        if ("CREATED".equals(order.getStatus())) {
            orderService.cancel(orderId);  // 超时取消
        }
    }
}
```

### 场景 2：幂等消费

```java
@Component
public class IdempotentConsumer implements RocketMQListener<MessageWrapper<?>> {

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void onMessage(MessageWrapper<?> wrapper) {
        String idempotentKey = "rocketmq:idempotent:" + wrapper.getMessageId();

        if (!redisUtil.setIfAbsent(idempotentKey, "1", Duration.ofHours(24))) {
            log.warn("重复消息，跳过: {}", wrapper.getMessageId());
            return;
        }

        // 正常业务处理...
    }
}
```

### 场景 3：Tag 分流不同业务逻辑

```java
// 一个 Topic，按 Tag 分流
@Component
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-all-consumer",
    selectorExpression = "order-created||order-paid||order-cancelled"
)
public class OrderAllConsumer implements RocketMQListener<MessageWrapper<OrderMessage>> {
    @Override
    public void onMessage(MessageWrapper<OrderMessage> wrapper) {
        switch (wrapper.getMessageType()) {
            case "order-created"   -> handleCreated(wrapper.getPayload());
            case "order-paid"      -> handlePaid(wrapper.getPayload());
            case "order-cancelled" -> handleCancelled(wrapper.getPayload());
        }
    }
}
```

### 场景 4：死信队列处理

当消费重试达到 `maxReconsumeTimes`（默认 16 次）后，消息进入死信 Topic `%DLQ%消费者组名`。创建一个消费者监听死信：

```java
@Component
@RocketMQMessageListener(
    topic = "%DLQ%order-consumer",  // RocketMQ 自动创建的 DLQ Topic
    consumerGroup = "dlq-order-consumer"
)
public class OrderDLQConsumer implements RocketMQListener<MessageWrapper<?>> {
    @Override
    public void onMessage(MessageWrapper<?> wrapper) {
        log.warn("[死信] 消息处理失败超过最大重试次数: {}", wrapper);
        // 入库 → 告警 → 人工处理
        deadLetterRepository.save(buildRecord(wrapper));
    }
}
```

### 场景 5：广播模式刷新本地缓存

```java
// CacheRefreshListener —— 广播方式刷新所有节点
@Component
@RocketMQMessageListener(
    topic = "cache-topic",
    consumerGroup = "cache-broadcast-group",
    messageModel = MessageModel.BROADCASTING  // 广播模式
)
public class CacheRefreshListener implements RocketMQListener<MessageWrapper<Map>> {
    @Override
    public void onMessage(MessageWrapper<Map> wrapper) {
        String cacheKey = (String) wrapper.getPayload().get("key");
        localCache.remove(cacheKey);
        log.info("[缓存刷新] key={} 已从本地缓存清除", cacheKey);
    }
}
```

---

## 常见问题

### Q1：RocketMQ 如何保证消息不丢？

| 环节 | 措施 |
|------|------|
| **发送方** | `syncSend()` 等待确认 + 失败重试 + 发送日志 |
| **Broker** | 同步刷盘（`flushDiskType=SYNC_FLUSH`）+ 主从复制 |
| **消费方** | 业务处理完成才返回（异常抛出触发重试） |

本模板封装了 `syncSend` + Confirm 回调 + 业务异常重试，三个环节全兼顾。

### Q2：什么时候用 RocketMQ，什么时候用 RabbitMQ？

- 需要**事务消息**、**延迟消息**、**高吞吐** → RocketMQ
- 需要**灵活路由**（Exchange + Binding）、**成熟生态** → RabbitMQ
- 选 RocketMQ 的典型标志：电商交易、秒杀、支付回调

### Q3：NameServer 挂了怎么办？

NameServer 是无状态的，可以部署多个。Producer / Consumer 本地缓存了路由信息，NameServer 全挂期间已建立的长连接仍能正常收发消息，但无法感知新增 Topic 和 Broker 变更。

### Q4：消息消费失败了会怎样？

默认最多重试 16 次（`maxReconsumeTimes`），每次间隔递增。达到上限后消息进入死信 Topic `%DLQ%消费者组名`。需要单独写一个 Dead Letter Consumer 处理（见场景 4）。

### Q5：为什么用 Tag 而不是建多个 Topic？

Topic 是重量级资源（涉及多个 MessageQueue），Tag 是轻量级的。一个 Topic 下可以有无穷多个 Tag。原则：**按业务域建 Topic，按操作类型建 Tag**。

```
✅ 推荐：topic=order-topic, tag=created/paid/cancelled
❌ 不推荐：topic=order-created-topic, topic=order-paid-topic
```

### Q6：顺序消息和并发消费同时存在怎么办？

一个 Topic 下可以有两个消费者组：

```java
// Group A：顺序消费
@RocketMQMessageListener(..., consumeMode = ConsumeMode.ORDERLY)

// Group B：并发消费
@RocketMQMessageListener(..., consumeMode = ConsumeMode.CONCURRENTLY)
```

两个组互不影响。

### Q7：rocketmq-spring-boot-starter 2.3.x 的 syncSendDelayTimeSeconds 和 syncSend 的 delayLevel 参数有什么区别？

- `syncSendDelayTimeSeconds(destination, payload, seconds)` — RocketMQ 5.x 新增，支持任意秒数
- `syncSend(destination, message, timeout, delayLevel)` — 老版本兼容，仅支持 18 个固定等级

本模板默认使用 `syncSendDelayTimeSeconds`（任意秒数），同时保留 `syncSendDelayLevel` 兼容方法。

### Q8：事务消息回查是在什么时候触发的？

Broker 长时间没收到 commit / rollback（默认 6 秒超时），会回调 `TransactionListener.checkLocalTransaction()`。回查最多 15 次，之间间隔递增。如果回查始终返回 UNKNOW，消息最终被丢弃。

**生产建议**：本地事务状态必须持久化到数据库，回查时查数据库确认。

---

## 版本兼容

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| rocketmq-spring-boot-starter | 2.3.2 |
| RocketMQ Server | 5.3.x（推荐 5.x，兼容 4.x） |
| Java | 17+ |
