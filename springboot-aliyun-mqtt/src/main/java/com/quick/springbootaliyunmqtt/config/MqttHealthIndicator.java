package com.quick.springbootaliyunmqtt.config;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * MQTT 健康检查指标，对接 Spring Boot Actuator。
 * <p>
 * 健康端点：GET /actuator/health
 * <p>
 * 返回示例：
 * <pre>
 * {
 *   "status": "UP",
 *   "components": {
 *     "mqtt": {
 *       "status": "UP",
 *       "details": {
 *         "connected": true,
 *         "serverUri": "tcp://post-cn-xxxx.mqtt.aliyuncs.com:1883",
 *         "clientId": "GID_DEFAULT@@@demo-device-001",
 *         "pendingMessages": 0
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
@Component
@ConditionalOnBean(MqttClient.class)
@RequiredArgsConstructor
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttClient mqttClient;
    private final MqttConnectionStateListener connectionStateListener;

    @Override
    public Health health() {
        boolean connected = mqttClient.isConnected();

        Health.Builder builder = connected ? Health.up() : Health.down();

        return builder
                .withDetail("connected", connected)
                .withDetail("serverUri", mqttClient.getServerURI())
                .withDetail("clientId", mqttClient.getClientId())
                .withDetail("pendingDeliveryTokens", mqttClient.getPendingDeliveryTokens().length)
                .build();
    }
}
