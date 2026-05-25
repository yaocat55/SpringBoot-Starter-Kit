package com.quick.springbootemqx.controller;

import com.quick.springbootemqx.model.DeviceMessage;
import com.quick.springbootemqx.model.EmqxMessageWrapper;
import com.quick.springbootemqx.publisher.EmqxPublisher;
import com.quick.springbootemqx.subscriber.EmqxSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * EMQX REST 测试接口。
 * <p>
 * 所有端点受 @ConditionalOnBean 保护，仅在 emqx.enabled=true 时注册。
 */
@Slf4j
@RestController
@RequestMapping("/api/emqx")
@RequiredArgsConstructor
@ConditionalOnBean(MqttClient.class)
public class EmqxController {

    private final EmqxPublisher publisher;
    private final EmqxSubscriber subscriber;
    private final MqttClient mqttClient;

    // ======================== 发布 ========================

    /** 发布原始字符串消息 */
    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestParam String topic,
                                       @RequestParam String payload,
                                       @RequestParam(defaultValue = "1") int qos,
                                       @RequestParam(defaultValue = "false") boolean retained) {
        publisher.publish(topic, payload, qos, retained);
        return Map.of("success", true, "topic", topic, "size", payload.length());
    }

    /** 发布设备消息（带信封包装） */
    @PostMapping("/device/{deviceId}/telemetry")
    public Map<String, Object> publishTelemetry(@PathVariable String deviceId,
                                                 @RequestBody DeviceMessage data) {
        EmqxMessageWrapper<DeviceMessage> wrapper = EmqxMessageWrapper.of(
                "DEVICE_TELEMETRY", deviceId, data);
        String topic = "/device/" + deviceId + "/telemetry";
        publisher.publishAsJson(topic, wrapper);
        log.info("设备遥测已发送: deviceId={}, type={}", deviceId, data.getDataType());
        return Map.of("success", true, "topic", topic, "messageId", wrapper.getMessageId());
    }

    /** 批量发布（压测用） */
    @PostMapping("/publish/batch")
    public Map<String, Object> publishBatch(@RequestParam String topic,
                                             @RequestParam(defaultValue = "100") int count,
                                             @RequestParam(defaultValue = "1") int qos) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            publisher.publish(topic, "batch-msg-" + i, qos, false);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("批量发布完成: {} 条, 耗时 {}ms", count, elapsed);
        return Map.of("success", true, "count", count, "elapsedMs", elapsed);
    }

    // ======================== 订阅 ========================

    /** 动态订阅 Topic */
    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestParam String topicFilter,
                                          @RequestParam(defaultValue = "1") int qos) {
        subscriber.subscribe(topicFilter, qos, (topic, msg) ->
                log.info("[动态订阅] topic={}, payload={}", topic, new String(msg.getPayload())));
        return Map.of("success", true, "topicFilter", topicFilter, "qos", qos);
    }

    /** 取消订阅 */
    @DeleteMapping("/subscribe/{topicFilter}")
    public Map<String, Object> unsubscribe(@PathVariable String topicFilter) {
        subscriber.unsubscribe(topicFilter);
        return Map.of("success", true, "topicFilter", topicFilter);
    }

    /** 查看当前订阅列表 */
    @GetMapping("/subscriptions")
    public Set<String> subscriptions() {
        return subscriber.getSubscribedTopics();
    }

    // ======================== 状态 ========================

    /** 连接状态 + 缓冲统计 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "connected", mqttClient.isConnected(),
                "serverUri", mqttClient.getServerURI(),
                "clientId", mqttClient.getClientId(),
                "pendingBufferSize", publisher.getPendingCount(),
                "flushedCount", publisher.getFlushedCount(),
                "droppedCount", publisher.getDroppedCount(),
                "subscriptions", subscriber.getSubscribedTopics().size(),
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
