package com.quick.springbootemqx.config;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * EMQX 健康检查 —— 注册到 Actuator，暴露在 /actuator/health。
 * <p>
 * 返回示例：
 * <pre>
 * "emqx": {
 *   "status": "UP",
 *   "serverUri": "tcp://localhost:1883",
 *   "clientId": "springboot-emqx-demo",
 *   "connected": true
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MqttClient.class)
public class EmqxHealthIndicator implements HealthIndicator {

    private final MqttClient mqttClient;

    @Override
    public Health health() {
        boolean connected = mqttClient.isConnected();
        return connected
                ? Health.up()
                    .withDetail("serverUri", mqttClient.getServerURI())
                    .withDetail("clientId", mqttClient.getClientId())
                    .withDetail("connected", true)
                    .build()
                : Health.down()
                    .withDetail("serverUri", mqttClient.getServerURI())
                    .withDetail("clientId", mqttClient.getClientId())
                    .withDetail("connected", false)
                    .build();
    }
}
