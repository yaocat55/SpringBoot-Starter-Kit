package com.quick.springrabbitmq.consumer;

import com.quick.springrabbitmq.config.RabbitQueueConfig;
import com.quick.springrabbitmq.model.MessageWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Fanout 模式消费者 —— 广播场景演示。
 * <p>
 * Fanout 交换机会忽略 Routing Key，消息会被投递到所有绑定的队列。
 * 这里两个消费者分别监听 fanout.queue1 和 fanout.queue2，
 * 发送一条消息后，两个消费者都会收到。
 * <p>
 * 典型场景：配置刷新通知、缓存清除广播、全服公告推送。
 */
@Slf4j
@Component
public class FanoutQueueConsumer {

    @RabbitListener(queues = RabbitQueueConfig.FANOUT_QUEUE_1, ackMode = "MANUAL")
    public void handleFanout1(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("[Fanout-1] 收到广播: messageType={}, payload={}",
                    wrapper.getMessageType(), wrapper.getPayload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = RabbitQueueConfig.FANOUT_QUEUE_2, ackMode = "MANUAL")
    public void handleFanout2(MessageWrapper<?> wrapper, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("[Fanout-2] 收到广播: messageType={}, payload={}",
                    wrapper.getMessageType(), wrapper.getPayload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
