package com.quick.springrabbitmq.util;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * RabbitMQ 管理工具类 —— 运行时动态创建/删除队列、交换机、绑定。
 * <p>
 * 适用于需要运行时动态管理 RabbitMQ 资源的场景：
 * <ul>
 *   <li>多租户：每个租户动态创建专属队列</li>
 *   <li>临时队列：活动期间的临时消息通道</li>
 *   <li>运维：排查问题时查看队列状态</li>
 * </ul>
 */
@Component
public class RabbitUtil {

    private final RabbitAdmin rabbitAdmin;

    public RabbitUtil(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    // ==================== 队列操作 ====================

    /** 动态声明持久化队列 */
    public Queue declareQueue(String queueName) {
        Queue queue = QueueBuilder.durable(queueName).build();
        rabbitAdmin.declareQueue(queue);
        return queue;
    }

    /** 声明队列（带参数，如 TTL、死信、优先级） */
    public Queue declareQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                               java.util.Map<String, Object> arguments) {
        Queue queue = new Queue(queueName, durable, exclusive, autoDelete, arguments);
        rabbitAdmin.declareQueue(queue);
        return queue;
    }

    /** 删除队列 */
    public void deleteQueue(String queueName, boolean unused, boolean empty) {
        rabbitAdmin.deleteQueue(queueName, unused, empty);
    }

    /** 清空队列（保留队列，仅删除所有消息） */
    public void purgeQueue(String queueName) {
        rabbitAdmin.purgeQueue(queueName, false);
    }

    // ==================== 绑定操作 ====================

    /** 声明绑定关系 */
    public void declareBinding(Binding binding) {
        rabbitAdmin.declareBinding(binding);
    }

    /** 移除绑定 */
    public void removeBinding(Binding binding) {
        rabbitAdmin.removeBinding(binding);
    }

    // ==================== 查询操作 ====================

    /** 获取队列属性（消息数、消费者数等） */
    public Properties getQueueProperties(String queueName) {
        return rabbitAdmin.getQueueProperties(queueName);
    }

    /** 获取队列消息数 */
    public int getQueueMessageCount(String queueName) {
        Properties props = getQueueProperties(queueName);
        if (props != null) {
            Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            return count != null ? Integer.parseInt(count.toString()) : 0;
        }
        return 0;
    }

    /** 获取队列消费者数 */
    public int getQueueConsumerCount(String queueName) {
        Properties props = getQueueProperties(queueName);
        if (props != null) {
            Object count = props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
            return count != null ? Integer.parseInt(count.toString()) : 0;
        }
        return 0;
    }
}
