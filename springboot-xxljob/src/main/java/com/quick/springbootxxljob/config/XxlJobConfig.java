package com.quick.springbootxxljob.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器配置 —— 创建 XxlJobSpringExecutor 并注册到调度中心。
 * <p>
 * 启动后执行器会自动向调度中心（Admin）注册心跳，调度中心通过 HTTP 回调
 * 执行器的 /run 和 /beat 接口来触发任务和管理生命周期。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true", matchIfMissing = true)
public class XxlJobConfig {

    /**
     * 绑定 xxl.job.* 配置到 XxlJobProperties。
     */
    @Bean
    @ConfigurationProperties(prefix = "xxl.job")
    public XxlJobProperties xxlJobProperties() {
        return new XxlJobProperties();
    }

    /**
     * 创建 XXL-JOB 执行器 Bean。
     * <p>
     * XxlJobSpringExecutor 做了三件事：
     * <ol>
     *   <li>扫描所有 @XxlJob 注解的方法，注册为 JobHandler</li>
     *   <li>启动内嵌 Server（接收 Admin 的 HTTP 调度请求）</li>
     *   <li>向 Admin 注册执行器 + 维持心跳</li>
     * </ol>
     */
    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties props) {
        log.info("XXL-JOB 执行器初始化: admin={}, appname={}, port={}",
                props.getAdmin().getAddresses(), props.getExecutor().getAppname(),
                props.getExecutor().getPort());

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(props.getAdmin().getAddresses());
        executor.setAppname(props.getExecutor().getAppname());
        executor.setPort(props.getExecutor().getPort());
        executor.setAccessToken(props.getAccessToken());
        executor.setLogPath(props.getExecutor().getLogpath());
        executor.setLogRetentionDays(props.getExecutor().getLogretentiondays());

        return executor;
    }

    /**
     * XXL-JOB 配置属性。
     */
    @lombok.Data
    public static class XxlJobProperties {
        private Admin admin = new Admin();
        private Executor executor = new Executor();
        private String accessToken = "default_token";

        @lombok.Data
        public static class Admin {
            /** 调度中心地址，多个用逗号分隔 */
            private String addresses = "http://localhost:8080/xxl-job-admin";
        }

        @lombok.Data
        public static class Executor {
            /** 执行器 AppName（需和 Admin 后台注册的一致） */
            private String appname = "springboot-xxljob-executor";
            /** 执行器端口（Admin 通过此端口回调） */
            private int port = 9999;
            /** 运行日志路径 */
            private String logpath = "logs/xxl-job";
            /** 日志保留天数 */
            private int logretentiondays = 30;
        }
    }
}
