package com.quick.springrabbitmq.consumer;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.MessageWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Topic 模式消费者 —— 通配符路由演示。
 * <p>
 * 路由规则：
 * <ul>
 *   <li>{@code log.#} 匹配 log.error、log.info、log.warn 等所有以 log. 开头的 routing key</li>
 *   <li>{@code log.error} 精确匹配 log.error</li>
 * </ul>
 * <p>
 * 当发送 routingKey=log.error 时，两个消费者都会收到；
 * 当发送 routingKey=log.info 时，仅 logQueue 消费者收到。
 * <p>
 * 典型场景：日志分级处理、按地域推送、IoT 设备按类型路由。
 */
@Slf4j
@Component
public class TopicQueueConsumer {

    /** 接收所有 log.* 消息（含 log.error, log.info, log.debug...） */
    @RabbitListener(queues = RabbitQueueConfig.TOPIC_QUEUE_LOG, ackMode = "MANUAL")
    public void handleAllLogs(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            log.info("[Topic-Log] routingKey={}, payload={}", routingKey, wrapper.getPayload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /** 仅接收 log.error 消息 */
    @RabbitListener(queues = RabbitQueueConfig.TOPIC_QUEUE_ERROR, ackMode = "MANUAL")
    public void handleErrorLogs(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.error("[Topic-Error] 收到错误消息，触发告警: payload={}", wrapper.getPayload());
            // 生产环境：发钉钉/企微告警、写入告警表
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
