package com.quick.springbootemqx.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * EMQX 消息订阅器 —— 支持动态订阅/取消订阅 + 通配符 Topic。
 * <p>
 * EMQX 支持以下通配符：
 * <ul>
 *   <li>{@code +} 单级通配：匹配一层，如 /device/+/event 匹配 /device/demo/event</li>
 *   <li>{@code #} 多级通配：匹配多层，如 /device/# 匹配 /device/demo/event/status</li>
 * </ul>
 * <p>
 * EMQX 还支持 {@code $share/group/topic} 格式的共享订阅，实现消费者负载均衡，
 * 这是 EMQX 企业版的亮点功能，标准 MQTT 5.0 也已纳入。
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
public class EmqxSubscriber {

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    /** 当前订阅列表: topicFilter → QoS */
    @Getter
    private final ConcurrentHashMap<String, Integer> subscriptions = new ConcurrentHashMap<>();

    public EmqxSubscriber(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // 默认订阅两个演示 Topic
        subscribe("/device/+/event", 1, (topic, msg) ->
                log.info("[设备事件] topic={}, payload={}", topic, new String(msg.getPayload())));

        subscribe("/device/+/telemetry", 1, (topic, msg) ->
                log.info("[设备遥测] topic={}, payload={}字节", topic, msg.getPayload().length));
    }

    // ======================== 订阅 API ========================

    /**
     * 订阅 Topic，并绑定回调处理器。
     *
     * @param topicFilter Topic 过滤器（支持 + / # 通配符）
     * @param qos         QoS 等级 0/1/2
     * @param handler     收到消息时的处理回调
     */
    public void subscribe(String topicFilter, int qos, BiConsumer<String, MqttMessage> handler) {
        try {
            MqttSubscription[] subs = {new MqttSubscription(topicFilter, qos)};
            IMqttMessageListener[] listeners = {(topic, message) -> handler.accept(topic, message)};
            mqttClient.subscribe(subs, listeners);
            subscriptions.put(topicFilter, qos);
            log.info("订阅成功: topicFilter={}, qos={}", topicFilter, qos);
        } catch (MqttException e) {
            log.error("订阅失败: topicFilter={}", topicFilter, e);
        }
    }

    /** 取消订阅。 */
    public void unsubscribe(String topicFilter) {
        try {
            mqttClient.unsubscribe(topicFilter);
            subscriptions.remove(topicFilter);
            log.info("取消订阅: topicFilter={}", topicFilter);
        } catch (MqttException e) {
            log.error("取消订阅失败: topicFilter={}", topicFilter, e);
        }
    }

    /** 获取所有已订阅的 Topic。 */
    public Set<String> getSubscribedTopics() {
        return subscriptions.keySet();
    }

    /** 是否为已连接状态。 */
    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    // ======================== 消息反序列化工具 ========================

    /**
     * 将收到的 MQTT 消息 payload 反序列化为目标类型。
     */
    public <T> T parsePayload(MqttMessage message, Class<T> type) {
        try {
            return objectMapper.readValue(message.getPayload(), type);
        } catch (Exception e) {
            log.error("消息反序列化失败: type={}, payload={}", type.getSimpleName(),
                    new String(message.getPayload()), e);
            return null;
        }
    }
}
