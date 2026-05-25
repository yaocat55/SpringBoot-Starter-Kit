package com.quick.springbootrocketmq.controller;

import com.quick.springbootrocketmq.model.OrderMessage;
import com.quick.springbootrocketmq.producer.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * RocketMQ 演示控制器 —— 所有发送模式的 REST 测试入口。
 * <p>
 * 启动项目后通过 curl 测试各种发送模式：
 * <pre>
 * # 1. 同步发送订单消息
 * curl -X POST http://localhost:8080/api/rocketmq/order/sync
 *
 * # 2. 异步发送
 * curl -X POST http://localhost:8080/api/rocketmq/order/async
 *
 * # 3. 单向发送
 * curl -X POST http://localhost:8080/api/rocketmq/order/oneway
 *
 * # 4. 顺序发送
 * curl -X POST http://localhost:8080/api/rocketmq/order/orderly
 *
 * # 5. 延迟发送（delayLevel=5，即 1 分钟）
 * curl -X POST "http://localhost:8080/api/rocketmq/delay?seconds=5"
 *
 * # 6. 事务消息
 * curl -X POST http://localhost:8080/api/rocketmq/order/transaction
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/rocketmq")
@RequiredArgsConstructor
public class RocketMQController {

    private final MessageProducer producer;

    // ==================== 同步发送 ====================

    /** 同步发送订单创建消息 */
    @PostMapping("/order/sync")
    public ResponseEntity<?> syncSendOrder() {
        OrderMessage order = buildDemoOrder();
        SendResult result = producer.syncSend("order-topic", "order-created", order);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "同步发送成功",
                "msgId", result.getMsgId(),
                "queueId", result.getMessageQueue().getQueueId(),
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 异步发送 ====================

    /** 异步发送订单消息 */
    @PostMapping("/order/async")
    public ResponseEntity<?> asyncSendOrder() {
        OrderMessage order = buildDemoOrder();
        producer.asyncSend("order-topic", "order-created", order,
                result -> log.info("[Controller] 异步发送回调-成功: msgId={}", result.getMsgId()),
                ex -> log.error("[Controller] 异步发送回调-失败", ex)
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "异步发送已提交（通过回调获取结果）",
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 单向发送 ====================

    /** 单向发送（发完即忘，无返回值） */
    @PostMapping("/order/oneway")
    public ResponseEntity<?> oneWaySend() {
        OrderMessage order = buildDemoOrder();
        producer.sendOneWay("order-topic", "order-created", order);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "单向发送完成（不等待确认）",
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 顺序发送 ====================

    /** 顺序发送 —— 相同 orderId 的消息保证顺序 */
    @PostMapping("/order/orderly")
    public ResponseEntity<?> orderlySend() {
        Long orderId = System.currentTimeMillis();

        // 模拟一个订单的生命周期：创建 → 支付 → 完成
        OrderMessage created = buildOrder(orderId, "CREATED");
        OrderMessage paid    = buildOrder(orderId, "PAID");
        OrderMessage done    = buildOrder(orderId, "DONE");

        producer.syncSendOrderly("order-topic", "order-status", created, String.valueOf(orderId));
        producer.syncSendOrderly("order-topic", "order-status", paid, String.valueOf(orderId));
        producer.syncSendOrderly("order-topic", "order-status", done, String.valueOf(orderId));

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "顺序消息已发送（CREATE → PAY → DONE）",
                "orderId", orderId,
                "note", "相同 hashKey 的消息在同一队列内 FIFO 消费"
        ));
    }

    // ==================== 延迟发送 ====================

    /** 延迟发送 —— 支持任意秒数（RocketMQ 5.x；4.x 请用 syncSendDelayLevel） */
    @PostMapping("/delay")
    public ResponseEntity<?> delaySend(@RequestParam(defaultValue = "5") int seconds) {
        Map<String, Object> payload = Map.of("task", "remindOrder", "orderId", "12345", "delaySeconds", seconds);
        SendResult result = producer.syncSendDelay("delay-topic", "delay-notify", payload, seconds);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "延迟消息已发送",
                "delaySeconds", seconds,
                "msgId", result.getMsgId()
        ));
    }

    // ==================== 事务消息 ====================

    /** 事务消息 —— Half-Message + 本地事务 + Commit/Rollback */
    @PostMapping("/order/transaction")
    public ResponseEntity<?> transactionSend() {
        OrderMessage order = buildDemoOrder();
        TransactionSendResult result = producer.sendMessageInTransaction(
                "order-topic", "order-created", order, "附加参数");
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "事务消息已发送",
                "sendStatus", result.getSendStatus().name(),
                "orderId", order.getOrderId()
        ));
    }

    // ==================== 多 Tag 演示 ====================

    /** 发送多种 Tag（演示消费端 Tag 过滤） */
    @PostMapping("/order/tags")
    public ResponseEntity<?> sendMultiTags() {
        OrderMessage order = buildDemoOrder();
        producer.send("order-topic", "order-created", order);
        // 模拟支付和取消消息（实际项目中按需发送）
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "已发送 order-created 消息",
                "note", "消费者通过 selectorExpression = 'order-created||order-paid||order-cancelled' 过滤"
        ));
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
