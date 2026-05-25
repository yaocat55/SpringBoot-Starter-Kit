package com.quick.springbootdynamictp.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.dynamictp.core.executor.DtpExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 订单处理服务 —— 演示 DtpExecutor 的使用。
 * <p>
 * 所有线程池都在 application.yml 的 {@code dynamictp.executors} 中定义，
 * DynamicTp 自动创建并注册为 Spring Bean，通过 @Resource 按名称注入即可。
 */
@Slf4j
@Service
public class OrderService {

    @Resource(name = "order-processor")
    private DtpExecutor orderProcessor;

    @Resource(name = "notification-sender")
    private DtpExecutor notificationSender;

    @Resource(name = "report-generator")
    private DtpExecutor reportGenerator;

    // ======================== 业务方法 ========================

    /**
     * 模拟批量订单处理。
     */
    public void processOrders(int count) {
        log.info("开始处理 {} 个订单...", count);
        for (int i = 0; i < count; i++) {
            int orderNo = i + 1;
            orderProcessor.execute(() -> {
                log.info("[订单处理] 订单 #{} 开始处理, 线程: {}",
                        orderNo, Thread.currentThread().getName());
                try {
                    // 模拟业务耗时
                    TimeUnit.MILLISECONDS.sleep(200 + (long) (Math.random() * 300));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("[订单处理] 订单 #{} 处理完成", orderNo);
            });
        }
        log.info("{} 个订单已提交到线程池", count);
    }

    /**
     * 模拟异步发送通知。
     */
    public CompletableFuture<Void> sendNotification(String userId, String message) {
        return CompletableFuture.runAsync(() -> {
            log.info("[通知] 向用户 {} 发送: {}", userId, message);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("[通知] 用户 {} 发送完成", userId);
        }, notificationSender);
    }

    /**
     * 模拟生成报表（异步）。
     */
    public CompletableFuture<String> generateReport(String reportType) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[报表] 开始生成 {} 报表, 时间={}", reportType, LocalDateTime.now());
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String result = reportType + "-report-" + System.currentTimeMillis();
            log.info("[报表] {} 报表生成完毕: {}", reportType, result);
            return result;
        }, reportGenerator);
    }

    // ======================== 监控信息 ========================

    /**
     * 获取所有线程池的实时状态。
     */
    public String getPoolStatus() {
        return String.format("""
                ╔══════════════════════════════════════════════════════════╗
                ║              线程池实时状态                               ║
                ╠══════════════════════════════════════════════════════════╣
                ║ [订单处理] 核心=%d, 最大=%d, 活跃=%d, 队列=%d/%d
                ║ [通知发送] 核心=%d, 最大=%d, 活跃=%d, 队列=%d/%d
                ║ [报表生成] 核心=%d, 最大=%d, 活跃=%d, 队列=%d/%d
                ╚══════════════════════════════════════════════════════════╝""",
                orderProcessor.getCorePoolSize(), orderProcessor.getMaximumPoolSize(),
                orderProcessor.getActiveCount(), orderProcessor.getQueueSize(),
                orderProcessor.getQueueCapacity(),
                notificationSender.getCorePoolSize(), notificationSender.getMaximumPoolSize(),
                notificationSender.getActiveCount(), notificationSender.getQueueSize(),
                notificationSender.getQueueCapacity(),
                reportGenerator.getCorePoolSize(), reportGenerator.getMaximumPoolSize(),
                reportGenerator.getActiveCount(), reportGenerator.getQueueSize(),
                reportGenerator.getQueueCapacity()
        );
    }
}
