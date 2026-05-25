package com.quick.springbootemqx.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EMQX 配置 —— 创建 MQTT 5.0 客户端，连接 EMQX Broker。
 * <p>
 * EMQX 使用简单的用户名/密码认证，无需 Alibaba Cloud 那种 HMAC-SHA1 签名，
 * 配置开箱即用。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "emqx.enabled", havingValue = "true")
public class EmqxConfig {

    /**
     * 绑定 emqx.* 的 YAML 配置到此 Bean。
     */
    @Bean
    @ConfigurationProperties(prefix = "emqx")
    public EmqxProperties emqxProperties() {
        return new EmqxProperties();
    }

    /**
     * 创建 MQTT 5.0 客户端并连接 EMQX Broker。
     *
     * @param props 配置属性
     * @return 已连接的 MqttClient，destroyMethod 确保应用关闭时断开连接
     */
    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient(EmqxProperties props) throws MqttException {
        String clientId = props.getClientId();
        String brokerUrl = props.getBrokerUrl();

        log.info("正在连接 EMQX Broker: {} clientId={}", brokerUrl, clientId);
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectionOptions options = buildConnectionOptions(props);
        client.connect(options);

        log.info("EMQX 连接成功: serverURI={}, clientId={}, sessionExpiry={}s",
                client.getServerURI(), client.getClientId(), props.getSessionExpiryInterval());
        return client;
    }

    private MqttConnectionOptions buildConnectionOptions(EmqxProperties props) {
        MqttConnectionOptions opts = new MqttConnectionOptions();

        // 认证
        opts.setUserName(props.getUsername());
        opts.setPassword(props.getPassword().getBytes());

        // MQTT 5.0 会话有效期（替代 MQTT 3.x 的 Clean Session 布尔值）
        opts.setSessionExpiryInterval(props.getSessionExpiryInterval());

        // 连接参数
        opts.setConnectionTimeout(props.getConnectionTimeout());
        opts.setKeepAliveInterval(props.getKeepAliveInterval());
        opts.setAutomaticReconnect(props.isAutoReconnect());
        opts.setMaxReconnectDelay(props.getReconnectMaxDelay());

        // MQTT 5.0 遗嘱消息
        if (props.isEnableLwt()) {
            MqttMessage lwtMsg = new MqttMessage(props.getLwtPayload().getBytes());
            lwtMsg.setQos(props.getLwtQos());
            lwtMsg.setRetained(props.isLwtRetained());
            opts.setWill(props.getLwtTopic(), lwtMsg);
        }

        return opts;
    }

    /**
     * EMQX 配置属性 Bean，由 @ConfigurationProperties 绑定。
     */
    @Data
    public static class EmqxProperties {
        /** Broker 地址，如 tcp://localhost:1883 */
        private String brokerUrl = "tcp://localhost:1883";
        /** MQTT 客户端 ID */
        private String clientId = "springboot-emqx-demo";
        /** 用户名 (EMQX 内置认证) */
        private String username = "admin";
        /** 密码 */
        private String password = "public";

        /** MQTT 5.0 会话有效期（秒），0 = 断开即销毁会话 */
        private Long sessionExpiryInterval = 3600L;
        /** 连接超时（秒） */
        private int connectionTimeout = 30;
        /** 心跳间隔（秒） */
        private int keepAliveInterval = 60;
        /** 是否自动重连 */
        private boolean autoReconnect = true;
        /** 最大重连延迟（毫秒） */
        private int reconnectMaxDelay = 10000;

        // 遗嘱消息
        private boolean enableLwt = false;
        private String lwtTopic = "/device/demo/status";
        private String lwtPayload = "OFFLINE";
        private int lwtQos = 1;
        private boolean lwtRetained = true;

        /** 离线缓冲区最大容量 */
        private int maxBufferSize = 10000;
    }
}
