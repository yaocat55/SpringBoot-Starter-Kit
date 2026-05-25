package com.quick.springbootemqx;

import com.quick.springbootemqx.demo.EmqxDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringbootEmqxApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootEmqxApplication.class, args);
    }

    /**
     * Demo 模式启动时自动演示 Pub/Sub 流程。
     * 仅在 EmqxDemoService 存在时执行（即 emqx.enabled=false）。
     */
    @Bean
    @ConditionalOnBean(EmqxDemoService.class)
    CommandLineRunner demoRunner(EmqxDemoService demoService) {
        return args -> demoService.runStartupDemo();
    }
}
