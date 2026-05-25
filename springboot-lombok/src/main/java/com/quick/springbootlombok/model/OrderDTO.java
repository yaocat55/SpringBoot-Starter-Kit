package com.quick.springbootlombok.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderDTO —— 演示 @Getter @Setter @ToString @EqualsAndHashCode 的独立使用。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderDTO {

    @EqualsAndHashCode.Include
    private String orderId;

    private String customerName;

    @ToString.Exclude
    private String internalNote;

    @ToString.Include(name = "金额")
    private BigDecimal amount;

    private List<String> items;
}
