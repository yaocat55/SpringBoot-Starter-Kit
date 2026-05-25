package com.quick.springbootopenfeign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * OpenFeign 演示应用 —— 同一个应用同时充当"服务端"和"客户端"。
 * <ul>
 *   <li>服务端：{@code UserServerController} 暴露标准 REST API</li>
 *   <li>客户端：{@code @FeignClient} 声明式接口，像调本地方法一样调远程 HTTP</li>
 * </ul>
 * <p>
 * {@code @EnableFeignClients} 扫描所有 {@code @FeignClient} 接口并生成动态代理。
 */
@EnableFeignClients
@SpringBootApplication
public class SpringbootOpenfeignApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootOpenfeignApplication.class, args);
    }
}
