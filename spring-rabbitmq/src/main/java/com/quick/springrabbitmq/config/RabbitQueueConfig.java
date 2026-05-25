package com.quick.springrabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 队列 / 交换机 / 绑定关系声明配置。
 * <p>
 * 覆盖四种交换机类型：
 * <ul>
 *   <li><b>Direct</b> —— 精确匹配 routing key，最常用</li>
 *   <li><b>Fanout</b> —— 广播到所有绑定队列，忽略 routing key</li>
 *   <li><b>Topic</b> —— 通配符匹配 routing key（* 匹配一个词，# 匹配零个或多个词）</li>
 *   <li><b>Headers</b> —— 按 Header 键值对匹配（此处省略，用得少）</li>
 * </ul>
 * <p>
 * 额外包含：
 * <ul>
 *   <li>死信队列</li>
 *   <li>延迟队列（基于 TTL + 死信实现，不依赖插件）</li>
 * </ul>
 */
@Configuration
public class RabbitQueueConfig {

    // ==================== Direct 模式 ====================
    public static final String DIRECT_EXCHANGE = "direct.exchange";
    public static final String DIRECT_QUEUE = "direct.queue";
    public static final String DIRECT_ROUTING_KEY = "direct.routingkey";

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public Queue directQueue() {
        return QueueBuilder.durable(DIRECT_QUEUE).build();
    }

    @Bean
    public Binding directBinding() {
        return BindingBuilder.bind(directQueue()).to(directExchange()).with(DIRECT_ROUTING_KEY);
    }

    // ==================== Fanout 模式（广播） ====================
    public static final String FANOUT_EXCHANGE = "fanout.exchange";
    public static final String FANOUT_QUEUE_1 = "fanout.queue1";
    public static final String FANOUT_QUEUE_2 = "fanout.queue2";

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue fanoutQueue1() {
        return QueueBuilder.durable(FANOUT_QUEUE_1).build();
    }

    @Bean
    public Queue fanoutQueue2() {
        return QueueBuilder.durable(FANOUT_QUEUE_2).build();
    }

    @Bean
    public Binding fanoutBinding1() {
        return BindingBuilder.bind(fanoutQueue1()).to(fanoutExchange());
    }

    @Bean
    public Binding fanoutBinding2() {
        return BindingBuilder.bind(fanoutQueue2()).to(fanoutExchange());
    }

    // ==================== Topic 模式（通配符） ====================
    public static final String TOPIC_EXCHANGE = "topic.exchange";
    public static final String TOPIC_QUEUE_LOG = "topic.queue.log";    // 接收所有日志
    public static final String TOPIC_QUEUE_ERROR = "topic.queue.error"; // 仅接收 error 级别
    public static final String TOPIC_ROUTING_KEY_LOG = "log.#";         // 匹配 log.info, log.error, log.debug 等
    public static final String TOPIC_ROUTING_KEY_ERROR = "log.error";   // 精确匹配

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public Queue topicQueueLog() {
        return QueueBuilder.durable(TOPIC_QUEUE_LOG).build();
    }

    @Bean
    public Queue topicQueueError() {
        return QueueBuilder.durable(TOPIC_QUEUE_ERROR).build();
    }

    @Bean
    public Binding topicBindingLog() {
        return BindingBuilder.bind(topicQueueLog()).to(topicExchange()).with(TOPIC_ROUTING_KEY_LOG);
    }

    @Bean
    public Binding topicBindingError() {
        return BindingBuilder.bind(topicQueueError()).to(topicExchange()).with(TOPIC_ROUTING_KEY_ERROR);
    }

    // ==================== 死信队列 ====================
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter.routingkey";

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY);
    }

    // ==================== 延迟队列（TTL + 死信实现，不依赖 rabbitmq_delayed_message_exchange 插件） ====================
    // 原理：消息发到"延迟队列"（绑定了死信交换机），TTL 到期后自动进入死信 → 死信交换机路由到真实消费队列
    public static final String DELAY_EXCHANGE = "delay.exchange";
    public static final String DELAY_QUEUE = "delay.queue";
    public static final String DELAY_ROUTING_KEY = "delay.routingkey";
    // 下面这个队列才是真正消费延迟消息的队列
    public static final String DELAY_PROCESS_EXCHANGE = "delay.process.exchange";
    public static final String DELAY_PROCESS_QUEUE = "delay.process.queue";
    public static final String DELAY_PROCESS_ROUTING_KEY = "delay.process.routingkey";

    /**
     * 延迟处理交换机（死信消息的最终目的地）
     */
    @Bean
    public DirectExchange delayProcessExchange() {
        return new DirectExchange(DELAY_PROCESS_EXCHANGE, true, false);
    }

    @Bean
    public Queue delayProcessQueue() {
        return QueueBuilder.durable(DELAY_PROCESS_QUEUE).build();
    }

    @Bean
    public Binding delayProcessBinding() {
        return BindingBuilder.bind(delayProcessQueue()).to(delayProcessExchange()).with(DELAY_PROCESS_ROUTING_KEY);
    }

    /**
     * 延迟队列 —— 没有消费者，消息 TTL 到期后自动转发到死信交换机
     * 死信交换机指向 DELAY_PROCESS_EXCHANGE，路由键 DELAY_PROCESS_ROUTING_KEY
     */
    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DELAY_PROCESS_EXCHANGE);
        args.put("x-dead-letter-routing-key", DELAY_PROCESS_ROUTING_KEY);
        return QueueBuilder.durable(DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING_KEY);
    }
}
