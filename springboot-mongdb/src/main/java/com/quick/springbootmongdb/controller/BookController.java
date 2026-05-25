package com.quick.springbootmongdb.controller;

import com.quick.springbootmongdb.model.Book;
import com.quick.springbootmongdb.model.Review;
import com.quick.springbootmongdb.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST 控制器 —— 验证 MongoDB CRUD 及高级查询。
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    // ======================== 基础 CRUD ========================

    @GetMapping
    public List<Book> listAll() {
        return bookService.getAllBooks();
    }

    @GetMapping("/{id}")
    public Book getById(@PathVariable String id) {
        return bookService.getBook(id);
    }

    @PostMapping
    public Book create(@RequestBody Book book) {
        return bookService.addBook(book);
    }

    @PutMapping("/{id}")
    public Book update(@PathVariable String id, @RequestBody Book book) {
        return bookService.updateBook(id, book);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        bookService.deleteBook(id);
        return Map.of("success", true, "deletedId", id);
    }

    // ======================== 方法命名查询 ========================

    @GetMapping("/search/author")
    public List<Book> searchByAuthor(@RequestParam String author) {
        return bookService.findByAuthor(author);
    }

    @GetMapping("/search/price")
    public List<Book> searchByPrice(@RequestParam BigDecimal min, @RequestParam BigDecimal max) {
        return bookService.findByPriceRange(min, max);
    }

    @GetMapping("/search/tag")
    public List<Book> searchByTag(@RequestParam String tag) {
        return bookService.findByTag(tag);
    }

    @GetMapping("/search/category")
    public List<Book> searchByCategory(@RequestParam String category) {
        return bookService.findByCategoryOrderByPriceDesc(category);
    }

    @GetMapping("/search/title-regex")
    public List<Book> searchByTitleRegex(@RequestParam String regex) {
        return bookService.findByTitleRegex(regex);
    }

    @GetMapping("/search/active")
    public List<Book> searchByActiveAndCategory(@RequestParam Boolean active,
                                                 @RequestParam String category) {
        return bookService.findByActiveAndCategory(active, category);
    }

    @GetMapping("/search/pages-gt")
    public List<Book> searchByMinPages(@RequestParam Integer pages) {
        return bookService.findByPagesGreaterThan(pages);
    }

    @GetMapping("/search/publisher-rating")
    public List<Book> searchByPublisherAndRating(@RequestParam String publisher,
                                                  @RequestParam int minRating) {
        return bookService.findByPublisherAndMinRating(publisher, minRating);
    }

    @GetMapping("/search/category-brief")
    public List<Book> searchBriefByCategory(@RequestParam String category) {
        return bookService.findBriefByCategory(category);
    }

    // ======================== MongoTemplate 高级查询 ========================

    @GetMapping("/search/advanced")
    public List<Book> advancedSearch(@RequestParam String category,
                                      @RequestParam BigDecimal minPrice,
                                      @RequestParam(defaultValue = "0") int minPages) {
        return bookService.advancedSearch(category, minPrice, minPages);
    }

    /** 批量操作：一键把某分类全部 setActive(true/false) */
    @PutMapping("/batch/active")
    public Map<String, Object> batchActive(@RequestParam String category,
                                            @RequestParam boolean active) {
        long count = bookService.batchUpdateActive(category, active);
        return Map.of("success", true, "affectedRows", count,
                "note", "MongoTemplate updateMulti，一条命令批量更新");
    }

    // ======================== MongoTemplate 增删改 ========================

    /** 批量插入（MongoTemplate.insert，不走 save 的 upsert 逻辑） */
    @PostMapping("/batch")
    public List<Book> batchInsert(@RequestBody List<Book> books) {
        return bookService.batchInsert(books);
    }

    /** 部分更新：只更新价格和 active，不改其他字段 */
    @PatchMapping("/{id}")
    public Map<String, Object> partialUpdate(@PathVariable String id,
                                              @RequestParam(required = false) BigDecimal price,
                                              @RequestParam(required = false) Boolean active) {
        long count = bookService.partialUpdate(id, price, active);
        return Map.of("success", true, "affectedRows", count);
    }

    /** 条件删除：删除某分类下所有书 */
    @DeleteMapping("/batch/category")
    public Map<String, Object> deleteByCategory(@RequestParam String category) {
        long count = bookService.deleteByCategory(category);
        return Map.of("success", true, "deletedCount", count);
    }

    // ======================== 聚合查询 ========================

    @GetMapping("/aggregate/category")
    public List<Map> aggregateByCategory() {
        return bookService.aggregateByCategory();
    }

    // ======================== 内嵌文档操作 ========================

    /** 给指定图书添加书评（操作内嵌数组） */
    @PostMapping("/{id}/reviews")
    public Book addReview(@PathVariable String id,
                           @RequestParam String user,
                           @RequestParam int rating,
                           @RequestParam String comment) {
        Book book = bookService.getBook(id);
        book.getReviews().add(Review.of(user, rating, comment));
        return bookService.addBook(book);
    }
}
