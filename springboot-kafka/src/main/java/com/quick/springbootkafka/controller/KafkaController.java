package com.quick.springbootkafka.controller;

import com.quick.springbootkafka.model.MessageWrapper;
import com.quick.springbootkafka.model.OrderMessage;
import com.quick.springbootkafka.producer.KafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kafka 演示控制器 —— 所有发送模式的 REST 测试入口。
 * <p>
 * 启动项目后通过 curl 测试各种发送模式：
 * <pre>
 * # 1. 同步发送订单消息
 * curl -X POST http://localhost:8080/api/kafka/order/sync
 *
 * # 2. 异步发送
 * curl -X POST http://localhost:8080/api/kafka/order/async
 *
 * # 3. 带 Key 发送（相同 Key 进同一分区，保证顺序）
 * curl -X POST http://localhost:8080/api/kafka/order/key
 *
 * # 4. 带 Header 发送
 * curl -X POST http://localhost:8080/api/kafka/order/headers
 *
 * # 5. 延迟消息
 * curl -X POST "http://localhost:8080/api/kafka/delay?seconds=30"
 *
 * # 6. 批量发送
 * curl -X POST http://localhost:8080/api/kafka/order/batch
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaController {

    private final KafkaProducer producer;

    // ==================== 同步发送 ====================

    /** 同步发送订单创建消息 */
    @PostMapping("/order/sync")
    public ResponseEntity<?> syncSendOrder() {
        OrderMessage order = buildDemoOrder();
        SendResult<String, Object> result = producer.syncSend("order-topic", order);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "同步发送成功",
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset(),
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 异步发送 ====================

    /** 异步发送订单消息 */
    @PostMapping("/order/async")
    public ResponseEntity<?> asyncSendOrder() {
        OrderMessage order = buildDemoOrder();
        producer.asyncSend("order-topic", order,
                result -> log.info("[Controller] 异步发送回调-成功: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()),
                ex -> log.error("[Controller] 异步发送回调-失败", ex)
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "异步发送已提交（通过 CompletableFuture 回调获取结果）",
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 带 Key 发送（顺序） ====================

    /** 带 Key 发送 —— 相同 orderId 的消息进入同一分区，保证消费顺序 */
    @PostMapping("/order/key")
    public ResponseEntity<?> sendWithKey() {
        Long orderId = System.currentTimeMillis();

        // 模拟一个订单的生命周期：创建 → 支付 → 完成
        OrderMessage created = buildOrder(orderId, "CREATED");
        OrderMessage paid    = buildOrder(orderId, "PAID");
        OrderMessage done    = buildOrder(orderId, "DONE");

        String key = String.valueOf(orderId);
        SendResult<String, Object> r1 = producer.syncSend("order-topic", key, created);
        SendResult<String, Object> r2 = producer.syncSend("order-topic", key, paid);
        SendResult<String, Object> r3 = producer.syncSend("order-topic", key, done);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "带 Key 消息已发送（CREATE → PAY → DONE）",
                "orderId", orderId,
                "note", "相同 Key 的消息在同一分区内 FIFO 消费（Kafka 仅保证分区级有序）"
        ));
    }

    // ==================== 带 Header 发送 ====================

    /** 带自定义 Header 发送 */
    @PostMapping("/order/headers")
    public ResponseEntity<?> sendWithHeaders() {
        OrderMessage order = buildDemoOrder();
        SendResult<String, Object> result = producer.syncSendWithHeaders(
                "order-topic", String.valueOf(order.getOrderId()), order,
                Map.of("traceId", java.util.UUID.randomUUID().toString(),
                       "source", "kafka-controller",
                       "version", "1.0")
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "带 Header 消息已发送",
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset(),
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 使用 MessageWrapper 发送 ====================

    /** 使用 MessageWrapper 发送（推荐方式） */
    @PostMapping("/order/wrapper")
    public ResponseEntity<?> sendWithWrapper() {
        OrderMessage order = buildDemoOrder();
        MessageWrapper<OrderMessage> wrapper = MessageWrapper.of(
                "OrderMessage", String.valueOf(order.getOrderId()), "kafka-controller", order);
        SendResult<String, Object> result = producer.syncSend("order-topic", wrapper);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "MessageWrapper 消息已发送",
                "messageId", wrapper.getMessageId(),
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset()
        ));
    }

    // ==================== 延迟发送 ====================

    /** 延迟消息（应用层模拟） */
    @PostMapping("/delay")
    public ResponseEntity<?> delaySend(@RequestParam(defaultValue = "30") int seconds) {
        Map<String, Object> payload = Map.of(
                "task", "remindOrder",
                "orderId", "12345",
                "delaySeconds", seconds);
        SendResult<String, Object> result = producer.syncSend("delay-topic", payload);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "延迟消息已发送（Kafka 无内置延迟，此处仅演示消费端时间检查）",
                "delaySeconds", seconds,
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset(),
                "note", "生产环境建议用 RocketMQ 延迟消息或 XXL-Job 定时任务"
        ));
    }

    // ==================== 批量发送 ====================

    /** 批量发送 */
    @PostMapping("/order/batch")
    public ResponseEntity<?> batchSend() {
        List<MessageWrapper<?>> wrappers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            OrderMessage order = OrderMessage.builder()
                    .orderId((long) (System.currentTimeMillis() + i))
                    .orderNo("ORD" + System.currentTimeMillis() + i)
                    .userId(1001L)
                    .amount(new BigDecimal("99.99"))
                    .status("CREATED")
                    .createTime(LocalDateTime.now())
                    .build();
            wrappers.add(MessageWrapper.of("OrderMessage", String.valueOf(order.getOrderId()), order));
        }
        producer.syncSendBatch("order-topic", wrappers);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "批量发送完成",
                "count", wrappers.size()
        ));
    }

    // ==================== 事务消息演示 ====================

    /** 事务发送（exactly-once） */
    @PostMapping("/order/transaction")
    public ResponseEntity<?> transactionSend() {
        OrderMessage order = buildDemoOrder();
        try {
            producer.executeInTransaction(template -> {
                template.send("order-topic", String.valueOf(order.getOrderId()), order);
                // 可在此处做其他操作（如写其他 Topic），默认一并提交
                return true;
            });
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "事务消息已提交",
                    "orderId", order.getOrderId()
            ));
        } catch (Exception e) {
            log.error("事务消息失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "事务消息已回滚: " + e.getMessage()
            ));
        }
    }

    // ==================== 工具方法 ====================

    private OrderMessage buildDemoOrder() {
        return OrderMessage.builder()
                .orderId(System.currentTimeMillis())
                .orderNo("ORD" + System.currentTimeMillis())
                .userId(1001L)
                .amount(new BigDecimal("299.99"))
                .status("CREATED")
                .createTime(LocalDateTime.now())
                .build();
    }

    private OrderMessage buildOrder(Long orderId, String status) {
        return OrderMessage.builder()
                .orderId(orderId)
                .orderNo("ORD" + orderId)
                .userId(1001L)
                .amount(new BigDecimal("299.99"))
                .status(status)
                .createTime(LocalDateTime.now())
                .build();
    }
}
