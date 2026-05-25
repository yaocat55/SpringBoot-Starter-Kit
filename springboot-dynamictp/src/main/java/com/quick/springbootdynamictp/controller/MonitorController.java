package com.quick.springbootdynamictp.controller;

import com.quick.springbootdynamictp.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.dynamictp.core.DtpRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 监控与测试 Controller —— 演示手动触发任务和查看线程池状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/dynamictp")
@RequiredArgsConstructor
public class MonitorController {

    private final OrderService orderService;

    // ======================== 触发任务 ========================

    /** 批量下单（压测），观察线程池自动伸缩 */
    @PostMapping("/orders/batch")
    public Map<String, Object> batchOrders(@RequestParam(defaultValue = "50") int count) {
        orderService.processOrders(count);
        return Map.of("success", true, "submitted", count, "hint", "观察控制台日志，看线程池如何调度");
    }

    /** 发送通知 */
    @PostMapping("/notify")
    public Map<String, Object> sendNotify(@RequestParam String userId,
                                           @RequestParam String message) {
        CompletableFuture<Void> future = orderService.sendNotification(userId, message);
        return Map.of("success", true, "userId", userId, "async", true);
    }

    /** 生成报表 */
    @PostMapping("/report")
    public Map<String, Object> generateReport(@RequestParam(defaultValue = "daily") String type) {
        CompletableFuture<String> future = orderService.generateReport(type);
        return Map.of("success", true, "reportType", type, "async", true,
                "hint", "等待 3 秒后查看日志输出");
    }

    // ======================== 线程池状态 ========================

    /** 查看所有线程池实时状态 */
    @GetMapping("/status")
    public String status() {
        return orderService.getPoolStatus();
    }

    /**
     * 运行时动态调整线程池参数（无需重启）。
     * <p>
     * 改完之后立刻生效，这就是 DynamicTp 的核心价值。
     */
    @PutMapping("/adjust")
    public Map<String, Object> adjust(@RequestParam String poolName,
                                       @RequestParam(required = false) Integer coreSize,
                                       @RequestParam(required = false) Integer maxSize,
                                       @RequestParam(required = false) Integer queueCapacity) {
        var executor = DtpRegistry.getDtpExecutor(poolName);
        if (executor == null) {
            return Map.of("success", false, "error", "线程池不存在: " + poolName);
        }
        if (coreSize != null) executor.setCorePoolSize(coreSize);
        if (maxSize != null) executor.setMaximumPoolSize(maxSize);
        if (queueCapacity != null) executor.onRefreshQueueCapacity(queueCapacity);

        log.info("线程池 {} 参数已动态调整: core={}, max={}, queueCapacity={}",
                poolName, coreSize, maxSize, queueCapacity);
        return Map.of("success", true, "poolName", poolName,
                "newCoreSize", coreSize, "newMaxSize", maxSize,
                "newQueueCapacity", queueCapacity,
                "tip", "参数已实时生效，无需重启");
    }

    /** 列出所有 DynamicTp 管理的线程池名称 */
    @GetMapping("/pools")
    public Map<String, Object> listAllPools() {
        return Map.of("pools", DtpRegistry.getAllExecutorNames());
    }
}
