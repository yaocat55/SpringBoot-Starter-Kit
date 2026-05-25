package com.quick.springbootemqx.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootemqx.config.EmqxConfig;
import com.quick.springbootemqx.config.EmqxConnectionStateListener;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * EMQX 消息发布器 —— 支持同步/异步发送 + 离线缓冲 + 断连重发。
 * <p>
 * 核心机制：当 EMQX 连接断开时，消息不会丢弃，而是暂存到环形缓冲区，
 * 重连成功后自动批量刷新。适用于设备弱网环境。
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
public class EmqxPublisher {

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final EmqxConnectionStateListener stateListener;
    private final EmqxConfig.EmqxProperties props;

    /** 离线消息缓冲区（有界队列） */
    private final LinkedBlockingQueue<PendingMessage> buffer;

    @Getter
    private volatile long droppedCount;

    @Getter
    private volatile long flushedCount;

    public EmqxPublisher(MqttClient mqttClient,
                         ObjectMapper objectMapper,
                         EmqxConnectionStateListener stateListener,
                         EmqxConfig.EmqxProperties props) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.stateListener = stateListener;
        this.props = props;
        this.buffer = new LinkedBlockingQueue<>(props.getMaxBufferSize());
    }

    @PostConstruct
    public void init() {
        // 重连后自动刷新离线缓冲区
        stateListener.setOnReconnectCallback(this::flushBuffer);
    }

    // ======================== 发布 API ========================

    /** 发布字符串消息到指定 Topic。 */
    public void publish(String topic, String payload, int qos, boolean retained) {
        if (mqttClient.isConnected()) {
            doSend(topic, payload, qos, retained);
        } else {
            bufferMessage(topic, payload, qos, retained);
        }
    }

    /** 发布字符串消息（QoS=1, 不保留）。 */
    public void publish(String topic, String payload) {
        publish(topic, payload, 1, false);
    }

    /** 发布 JSON 对象（QoS=1, 不保留）。 */
    public void publishAsJson(String topic, Object payload) {
        try {
            publish(topic, objectMapper.writeValueAsString(payload), 1, false);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: {}", payload.getClass().getSimpleName(), e);
        }
    }

    // ======================== 内部方法 ========================

    private void doSend(String topic, String payload, int qos, boolean retained) {
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            msg.setRetained(retained);
            mqttClient.publish(topic, msg);
            log.debug("消息已发送: topic={}, qos={}, size={}字节", topic, qos, payload.length());
        } catch (MqttException e) {
            log.error("发送失败: topic={}", topic, e);
            bufferMessage(topic, payload, qos, retained);
        }
    }

    /** 消息写入离线缓冲区，队列满时丢弃最旧的消息。 */
    private void bufferMessage(String topic, String payload, int qos, boolean retained) {
        PendingMessage pm = new PendingMessage(topic, payload, qos, retained, LocalDateTime.now());
        while (!buffer.offer(pm)) {
            buffer.poll(); // 丢弃旧消息，给新消息腾位置
            droppedCount++;
        }
        log.warn("消息已缓冲 (EMQX 离线), 当前缓冲数={}, 总丢弃数={}", buffer.size(), droppedCount);
    }

    /** 批量刷新离线缓冲区（重连后由 StateListener 自动调用）。 */
    private void flushBuffer() {
        int batch = buffer.size();
        if (batch == 0) return;

        log.info("开始刷新 EMQX 离线缓冲区, 待发送 {} 条", batch);
        int success = 0, fail = 0;
        PendingMessage pm;
        while ((pm = buffer.poll()) != null) {
            try {
                doSend(pm.topic, pm.payload, pm.qos, pm.retained);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("缓冲区消息重发失败: topic={}", pm.topic, e);
            }
        }
        flushedCount += success;
        log.info("EMQX 离线缓冲区刷新完成: 成功 {} 条, 失败 {} 条", success, fail);
    }

    /** 当前缓冲区待发送消息数。 */
    public int getPendingCount() {
        return buffer.size();
    }

    // ======================== 内部类 ========================

    private record PendingMessage(String topic, String payload, int qos, boolean retained,
                                  LocalDateTime timestamp) {}
}
