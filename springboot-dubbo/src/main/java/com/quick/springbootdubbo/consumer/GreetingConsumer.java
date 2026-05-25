package com.quick.springbootdubbo.consumer;

import com.quick.springbootdubbo.api.GreetingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Dubbo 消费者 —— 下游服务。
 * <p>
 * {@code @DubboReference} 注入远程服务代理，本示例中因为提供者和消费者
 * 在同一个 JVM，Dubbo 会自动优化为 injvm 调用（不走网络），但调用链路和
 * 远程 RPC 完全一致。分离部署后自动切换为远程调用，业务代码零改动。
 */
@Slf4j
@Component
public class GreetingConsumer {

    @DubboReference(version = "1.0.0")
    private GreetingService greetingService;

    /**
     * 通过 Dubbo RPC 调用远程 sayHello。
     */
    public String callSayHello(String name) {
        log.info("[消费者] 发起 Dubbo RPC 调用 sayHello, name={}", name);
        String result = greetingService.sayHello(name);
        log.info("[消费者] 收到响应: {}", result);
        return result;
    }

    /**
     * 通过 Dubbo RPC 获取提供者信息。
     */
    public String callGetServerInfo() {
        log.info("[消费者] 发起 Dubbo RPC 调用 getServerInfo");
        String result = greetingService.getServerInfo();
        log.info("[消费者] 收到响应: {}", result);
        return result;
    }
}
