package com.quick.springrabbitmq.controller;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.OrderMessage;
import com.quick.springrabbitmq.producer.MessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * RabbitMQ 演示控制器 —— 所有发送模式的使用示例。
 * <p>
 * 启动项目后访问这些接口即可观察消息的发送和消费：
 * <pre>
 * # Direct 发送
 * curl -X POST http://localhost:8080/api/rabbit/direct
 *
 * # Fanout 广播
 * curl -X POST http://localhost:8080/api/rabbit/fanout
 *
 * # Topic 日志消息
 * curl -X POST http://localhost:8080/api/rabbit/topic/log -H "Content-Type: application/json" -d '{"routingKey":"log.info","content":"系统启动完成"}'
 *
 * # Topic 错误消息
 * curl -X POST http://localhost:8080/api/rabbit/topic/log -H "Content-Type: application/json" -d '{"routingKey":"log.error","content":"数据库连接失败"}'
 *
 * # 延迟消息（5 秒后消费）
 * curl -X POST http://localhost:8080/api/rabbit/delay?seconds=5
 *
 * # 发送订单消息
 * curl -X POST http://localhost:8080/api/rabbit/order
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit")
@RequiredArgsConstructor
public class RabbitMQController {

    private final MessagePublisher publisher;

    // ==================== Direct 模式演示 ====================

    /** 发送一条 Direct 消息 */
    @PostMapping("/direct")
    public ResponseEntity<?> sendDirect() {
        OrderMessage order = buildDemoOrder();
        publisher.sendDirect(RabbitQueueConfig.DIRECT_ROUTING_KEY, order);
        return ResponseEntity.ok(Map.of("code", 200, "message", "Direct 消息已发送"));
    }

    // ==================== Fanout 模式演示 ====================

    /** 发送一条 Fanout 广播消息（两个队列都会收到） */
    @PostMapping("/fanout")
    public ResponseEntity<?> sendFanout() {
        publisher.sendFanout(Map.of("event", "cache.refresh", "timestamp", System.currentTimeMillis()));
        return ResponseEntity.ok(Map.of("code", 200, "message", "Fanout 广播已发送，fanout.queue1 和 fanout.queue2 都会收到"));
    }

    // ==================== Topic 模式演示 ====================

    /** 发送 Topic 消息 */
    @PostMapping("/topic/log")
    public ResponseEntity<?> sendTopic(@RequestBody Map<String, String> body) {
        String routingKey = body.getOrDefault("routingKey", "log.info");
        String content = body.getOrDefault("content", "default log content");
        publisher.sendTopic(routingKey, Map.of("message", content));
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Topic 消息已发送",
                "routingKey", routingKey,
                "note", routingKey.equals("log.error")
                        ? "topic.queue.log 和 topic.queue.error 都会收到"
                        : "仅 topic.queue.log 收到"
        ));
    }

    // ==================== 延迟消息演示 ====================

    /** 发送延迟消息 */
    @PostMapping("/delay")
    public ResponseEntity<?> sendDelay(@RequestParam(defaultValue = "5") int seconds) {
        long delayMillis = seconds * 1000L;
        publisher.sendWithDelay(Map.of("task", "remindOrder", "orderId", "12345"), delayMillis);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "延迟消息已发送，约 " + seconds + " 秒后消费",
                "delayMillis", delayMillis
        ));
    }

    // ==================== 业务消息演示 ====================

    /** 发送一条订单消息（演示业务消息的标准写法） */
    @PostMapping("/order")
    public ResponseEntity<?> sendOrder() {
        OrderMessage order = buildDemoOrder();
        publisher.sendDirect(RabbitQueueConfig.DIRECT_ROUTING_KEY, order);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "订单消息已发送",
                "orderId", order.getOrderId(),
                "orderNo", order.getOrderNo()
        ));
    }

    /** 发送通用消息到指定 exchange 和 routingKey */
    @PostMapping("/send")
    public ResponseEntity<?> sendGeneral(@RequestParam String exchange,
                                         @RequestParam String routingKey,
                                         @RequestBody Map<String, Object> payload) {
        publisher.send(exchange, routingKey, payload);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "消息已发送",
                "exchange", exchange,
                "routingKey", routingKey
        ));
    }

    // ==================== 优先级消息演示 ====================

    /** 发送带优先级的消息 */
    @PostMapping("/priority")
    public ResponseEntity<?> sendPriority(@RequestParam(defaultValue = "5") int priority) {
        publisher.sendWithPriority(
                RabbitQueueConfig.DIRECT_EXCHANGE,
                RabbitQueueConfig.DIRECT_ROUTING_KEY,
                Map.of("task", "vipOrder", "priority", priority),
                priority
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "优先级消息已发送",
                "priority", priority
        ));
    }

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
}
