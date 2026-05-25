package com.quick.springbootaliyunmqtt.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootaliyunmqtt.config.MqttConnectionStateListener;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT 消息发布器 —— 覆盖 QoS 0/1/2 三种发送模式，内置断线缓冲与自动重发。
 * <p>
 * 断线缓冲机制：
 * <pre>
 * 正常发送：publish() → mqttClient.publish() → Broker
 *
 * 断线期间：publish() → 写入本地缓冲队列（内存）
 * 重连完成：connectComplete 回调 → 自动清空缓冲队列 → 逐条补发
 * </pre>
 * <p>
 * 使用示例：
 * <pre>
 * mqttPublisher.publish("/device/demo/temperature", "{\"temp\":25.6}", 1, false);
 *
 * // 查看缓冲状态
 * long pending = mqttPublisher.getPendingCount();
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnBean(MqttClient.class)
public class MqttPublisher {

    private final MqttClient mqttClient;
    private final ObjectMapper mqttObjectMapper;
    private final MqttConnectionStateListener connectionStateListener;

    /** 断线缓冲队列（有界，防止内存溢出） */
    private final LinkedBlockingQueue<PendingMessage> buffer;

    /** 缓冲最大容量 */
    private final int maxBufferSize;

    /** 缓冲被丢弃的消息总数 */
    private final AtomicLong droppedCount = new AtomicLong(0);

    /** 缓冲成功补发的消息总数 */
    private final AtomicLong flushedCount = new AtomicLong(0);

    public MqttPublisher(MqttClient mqttClient,
                         ObjectMapper mqttObjectMapper,
                         MqttConnectionStateListener connectionStateListener) {
        this.mqttClient = mqttClient;
        this.mqttObjectMapper = mqttObjectMapper;
        this.connectionStateListener = connectionStateListener;
        this.maxBufferSize = 10000;
        this.buffer = new LinkedBlockingQueue<>(maxBufferSize);
    }

    @PostConstruct
    public void init() {
        connectionStateListener.setOnReconnectCallback(this::flushBuffer);
        connectionStateListener.setOnDisconnectCallback(() ->
                log.warn("MQTT 连接断开，消息将进入本地缓冲（当前积压: {}）", buffer.size()));
    }

    // ==================== 发布 API ====================

    /**
     * 发布消息到指定 Topic（指定 QoS 和 Retained）。
     * <p>
     * 断线时消息自动进入本地缓冲，重连后自动补发。
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        if (isConnected()) {
            doPublish(topic, payload, qos, retained);
        } else {
            bufferMessage(topic, payload, qos, retained);
        }
    }

    /** 发布消息（默认 QoS=1，不保留）。 */
    public void publish(String topic, String payload) {
        publish(topic, payload, 1, false);
    }

    /** 发布对象为 JSON。 */
    public void publishAsJson(String topic, Object payload, int qos, boolean retained) {
        try {
            String json = mqttObjectMapper.writeValueAsString(payload);
            publish(topic, json, qos, retained);
        } catch (Exception e) {
            log.error("MQTT 消息序列化失败: topic={}", topic, e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    /** 发布对象为 JSON（默认 QoS=1，不保留）。 */
    public void publishAsJson(String topic, Object payload) {
        publishAsJson(topic, payload, 1, false);
    }

    // ==================== 缓冲状态 ====================

    /** 当前缓冲中的消息数 */
    public long getPendingCount() {
        return buffer.size();
    }

    /** 累计丢弃的消息数（缓冲满时触发） */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /** 累计补发成功的消息数 */
    public long getFlushedCount() {
        return flushedCount.get();
    }

    /** 缓冲区最大容量 */
    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    // ==================== 内部实现 ====================

    private boolean isConnected() {
        return mqttClient.isConnected();
    }

    private void doPublish(String topic, String payload, int qos, boolean retained) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            mqttClient.publish(topic, message);
            log.debug("MQTT 发送成功: topic={}, qos={}, retained={}, payload={}", topic, qos, retained, truncate(payload));
        } catch (MqttException e) {
            log.error("MQTT 发送失败: topic={}, qos={}", topic, qos, e);
            throw new RuntimeException("MQTT 消息发送失败", e);
        }
    }

    /**
     * 断线时将消息写入本地缓冲。
     */
    private void bufferMessage(String topic, String payload, int qos, boolean retained) {
        PendingMessage msg = new PendingMessage(topic, payload, qos, retained, System.currentTimeMillis());
        if (buffer.offer(msg)) {
            log.debug("消息已缓冲: topic={}, qos={}, 缓冲队列大小={}/{}", topic, qos, buffer.size(), maxBufferSize);
        } else {
            droppedCount.incrementAndGet();
            log.warn("缓冲队列已满({})，消息被丢弃: topic={}", maxBufferSize, topic);
        }
    }

    /**
     * 重连后清空缓冲队列，逐条补发。
     */
    private void flushBuffer() {
        int size = buffer.size();
        if (size == 0) return;

        log.info("开始补发缓冲消息: count={}", size);
        int success = 0, fail = 0;

        // 一次性取出全部，避免边发边有新消息进来
        PendingMessage msg;
        while ((msg = buffer.poll()) != null) {
            try {
                doPublish(msg.topic, msg.payload, msg.qos, msg.retained);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("缓冲消息补发失败: topic={}, qos={}", msg.topic, msg.qos, e);
            }
        }

        flushedCount.addAndGet(success);
        log.info("缓冲消息补发完成: 成功={}, 失败={}", success, fail);
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    // ==================== 内部类 ====================

    @Data
    @AllArgsConstructor
    static class PendingMessage {
        String topic;
        String payload;
        int qos;
        boolean retained;
        long timestamp;
    }
}
