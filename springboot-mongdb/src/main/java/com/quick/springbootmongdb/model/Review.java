package com.quick.springbootmongdb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书评 —— 嵌入式子文档，一本书可以有多个书评。
 * <p>
 * 在 Book 文档中表现为内嵌数组：
 * <pre>{@code
 * {
 *   "title": "...",
 *   "reviews": [
 *     { "user": "张三", "rating": 5, "comment": "非常好", "createdAt": "..." },
 *     { "user": "李四", "rating": 3, "comment": "还行",   "createdAt": "..." }
 *   ]
 * }
 * }</pre>
 * 一条 SQL 需要 JOIN，MongoDB 一条查询直接拿到这本书的所有书评。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    private String user;
    private Integer rating;     // 1-5 星
    private String comment;
    private LocalDateTime createdAt;

    public static Review of(String user, int rating, String comment) {
        return new Review(user, rating, comment, LocalDateTime.now());
    }
}
