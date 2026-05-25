package com.quick.springbootxxljob.controller;

import com.quick.springbootxxljob.handler.SampleJobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 手动触发 Job 的 REST 接口 —— 不依赖 Admin 调度中心也能直接调接口测试。
 * <p>
 * 这些接口直接调用 SampleJobHandler 的方法，效果和 Admin 调度完全一样。
 * 方便本地开发调试，不用每次都去 Admin 后台点「执行一次」。
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final SampleJobHandler sampleJobHandler;

    /** 触发简单任务 */
    @PostMapping("/simple")
    public Map<String, Object> triggerSimpleJob() {
        sampleJobHandler.simpleJob();
        return Map.of("success", true, "job", "simpleJob", "time", LocalDateTime.now().toString());
    }

    /** 触发分片广播任务 */
    @PostMapping("/sharding")
    public Map<String, Object> triggerShardingJob() {
        sampleJobHandler.shardingJob();
        return Map.of("success", true, "job", "shardingJob", "time", LocalDateTime.now().toString());
    }

    /** 触发带参数的任务 */
    @PostMapping("/param")
    public Map<String, Object> triggerParamJob(@RequestParam(defaultValue = "daily_report") String type) {
        // 注意：XxlJobHelper.getJobParam() 只在 Admin HTTP 回调时才有值
        // 这里直接调方法，param 会为空，所以 paramJob 需要改造才能接收参数
        // 此处仅演示手动触发
        sampleJobHandler.paramJob();
        return Map.of("success", true, "job", "paramJob", "param", type,
                "note", "直接调用时 XxlJobHelper.getJobParam() 为空，实际参数由 Admin 调度时传入",
                "time", LocalDateTime.now().toString());
    }

    /** 触发父任务（父任务完成后会自动触发子任务） */
    @PostMapping("/parent-child")
    public Map<String, Object> triggerParentChild() {
        sampleJobHandler.parentJob();
        sampleJobHandler.childJob();
        return Map.of("success", true, "job", "parentJob → childJob", "time", LocalDateTime.now().toString());
    }

    /** 触发长时间运行的任务 */
    @PostMapping("/long-running")
    public Map<String, Object> triggerLongRunning() throws InterruptedException {
        sampleJobHandler.longRunningJob();
        return Map.of("success", true, "job", "longRunningJob", "time", LocalDateTime.now().toString());
    }

    /** 触发失败重试任务（不带参 → 模拟失败；带参 → 成功） */
    @PostMapping("/fail-retry")
    public Map<String, Object> triggerFailRetry(@RequestParam(required = false) String param) {
        // 由于是直接调用无法模拟 XXL-JOB 的重试机制，这里演示传参控制成败
        // 实际重试由 Admin 在任务失败后自动发起
        sampleJobHandler.failRetryJob();
        return Map.of("success", true, "job", "failRetryJob",
                "note", "直接调用时 XxlJobHelper.getJobParam() 为空=模拟失败；Admin 调度时传参则可成功，并自动重试",
                "time", LocalDateTime.now().toString());
    }

    /** 触发广播任务 */
    @PostMapping("/broadcast")
    public Map<String, Object> triggerBroadcast() {
        sampleJobHandler.broadcastJob();
        return Map.of("success", true, "job", "broadcastJob", "time", LocalDateTime.now().toString());
    }

    /** 一键触发所有任务 */
    @PostMapping("/all")
    public Map<String, Object> triggerAll() throws InterruptedException {
        sampleJobHandler.simpleJob();
        sampleJobHandler.shardingJob();
        sampleJobHandler.paramJob();
        sampleJobHandler.parentJob();
        sampleJobHandler.childJob();
        sampleJobHandler.longRunningJob();
        sampleJobHandler.failRetryJob();
        sampleJobHandler.broadcastJob();
        return Map.of("success", true, "count", 8,
                "note", "全部 8 个任务已执行（含 parentJob+childJob 各一次），查看控制台日志确认结果",
                "time", LocalDateTime.now().toString());
    }
}
