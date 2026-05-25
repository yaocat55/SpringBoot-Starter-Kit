# Spring Boot Kafka 开箱即用模板

Spring Boot 3.5.x + Spring Kafka 全场景消息队列模板，覆盖 Producer 全部发送模式、Consumer 手动 ACK/死信/重试、事务 Exactly-Once，配套 REST 演示接口。**复制即用，告别重复造轮子。**

---

## 目录

- [Kafka vs RabbitMQ vs RocketMQ](#kafka-vs-rabbitmq-vs-rocketmq)
- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [配置文件详解](#配置文件详解)
- [核心概念](#核心概念)
- [Producer —— 6 种发送模式](#producer--6-种发送模式)
- [Consumer —— 消费与错误处理](#consumer--消费与错误处理)
- [文件职责一览](#文件职责一览)
- [使用指南 —— 从接入到上线](#使用指南--从接入到上线)
- [常见场景](#常见场景)
- [FAQ](#faq)

---

## Kafka vs RabbitMQ vs RocketMQ

| 维度 | **Kafka** | RabbitMQ | RocketMQ |
|------|----------|----------|----------|
| 设计定位 | 分布式流平台、日志/大数据 | 传统消息队列、企业集成 | 金融级分布式消息中间件 |
| 吞吐量 | **极高**（百万条/秒） | 中等（万条/秒） | 高（十万条/秒） |
| 延迟 | 毫秒级 | 微秒级 | 毫秒级 |
| 消息持久化 | 磁盘顺序写，默认持久化 | 内存/磁盘可选 | 磁盘 + 同步刷盘可选 |
| 消费模式 | Pull（长轮询） | Push（基于 AMQP） | Pull（长轮询） |
| 消息顺序 | **分区内 FIFO**（Key 哈希） | 队列内 FIFO | 队列内 FIFO（同步刷盘保证） |
| 延迟消息 | ❌ 无内置 | ✅ TTL + DLX | ✅ 18 个延迟等级 |
| 事务消息 | ✅ idempotent producer + transaction | ❌ 无 | ✅ Half-Message + 回查 |
| 死信队列 | ✅ 通过 DeadLetterPublishingRecoverer | ✅ DLX | ✅ 自动 %DLQ% Topic |
| 广播消费 | 不同 groupId 独立消费 | Fanout Exchange | BROADCASTING 模式 |
| 消息回溯 | ✅ 按 Offset 重置（天生支持） | ❌ 消息消费后删除 | ✅ 按时间/Offset 重置 |
| 管理界面 | 需外部工具（AKHQ / Kafka UI） | 内置 Management 插件 | 自带 Dashboard（5.x） |
| Spring 集成 | spring-kafka | spring-amqp | rocketmq-spring-boot-starter |

**选型建议：**
- **日志采集、流处理、大数据管道** → Kafka（吞吐优先，持久化，回溯方便）
- **业务消息、RPC、微服务解耦** → RocketMQ（事务消息、延迟消息开箱即用）
- **低延迟、复杂路由、管理简单** → RabbitMQ（毫秒延迟，Exchange 灵活路由）

---

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                    Kafka Brokers                     │
│  ┌─────────────────────────────────────────────────┐ │
│  │  order-topic (3 partitions, 1 replica)          │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐      │ │
│  │  │Partition0│  │Partition1│  │Partition2│      │ │
│  │  │ [0][1][2]│  │ [0][1][2] │  │ [0][1][2] │      │ │
│  │  └──────────┘  └──────────┘  └──────────┘      │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────┐ │
│  │  order-topic.DLT (死信队列)                      │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────┬──────────────────────────────────┘
                   │
    ┌──────────────┴──────────────┐
    │                             │
    ▼                             ▼
┌───────────┐              ┌───────────────┐
│ Producer  │              │   Consumer    │
│           │   send()     │               │
│ KafkaTpl  │──────────────▶│ @KafkaListener│
│           │              │  Manual ACK   │
│ · sync    │              │  Retry → DLT  │
│ · async   │              │               │
│ · key     │              │  ┌─────────┐  │
│ · headers │              │  │ order-  │  │
│ · batch   │              │  │ topic   │  │
│ · txn     │              │  └─────────┘  │
└───────────┘              │  ┌─────────┐  │
                           │  │ DLT     │  │
                           │  └─────────┘  │
                           └───────────────┘

流程说明：
1. Producer 通过 KafkaTemplate 发送消息（sync/async/with-key）
2. 消息根据 Key 哈希进入对应 Partition（无 Key 则轮询）
3. Consumer 通过 @KafkaListener 长轮询拉取消息
4. 手动 ACK 确认消费完成，Offset 提交
5. 消费失败 → DefaultErrorHandler 重试 3 次 → 仍失败投递 DLT
6. 死信消息由 DeadLetterListener 统一处理（告警/人工介入）
```

---

## 快速开始

### 1. 启动 Kafka（Docker）

```bash
# 单节点 Kafka（KRaft 模式，无需 ZooKeeper）
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  apache/kafka:latest
```

> 如果已有 ZooKeeper 模式的 Kafka 集群，修改 `application.yml` 中的 `bootstrap-servers` 即可。

### 2. 启动应用

```bash
cd springboot-kafka
mvn spring-boot:run
```

启动后会自动创建 `order-topic`（3分区）、`order-topic.DLT`（死信）、`delay-topic`。

### 3. 测试发送

```bash
# 同步发送订单消息
curl -X POST http://localhost:8080/api/kafka/order/sync

# 带 Key 发送（同一 orderId 进入同一分区，保证顺序）
curl -X POST http://localhost:8080/api/kafka/order/key

# 异步发送
curl -X POST http://localhost:8080/api/kafka/order/async

# 带 Header 发送（元数据传递）
curl -X POST http://localhost:8080/api/kafka/order/headers

# 使用 MessageWrapper 发送（推荐方式）
curl -X POST http://localhost:8080/api/kafka/order/wrapper

# 延迟消息
curl -X POST "http://localhost:8080/api/kafka/delay?seconds=30"

# 批量发送
curl -X POST http://localhost:8080/api/kafka/order/batch

# 事务发送
curl -X POST http://localhost:8080/api/kafka/order/transaction
```

---

## 配置文件详解

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092    # Broker 地址（集群逗号分隔）

    producer:
      key-serializer: StringSerializer   # Key 序列化器（固定 String）
      value-serializer: JsonSerializer   # Value 序列化器（Jackson JSON）
      acks: all                          # 确认：0|1|all（all=最强可靠）
      retries: 5                         # 重试次数
      batch-size: 16384                  # 批量大小（字节）
      linger-ms: 5                       # 凑批等待时间（ms）
      buffer-memory: 33554432            # 缓冲区内存（32MB）
      compression-type: snappy           # 压缩：none|gzip|snappy|lz4|zstd
      properties:
        enable.idempotence: true         # 幂等性（保证不重复）

    consumer:
      key-deserializer: StringDeserializer
      value-deserializer: JsonDeserializer
      group-id: ${spring.application.name}-consumer-group
      auto-offset-reset: earliest        # earliest|latest|none
      enable-auto-commit: false          # 手动 ACK（推荐）
      max-poll-records: 100              # 单次拉取上限
      properties:
        spring.json.trusted.packages: "com.quick.springbootkafka.model"
        spring.json.type.mapping: "OrderMessage:com.quick.springbootkafka.model.OrderMessage"

    listener:
      concurrency: 4                     # 并发消费者数
      ack-mode: manual                   # 手动确认
```

**关键配置说明：**

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| `acks` | 可靠性级别 | `all`（生产），`1`（日志） |
| `enable.idempotence` | 幂等生产者，防止网络重试导致重复 | `true`（生产） |
| `compression-type` | 压缩算法 | `snappy`（平衡 CPU/压缩比） |
| `auto-offset-reset` | 无 offset 时从哪开始 | `earliest`（新组），`latest`（已有组） |
| `enable-auto-commit` | 自动提交 offset | `false`（推荐手动控制） |
| `max-poll-records` | 单次拉取条数 | 100-500（根据消息大小调整） |

---

## 核心概念

### 1. 消息模型

```
Topic (主题)
  └── Partition 0 (分区)
       ├── [offset 0] msg-A
       ├── [offset 1] msg-B
       └── [offset 2] msg-C
  └── Partition 1
       ├── [offset 0] msg-D
       └── [offset 1] msg-E
  └── Partition 2
       └── [offset 0] msg-F
```

- **Topic**：逻辑上的消息分类，类似数据库的"表"
- **Partition**：物理分区，消息按 Key 哈希分配（无 Key 则轮询）
- **Offset**：消息在分区内的唯一序号，消费者通过 Offset 记录消费进度
- **Consumer Group**：消费者组，组内每个消费者负责一部分分区（负载均衡）

### 2. 分区与顺序

```
Producer                  Consumer Group
  │                           │
  │  msgs with Key="1001"     │
  ├─ Partition 0 ◄────────────┤ Consumer-1 (处理 Partition 0, 1)
  │  [CREATE][PAY][DONE]      │
  │                           │
  │  msgs with Key="1002"     │
  ├─ Partition 1 ◄────────────┤ Consumer-1
  │  [CREATE][PAY]            │
  │                           │
  │  msgs with no Key         │
  └─ Partition 2 ◄────────────┤ Consumer-2 (处理 Partition 2)
     [random]                 │
```

**核心保证：** Kafka 只保证**分区内有序**（同一 Key → 同一分区 → FIFO），不保证跨分区有序。

### 3. Offset 管理

```
Partition 0:  [0] [1] [2] [3] [4] [5] ...
                  ▲           ▲
                  │           │
            committed    latest (LEO)
            offset=1     offset=5
                         
            Lag = 5 - 1 = 4 (未消费的消息数)
```

- **committed offset**：消费者已确认的位点（重启后从这里开始）
- **LEO (Log End Offset)**：分区最新消息的位点
- **Lag**：消费延迟（LEO - committed offset），监控核心指标

---

## Producer —— 6 种发送模式

### 1. 同步发送（最常用，等待确认）

```java
SendResult<String, Object> result = producer.syncSend("order-topic", orderMsg);
// 阻塞等待，获取分区和 offset
int partition = result.getRecordMetadata().partition();
long offset = result.getRecordMetadata().offset();
```

### 2. 异步发送（非阻塞，高吞吐）

```java
producer.asyncSend("order-topic", orderMsg,
    result -> log.info("发送成功: partition={}", result.getRecordMetadata().partition()),
    ex -> log.error("发送失败", ex)
);
```

### 3. 带 Key 发送（保证区内的顺序）

```java
// 相同 orderId 的消息进入同一分区 → FIFO
String key = String.valueOf(orderId);
producer.syncSend("order-topic", key, orderCreatedMsg);
producer.syncSend("order-topic", key, orderPaidMsg);
producer.syncSend("order-topic", key, orderDoneMsg);
```

### 4. 带 Header 发送（元数据传递）

```java
producer.syncSendWithHeaders("order-topic", key, payload,
    Map.of("traceId", "abc-123",
           "source", "order-service",
           "version", "1.0")
);
// 消费端通过 ConsumerRecord.headers() 读取
```

### 5. 批量发送

```java
List<MessageWrapper<?>> wrappers = List.of(wrapper1, wrapper2, wrapper3);
producer.syncSendBatch("order-topic", wrappers);
```

### 6. 事务发送（Exactly-Once）

```java
producer.executeInTransaction(template -> {
    template.send("order-topic", key, orderMsg);
    template.send("inventory-topic", key, inventoryMsg);
    template.send("points-topic", key, pointsMsg);
    return true; // 返回 true 提交，抛异常回滚
});
```

**使用前提：** Producer 配置 `transaction-id-prefix`，Consumer 设置 `isolation.level=read_committed`。

---

## Consumer —— 消费与错误处理

### 手动 ACK 三种情况

```java
@KafkaListener(topics = "order-topic", groupId = "${spring.application.name}-consumer-group")
public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
    try {
        // 1. 正常处理 → ACK 提交 Offset
        processOrder(order);
        ack.acknowledge();

    } catch (IllegalArgumentException e) {
        // 2. 业务异常 → 直接 ACK，不重试（避免死循环）
        log.error("业务异常，跳过: {}", e.getMessage());
        ack.acknowledge();

    } catch (Exception e) {
        // 3. 系统异常 → 抛出，触发 DefaultErrorHandler 重试
        throw new RuntimeException("消费失败", e);
    }
}
```

### 错误处理流程

```
消息到达 → Consumer 处理
    │
    ├── 成功 → ack.acknowledge() → Offset 提交 ✔️
    │
    └── 失败 → RuntimeException
              │
              ├── 重试 1 (2s 后)
              ├── 重试 2 (2s 后)
              ├── 重试 3 (2s 后)
              │
              └── 3 次全失败 → 投递 order-topic.DLT
                              │
                              └── DeadLetterListener 消费死信
                                  · 记录日志/告警
                                  · 写入 DB 死信表
                                  · 人工处理
```

### 死信消息信息

死信消息携带原始上下文到 Kafka Header：
- `x-original-topic` — 来源 Topic
- `x-original-partition` / `x-original-offset` — 原始位点
- `x-exception-message` / `x-exception-stacktrace` — 异常详情

---

## 文件职责一览

```
springboot-kafka/
├── pom.xml                                          # 依赖：spring-kafka, web, json, lombok
├── README.md                                        # 本文档
└── src/main/
    ├── java/com/quick/springbootkafka/
    │   ├── KafkaApplication.java                    # Spring Boot 启动类
    │   ├── config/
    │   │   └── KafkaConfig.java                     # 核心配置
    │   │       · kafkaObjectMapper()                 #   ObjectMapper（JavaTimeModule）
    │   │       · kafkaTemplate()                     #   KafkaTemplate Bean
    │   │       · kafkaListenerContainerFactory()     #   Consumer 工厂（手动ACK）
    │   │       · kafkaErrorHandler()                 #   错误处理器（重试 + 死信）
    │   │       · orderTopic() / deadLetterTopic()    #   NewTopic 自动创建
    │   ├── producer/
    │   │   └── KafkaProducer.java                   # 生产者（6 种模式）
    │   │       · syncSend / asyncSend                #   同步 / 异步
    │   │       · syncSend(key)                       #   带 Key（分区有序）
    │   │       · syncSendWithHeaders()               #   带自定义 Header
    │   │       · syncSendToPartition()               #   指定分区
    │   │       · syncSendBatch()                     #   批量发送
    │   │       · executeInTransaction()              #   事务（Exactly-Once）
    │   ├── consumer/
    │   │   ├── OrderMessageListener.java             # 订单消费者（手动ACK + 重试）
    │   │   ├── DeadLetterListener.java               # 死信消费者（告警/人工处理）
    │   │   └── DelayMessageConsumer.java             # 延迟消息消费示例
    │   ├── controller/
    │   │   └── KafkaController.java                  # REST 测试接口（7 个端点）
    │   ├── model/
    │   │   ├── MessageWrapper.java                   # 通用消息包装类
    │   │   └── OrderMessage.java                     # 订单领域模型
    │   └── util/
    │       └── KafkaUtil.java                        # 管理工具类
    │           · listTopics / createTopic / deleteTopic  # Topic 管理
    │           · describeTopic()                     #   Topic 详情
    │           · getPartitionOffsets()               #   分区 Offset 查询
    │           · getConsumerGroupOffsets()            #   消费者组 Offset 查询
    └── resources/
        └── application.yml                           # Kafka 全部配置 + 注释
```

---

## 使用指南 —— 从接入到上线

### Step 1：引入依赖

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Spring Boot 3.5.x 自带版本管理，无需指定 version。

### Step 2：配置连接

```yaml
spring:
  kafka:
    bootstrap-servers: broker1:9092,broker2:9092,broker3:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: my-service-group
      enable-auto-commit: false
    listener:
      ack-mode: manual
```

### Step 3：复制模型类

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> {
    private String messageId = UUID.randomUUID().toString();
    private String messageKey;
    private String source;
    private LocalDateTime timestamp = LocalDateTime.now();
    private String messageType;
    private T payload;

    public static <T> MessageWrapper<T> of(String messageType, T payload) {
        return MessageWrapper.<T>builder()
                .messageType(messageType).payload(payload).build();
    }
}
```

### Step 4：发送消息

```java
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final KafkaProducer producer;

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req) {
        OrderMessage msg = OrderMessage.builder()
                .orderId(System.currentTimeMillis())
                .amount(req.getAmount())
                .status("CREATED")
                .build();

        // 用 orderId 作为 Key，保证同一订单消息有序
        SendResult<String, Object> result = producer.syncSend(
                "order-topic", String.valueOf(msg.getOrderId()), msg);

        return ResponseEntity.ok(Map.of("orderId", msg.getOrderId()));
    }
}
```

### Step 5：消费消息

```java
@Slf4j
@Component
public class OrderConsumer {

    @KafkaListener(topics = "order-topic", groupId = "order-service-group")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        OrderMessage order = parseOrder(record.value());
        try {
            // 处理业务逻辑
            orderService.process(order);
            ack.acknowledge();
        } catch (BusinessException e) {
            // 业务异常：跳过
            log.warn("业务异常: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            // 系统异常：重试
            throw new RuntimeException(e);
        }
    }
}
```

### Step 6：监控 Lag

```java
// 查看消费积压
Map<String, Map<String, Long>> offsets = kafkaUtil.getConsumerGroupOffsets("order-service-group");
offsets.forEach((tp, info) -> {
    long lag = info.get("lag");
    if (lag > 1000) log.warn("消费积压: {} lag={}", tp, lag);
});
```

---

## 常见场景

### 场景 1：订单状态流转（分区顺序消息）

```java
// 同一订单的 CREATE → PAY → SHIP → DONE 进入同一分区，保证有序
String orderId = "ORD-12345";

producer.syncSend("order-topic", orderId, OrderMessage.builder()
        .orderId(orderId).status("CREATED").build());

producer.syncSend("order-topic", orderId, OrderMessage.builder()
        .orderId(orderId).status("PAID").build());

producer.syncSend("order-topic", orderId, OrderMessage.builder()
        .orderId(orderId).status("SHIPPED").build());
```

### 场景 2：日志采集与流处理

```java
// 日志采集 → Kafka → Flink/Spark → 数据仓库
// Producer 端：异步 + 压缩，追求吞吐
producer.asyncSend("access-log", accessLog,
    result -> {},  // 不关心单条结果
    ex -> log.warn("日志写入失败: {}", ex.getMessage())
);

// 配置建议：
// acks=1, compression-type=lz4, linger-ms=10, batch-size=65536
```

### 场景 3：分布式事务（Saga 模式）

```java
// 使用 Kafka 事务保证多个 Topic 写入的原子性
producer.executeInTransaction(template -> {
    template.send("order-topic", orderId, orderMsg);       // 创建订单
    template.send("inventory-topic", sku, inventoryMsg);    // 扣库存
    template.send("coupon-topic", userId, couponMsg);       // 核销优惠券
    return true;
});
// 如果库存或优惠券操作失败 → 抛异常 → 整个事务回滚
```

### 场景 4：消息重放与补偿

```java
// 从指定 Offset 重新消费（数据修复）
// 方式 1：重置消费者组 Offset
// kafka-consumer-groups --reset-offsets --to-offset 100 --group my-group --topic order-topic:0 --execute

// 方式 2：创建新的消费者组从 earliest 开始
// group-id: replay-group
// auto-offset-reset: earliest
```

### 场景 5：死信告警与人工处理

```java
// DeadLetterListener 自动捕获死信
@KafkaListener(topics = "order-topic.DLT", groupId = "dlt-group")
public void onDeadLetter(ConsumerRecord<String, Object> record, Acknowledgment ack) {
    // 提取原始信息
    String originalTopic = getHeader(record, "x-original-topic");
    String exceptionMsg = getHeader(record, "x-exception-message");

    // 发送钉钉/企微告警
    alertService.sendAlert(String.format(
        "消息消费失败: topic=%s, error=%s", originalTopic, exceptionMsg));

    // 写入死信数据库表
    deadLetterRepository.save(new DeadLetterRecord(record));

    ack.acknowledge();
}
```

---

## FAQ

### 1. NameServer 需要高可用吗？Kafka 怎么保证 Broker 高可用？

Kafka 不依赖 NameServer，Broker 本身就是去中心化架构。高可用通过以下方式：
- **多 Broker 集群**：至少 3 个 Broker（生产环境）
- **多副本**：`replication-factor=3`（每个分区 3 个副本，1 Leader + 2 Follower）
- **ISR 机制**：只有同步完成的副本才参与 Leader 选举
- **acks=all**：所有 ISR 确认才算写入成功

### 2. 消息重复消费了怎么办？

Kafka 的 `At-Least-Once` 语义 + 网络重试可能导致重复。解决方案：
- **生产者幂等**：`enable.idempotence=true`（单分区、单会话内不重复）
- **消费者去重**：基于 `messageId` 做 Redis/DB 去重（SETNX / INSERT IGNORE）
- **事务**：跨分区 Exactly-Once 用 `executeInTransaction()`

### 3. Kafka 没有延迟消息，怎么实现订单超时取消？

三种方案：
- **RocketMQ**：原生支持 18 个延迟等级，用 RocketMQ 做延迟消息，Kafka 做主流程
- **定时任务扫表**：XXL-Job / Quartz 每 5 秒扫订单表，到期未支付则取消
- **Redis ZSet**：`ZADD delay_queue {executeTime} {orderId}`，定时轮询 `ZRANGEBYSCORE`

本模板中的 `DelayMessageConsumer` 演示了消费端时间检查模式，但仅适用于对精度要求不高的场景。

### 4. 消费者 Lag 持续增长怎么办？

常见原因和解决：
- **消费者太少** → 扩容消费者实例（不超过分区数）
- **分区太少** → 增加 Topic 分区数（需注意有序消息场景慎用）
- **单条处理慢** → 优化消费逻辑、批量处理、异步化
- **消费者挂了** → 检查消费者健康状态和 Rebalance

### 5. __TypeId__ 反序列化报错怎么办？

在 `application.yml` 中配置信任包和类型映射：
```yaml
spring:
  kafka:
    consumer:
      properties:
        spring.json.trusted.packages: "com.yourcompany.yourproject.model"
        spring.json.type.mapping: "OrderMessage:com.yourcompany.yourproject.model.OrderMessage"
```

### 6. 消息体超过 1MB 怎么办？

Kafka 默认 `max.request.size=1MB`，可以调大：
```properties
# Producer
spring.kafka.producer.properties.max.request.size=10485760   # 10MB

# Broker (server.properties)
message.max.bytes=10485760
replica.fetch.max.bytes=10485760
```

但不推荐发大消息，更好的做法：**大文件走 OSS/COS，Kafka 只传 URL**。

### 7. 如何保证跨分区的事务一致性？

使用 `KafkaTemplate.executeInTransaction()`：
```java
producer.executeInTransaction(template -> {
    template.send("topic-A", key1, msgA);
    template.send("topic-B", key2, msgB);
    return true;
});
```
前提：Producer 配置 `transaction-id-prefix`，且 Kafka 集群已启用事务支持。

### 8. Kafka 和 RocketMQ 怎么选？

| 需求 | 选择 |
|------|------|
| 日志/大数据/流处理 | **Kafka** |
| 业务消息/微服务解耦 | **RocketMQ** |
| 需要延迟消息/事务消息 | **RocketMQ**（开箱即用） |
| 需要消息回溯重放 | **Kafka**（Offset 天生支持） |
| 团队技术栈轻量 | 两者皆可，看运维成本 |

其实很多公司两个都用：Kafka 做数据管道，RocketMQ 做业务消息。

---

## License

MIT — 团队成员和第三方开发者自由使用。
