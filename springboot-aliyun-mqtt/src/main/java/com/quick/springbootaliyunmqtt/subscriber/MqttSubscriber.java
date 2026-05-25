package com.quick.springbootaliyunmqtt.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootaliyunmqtt.model.MqttMessageWrapper;
import com.quick.springbootaliyunmqtt.model.OrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 订阅器 —— 支持精确 Topic 和通配符订阅，内置消息分发逻辑。
 * <p>
 * MQTT 通配符规则：
 * <pre>
 * "+" — 单层通配符，匹配一层 topic，如 /device/+/event 匹配 /device/demo/event
 * "#" — 多层通配符，匹配所有剩余层级，如 /device/# 匹配 /device 下的所有子 topic
 * </pre>
 * <p>
 * 业务异常直接 ACK（跳过），系统异常记录后继续（MQTT QoS≥1 会重投）。
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttSubscriber {

    private final MqttClient mqttClient;
    private final ObjectMapper mqttObjectMapper;

    /** 记录订阅的 Topic → QoS */
    private final Map<String, Integer> subscribedTopics = new ConcurrentHashMap<>();

    // ==================== 订阅注册 ====================

    /**
     * 应用启动后自动订阅演示 Topic。
     */
    @PostConstruct
    public void init() {
        subscribe("/device/+/event", 1, this::handleDeviceEvent);
        subscribe("/device/+/order", 1, this::handleOrderMessage);
        subscribe("/device/+/status", 1, this::handleStatusMessage);
        log.info("MQTT 订阅初始化完成: topics={}", subscribedTopics.keySet());
    }

    /**
     * 订阅一个 Topic（同步等待 Broker 确认）。
     *
     * @param topicFilter Topic 过滤器（支持通配符 + / #）
     * @param qos         订阅 QoS
     * @param listener    消息处理回调
     */
    public void subscribe(String topicFilter, int qos, IMqttMessageListener listener) {
        try {
            mqttClient.subscribe(topicFilter, qos, listener);
            subscribedTopics.put(topicFilter, qos);
            log.info("MQTT 订阅成功: topicFilter={}, qos={}", topicFilter, qos);
        } catch (MqttException e) {
            log.error("MQTT 订阅失败: topicFilter={}", topicFilter, e);
            throw new RuntimeException("MQTT 订阅失败", e);
        }
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(String topicFilter) {
        try {
            mqttClient.unsubscribe(topicFilter);
            subscribedTopics.remove(topicFilter);
            log.info("MQTT 取消订阅: topicFilter={}", topicFilter);
        } catch (MqttException e) {
            log.error("MQTT 取消订阅失败: topicFilter={}", topicFilter, e);
        }
    }

    // ==================== 获取信息 ====================

    public Set<String> getSubscribedTopics() {
        return subscribedTopics.keySet();
    }

    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    // ==================== 消息处理回调 ====================

    /**
     * 通用消息处理入口 —— 将字节数组反序列化为 MessageWrapper 后分发。
     */
    private void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        int qos = message.getQos();
        log.info("MQTT 收到消息: topic={}, qos={}, payload={}", topic, qos, truncate(payload));

        try {
            MqttMessageWrapper<?> wrapper = mqttObjectMapper.readValue(payload, MqttMessageWrapper.class);
            log.debug("反序列化成功: messageId={}, messageType={}", wrapper.getMessageId(), wrapper.getMessageType());
        } catch (JsonProcessingException e) {
            log.warn("消息反序列化失败（非 MessageWrapper 格式，可能是原生消息）: topic={}, payload={}", topic, truncate(payload));
        }
    }

    private void handleDeviceEvent(String topic, MqttMessage message) {
        log.info("[设备事件] topic={}, qos={}", topic, message.getQos());
        handleMessage(topic, message);
    }

    private void handleOrderMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            MqttMessageWrapper<OrderMessage> wrapper = mqttObjectMapper.readValue(
                    payload,
                    mqttObjectMapper.getTypeFactory().constructParametricType(MqttMessageWrapper.class, OrderMessage.class));
            OrderMessage order = wrapper.getPayload();
            log.info("[订单消息] orderId={}, orderNo={}, amount={}, status={}",
                    order.getOrderId(), order.getOrderNo(), order.getAmount(), order.getStatus());
        } catch (Exception e) {
            log.error("订单消息解析失败: topic={}, payload={}", topic, truncate(payload), e);
        }
    }

    private void handleStatusMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("[设备状态] topic={}, status={}", topic, truncate(payload));
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
