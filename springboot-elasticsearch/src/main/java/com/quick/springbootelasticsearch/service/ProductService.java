package com.quick.springbootelasticsearch.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import com.quick.springbootelasticsearch.model.Product;
import com.quick.springbootelasticsearch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品搜索 Service —— 演示所有 ES 检索场景。
 * <p>
 * 简单查询走 Repository，复杂查询走 ElasticsearchOperations（底层 ES 8.x 新客户端）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ElasticsearchOperations operations;

    // ================================================================
    // 基础 CRUD
    // ================================================================

    public Product save(Product product) {
        product.setCreateTime(product.getCreateTime() == null
                ? java.time.LocalDateTime.now() : product.getCreateTime());
        product.setUpdateTime(java.time.LocalDateTime.now());
        return productRepository.save(product);
    }

    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }

    public List<Product> findAll() {
        var result = new ArrayList<Product>();
        productRepository.findAll().forEach(result::add);
        return result;
    }

    public void deleteById(String id) {
        productRepository.deleteById(id);
    }

    public List<Product> saveBatch(List<Product> products) {
        var saved = new ArrayList<Product>();
        for (Product p : products) {
            saved.add(save(p));
        }
        return saved;
    }

    // ================================================================
    // Term 精确查询（不分词）
    // ================================================================

    public List<Product> termQuery(String field, String value) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field(field).value(value)))
                .build();
        return toList(operations.search(query, Product.class));
    }

    // ================================================================
    // Match 全文搜索（带高亮）
    // ================================================================

    public SearchResult matchSearch(String keyword, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("name").query(keyword)))
                .withPageable(PageRequest.of(page, size))
                .withHighlightQuery(buildHighlight("name", "description"))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // Multi-Match 多字段搜索
    // ================================================================

    public SearchResult multiMatch(String keyword, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(mm -> mm
                        .fields("name^3", "description^2", "brand", "category", "tags")
                        .query(keyword)))
                .withPageable(PageRequest.of(page, size))
                .withHighlightQuery(buildHighlight("name", "description"))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // Bool 组合查询
    // ================================================================

    public SearchResult boolQuery(String keyword, String category, String brand,
                                  BigDecimal minPrice, BigDecimal maxPrice,
                                  int page, int size) {
        var builder = NativeQuery.builder();

        builder.withQuery(q -> q.bool(b -> {
            if (keyword != null && !keyword.isEmpty()) {
                b.must(m -> m.multiMatch(mm -> mm
                        .fields("name^3", "description^2")
                        .query(keyword)));
            }
            if (category != null && !category.isEmpty()) {
                b.filter(f -> f.term(t -> t.field("category").value(category)));
            }
            if (brand != null && !brand.isEmpty()) {
                b.filter(f -> f.term(t -> t.field("brand").value(brand)));
            }
            if (minPrice != null || maxPrice != null) {
                b.filter(f -> f.range(r -> r.number(nr -> {
                    nr.field("price");
                    if (minPrice != null) nr.gte(minPrice.doubleValue());
                    if (maxPrice != null) nr.lte(maxPrice.doubleValue());
                    return nr;
                })));
            }
            b.mustNot(mn -> mn.term(t -> t.field("onSale").value(false)));
            return b;
        }));

        builder.withPageable(PageRequest.of(page, size));
        builder.withHighlightQuery(buildHighlight("name", "description"));
        builder.withSort(Sort.by(Sort.Direction.DESC, "_score"));

        return executeSearch(builder.build(), page);
    }

    // ================================================================
    // Fuzzy 模糊查询
    // ================================================================

    public SearchResult fuzzySearch(String keyword, int fuzziness, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.fuzzy(f -> f
                        .field("name")
                        .value(keyword)
                        .fuzziness(String.valueOf(fuzziness))))
                .withPageable(PageRequest.of(page, size))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // 通配符 & 前缀
    // ================================================================

    public SearchResult wildcardSearch(String field, String pattern, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.wildcard(w -> w.field(field).value(pattern)))
                .withPageable(PageRequest.of(page, size))
                .build();
        return executeSearch(query, page);
    }

    public SearchResult prefixSearch(String prefix, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.prefix(p -> p.field("name").value(prefix)))
                .withPageable(PageRequest.of(page, size))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // 范围查询
    // ================================================================

    public SearchResult rangeQuery(String field, BigDecimal min, BigDecimal max,
                                   int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.range(r -> r.number(nr -> {
                    nr.field(field);
                    if (min != null) nr.gte(min.doubleValue());
                    if (max != null) nr.lte(max.doubleValue());
                    return nr;
                })))
                .withPageable(PageRequest.of(page, size))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // Nested 嵌套查询（查 SKU 里的颜色）
    // ================================================================

    public SearchResult nestedSearch(String color, int page, int size) {
        var query = NativeQuery.builder()
                .withQuery(q -> q.nested(n -> n
                        .path("skus")
                        .query(nq -> nq.term(t -> t.field("skus.color").value(color)))))
                .withPageable(PageRequest.of(page, size))
                .build();
        return executeSearch(query, page);
    }

    // ================================================================
    // 聚合：品牌分组统计
    // ================================================================

    public Map<String, Long> aggregateByBrand() {
        var query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withAggregation("brand_count",
                        Aggregation.of(a -> a.terms(ta -> ta.field("brand").size(50))))
                .build();

        var hits = operations.search(query, Product.class);
        return extractTermAgg(hits, "brand_count");
    }

    // ================================================================
    // 聚合：价格区间统计
    // ================================================================

    public Map<String, Long> aggregateByPriceRange() {
        var query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withAggregation("price_ranges",
                        Aggregation.of(a -> a.range(ra -> ra
                                .field("price")
                                .ranges(
                                        AggregationRange.of(r -> r.key("0-100").to(100.0)),
                                        AggregationRange.of(r -> r.key("100-500").from(100.0).to(500.0)),
                                        AggregationRange.of(r -> r.key("500-1000").from(500.0).to(1000.0)),
                                        AggregationRange.of(r -> r.key("1000-5000").from(1000.0).to(5000.0)),
                                        AggregationRange.of(r -> r.key("5000+").from(5000.0))
                                ))))
                .build();

        var hits = operations.search(query, Product.class);
        return extractRangeAgg(hits, "price_ranges");
    }

    // ================================================================
    // 搜索建议（Completion Suggester）
    // ================================================================

    public List<String> suggest(String prefix) {
        var query = NativeQuery.builder()
                .withSuggester(Suggester.of(s -> s
                        .suggesters("name-suggest", fs -> fs
                                .prefix(prefix)
                                .completion(cs -> cs.field("suggest").size(5)))))
                .build();

        var hits = operations.search(query, Product.class);
        var suggestResult = hits.getSuggest();
        if (suggestResult == null) return List.of();

        var suggestion = suggestResult.getSuggestion("name-suggest");
        if (suggestion == null) return List.of();

        List<String> result = new ArrayList<>();
        for (var entry : suggestion.getEntries()) {
            for (var opt : entry.getOptions()) {
                result.add(opt.getText());
            }
        }
        return result;
    }

    // ================================================================
    // 排序 + 分页
    // ================================================================

    public SearchResult sortedSearch(String category, String sortField, String sortDir,
                                     int page, int size) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        var builder = NativeQuery.builder();
        if (category != null && !category.isEmpty()) {
            builder.withQuery(q -> q.term(t -> t.field("category").value(category)));
        } else {
            builder.withQuery(q -> q.matchAll(m -> m));
        }
        builder.withPageable(PageRequest.of(page, size).withSort(Sort.by(dir, sortField)));

        return executeSearch(builder.build(), page);
    }

    // ================================================================
    // 高亮
    // ================================================================

    private static HighlightQuery buildHighlight(String... fields) {
        var highlightFields = new ArrayList<HighlightField>();
        for (String f : fields) {
            var params = HighlightFieldParameters.builder()
                    .withPreTags("<em>")
                    .withPostTags("</em>")
                    .withFragmentSize(100)
                    .withNumberOfFragments(3)
                    .build();
            highlightFields.add(new HighlightField(f, params));
        }
        return new HighlightQuery(
                new Highlight(null, highlightFields),
                Product.class);
    }

    // ================================================================
    // 聚合提取
    // ================================================================

    private Map<String, Long> extractTermAgg(SearchHits<?> hits, String aggName) {
        Map<String, Long> result = new LinkedHashMap<>();
        var container = hits.getAggregations();
        if (container == null) return result;

        var elasticAggs = (ElasticsearchAggregations) container;
        var elasticAgg = elasticAggs.get(aggName);
        if (elasticAgg == null) return result;

        var agg = elasticAgg.aggregation();
        var aggregate = agg.getAggregate();
        if (!aggregate.isSterms()) return result;

        for (var bucket : aggregate.sterms().buckets().array()) {
            result.put(bucket.key().stringValue(), bucket.docCount());
        }
        return result;
    }

    private Map<String, Long> extractRangeAgg(SearchHits<?> hits, String aggName) {
        Map<String, Long> result = new LinkedHashMap<>();
        var container = hits.getAggregations();
        if (container == null) return result;

        var elasticAggs = (ElasticsearchAggregations) container;
        var elasticAgg = elasticAggs.get(aggName);
        if (elasticAgg == null) return result;

        var agg = elasticAgg.aggregation();
        var aggregate = agg.getAggregate();
        if (!aggregate.isRange()) return result;

        for (var bucket : aggregate.range().buckets().array()) {
            result.put(bucket.key(), bucket.docCount());
        }
        return result;
    }

    // ================================================================
    // 辅助
    // ================================================================

    private List<Product> toList(SearchHits<Product> hits) {
        return hits.stream()
                .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private SearchResult executeSearch(NativeQuery query, int page) {
        var hits = operations.search(query, Product.class);
        return SearchResult.from(hits, page);
    }

    // ================================================================
    // 搜索结果封装
    // ================================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private long totalHits;
        private int page;
        private int size;
        private List<SearchItem> items;

        public static SearchResult from(SearchHits<Product> hits, int page) {
            List<SearchItem> items = hits.stream().map(hit -> {
                Product p = hit.getContent();
                Map<String, List<String>> highlights = new LinkedHashMap<>();
                hit.getHighlightFields().forEach((field, values) ->
                        highlights.put(field, new ArrayList<>(values)));
                return SearchItem.builder()
                        .product(p)
                        .score(hit.getScore())
                        .highlights(highlights)
                        .build();
            }).collect(Collectors.toList());

            return SearchResult.builder()
                    .totalHits(hits.getTotalHits())
                    .page(page)
                    .size(items.size())
                    .items(items)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchItem {
        private Product product;
        private float score;
        private Map<String, List<String>> highlights;
    }
}
