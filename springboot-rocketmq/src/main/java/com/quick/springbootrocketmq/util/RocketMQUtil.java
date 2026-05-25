package com.quick.springbootrocketmq.util;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RocketMQ 工具类 —— 原生客户端操作辅助。
 * <p>
 * {@link RocketMQTemplate} 已满足大部分需求，此类提供原生 {@link DefaultMQProducer} 的
 * 低阶 API 封装，适用于需要直接操作底层客户端的场景。
 */
@Component
public class RocketMQUtil {

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQUtil(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    // ==================== 消息查询 ====================

    /**
     * 根据 msgId 查询消息（需要 Broker 开启 trace）。
     * <p>注意：此 API 依赖消息轨迹功能，非所有环境都可用。
     */
    public Message queryMessageById(String topic, String msgId) throws Exception {
        DefaultMQProducer producer = rocketMQTemplate.getProducer();
        return producer.viewMessage(topic, msgId);
    }

    // ==================== 原生消息发送（不走 Template） ====================

    /** 使用原生 DefaultMQProducer 发送消息（适合需要精细控制的场景） */
    public SendResult sendRawMessage(String topic, String tag, String key, String body) throws Exception {
        DefaultMQProducer producer = rocketMQTemplate.getProducer();
        Message message = new Message(topic, tag, key, body.getBytes(StandardCharsets.UTF_8));
        return producer.send(message);
    }
}
