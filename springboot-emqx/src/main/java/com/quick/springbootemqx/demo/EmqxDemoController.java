package com.quick.springbootemqx.demo;

import com.quick.springbootemqx.model.DeviceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Demo 模式 REST 接口 —— 当没有真实 MQTT 客户端时激活。
 * <p>
 * 接口路径和真实 {@code EmqxController} 完全一致，确保切换到真实 EMQX 后
 * 前端/调用方无需改动 URL。
 */
@Slf4j
@RestController
@RequestMapping("/api/emqx")
@RequiredArgsConstructor
@ConditionalOnMissingBean(org.eclipse.paho.mqttv5.client.MqttClient.class)
public class EmqxDemoController {

    private final EmqxDemoService demoService;

    // ======================== 发布 ========================

    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestParam String topic,
                                       @RequestParam String payload) {
        demoService.publish(topic, payload);
        return Map.of("mode", "demo", "success", true, "topic", topic);
    }

    @PostMapping("/device/{deviceId}/telemetry")
    public Map<String, Object> publishTelemetry(@PathVariable String deviceId,
                                                 @RequestBody DeviceMessage data) {
        demoService.publishDeviceTelemetry(deviceId, data);
        return Map.of("mode", "demo", "success", true, "deviceId", deviceId);
    }

    @PostMapping("/publish/batch")
    public Map<String, Object> publishBatch(@RequestParam String topic,
                                             @RequestParam(defaultValue = "100") int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            demoService.publish(topic, "batch-msg-" + i);
        }
        long elapsed = System.currentTimeMillis() - start;
        return Map.of("mode", "demo", "success", true, "count", count, "elapsedMs", elapsed);
    }

    // ======================== 订阅 ========================

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestParam String topicFilter) {
        demoService.subscribe(topicFilter, (topic, payload) ->
                log.info("[Demo 动态订阅] topic={}, payload={}", topic, payload));
        return Map.of("mode", "demo", "success", true, "topicFilter", topicFilter);
    }

    @DeleteMapping("/subscribe/{topicFilter}")
    public Map<String, Object> unsubscribe(@PathVariable String topicFilter) {
        demoService.unsubscribe(topicFilter);
        return Map.of("mode", "demo", "success", true, "topicFilter", topicFilter);
    }

    @GetMapping("/subscriptions")
    public Map<String, Object> subscriptions() {
        return Map.of("mode", "demo", "subscriptions", demoService.getSubscriptions());
    }

    // ======================== 状态 ========================

    @GetMapping("/status")
    public Map<String, Object> status() {
        return demoService.getStatus();
    }

    @GetMapping("/messages")
    public Map<String, Object> messages() {
        return Map.of("mode", "demo", "count", demoService.getMessageLog().size(),
                "messages", demoService.getMessageLog());
    }
}
