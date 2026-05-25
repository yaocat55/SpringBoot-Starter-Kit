package com.quick.springbootmongdb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 出版社 —— 嵌入式子文档（不会单独成为一个 Collection）。
 * <p>
 * 在 MongoDB 中，Publisher 不是一个独立的表，而是嵌入在 Book 文档内部：
 * <pre>{@code
 * {
 *   "title": "...",
 *   "publisher": {
 *     "name": "机械工业出版社",
 *     "city": "北京",
 *     "year": 2020
 *   }
 * }
 * }</pre>
 * 这跟 MySQL 的外键关联完全不同：MongoDB 直接用嵌套 JSON 表达"属于"关系。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Publisher {
    private String name;
    private String city;
    private Integer year;
}
