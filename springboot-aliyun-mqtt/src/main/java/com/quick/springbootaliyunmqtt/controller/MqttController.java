package com.quick.springbootaliyunmqtt.controller;

import com.quick.springbootaliyunmqtt.config.MqttConnectionStateListener;
import com.quick.springbootaliyunmqtt.model.MqttMessageWrapper;
import com.quick.springbootaliyunmqtt.model.OrderMessage;
import com.quick.springbootaliyunmqtt.publisher.MqttPublisher;
import com.quick.springbootaliyunmqtt.subscriber.MqttSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MQTT REST 演示接口。
 * <p>
 * 测试示例：
 * <pre>
 * # QoS 0 发送（最多一次，无确认）
 * curl -X POST "http://localhost:8080/api/mqtt/publish?topic=/device/demo/event&payload=hello&qos=0"
 *
 * # QoS 1 发送（至少一次，默认）
 * curl -X POST "http://localhost:8080/api/mqtt/publish?topic=/device/demo/event&payload=hello"
 *
 * # QoS 2 发送（恰好一次）
 * curl -X POST "http://localhost:8080/api/mqtt/publish?topic=/device/demo/event&payload=hello&qos=2"
 *
 * # 发送 JSON 订单消息
 * curl -X POST http://localhost:8080/api/mqtt/order
 *
 * # 查看当前订阅
 * curl http://localhost:8080/api/mqtt/subscriptions
 *
 * # 查看连接状态
 * curl http://localhost:8080/api/mqtt/status
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/mqtt")
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttController {

    private final MqttPublisher mqttPublisher;
    private final MqttSubscriber mqttSubscriber;
    private final MqttConnectionStateListener connectionStateListener;

    // ==================== 发布消息 ====================

    /**
     * 发布消息到指定 Topic。
     *
     * @param topic    MQTT Topic
     * @param payload  消息内容
     * @param qos      QoS 0/1/2，默认 1
     * @param retained 是否保留消息，默认 false
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestParam String topic,
            @RequestParam String payload,
            @RequestParam(defaultValue = "1") int qos,
            @RequestParam(defaultValue = "false") boolean retained) {

        if (qos < 0 || qos > 2) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "QoS 必须为 0/1/2"));
        }

        mqttPublisher.publish(topic, payload, qos, retained);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "消息发布成功",
                "data", Map.of("topic", topic, "qos", qos, "retained", retained)
        ));
    }

    /**
     * 发布一个演示订单消息（使用 MqttMessageWrapper 包装）。
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> publishOrder() {
        OrderMessage order = buildDemoOrder();
        MqttMessageWrapper<OrderMessage> wrapper = MqttMessageWrapper.of("ORDER_CREATED", order.getOrderNo(), "mqtt-demo", order);

        String topic = "/device/demo/order";
        mqttPublisher.publishAsJson(topic, wrapper);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "订单消息发布成功",
                "data", Map.of("topic", topic, "orderId", order.getOrderId(), "orderNo", order.getOrderNo())
        ));
    }

    /**
     * 批量发布消息到同一个 Topic（用于压测）。
     */
    @PostMapping("/publish/batch")
    public ResponseEntity<Map<String, Object>> publishBatch(
            @RequestParam String topic,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "1") int qos) {

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String payload = "{\"seq\":" + i + ",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
            mqttPublisher.publish(topic, payload, qos, false);
        }
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "批量发布完成",
                "data", Map.of("count", count, "elapsedMs", elapsed)
        ));
    }

    // ==================== 订阅 ====================

    /**
     * 手动订阅一个 Topic（运行时动态订阅）。
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
            @RequestParam String topicFilter,
            @RequestParam(defaultValue = "1") int qos) {

        mqttSubscriber.subscribe(topicFilter, qos, (topic, message) -> {
            String payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("[动态订阅] topic={}, qos={}, payload={}", topic, message.getQos(), payload);
        });

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "订阅成功",
                "data", Map.of("topicFilter", topicFilter, "qos", qos)
        ));
    }

    /**
     * 取消订阅。
     */
    @DeleteMapping("/subscribe/{topicFilter}")
    public ResponseEntity<Map<String, Object>> unsubscribe(@PathVariable String topicFilter) {
        mqttSubscriber.unsubscribe(topicFilter);
        return ResponseEntity.ok(Map.of("code", 200, "message", "取消订阅成功", "topic", topicFilter));
    }

    /**
     * 查看当前订阅列表。
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptions() {
        Set<String> topics = mqttSubscriber.getSubscribedTopics();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "订阅列表",
                "subscriptions", topics,
                "count", topics.size()
        ));
    }

    // ==================== 连接状态 ====================

    /**
     * 查看 MQTT 连接状态与缓冲统计。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean connected = mqttSubscriber.isConnected();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "connected", connected,
                "subscriptions", mqttSubscriber.getSubscribedTopics(),
                "buffer", Map.of(
                        "pending", mqttPublisher.getPendingCount(),
                        "dropped", mqttPublisher.getDroppedCount(),
                        "flushed", mqttPublisher.getFlushedCount(),
                        "capacity", mqttPublisher.getMaxBufferSize()
                )
        ));
    }

    // ==================== 辅助方法 ====================

    private OrderMessage buildDemoOrder() {
        long seq = System.currentTimeMillis() % 10000;
        return OrderMessage.builder()
                .orderId(seq)
                .orderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(1001L)
                .amount(new BigDecimal("99.99"))
                .status("CREATED")
                .createTime(LocalDateTime.now())
                .build();
    }
}
