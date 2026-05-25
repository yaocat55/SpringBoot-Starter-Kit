package com.quick.springbootlombok.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.With;

/**
 * Product —— 演示 @With 创建"修改了某个字段"的副本（不可变风格的更新）。
 */
@Data
@Builder
@AllArgsConstructor
public class Product {

    private Long id;
    private String name;

    @With
    private Double price;

    @With
    private Integer stock;
}
