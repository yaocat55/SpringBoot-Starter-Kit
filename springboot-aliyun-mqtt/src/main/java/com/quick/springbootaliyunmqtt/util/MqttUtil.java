package com.quick.springbootaliyunmqtt.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 工具类 —— 提供连接管理、同步/异步发布、回调注册等便捷方法。
 * <p>
 * 主要能力：
 * <pre>
 * // 同步发布（等待 Broker 确认）
 * mqttUtil.publishAndWait("/device/demo/event", "hello", 1, 10, TimeUnit.SECONDS);
 *
 * // 异步发布（CompletableFuture）
 * mqttUtil.publishAsync("/device/demo/event", "hello", 1)
 *         .thenAccept(msgId -> log.info("发送成功: {}", msgId));
 *
 * // 查询连接状态
 * boolean connected = mqttUtil.isConnected();
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttUtil {

    private final MqttClient mqttClient;

    // ==================== 连接管理 ====================

    /**
     * 判断是否已连接到 Broker。
     */
    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    /**
     * 获取当前 Broker 地址。
     */
    public String getServerUri() {
        return mqttClient.getServerURI();
    }

    /**
     * 获取当前 Client ID。
     */
    public String getClientId() {
        return mqttClient.getClientId();
    }

    /**
     * 获取未完成的交付令牌数（用于监控积压消息）。
     */
    public int getPendingDeliveryTokens() {
        return mqttClient.getPendingDeliveryTokens().length;
    }

    // ==================== 同步发布（等待 Broker 确认） ====================

    /**
     * 同步发布消息，等待 Broker 确认后返回。
     * <p>
     * 通过 {@link MqttTopic#publish} 获取 DeliveryToken，
     * 阻塞等待 {@code token.waitForCompletion()} 完成。
     * <p>
     * 对应 MQTT 命令：PUBLISH topic qos retained payload
     *
     * @param topic    MQTT Topic
     * @param payload  消息负载
     * @param qos      QoS 0/1/2
     * @param retained 保留消息
     * @param timeout  超时时间
     * @param unit     超时单位
     * @throws RuntimeException 发送/超时失败时抛出
     */
    public void publishAndWait(String topic, String payload, int qos, boolean retained, long timeout, TimeUnit unit) {
        try {
            MqttTopic mqttTopic = mqttClient.getTopic(topic);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            mqttTopic.publish(message).waitForCompletion(unit.toMillis(timeout));
            log.debug("MQTT 同步发送完成: topic={}, qos={}", topic, qos);
        } catch (MqttException e) {
            log.error("MQTT 同步发送失败: topic={}", topic, e);
            throw new RuntimeException("MQTT 同步发送失败: topic=" + topic, e);
        }
    }

    /**
     * 同步发布（默认 10 秒超时，不保留消息）。
     */
    public void publishAndWait(String topic, String payload, int qos) {
        publishAndWait(topic, payload, qos, false, 10, TimeUnit.SECONDS);
    }

    // ==================== 异步发布 ====================

    /**
     * 异步发布消息，返回 CompletableFuture。
     *
     * @param topic    MQTT Topic
     * @param payload  消息负载
     * @param qos      QoS 0/1/2
     * @param retained 保留消息
     * @return CompletableFuture，完成时返回 messageId（QoS 0 返回 -1）
     */
    public CompletableFuture<Integer> publishAsync(String topic, String payload, int qos, boolean retained) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        try {
            // QoS 0 无确认，直接标记完成
            if (qos == 0) {
                mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
                future.complete(-1);
                return future;
            }

            MqttTopic mqttTopic = mqttClient.getTopic(topic);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);

            mqttTopic.publish(message).setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    future.complete(asyncActionToken.getMessageId());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (MqttException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // ==================== 回调注册 ====================

    /**
     * 注册全局 MQTT 回调（连接断开、消息到达、发送完成）。
     * <p>
     * 注意：设置此回调会覆盖 MqttSubscriber 中的订阅回调，
     * 一般仅用于监控/告警场景，不推荐与订阅器混用。
     */
    public void setCallback(MqttCallback callback) {
        mqttClient.setCallback(callback);
    }
}
