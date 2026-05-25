package com.quick.springbootemqx.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EMQX 连接状态监听器 —— 监听连接/断连/重连事件。
 * <p>
 * MQTT 5.0 的 disconnectReasonString 能告诉你断连的具体原因（服务端重启、认证失败等），
 * 比 MQTT 3.x 的纯数字错误码友好很多。
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
public class EmqxConnectionStateListener implements MqttCallback {

    private final MqttClient mqttClient;

    @Getter
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** 重连回调 —— 供 Publisher 在重连后刷新离线缓冲区 */
    @Getter
    private volatile Runnable onReconnectCallback;

    /** 断连回调 —— 供外部监听连接状态变化 */
    @Getter
    private volatile Runnable onDisconnectCallback;

    public EmqxConnectionStateListener(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @PostConstruct
    public void init() {
        mqttClient.setCallback(this);
    }

    public void setOnReconnectCallback(Runnable callback) {
        this.onReconnectCallback = callback;
    }

    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    // ---------- MqttCallback 回调 ----------

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connected.set(true);
        log.info("EMQX {}连接成功: {}", reconnect ? "重连" : "首次", serverURI);
        if (reconnect && onReconnectCallback != null) {
            onReconnectCallback.run();
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        connected.set(false);
        // MQTT 5.0 特有的原因字符串
        String reason = disconnectResponse.getReasonString();
        log.warn("EMQX 连接断开: reasonCode={}, reasonString={}",
                disconnectResponse.getReturnCode(), reason);
        if (onDisconnectCallback != null) {
            onDisconnectCallback.run();
        }
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        log.error("EMQX MQTT 异常", exception);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 消息由 EmqxSubscriber 的 subscription-level callback 处理，
        // 此处仅做全局 trace 日志
        log.trace("收到消息: topic={}, qos={}, payloadLength={}",
                topic, message.getQos(), message.getPayload().length);
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        log.trace("消息送达确认: messageId={}", token.getMessageId());
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // MQTT 5.0 增强认证回调
        log.debug("收到 AUTH 包: reasonCode={}", reasonCode);
    }
}
