package com.quick.springbootrocketmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置 —— 自定义 Jackson ObjectMapper。
 * <p>
 * RocketMQ Spring Boot Starter 自动配置了 {@code RocketMQTemplate} 和
 * {@code RocketMQMessageConverter}，无需手动创建。
 * <p>
 * 此处仅注册带 Java 8 时间支持的 {@link ObjectMapper}，
 * RocketMQ 的消息序列化会自动使用它。
 * <p>
 * 与 RabbitMQ 的重要区别：
 * <ul>
 *   <li>RocketMQ 没有 Exchange，消息直接发到 Topic + Tag</li>
 *   <li>RocketMQ 消费者通过 {@code @RocketMQMessageListener} 注解自动注册</li>
 *   <li>RocketMQ 自带延迟消息（支持任意秒数，RocketMQ 5.x 起）</li>
 *   <li>RocketMQ 自带事务消息（Half-Message 二阶段提交）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RocketMQConfig {

    /**
     * 注册带 JavaTimeModule 的 ObjectMapper，供 RocketMQ 消息序列化使用。
     * <p>
     * RocketMQ 默认的 MessageConverter 会自动发现此 Bean 并使用它做 JSON 序列化。
     */
    @Bean
    public ObjectMapper rocketMQObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
