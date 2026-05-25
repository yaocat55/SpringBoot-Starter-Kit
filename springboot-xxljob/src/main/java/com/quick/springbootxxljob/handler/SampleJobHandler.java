package com.quick.springbootxxljob.handler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * XXL-JOB 示例 Job Handler —— 演示各种任务模式。
 * <p>
 * 每个 @XxlJob 注解的方法都会在 Admin 后台的「任务管理」中作为一个可调度的任务出现。
 * 调度方式选 "BEAN"，JobHandler 填方法名即可。
 */
@Slf4j
@Component
public class SampleJobHandler {

    // ======================== 1. 简单任务 ========================

    /**
     * 最简单的定时任务 —— 打印一行日志。
     * <p>
     * Admin 后台配置：
     *   - 运行模式: BEAN
     *   - JobHandler: simpleJob
     *   - Cron: 0/30 * * * * ?  (每 30 秒执行一次)
     */
    @XxlJob("simpleJob")
    public void simpleJob() {
        log.info("XXL-JOB 简单任务执行: 当前时间={}, 任务参数={}",
                LocalDateTime.now(), XxlJobHelper.getJobParam());
        XxlJobHelper.handleSuccess("执行成功");
    }

    // ======================== 2. 分片广播任务 ========================

    /**
     * 分片广播任务 —— 多实例并行处理大数据量。
     * <p>
     * Admin 后台配置：
     *   - 运行模式: BEAN
     *   - JobHandler: shardingJob
     *   - 路由策略: 分片广播
     * <p>
     * 原理：Admin 将任务广播给执行器集群的每个节点，节点根据 shardIndex/shardTotal
     * 只处理自己负责的那一段数据。比如 10000 条数据 4 个节点，每个节点处理 2500 条。
     */
    @XxlJob("shardingJob")
    public void shardingJob() {
        int shardIndex = XxlJobHelper.getShardIndex();   // 当前分片序号 (0-based)
        int shardTotal = XxlJobHelper.getShardTotal();   // 总分片数

        log.info("分片任务开始: 当前分片={}/{}, 参数={}",
                shardIndex, shardTotal, XxlJobHelper.getJobParam());

        // 模拟处理一批数据：每个分片只处理自己那段
        int totalRecords = 10000;
        int perShard = totalRecords / shardTotal;
        int start = shardIndex * perShard;
        int end = (shardIndex == shardTotal - 1) ? totalRecords : start + perShard;

        for (int i = start; i < end; i++) {
            // 实际业务：查询数据库 → 处理 → 写回
        }

        log.info("分片任务完成: 分片={}, 处理记录 {}→{} ({} 条)",
                shardIndex, start, end, end - start);
        XxlJobHelper.handleSuccess("分片 " + shardIndex + " 处理完成");
    }

    // ======================== 3. 带参数的任务 ========================

    /**
     * 通过 Admin 后台动态传参的任务。
     * <p>
     * Admin 后台配置：
     *   - 运行模式: BEAN
     *   - JobHandler: paramJob
     *   - 任务参数: {"userId":123,"type":"daily_report"}
     * <p>
     * 每次触发时可以在 Admin 后台改参数，无需重新部署。
     */
    @XxlJob("paramJob")
    public void paramJob() {
        String param = XxlJobHelper.getJobParam();
        log.info("参数化任务执行: param={}", param);

        // 根据参数执行不同逻辑
        if (param.contains("daily_report")) {
            log.info("  → 生成日报...");
        } else if (param.contains("sync")) {
            log.info("  → 执行数据同步...");
        }

        XxlJobHelper.handleSuccess("参数化任务完成");
    }

    // ======================== 4. 子任务链演示 ========================

    /**
     * 父任务 —— 执行完成后触发子任务。
     * <p>
     * Admin 后台配置：
     *   - JobHandler: parentJob
     *   - 子任务ID: 填 childJob 对应的任务 ID（多个用逗号分隔）
     *   - 阻塞处理策略: 串行
     */
    @XxlJob("parentJob")
    public void parentJob() {
        log.info("父任务执行: 处理第一阶段逻辑...");
        // 执行完成后 XXL-JOB 会自动触发子任务
        XxlJobHelper.handleSuccess("父任务完成，即将触发子任务");
    }

    @XxlJob("childJob")
    public void childJob() {
        log.info("子任务执行: 处理第二阶段逻辑（由父任务触发）...");
        XxlJobHelper.handleSuccess("子任务完成");
    }

    // ======================== 5. 超时/慢任务演示 ========================

    /**
     * 模拟长时间运行的任务。
     * <p>
     * Admin 后台可设置「任务超时时间」，超时后 Admin 会标记任务失败并告警。
     */
    @XxlJob("longRunningJob")
    public void longRunningJob() throws InterruptedException {
        log.info("长时间任务开始...");
        for (int i = 1; i <= 10; i++) {
            TimeUnit.SECONDS.sleep(1);
            log.info("  进度: {}/10", i);
        }
        log.info("长时间任务完成");
        XxlJobHelper.handleSuccess("长时间任务执行完毕");
    }

    // ======================== 6. 失败重试演示 ========================

    /**
     * 模拟失败的任务（配合 Admin 的失败重试机制）。
     * <p>
     * Admin 后台配置：
     *   - 失败重试次数: 3
     */
    @XxlJob("failRetryJob")
    public void failRetryJob() {
        String param = XxlJobHelper.getJobParam();
        log.info("失败重试任务开始, 参数={}", param);

        // 模拟：前两次失败，第三次成功
        if (param == null || param.isEmpty()) {
            log.error("任务失败 (缺少参数)");
            XxlJobHelper.handleFail("缺少必要参数，等待重试");
            return;
        }
        log.info("任务成功 (参数={})", param);
        XxlJobHelper.handleSuccess("重试成功");
    }

    // ======================== 7. 广播执行（广播所有节点） ========================

    /**
     * 广播任务 —— 所有在线执行器节点同时执行。
     * <p>
     * 适用场景：清理所有节点本地缓存、批量刷新配置等。
     * Admin 后台路由策略选 "广播"。
     */
    @XxlJob("broadcastJob")
    public void broadcastJob() {
        log.info("广播任务在本节点执行: 执行器地址={}, 时间={}",
                XxlJobHelper.getJobParam(), LocalDateTime.now());
        // 例如：清理本节点本地缓存
        XxlJobHelper.handleSuccess("本节点广播任务完成");
    }
}
