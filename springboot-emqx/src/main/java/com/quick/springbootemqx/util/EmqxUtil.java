package com.quick.springbootemqx.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * EMQX 工具类 —— 提供同步/异步发送、连接状态查询等便捷方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MqttClient.class)
public class EmqxUtil {

    private final MqttClient mqttClient;

    /** 是否已连接。 */
    public boolean isConnected() {
        return mqttClient.isConnected();
    }

    /** 获取 Broker 地址。 */
    public String getServerUri() {
        return mqttClient.getServerURI();
    }

    /** 获取客户端 ID。 */
    public String getClientId() {
        return mqttClient.getClientId();
    }

    /**
     * 同步发送消息。
     * MQTT 5.0 Paho 客户端的 publish 在 QoS 1/2 时会等待 Broker 确认后返回。
     *
     * @param topic   目标 Topic
     * @param payload 消息内容
     * @param qos     QoS 等级
     * @param retained 是否保留消息
     */
    public void publish(String topic, String payload, int qos, boolean retained) throws MqttException {
        mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
    }

    /** 同步发送（QoS 1, 不保留）。 */
    public void publish(String topic, String payload) throws MqttException {
        publish(topic, payload, 1, false);
    }

    /**
     * 异步发送，返回 CompletableFuture。
     * 发送操作在后台线程执行，QoS 1/2 等待 Broker 确认后完成。
     */
    public CompletableFuture<Void> publishAsync(String topic, String payload, int qos) {
        return CompletableFuture.runAsync(() -> {
            try {
                mqttClient.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, false);
            } catch (MqttException e) {
                throw new RuntimeException("异步发送失败: topic=" + topic, e);
            }
        });
    }

    /** 异步发送（QoS 1, 不保留）。 */
    public CompletableFuture<Void> publishAsync(String topic, String payload) {
        return publishAsync(topic, payload, 1);
    }
}
