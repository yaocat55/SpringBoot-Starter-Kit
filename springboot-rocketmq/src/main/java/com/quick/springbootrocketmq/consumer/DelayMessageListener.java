package com.quick.springbootrocketmq.consumer;

import com.quick.springbootrocketmq.model.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 延迟消息消费者 —— 接收延迟消息。
 * <p>
 * 适用场景：
 * <ul>
 *   <li>订单超时未支付取消（30 分钟后检查）</li>
 *   <li>定时提醒通知</li>
 *   <li>延迟重试</li>
 * </ul>
 * <p>
 * RocketMQ 内置 18 个延迟等级（非任意时间，如需任意延迟需业务层循环发送）：
 * <pre>
 * 1=1s   2=5s   3=10s   4=30s   5=1m   6=2m
 * 7=3m   8=4m   9=5m   10=6m  11=7m  12=8m
 * 13=9m  14=10m 15=20m  16=30m 17=1h  18=2h
 * </pre>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rocketmq-config.delay-topic:delay-topic}",
        consumerGroup = "${rocketmq-config.delay-topic:delay-topic}-consumer",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
public class DelayMessageListener implements RocketMQListener<MessageWrapper<?>> {

    @Override
    public void onMessage(MessageWrapper<?> wrapper) {
        // 计算延迟时间（从发送到消费的间隔）
        long delaySeconds = ChronoUnit.SECONDS.between(wrapper.getTimestamp(), LocalDateTime.now());

        log.info("[延迟消息] 收到延迟消息: messageType={}, delaySeconds={}, payload={}",
                wrapper.getMessageType(), delaySeconds, wrapper.getPayload());

        // 通用延迟任务处理
        processDelayTask(wrapper);
    }

    @SuppressWarnings("unchecked")
    private void processDelayTask(MessageWrapper<?> wrapper) {
        String messageType = wrapper.getMessageType();
        if (messageType == null) return;

        // 按消息类型路由处理
        switch (messageType) {
            case "delay-notify":
                // 延迟通知处理
                log.info("[延迟通知] 执行延迟任务: {}", wrapper.getPayload());
                break;
            default:
                log.info("[延迟任务] 未知类型: {}", messageType);
        }
    }
}
