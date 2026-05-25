package com.quick.springbootaliyunmqtt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * MQTT 客户端配置 —— 适配阿里云微消息队列 MQTT 版。
 * <p>
 * 阿里云 MQTT 认证流程：
 * 1. 使用 AccessKey + SecretKey 对 ClientId 做 HMAC-SHA1 签名
 * 2. 签名结果作为密码（password），用户名（userName）固定为 Signature|AccessKey|InstanceId
 * <p>
 * 接入文档参考：https://help.aliyun.com/document_detail/42420.html
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "aliyun.mqtt")
@ConditionalOnProperty(name = "aliyun.mqtt.enabled", havingValue = "true")
@Getter
@Setter
public class MqttConfig {

    /** Broker 地址，如 tcp://post-cn-xxxx.mqtt.aliyuncs.com:1883 */
    private String brokerUrl;

    /** MQTT Client ID，阿里云格式 GID_<GroupID>@@@<ClientID> */
    private String clientId;

    /** 实例 ID（post-cn-xxxx） */
    private String instanceId;

    /** AccessKey */
    private String accessKey;

    /** SecretKey */
    private String secretKey;

    /** 清除会话 */
    private boolean cleanSession = true;

    /** 连接超时（秒） */
    private int connectionTimeout = 30;

    /** Keep Alive（秒） */
    private int keepAliveInterval = 60;

    /** 自动重连 */
    private boolean autoReconnect = true;

    /** 重连间隔（毫秒） */
    private long reconnectIntervalMs = 5000;

    /** 遗嘱消息开关 */
    private boolean enableLwt = false;

    /** 遗嘱 Topic */
    private String lwtTopic = "device/offline";

    /** 遗嘱消息内容 */
    private String lwtMessage = "offline";

    /** 遗嘱消息 QoS */
    private int lwtQos = 1;

    /** 遗嘱消息 Retained */
    private boolean lwtRetained = false;

    // ==================== MqttClient Bean ====================

    /**
     * 构建 MQTT 连接选项，包含阿里云认证签名和会话参数。
     */
    public MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();

        // 阿里云 MQTT 认证：用户名格式 Signature|AccessKey|InstanceId
        options.setUserName("Signature|" + accessKey + "|" + instanceId);

        // 密码：对 ClientId 做 HMAC-SHA1 签名后 Base64 编码
        options.setPassword(generatePassword(clientId, secretKey).toCharArray());

        // 会话参数
        options.setCleanSession(cleanSession);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);

        // 自动重连
        options.setAutomaticReconnect(autoReconnect);
        if (autoReconnect) {
            options.setMaxReconnectDelay((int) reconnectIntervalMs);
        }

        // 遗嘱消息（LWT）
        if (enableLwt) {
            options.setWill(lwtTopic, lwtMessage.getBytes(StandardCharsets.UTF_8), lwtQos, lwtRetained);
        }

        return options;
    }

    /**
     * 创建并连接 MQTT 客户端。
     * <p>
     * destroyMethod = "disconnect" 确保应用关闭时优雅断开连接。
     */
    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient() throws MqttException {
        log.info("初始化 MQTT 客户端: broker={}, clientId={}", brokerUrl, clientId);

        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions options = buildConnectOptions();

        client.connect(options);
        log.info("MQTT 连接成功: serverUri={}, clientId={}", client.getServerURI(), client.getClientId());

        return client;
    }

    // ==================== ObjectMapper ====================

    @Bean
    public ObjectMapper mqttObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ==================== 密码签名工具 ====================

    /**
     * 阿里云 MQTT 密码生成算法：
     * 使用 SecretKey 对 ClientId 做 HMAC-SHA1 签名，返回 Base64 编码。
     */
    static String generatePassword(String clientId, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec spec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(spec);
            byte[] rawHmac = mac.doFinal(clientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("生成 MQTT 密码失败", e);
        }
    }
}
