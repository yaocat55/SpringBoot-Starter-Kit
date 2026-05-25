package com.quick.springbootgrpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gRPC 演示应用 —— 同一个应用同时启动 gRPC Server 和 gRPC Client。
 * <ul>
 *   <li>gRPC Server（上游）：监听 9090 端口，对外暴露 GreetingService</li>
 *   <li>gRPC Client（下游）：通过 {@code @GrpcClient} 注入 stub，发起 gRPC 调用</li>
 * </ul>
 * <p>
 * 不需要额外注解：{@code grpc-spring-boot-starter} 的自动配置已接管 Server/Client 的启停。
 */
@SpringBootApplication
public class SpringbootGrpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootGrpcApplication.class, args);
    }
}
