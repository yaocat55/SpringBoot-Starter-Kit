package com.quick.springbootdubbo;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dubbo 3.x 演示应用 —— 同一个应用同时充当"提供者"和"消费者"两个角色。
 * <ul>
 *   <li>提供者（上游）：对外暴露 GreetingService 接口</li>
 *  *   <li>消费者（下游）：通过 @DubboReference 注入 GreetingService 发起 RPC 调用</li>
 * </ul>
 */
@EnableDubbo
@SpringBootApplication
public class SpringbootDubboApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootDubboApplication.class, args);
    }
}
