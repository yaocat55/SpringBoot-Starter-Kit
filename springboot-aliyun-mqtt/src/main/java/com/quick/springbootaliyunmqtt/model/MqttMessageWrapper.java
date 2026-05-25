package com.quick.springbootaliyunmqtt.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MQTT 通用消息包装器，与 Kafka/RabbitMQ 模块保持一致的 envelope 风格。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttMessageWrapper<T> {

    /** 消息唯一 ID */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();

    /** 业务 Key（如设备 ID、订单号） */
    private String messageKey;

    /** 消息来源 */
    private String source;

    /** 消息时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 消息类型标识 */
    private String messageType;

    /** 业务负载 */
    private T payload;

    // ==================== 工厂方法 ====================

    public static <T> MqttMessageWrapper<T> of(String messageType, T payload) {
        return MqttMessageWrapper.<T>builder()
                .messageType(messageType)
                .payload(payload)
                .build();
    }

    public static <T> MqttMessageWrapper<T> of(String messageType, String messageKey, T payload) {
        return MqttMessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .payload(payload)
                .build();
    }

    public static <T> MqttMessageWrapper<T> of(String messageType, String messageKey, String source, T payload) {
        return MqttMessageWrapper.<T>builder()
                .messageType(messageType)
                .messageKey(messageKey)
                .source(source)
                .payload(payload)
                .build();
    }

    /**
     * 序列化为 JSON 字符串（用于 MQTT 发布）
     */
    @SneakyThrows
    public String toJson() {
        return new ObjectMapper().writeValueAsString(this);
    }
}
