package com.quick.springbootmongdb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图书实体 —— 核心演示 @Document 的用法。
 * <p>
 * 这个类完整展示了 MongoDB 文档模型和关系型数据库（MySQL）的本质区别：
 * <ul>
 *   <li>嵌套子文档 (Publisher) → MySQL 需要另一张表 + JOIN</li>
 *   <li>内嵌数组 (List&lt;Review&gt;) → MySQL 需要第三张关联表</li>
 *   <li>动态字段 (Map) → MySQL 需要预留列或用 JSON 字段</li>
 *   <li>数组基本类型 (List&lt;String&gt;) → MySQL 几乎不支持</li>
 * </ul>
 * <p>
 * MongoDB 中的存储形式：
 * <pre>{@code
 * {
 *   "_id": ObjectId("..."),
 *   "title": "Spring实战",
 *   "author": "Craig Walls",
 *   "category": "技术",
 *   "price": 99.00,
 *   "pages": 500,
 *   "publish_date": "2022-01-15",
 *   "tags": ["Java", "Spring", "后端"],
 *   "publisher": {
 *     "name": "人民邮电出版社",
 *     "city": "北京",
 *     "year": 2022
 *   },
 *   "reviews": [
 *     { "user": "张三", "rating": 5, "comment": "非常好", "created_at": "..." }
 *   ],
 *   "metadata": { "format": "纸质", "language": "中文" },
 *   "active": true,
 *   "created_at": "2025-01-01T10:00:00",
 *   "updated_at": "2025-01-02T15:30:00"
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "books")    // 对应 MongoDB 的 collection "books"
public class Book {

    @Id                              // MongoDB 主键，不赋值时自动生成 ObjectId
    private String id;

    @Indexed                         // 建单字段索引（加速按书名查询）
    private String title;

    @Indexed
    private String author;

    @Field("category")               // 显式指定字段名（不指定则自动驼峰转下划线）
    private String category;

    private BigDecimal price;

    private Integer pages;

    @Field("publish_date")
    @Indexed
    private LocalDate publishDate;   // Java 8+ 时间类型，MongoDB 驱动自动转换

    private List<String> tags;       // 数组字段：["Java", "Spring"]

    private Publisher publisher;     // 嵌入式子文档（一个对象嵌套在 Book 文档内）

    @Builder.Default
    private List<Review> reviews = new ArrayList<>();  // 嵌入式数组（多个子文档）

    private Map<String, Object> metadata;  // 动态字段：可以存任意 key-value

    private Boolean active;

    @CreatedDate                     // 配合 @EnableMongoAuditing，自动填创建时间
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate                // 自动填更新时间
    @Field("updated_at")
    private LocalDateTime updatedAt;
}
