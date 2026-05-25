package com.quick.springbootdubbo.controller;

import com.quick.springbootdubbo.api.GreetingService;
import com.quick.springbootdubbo.consumer.GreetingConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST 控制器 —— 验证 Dubbo RPC 调用链路。
 * <p>
 * 提供两种调用方式：
 * <ul>
 *   <li>通过 {@link GreetingConsumer}（@Component 方式，推荐生产使用）</li>
 *   <li>直接通过 {@code @DubboReference} 注入（零封装方式，演示用）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/dubbo")
@RequiredArgsConstructor
public class GreetingController {

    private final GreetingConsumer greetingConsumer;

    /** 直接注入 Dubbo 远程代理（与 Consumer 内部注入的是同一个服务） */
    @DubboReference(version = "1.0.0")
    private GreetingService greetingService;

    // ======================== 通过 Consumer 调用 ========================

    /** 通过封装好的 Consumer 打招呼 */
    @GetMapping("/consumer/hello")
    public Map<String, Object> consumerHello(@RequestParam(defaultValue = "Dubbo") String name) {
        String result = greetingConsumer.callSayHello(name);
        return Map.of("success", true, "result", result, "caller", "GreetingConsumer");
    }

    /** 通过封装好的 Consumer 获取提供者信息 */
    @GetMapping("/consumer/info")
    public Map<String, Object> consumerInfo() {
        String result = greetingConsumer.callGetServerInfo();
        return Map.of("success", true, "result", result, "caller", "GreetingConsumer");
    }

    // ======================== 直接 @DubboReference 调用 ========================

    /** 直接通过 @DubboReference 注入的服务代理打招呼 */
    @GetMapping("/direct/hello")
    public Map<String, Object> directHello(@RequestParam(defaultValue = "Dubbo") String name) {
        String result = greetingService.sayHello(name);
        log.info("[Controller 直接调用] result={}", result);
        return Map.of("success", true, "result", result, "caller", "@DubboReference");
    }

    /** 直接通过 @DubboReference 获取提供者信息 */
    @GetMapping("/direct/info")
    public Map<String, Object> directInfo() {
        String result = greetingService.getServerInfo();
        log.info("[Controller 直接调用] result={}", result);
        return Map.of("success", true, "result", result, "caller", "@DubboReference");
    }

    // ======================== 对比测试 ========================

    /** 同时用 Consumer 和直接注入两种方式调用，对比结果是否一致 */
    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "Dubbo") String name) {
        String viaConsumer = greetingConsumer.callSayHello(name);
        String viaDirect = greetingService.sayHello(name + "-direct");
        return Map.of("success", true,
                "viaConsumer", viaConsumer,
                "viaDirect", viaDirect,
                "note", "两种调用方式最终走的是同一个 Dubbo 服务提供者");
    }
}
