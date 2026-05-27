package com.quick.springbootelasticsearch.controller;

import com.quick.springbootelasticsearch.model.Product;
import com.quick.springbootelasticsearch.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 搜索 Controller —— 15 种检索场景，每种都有 curl 示例。
 * <p>
 * <h3>启动前准备</h3>
 * <ol>
 *   <li>确保 ES 已启动（默认 localhost:9200）</li>
 *   <li>如需中文分词，安装 IK 插件：
 *     <pre>./bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/8.12.0</pre>
 *   </li>
 *   <li>启动应用，自动创建索引并插入 10 条测试数据</li>
 * </ol>
 * <p>
 * <h3>快速验证</h3>
 * <pre>
 *   # 1. 查看所有商品
 *   curl http://localhost:8086/api/products
 *
 *   # 2. 搜索"手机"
 *   curl "http://localhost:8086/api/products/search/match?keyword=手机"
 *
 *   # 3. 多字段搜索
 *   curl "http://localhost:8086/api/products/search/multi-match?q=华为"
 *
 *   # 4. 组合查询
 *   curl -X POST http://localhost:8086/api/products/search/bool \
 *     -H "Content-Type: application/json" \
 *     -d '{"keyword":"手机","category":"电子产品","minPrice":1000,"maxPrice":8000}'
 * </pre>
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class SearchController {

    private final ProductService productService;

    // ================================================================
    // 基础 CRUD
    // ================================================================

    /** 全量查询 */
    @GetMapping
    public List<Product> listAll() {
        return productService.findAll();
    }

    /** 按 ID 查 */
    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable String id) {
        Optional<Product> p = productService.findById(id);
        return p.<Map<String, Object>>map(product -> Map.of(
                        "success", true, "data", product))
                .orElseGet(() -> Map.of("success", false, "msg", "商品不存在"));
    }

    /** 新增 */
    @PostMapping
    public Product create(@RequestBody Product product) {
        return productService.save(product);
    }

    /** 更新 */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Product product) {
        if (productService.findById(id).isEmpty()) {
            return Map.of("success", false, "msg", "商品不存在");
        }
        product.setId(id);
        Product saved = productService.save(product);
        return Map.of("success", true, "data", saved);
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        productService.deleteById(id);
        return Map.of("success", true, "msg", "已删除");
    }

    /** 批量插入 */
    @PostMapping("/batch")
    public Map<String, Object> batchInsert(@RequestBody List<Product> products) {
        List<Product> saved = productService.saveBatch(products);
        return Map.of("success", true, "count", saved.size());
    }

    // ================================================================
    // Term 精确查询
    // ================================================================

    /**
     * Term 精确查询 —— 不分词，用于 keyword 类型字段。
     * <pre>curl "http://localhost:8086/api/products/search/term?field=brand&value=华为"</pre>
     */
    @GetMapping("/search/term")
    public List<Product> termSearch(@RequestParam String field,
                                    @RequestParam String value) {
        return productService.termQuery(field, value);
    }

    // ================================================================
    // Match 全文搜索
    // ================================================================

    /**
     * Match 全文搜索 —— 分词后检索，结果带高亮。
     * <pre>curl "http://localhost:8086/api/products/search/match?keyword=手机&page=0&size=10"</pre>
     */
    @GetMapping("/search/match")
    public ProductService.SearchResult matchSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.matchSearch(keyword, page, size);
    }

    // ================================================================
    // Multi-Match 多字段搜索
    // ================================================================

    /**
     * 多字段搜索 —— name^3, description^2, brand, category, tags 联合检索。
     * <p>^3 ^2 是权重提升，匹配 name 的文档排名更靠前。
     * <pre>curl "http://localhost:8086/api/products/search/multi-match?q=华为5G&page=0&size=10"</pre>
     */
    @GetMapping("/search/multi-match")
    public ProductService.SearchResult multiMatch(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.multiMatch(keyword, page, size);
    }

    // ================================================================
    // Bool 组合查询
    // ================================================================

    /**
     * Bool 组合查询 —— must / filter / mustNot 自由组合。
     * <pre>
     * curl -X POST http://localhost:8086/api/products/search/bool \
     *   -H "Content-Type: application/json" \
     *   -d '{"keyword":"手机","category":"电子产品","brand":"华为","minPrice":1000,"maxPrice":8000,"page":0,"size":10}'
     * </pre>
     */
    @PostMapping("/search/bool")
    public ProductService.SearchResult boolSearch(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.get("keyword");
        String category = (String) body.get("category");
        String brand = (String) body.get("brand");
        BigDecimal minPrice = body.get("minPrice") != null
                ? new BigDecimal(body.get("minPrice").toString()) : null;
        BigDecimal maxPrice = body.get("maxPrice") != null
                ? new BigDecimal(body.get("maxPrice").toString()) : null;
        int page = body.get("page") != null ? ((Number) body.get("page")).intValue() : 0;
        int size = body.get("size") != null ? ((Number) body.get("size")).intValue() : 10;
        return productService.boolQuery(keyword, category, brand, minPrice, maxPrice, page, size);
    }

    // ================================================================
    // Fuzzy 模糊查询
    // ================================================================

    /**
     * Fuzzy 模糊查询 —— 容忍拼写错误，fuzziness=1 允许 1 个字符差异。
     * <pre>
     *   # "oppo" 能搜到 "OPPO"
     *   curl "http://localhost:8086/api/products/search/fuzzy?keyword=oppo&fuzziness=1"
     * </pre>
     */
    @GetMapping("/search/fuzzy")
    public ProductService.SearchResult fuzzySearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int fuzziness,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.fuzzySearch(keyword, fuzziness, page, size);
    }

    // ================================================================
    // 通配符 & 前缀
    // ================================================================

    /**
     * 通配符查询 —— * 匹配任意字符，? 匹配单个字符。
     * <pre>curl "http://localhost:8086/api/products/search/wildcard?field=name&pattern=华*"</pre>
     */
    @GetMapping("/search/wildcard")
    public ProductService.SearchResult wildcardSearch(
            @RequestParam String field,
            @RequestParam String pattern,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.wildcardSearch(field, pattern, page, size);
    }

    /**
     * 前缀查询 —— 搜"华"找到"华为"、"华为手机"等。
     * <pre>curl "http://localhost:8086/api/products/search/prefix?prefix=华"</pre>
     */
    @GetMapping("/search/prefix")
    public ProductService.SearchResult prefixSearch(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.prefixSearch(prefix, page, size);
    }

    // ================================================================
    // 范围查询
    // ================================================================

    /**
     * 范围查询 —— 价格区间、日期区间等。
     * <pre>curl "http://localhost:8086/api/products/search/range?field=price&min=1000&max=5000"</pre>
     */
    @GetMapping("/search/range")
    public ProductService.SearchResult rangeSearch(
            @RequestParam String field,
            @RequestParam(required = false) BigDecimal min,
            @RequestParam(required = false) BigDecimal max,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.rangeQuery(field, min, max, page, size);
    }

    // ================================================================
    // Nested 嵌套查询
    // ================================================================

    /**
     * Nested 嵌套查询 —— 查 SKU 中的颜色。
     * <pre>curl "http://localhost:8086/api/products/search/nested?color=黑色"</pre>
     */
    @GetMapping("/search/nested")
    public ProductService.SearchResult nestedSearch(
            @RequestParam String color,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.nestedSearch(color, page, size);
    }

    // ================================================================
    // 聚合统计
    // ================================================================

    /**
     * 按品牌聚合统计。
     * <pre>curl http://localhost:8086/api/products/search/agg/brand</pre>
     */
    @GetMapping("/search/agg/brand")
    public Map<String, Long> aggByBrand() {
        return productService.aggregateByBrand();
    }

    /**
     * 按价格区间聚合统计。
     * <pre>curl http://localhost:8086/api/products/search/agg/price</pre>
     */
    @GetMapping("/search/agg/price")
    public Map<String, Long> aggByPriceRange() {
        return productService.aggregateByPriceRange();
    }

    // ================================================================
    // 搜索建议
    // ================================================================

    /**
     * 搜索建议 —— 输入"华"自动补全为"华为Mate60"等。
     * <pre>curl "http://localhost:8086/api/products/search/suggest?prefix=华"</pre>
     */
    @GetMapping("/search/suggest")
    public Map<String, Object> suggest(@RequestParam String prefix) {
        List<String> suggestions = productService.suggest(prefix);
        return Map.of("success", true, "prefix", prefix, "suggestions", suggestions);
    }

    // ================================================================
    // 排序 + 分页
    // ================================================================

    /**
     * 自定义排序 + 分页。
     * <pre>curl "http://localhost:8086/api/products/search/sorted?category=电子产品&sortField=price&sortDir=asc&page=0&size=10"</pre>
     */
    @GetMapping("/search/sorted")
    public ProductService.SearchResult sortedSearch(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "price") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.sortedSearch(category, sortField, sortDir, page, size);
    }

}
