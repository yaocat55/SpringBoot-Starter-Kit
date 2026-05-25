package com.quick.springbootdubbo.provider;

import com.quick.springbootdubbo.api.GreetingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GreetingService 提供者实现 —— 上游服务。
 * <p>
 * {@code @DubboService} 注解会将本实现注册为 Dubbo 服务提供者，
 * 注册到配置的注册中心（本示例使用组播），等待消费者调用。
 */
@Slf4j
@DubboService(version = "1.0.0")
public class GreetingServiceImpl implements GreetingService {

    @Override
    public String sayHello(String name) {
        log.info("[提供者] 收到 sayHello 请求, name={}", name);
        String message = String.format("Hello %s, from Dubbo Provider!", name);
        log.info("[提供者] sayHello 响应: {}", message);
        return message;
    }

    @Override
    public String getServerInfo() {
        String info = String.format("Provider[%s] @ %s",
                Thread.currentThread().getName(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("[提供者] getServerInfo 被调用: {}", info);
        return info;
    }
}
