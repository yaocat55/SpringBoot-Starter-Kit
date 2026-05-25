package com.quick.springrabbitmq.consumer;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.MessageWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Direct 模式消费者 —— 演示手动 ACK / NACK / REJECT。
 * <p>
 * 手动 ACK 三种处理方式：
 * <ul>
 *   <li><b>basicAck</b> —— 确认消费成功，消息从队列中移除</li>
 *   <li><b>basicNack(requeue=true)</b> —— 消费失败，消息重新入队（注意死循环风险）</li>
 *   <li><b>basicNack(requeue=false)</b> —— 消费失败，消息丢弃或进入死信（推荐）</li>
 *   <li><b>basicReject</b> —— 同上，但只能拒绝单条（nack 可批量）</li>
 * </ul>
 */
@Slf4j
@Component
public class DirectQueueConsumer {

    @RabbitListener(queues = RabbitQueueConfig.DIRECT_QUEUE, ackMode = "MANUAL")
    public void handleDirect(MessageWrapper<?> wrapper, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("[Direct] 收到消息: messageType={}, messageId={}, payload={}",
                    wrapper.getMessageType(), wrapper.getMessageId(), wrapper.getPayload());

            // ===== 模拟业务处理 =====
            processBusinessLogic(wrapper);

            // 手动确认：业务成功，删除消息
            channel.basicAck(deliveryTag, false);

        } catch (BusinessException e) {
            // 业务异常（如金额不合法）—— 无需重试，直接拒绝让消息进入死信
            log.error("[Direct] 业务异常，消息拒绝: messageId={}, error={}",
                    wrapper.getMessageId(), e.getMessage());
            try {
                // requeue=false → 不重新入队，进入死信队列
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("[Direct] NACK 失败", ex);
            }
        } catch (Exception e) {
            // 系统异常（如网络抖动）—— 可重试，重新入队
            log.error("[Direct] 系统异常，消息重新入队: messageId={}", wrapper.getMessageId(), e);
            try {
                // requeue=true → 重新入队等待重试
                // 注意：配置了重试次数后，exceed 重试次数的消息会被 ListenerContainer 自动处理
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("[Direct] NACK 失败", ex);
            }
        }
    }

    private void processBusinessLogic(MessageWrapper<?> wrapper) {
        // 实际项目：在这里写真正的业务逻辑
    }

    /** 业务异常（不重试） */
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message) {
            super(message);
        }
    }
}
