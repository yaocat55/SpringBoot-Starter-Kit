package com.quick.springbootemqx.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备消息 —— 演示 IoT 设备上报数据的典型结构。
 * <p>
 * 实际项目中替换为你自己的业务模型即可。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMessage {

    /** 设备 ID */
    private String deviceId;

    /** 数据类型：temperature / humidity / gps / alarm */
    private String dataType;

    /** 数值 */
    private BigDecimal value;

    /** 单位 */
    private String unit;

    /** 扩展数据（经纬度、电池电量等任意键值对） */
    private Map<String, Object> extra;

    /** 设备端采集时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;
}
