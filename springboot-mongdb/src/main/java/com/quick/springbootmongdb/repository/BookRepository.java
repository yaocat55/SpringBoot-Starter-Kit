package com.quick.springbootmongdb.repository;

import com.quick.springbootmongdb.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.math.BigDecimal;
import java.util.List;

/**
 * 图书 Repository —— 继承 MongoRepository 自带 CRUD，无需写实现。
 * <p>
 * Spring Data MongoDB 会根据方法名自动生成查询逻辑：
 * <pre>
 *   findByAuthor  →  db.books.find({"author": "xxx"})
 *   findByPriceBetween → db.books.find({"price": {"$gte": min, "$lte": max}})
 * </pre>
 * 复杂查询用 {@code @Query} 写原生 MongoDB 查询语句（JSON 格式）。
 */
public interface BookRepository extends MongoRepository<Book, String> {

    // ======================== 方法命名查询（Spring Data 自动解析） ========================

    /** 按作者精确查询 */
    List<Book> findByAuthor(String author);
    /** 按分类查询 + 按价格排序 */
    List<Book> findByCategoryOrderByPriceDesc(String category);

    /** 价格区间查询：price >= min AND price <= max */
    List<Book> findByPriceBetween(BigDecimal min, BigDecimal max);

    /** 书名模糊匹配（正则） */
    List<Book> findByTitleRegex(String regex);

    /** 按标签包含（tags 是数组，contains 匹配数组中的元素） */
    List<Book> findByTagsContaining(String tag);

    /** 按 active 状态 + 分类查询 */
    List<Book> findByActiveAndCategory(Boolean active, String category);

    /** 页数大于指定值 */
    List<Book> findByPagesGreaterThan(Integer pages);

    // ======================== 原生 MongoDB 查询（@Query） ========================

    /**
     * 自定义复杂查询 —— 出版社名 + 最低评分。
     * 这里写的是 MongoDB 原生查询 JSON，和 mongo shell 里完全一样。
     */
    @Query("{ 'publisher.name': ?0, 'reviews.rating': { $gte: ?1 } }")
    List<Book> findByPublisherAndMinRating(String publisherName, int minRating);

    /**
     * 只返回部分字段（投影），减少传输量。
     */
    @Query(value = "{ 'category': ?0 }", fields = "{ 'title': 1, 'author': 1, 'price': 1 }")
    List<Book> findBriefByCategory(String category);
}
