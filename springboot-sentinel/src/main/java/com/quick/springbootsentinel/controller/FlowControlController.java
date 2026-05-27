package com.quick.springbootsentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.quick.springbootsentinel.handler.CustomBlockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流演示 Controller。
 * <p>
 * 每种限流策略都有独立接口，用 curl / 浏览器快速压测就能看到效果。
 * <p>
 * <h3>快速验证</h3>
 * <pre>
 *   # QPS 限流：连续刷新 3 次，第 3 次开始返回 429
 *   curl http://localhost:8085/api/flow/qps
 *
 *   # 并发限流：用 ab 或 jmeter 模拟 6 个并发
 *   ab -n 50 -c 6 http://localhost:8085/api/flow/thread
 *
 *   # 排队等待：连续快速请求，观察是否排队
 *   for i in {1..20}; do curl http://localhost:8085/api/flow/queue; echo; done
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/flow")
public class FlowControlController {

    // ======================== QPS 限流 ========================

    /**
     * QPS 限流 —— 每秒最多 2 个请求。
     * <p>
     * 规则见 {@code SentinelConfig.initFlowRules()} 中 "qpsLimit"。
     * 连续刷新 3 次浏览器就会看到"被限流"的 JSON。
     */
    @GetMapping("/qps")
    @SentinelResource(value = "qpsLimit",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> qpsLimit() {
        log.info("[QPS 限流] 请求通过");
        return Map.of("success", true, "msg", "请求正常通过 QPS 限流检查",
                "tip", "连续刷新 3 次试试");
    }

    // ======================== 并发线程数限流 ========================

    /**
     * 并发线程数限流 —— 同时最多 5 个线程。
     * <p>
     * 用 JMeter 或 ab 开 6+ 并发就能看到限流效果。
     * 单次 curl 看不到，因为这个接口返回很快。
     */
    @GetMapping("/thread")
    @SentinelResource(value = "threadLimit",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> threadLimit() throws InterruptedException {
        log.info("[线程限流] 开始处理，模拟耗时操作...");
        TimeUnit.MILLISECONDS.sleep(300);  // 模拟业务处理
        return Map.of("success", true, "msg", "请求正常通过并发限流检查",
                "tip", "用 ab -n 50 -c 6 来压测");
    }

    // ======================== Warm Up（冷启动） ========================

    /**
     * Warm Up —— 刚启动时 QPS 从 1/3 逐步升到目标值。
     * <p>
     * 服务刚启动的前 10 秒，QPS 会从 ~3 逐渐上升到 10。
     * 过了预热期后就是稳定的 10 QPS。
     */
    @GetMapping("/warmup")
    @SentinelResource(value = "warmUpLimit",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> warmUpLimit() {
        return Map.of("success", true, "msg", "Warm Up 阶段通过",
                "tip", "刚启动时 QPS 会从低到高逐渐放开");
    }

    // ======================== 排队等待 ========================

    /**
     * 排队等待（匀速器） —— 突发流量排队处理，不是直接拒绝。
     * <p>
     * 每秒通过 5 个请求，超过的排队最多 500ms。
     * 适用于对延迟不敏感但不想丢请求的场景（如异步任务提交）。
     */
    @GetMapping("/queue")
    @SentinelResource(value = "queueLimit",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> queueLimit() {
        log.info("[排队等待] 请求通过匀速器");
        return Map.of("success", true, "msg", "排队等待通过",
                "tip", "连续发送 20 个请求，观察哪些被限流");
    }

    // ======================== 热点参数限流 ========================

    /**
     * 热点参数限流 —— 对不同参数值设置不同 QPS。
     * <p>
     * 普通用户 userId 每秒 3 次，"vip" 用户每秒 10 次。
     * <pre>
     *   # 普通用户：快速刷 4 次触发限流
     *   curl "http://localhost:8085/api/flow/hot?userId=normal"
     *
     *   # VIP 用户：刷 10 次也不会限流
     *   curl "http://localhost:8085/api/flow/hot?userId=vip"
     * </pre>
     */
    @GetMapping("/hot")
    @SentinelResource(value = "hotParamLimit",
            blockHandler = "handleBlock",
            blockHandlerClass = CustomBlockHandler.class)
    public Map<String, Object> hotParamLimit(@RequestParam String userId) {
        log.info("[热点限流] userId={} 通过", userId);
        return Map.of("success", true, "msg", "参数 " + userId + " 通过热点限流",
                "tip", userId.equals("vip") ? "VIP 用户，QPS 上限更高" : "普通用户，QPS 上限 3");
    }

}
