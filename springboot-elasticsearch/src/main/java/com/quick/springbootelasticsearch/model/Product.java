package com.quick.springbootelasticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品文档 —— 演示 ES 各种字段类型和嵌套结构。
 * <p>
 * <h3>核心注解说明</h3>
 * <ul>
 *   <li>{@code @Document} — 标记为一个 ES 文档，指定索引名</li>
 *   <li>{@code @Id} — 文档 _id</li>
 *   <li>{@code @Field} — 字段映射配置：type, index, analyzer, searchAnalyzer 等</li>
 *   <li>{@code @MultiField} — 一个字段多种索引方式（如 keyword + text）</li>
 *   <li>{@code @GeoPointField} — 地理位置字段</li>
 *   <li>{@code @CompletionField} — 搜索建议字段</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
@Setting(shards = 1, replicas = 0, refreshInterval = "1s")
public class Product {

    @Id
    private String id;

    // ---- 商品名：text 用于全文搜索 + keyword 用于精确排序 ----
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String name;

    // ---- 描述：全文搜索 ----
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    // ---- 品牌：精确匹配 ----
    @Field(type = FieldType.Keyword)
    private String brand;

    // ---- 分类：精确匹配 + 聚合用 ----
    @Field(type = FieldType.Keyword)
    private String category;

    // ---- 标签：多值 keyword 数组 ----
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    // ---- 价格 ----
    @Field(type = FieldType.Double)
    private BigDecimal price;

    // ---- 库存 ----
    @Field(type = FieldType.Integer)
    private Integer stock;

    // ---- 评分（1-5） ----
    @Field(type = FieldType.Double)
    private Double rating;

    // ---- 是否在售 ----
    @Field(type = FieldType.Boolean)
    private Boolean onSale;

    // ---- 商品图片 ----
    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    // ---- 地理位置（经纬度） ----
    @GeoPointField
    private GeoPoint location;

    // ---- 搜索建议补全 ----
    @CompletionField(maxInputLength = 50)
    private Completion suggest;

    // ---- 嵌套对象：SKU 规格（Nested 类型，独立文档存储） ----
    @Field(type = FieldType.Nested)
    private List<Sku> skus;

    // ---- 创建时间 ----
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ---- 更新时间 ----
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ================================================================
    // 内嵌类
    // ================================================================

    /**
     * SKU 规格 —— Nested 类型，每条 SKU 是独立的可搜索文档。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sku {
        @Field(type = FieldType.Keyword)
        private String skuCode;

        @Field(type = FieldType.Keyword)
        private String color;

        @Field(type = FieldType.Keyword)
        private String size;

        @Field(type = FieldType.Double)
        private BigDecimal skuPrice;

        @Field(type = FieldType.Integer)
        private Integer skuStock;
    }

    /**
     * 地理位置。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoPoint {
        private double lat;
        private double lon;
    }

    /**
     * 搜索建议。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Completion {
        @Field(type = FieldType.Keyword)
        private String input;

        @Field(type = FieldType.Integer)
        private Integer weight;
    }
}
