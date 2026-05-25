package com.quick.springrabbitmq.consumer;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.MessageWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者 —— 接收处理失败的消息。
 * <p>
 * 以下情况消息会进入死信：
 * <ol>
 *   <li>消费者 basicNack(requeue=false) 或 basicReject(requeue=false)</li>
 *   <li>消息 TTL 过期（队列设置 x-message-ttl 或消息设置 expiration）</li>
 *   <li>队列达到最大长度（x-max-length）</li>
 * </ol>
 * <p>
 * 死信处理策略：
 * <ul>
 *   <li>记录到数据库，人工处理</li>
 *   <li>发送告警（钉钉 / 邮件 / 企微）</li>
 *   <li>定时任务补偿重试</li>
 * </ul>
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @RabbitListener(queues = RabbitQueueConfig.DEAD_LETTER_QUEUE, ackMode = "MANUAL")
    public void handleDeadLetter(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 死信中的原始 routing key、exchange 等元信息
            String origRoutingKey = message.getMessageProperties()
                    .getHeader("x-first-death-routing-key");
            String origQueue = message.getMessageProperties()
                    .getHeader("x-first-death-queue");
            String deathReason = message.getMessageProperties()
                    .getHeader("x-first-death-reason");

            log.warn("[死信] 收到死信消息: messageId={}, messageType={}, " +
                            "origQueue={}, origRoutingKey={}, deathReason={}, payload={}",
                    wrapper.getMessageId(), wrapper.getMessageType(),
                    origQueue, origRoutingKey, deathReason, wrapper.getPayload());

            // ===== 死信处理策略（按需开启） =====

            // 1. 记录到数据库，方便后续排查和补偿
            // deadLetterRepository.save(buildDeadLetterRecord(wrapper, message));

            // 2. 发送告警
            // alertService.sendAlert("RabbitMQ 死信", wrapper.toString());

            // 3. 确认消费（死信被处理）
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[死信] 死信处理本身也失败了，人工介入: messageId={}", wrapper.getMessageId(), e);
            // 连死信处理都失败了，记录到本地文件/数据库后手动 ACK，避免死循环
            channel.basicAck(deliveryTag, false);
        }
    }

    /** 延迟消息消费者 —— 接收 TTL 到期后路由过来的延迟消息 */
    @RabbitListener(queues = RabbitQueueConfig.DELAY_PROCESS_QUEUE, ackMode = "MANUAL")
    public void handleDelayedMessage(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            long delay = System.currentTimeMillis() -
                    wrapper.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            log.info("[延迟消息] 收到延迟消息: messageType={}, 实际延迟={}ms, payload={}",
                    wrapper.getMessageType(), delay, wrapper.getPayload());

            // 业务处理
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
