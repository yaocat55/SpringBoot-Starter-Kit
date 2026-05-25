package com.quick.springbootlombok.model;

import lombok.Builder;
import lombok.Value;

/**
 * ImmutableConfig —— @Value 创建不可变类。
 * 相当于 final 字段 + @Getter + @EqualsAndHashCode + @ToString + 全参构造。
 */
@Value
@Builder
public class ImmutableConfig {

    String appName;
    String version;
    int maxRetries;

    @Builder.Default
    long timeoutMs = 5000L;
}
