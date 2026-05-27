package com.quick.springbootsentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.quick.springbootsentinel.handler.CustomBlockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 熔断演示 Controller。
 * <p>
 * 三种熔断策略：慢调用比例、异常比例、异常数。
 * 每个接口都有 curl 示例，方便快速压测。
 */
@Slf4j
@RestController
@RequestMapping("/api/circuit")
public class CircuitBreakController {

    // ======================== 慢调用比例熔断 ========================

    /**
     * 慢调用比例熔断 —— 响应超过 200ms 的比例 > 50% 时触发熔断。
     * <p>
     * 每 3 次请求中有 2 次随机慢（300-800ms），就会触发熔断。
     * 熔断 10 秒后进入半开状态。
     * <pre>
     *   # 连续调用 10 次，触发熔断后返回 429
     *   for i in {1..10}; do curl http://localhost:8085/api/circuit/slow; echo; done
     * </pre>
     */
    @GetMapping("/slow")
    @SentinelResource(value = "slowCallBreak",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> slowCallBreak() throws InterruptedException {
        // 随机模拟慢调用：70% 概率慢（>200ms），30% 正常
        boolean isSlow = ThreadLocalRandom.current().nextDouble() < 0.7;
        if (isSlow) {
            long delay = 300 + ThreadLocalRandom.current().nextLong(500);
            log.warn("[慢调用] 模拟慢请求，延迟 {}ms", delay);
            TimeUnit.MILLISECONDS.sleep(delay);
            return Map.of("success", true, "msg", "这次请求很慢（%dms），超过 200ms 阈值".formatted(delay));
        }
        log.info("[慢调用] 正常响应");
        return Map.of("success", true, "msg", "正常响应 < 200ms");
    }

    // ======================== 异常比例熔断 ========================

    /**
     * 异常比例熔断 —— 异常比例 > 50% 时触发熔断。
     * <p>
     * 每 3 次请求中 2 次抛异常，触发熔断后直接降级返回 JSON。
     * <pre>
     *   for i in {1..10}; do curl http://localhost:8085/api/circuit/exception-ratio; echo; done
     * </pre>
     */
    @GetMapping("/exception-ratio")
    @SentinelResource(value = "exceptionRatioBreak",
            fallback = "exceptionRatioFallback",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> exceptionRatioBreak() {
        boolean willFail = ThreadLocalRandom.current().nextDouble() < 0.7;
        if (willFail) {
            throw new RuntimeException("[异常比例] 模拟业务异常");
        }
        return Map.of("success", true, "msg", "正常响应（没有抛异常）");
    }

    /** 异常比例熔断的回退方法 —— 返回兜底数据 */
    public Map<String, Object> exceptionRatioFallback(Throwable t) {
        log.error("[异常比例-兜底] 触发 fallback: {}", t.getMessage());
        return Map.of("success", false, "msg", "服务降级：当前接口故障，已返回兜底数据",
                "fallback", true);
    }

    // ======================== 异常数熔断 ========================

    /**
     * 异常数熔断 —— 1 分钟内异常超过 5 次触发熔断。
     * <p>
     * 故意抛异常，连续调用 6 次触发熔断。
     * <pre>
     *   for i in {1..10}; do curl http://localhost:8085/api/circuit/exception-count; echo; done
     * </pre>
     */
    @GetMapping("/exception-count")
    @SentinelResource(value = "exceptionCountBreak",
            fallback = "exceptionCountFallback",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> exceptionCountBreak(@PathVariable(required = false) String id) {
        throw new RuntimeException("[异常数] 模拟业务异常，累计 6 次以上触发熔断");
    }

    public Map<String, Object> exceptionCountFallback(Throwable t) {
        log.error("[异常数-兜底] 触发 fallback: {}", t.getMessage());
        return Map.of("success", false, "msg", "服务已熔断/降级，返回兜底数据",
                "fallback", true);
    }

}
