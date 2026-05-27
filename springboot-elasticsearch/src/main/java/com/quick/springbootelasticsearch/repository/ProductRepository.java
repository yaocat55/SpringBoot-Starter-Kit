package com.quick.springbootelasticsearch.repository;

import com.quick.springbootelasticsearch.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 ES Repository。
 * <p>
 * 方法命名规则和 Spring Data JPA 一样，Spring Data ES 会自动生成 DSL。
 * 复杂查询走 {@code ProductService} 的 ElasticsearchOperations。
 */
public interface ProductRepository extends ElasticsearchRepository<Product, String> {

    // ======================== 精确匹配 ========================

    /** 按品牌精确查 */
    List<Product> findByBrand(String brand);

    /** 按分类精确查 */
    List<Product> findByCategory(String category);

    // ======================== 范围查询 ========================

    /** 价格区间 */
    List<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    /** 库存大于等于 */
    List<Product> findByStockGreaterThanEqual(Integer minStock);

    // ======================== 全文搜索 ========================

    /** 商品名全文搜索（走 ik 分词） */
    List<Product> findByNameMatches(String keyword);

    /** 描述全文搜索 */
    List<Product> findByDescriptionMatches(String keyword);

    // ======================== 布尔查询 ========================

    /** 分类 + 在售状态 */
    List<Product> findByCategoryAndOnSale(String category, Boolean onSale);

    /** 品牌 + 价格范围 */
    List<Product> findByBrandAndPriceBetween(String brand, BigDecimal min, BigDecimal max);

    // ======================== 模糊查询 ========================

    /** 模糊查询（允许拼写错误） */
    List<Product> findByNameLike(String keyword);

    // ======================== 前缀 ========================

    /** 按商品名前缀匹配 */
    List<Product> findByNameStartingWith(String prefix);

    // ======================== 分页 ========================

    /** 分页查询，带自定义排序 */
    Page<Product> findByCategory(String category, Pageable pageable);
}
