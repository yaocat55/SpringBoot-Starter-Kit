package com.quick.springbootaliyunmqtt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT 连接状态监听器 —— 注册 {@link MqttCallbackExtended} 感知连接/断开/重连事件。
 * <p>
 * 三个事件：
 * <pre>
 * connectComplete — 首次连接成功 或 断线后重连成功（reconnect=true 表示重连）
 * connectionLost  — 连接断开（网络中断、Broker 重启等）
 * messageArrived  — 收到消息（此处不处理，由 MqttSubscriber 的订阅回调处理）
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttConnectionStateListener {

    private final MqttClient mqttClient;

    /** 连接状态（供 HealthIndicator 查询） */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** 重连回调（由外部注入） */
    private volatile Runnable onReconnectCallback;

    /** 断连回调（由外部注入） */
    private volatile Runnable onDisconnectCallback;

    @PostConstruct
    public void init() {
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected.set(true);
                if (reconnect) {
                    log.info("MQTT 重连成功: serverURI={}", serverURI);
                    if (onReconnectCallback != null) {
                        onReconnectCallback.run();
                    }
                } else {
                    log.info("MQTT 首次连接成功: serverURI={}", serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                connected.set(false);
                log.warn("MQTT 连接断开: {}", cause != null ? cause.getMessage() : "未知原因");
                if (onDisconnectCallback != null) {
                    onDisconnectCallback.run();
                }
            }

            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {
                // 消息处理由 MqttSubscriber 的订阅级回调负责，此处不处理
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                log.debug("MQTT 发送确认: messageId={}", token.getMessageId());
            }
        });
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setOnReconnectCallback(Runnable callback) {
        this.onReconnectCallback = callback;
    }

    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }
}
