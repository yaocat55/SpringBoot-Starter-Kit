package com.quick.springbootemqx.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootemqx.model.DeviceMessage;
import com.quick.springbootemqx.model.EmqxMessageWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * EMQX 演示模式 —— 内置内存 Pub/Sub，无需安装 EMQX 即可跑通完整流程。
 * <p>
 * 当 {@code emqx.enabled=false}（默认值）时，这个 Bean 代替真实的 MQTT 客户端工作。
 * 消息在 JVM 内存中通过 Topic 路由，对外暴露完全相同的 REST 接口。
 * <p>
 * 切换到真实 EMQX：只需将 {@code emqx.enabled=true} 并配置 Broker 地址，
 * 本 Bean 会被 {@code @ConditionalOnMissingBean(MqttClient.class)} 自动替换掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnMissingBean(org.eclipse.paho.mqttv5.client.MqttClient.class)
public class EmqxDemoService {

    private final ObjectMapper objectMapper;

    /** Topic → 订阅者列表 */
    private final ConcurrentHashMap<String, List<BiConsumer<String, String>>> subscribers = new ConcurrentHashMap<>();

    /** 已发送的消息记录（用于随时查看） */
    private final List<Map<String, Object>> messageLog = new CopyOnWriteArrayList<>();

    private static final int MAX_LOG_SIZE = 200;

    // ======================== 发布 API ========================

    /** 发布消息到指定 Topic，所有匹配的订阅者都会收到。 */
    public void publish(String topic, String payload) {
        log.info("[Demo 模式] 发布消息 → topic={}, payload={}", topic, payload);

        // 记录到消息日志
        if (messageLog.size() < MAX_LOG_SIZE) {
            messageLog.add(Map.of(
                    "topic", topic,
                    "payload", payload,
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        // 分发给匹配的订阅者
        subscribers.forEach((filter, listeners) -> {
            if (topicMatches(filter, topic)) {
                for (BiConsumer<String, String> listener : listeners) {
                    try {
                        listener.accept(topic, payload);
                    } catch (Exception e) {
                        log.error("[Demo 模式] 订阅者处理失败: filter={}", filter, e);
                    }
                }
            }
        });
    }

    /** 发布设备遥测消息（自动套信封）。 */
    public void publishDeviceTelemetry(String deviceId, DeviceMessage data) {
        EmqxMessageWrapper<DeviceMessage> wrapper = EmqxMessageWrapper.of(
                "DEVICE_TELEMETRY", deviceId, data);
        String topic = "/device/" + deviceId + "/telemetry";
        try {
            String json = objectMapper.writeValueAsString(wrapper);
            publish(topic, json);
        } catch (Exception e) {
            log.error("[Demo 模式] JSON 序列化失败", e);
        }
    }

    // ======================== 订阅 API ========================

    /** 订阅某个 Topic Filter（支持 + 通配符）。 */
    public void subscribe(String topicFilter, BiConsumer<String, String> handler) {
        subscribers.computeIfAbsent(topicFilter, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("[Demo 模式] 订阅成功: topicFilter={}", topicFilter);
    }

    /** 取消订阅。 */
    public void unsubscribe(String topicFilter) {
        subscribers.remove(topicFilter);
    }

    // ======================== 查询 API ========================

    /** 获取当前订阅列表。 */
    public Map<String, Integer> getSubscriptions() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        subscribers.forEach((filter, listeners) -> result.put(filter, listeners.size()));
        return result;
    }

    /** 获取最近的消息记录。 */
    public List<Map<String, Object>> getMessageLog() {
        return messageLog;
    }

    /** 获取状态摘要。 */
    public Map<String, Object> getStatus() {
        return Map.of(
                "mode", "demo (JVM 内存 Pub/Sub，无需 EMQX)",
                "connected", true,
                "subscriptions", subscribers.size(),
                "messageCount", messageLog.size(),
                "timestamp", LocalDateTime.now().toString(),
                "tip", "设置 emqx.enabled=true 即可切换到真实 EMQX"
        );
    }

    // ======================== Topic 匹配逻辑 ========================

    /**
     * 简单的 MQTT Topic 匹配（支持 + 单级通配）。
     * @param filter 订阅的 Topic Filter，如 /device/+/event
     * @param topic  实际 Topic，如 /device/demo/event
     */
    private boolean topicMatches(String filter, String topic) {
        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");
        if (filterParts.length != topicParts.length) return false;
        for (int i = 0; i < filterParts.length; i++) {
            if (filterParts[i].equals("+") || filterParts[i].equals("#")) continue;
            if (!filterParts[i].equals(topicParts[i])) return false;
        }
        return true;
    }

    // ======================== 启动演示 ========================

    /** 自动运行一段演示：监听 Topic → 发送消息 → 收到回调。 */
    public void runStartupDemo() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║        EMQX Demo 模式 —— 零依赖 Pub/Sub 演示          ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  当前工作在 JVM 内存模式，无需安装任何中间件            ║");
        log.info("║  设 emqx.enabled=true 即可切换到真实 EMQX Broker      ║");
        log.info("╚══════════════════════════════════════════════════════╝");
        log.info("");

        // 1. 订阅一个 Topic
        subscribe("/device/+/event", (topic, payload) ->
                log.info("  📩 [Demo 订阅回调] 收到消息: topic={}, payload={}...",
                        topic, payload.substring(0, Math.min(50, payload.length()))));

        // 2. 发布一条消息
        publish("/device/demo/event", "{\"type\":\"temperature\",\"value\":25.5,\"unit\":\"celsius\"}");

        // 3. 发布一条设备遥测
        DeviceMessage telemetry = DeviceMessage.builder()
                .deviceId("demo")
                .dataType("humidity")
                .value(new BigDecimal("68.3"))
                .unit("percent")
                .collectTime(LocalDateTime.now())
                .build();
        publishDeviceTelemetry("demo", telemetry);

        // 4. 打印状态
        log.info("");
        log.info("当前状态: 订阅数={}, 消息记录数={}", subscribers.size(), messageLog.size());
        log.info("REST API 可用 → http://localhost:8081/api/emqx/status");
        log.info("");
    }
}
