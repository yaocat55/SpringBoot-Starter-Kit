package com.quick.springbootsentinel.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.DefaultBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowItem;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * Sentinel 全局配置。
 * <p>
 * 规则在 @PostConstruct 中初始化，方便复制到任意项目直接用。
 * 生产环境建议把规则迁移到 Nacos 持久化（见 application.yml 注释）。
 */
@Slf4j
@Configuration
public class SentinelConfig {

    // ================================================================
    // 限流规则
    // ================================================================

    @PostConstruct
    public void initFlowRules() {
        // ---- QPS 限流 ----
        FlowRule qpsRule = new FlowRule("qpsLimit");
        qpsRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        qpsRule.setCount(2);            // 每秒最多 2 个请求
        qpsRule.setLimitApp("default");

        // ---- 并发线程数限流 ----
        FlowRule threadRule = new FlowRule("threadLimit");
        threadRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        threadRule.setCount(5);          // 同时最多 5 个线程在处理

        // ---- Warm Up（冷启动，防止瞬间流量打垮刚启动的服务） ----
        FlowRule warmUpRule = new FlowRule("warmUpLimit");
        warmUpRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        warmUpRule.setCount(10);                        // 预热后的稳定 QPS
        warmUpRule.setWarmUpPeriodSec(10);              // 10 秒内从 QPS/3 → QPS
        warmUpRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);

        // ---- 排队等待（匀速器，突发流量排队处理而不是拒绝） ----
        FlowRule queueRule = new FlowRule("queueLimit");
        queueRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        queueRule.setCount(5);                          // 每秒通过 5 个
        queueRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        queueRule.setMaxQueueingTimeMs(500);            // 最多排队 500ms，超时则拒绝

        FlowRuleManager.loadRules(List.of(qpsRule, threadRule, warmUpRule, queueRule));
        log.info("[Sentinel] 限流规则已加载，共 {} 条", FlowRuleManager.getRules().size());
    }

    // ================================================================
    // 熔断规则
    // ================================================================

    @PostConstruct
    public void initDegradeRules() {
        // ---- 慢调用比例熔断 ----
        // ---- 慢调用比例熔断（grade=0: SLOW_REQUEST_RATIO） ----
        DegradeRule slowCallRule = new DegradeRule("slowCallBreak");
        slowCallRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        slowCallRule.setCount(200);                       // 响应超过 200ms 算慢调用
        slowCallRule.setTimeWindow(10);                   // 熔断 10 秒后进入半开状态
        slowCallRule.setMinRequestAmount(5);              // 统计窗口内最少 5 个请求才判断
        slowCallRule.setSlowRatioThreshold(0.5);          // 慢调用比例 > 50% 则熔断
        slowCallRule.setStatIntervalMs(10000);            // 统计窗口 10 秒

        // ---- 异常比例熔断（grade=1: ERROR_RATIO） ----
        DegradeRule exceptionRatioRule = new DegradeRule("exceptionRatioBreak");
        exceptionRatioRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        exceptionRatioRule.setCount(0.5);                 // 异常比例 > 50% 则熔断
        exceptionRatioRule.setTimeWindow(10);
        exceptionRatioRule.setMinRequestAmount(5);
        exceptionRatioRule.setStatIntervalMs(10000);

        // ---- 异常数熔断（grade=2: ERROR_COUNT） ----
        DegradeRule exceptionCountRule = new DegradeRule("exceptionCountBreak");
        exceptionCountRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
        exceptionCountRule.setCount(5);                   // 1 分钟内异常 > 5 次则熔断
        exceptionCountRule.setTimeWindow(10);
        exceptionCountRule.setMinRequestAmount(5);
        exceptionCountRule.setStatIntervalMs(60000);      // 统计窗口 60 秒

        DegradeRuleManager.loadRules(List.of(slowCallRule, exceptionRatioRule, exceptionCountRule));
        log.info("[Sentinel] 熔断规则已加载，共 {} 条", DegradeRuleManager.getRules().size());
    }

    // ================================================================
    // 热点参数限流规则
    // ================================================================

    @PostConstruct
    public void initParamFlowRules() {
        ParamFlowRule rule = new ParamFlowRule("hotParamLimit");
        rule.setParamIdx(0);                              // 对第 0 个参数限流
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(3);                                 // 默认每秒 3 次
        rule.setDurationInSec(1);

        // 针对特定值的例外：参数值 = "vip" 时放宽到 10 QPS
        ParamFlowItem item = new ParamFlowItem();
        item.setObject("vip");
        item.setCount(10);
        item.setClassType(String.class.getName());
        rule.setParamFlowItemList(Collections.singletonList(item));

        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
        log.info("[Sentinel] 热点参数限流规则已加载");
    }

    // ================================================================
    // 系统自适应限流
    // ================================================================

    @PostConstruct
    public void initSystemRules() {
        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.8);                  // CPU 使用率 > 80% 自动限流

        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(4.0);               // 系统 load > 4.0 自动限流

        SystemRule avgRtRule = new SystemRule();
        avgRtRule.setAvgRt(100);                          // 平均 RT > 100ms 自动限流

        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(200);                              // 入口 QPS > 200 自动限流

        SystemRuleManager.loadRules(List.of(cpuRule, loadRule, avgRtRule, qpsRule));
        log.info("[Sentinel] 系统自适应规则已加载");
    }

    // ================================================================
    // 全局 Block 异常处理 —— 返回 JSON 而不是默认的白色错误页
    // ================================================================

    @Bean
    public BlockExceptionHandler blockExceptionHandler() {
        return (request, response, e) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);  // Too Many Requests

            String ruleName = e.getRule().getResource();
            String msg = switch (e.getClass().getSimpleName()) {
                case "FlowException"       -> "【限流】资源 [%s] 被限流，请稍后重试。".formatted(ruleName);
                case "DegradeException"    -> "【熔断】资源 [%s] 已熔断，请稍后重试。".formatted(ruleName);
                case "ParamFlowException"  -> "【热点限流】资源 [%s] 热点参数被限流。".formatted(ruleName);
                case "SystemBlockException" -> "【系统保护】系统负载过高，已触发自适应限流。";
                case "AuthorityException"  -> "【授权】资源 [%s] 未授权访问。".formatted(ruleName);
                default                    -> "【流控】资源 [%s] 触发流控: %s".formatted(ruleName, e.getClass().getSimpleName());
            };

            response.getWriter().write("""
                    {"code":429,"msg":"%s","success":false}
                    """.formatted(msg).strip());
        };
    }

}
