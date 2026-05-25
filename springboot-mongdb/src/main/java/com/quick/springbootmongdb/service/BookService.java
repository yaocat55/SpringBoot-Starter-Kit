package com.quick.springbootmongdb.service;

import com.quick.springbootmongdb.model.Book;
import com.quick.springbootmongdb.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Book Service: two ways to operate MongoDB.
 * <ol>
 *   <li>{@link BookRepository}: declarative, extends interface, for standard CRUD + simple queries</li>
 *   <li>{@link MongoTemplate}: programmatic, flexible query building, for complex conditions / aggregation</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final MongoTemplate mongoTemplate;

    // ==================== Repository (declarative) ====================

    public Book addBook(Book book) {
        return bookRepository.save(book);
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book getBook(String id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
    }

    public Book updateBook(String id, Book book) {
        Book existing = getBook(id);
        existing.setTitle(book.getTitle());
        existing.setAuthor(book.getAuthor());
        existing.setPrice(book.getPrice());
        existing.setTags(book.getTags());
        existing.setPublisher(book.getPublisher());
        return bookRepository.save(existing);
    }

    public void deleteBook(String id) {
        bookRepository.deleteById(id);
    }

    public List<Book> findByAuthor(String author) {
        return bookRepository.findByAuthor(author);
    }

    public List<Book> findByPriceRange(BigDecimal min, BigDecimal max) {
        return bookRepository.findByPriceBetween(min, max);
    }

    public List<Book> findByTag(String tag) {
        return bookRepository.findByTagsContaining(tag);
    }

    public List<Book> findByCategoryOrderByPriceDesc(String category) {
        return bookRepository.findByCategoryOrderByPriceDesc(category);
    }

    public List<Book> findByTitleRegex(String regex) {
        return bookRepository.findByTitleRegex(regex);
    }

    public List<Book> findByActiveAndCategory(Boolean active, String category) {
        return bookRepository.findByActiveAndCategory(active, category);
    }

    public List<Book> findByPagesGreaterThan(Integer pages) {
        return bookRepository.findByPagesGreaterThan(pages);
    }

    public List<Book> findByPublisherAndMinRating(String publisherName, int minRating) {
        return bookRepository.findByPublisherAndMinRating(publisherName, minRating);
    }

    public List<Book> findBriefByCategory(String category) {
        return bookRepository.findBriefByCategory(category);
    }

    // ==================== MongoTemplate (programmatic) ====================

    public List<Book> advancedSearch(String category, BigDecimal minPrice, int minPages) {
        Query query = new Query();
        query.addCriteria(Criteria.where("category").is(category)
                .and("price").gte(minPrice)
                .and("pages").gte(minPages));
        query.limit(20);
        return mongoTemplate.find(query, Book.class);
    }

    public long batchUpdateActive(String category, boolean active) {
        Query query = new Query(Criteria.where("category").is(category));
        Update update = new Update().set("active", active);
        return mongoTemplate.updateMulti(query, update, Book.class).getModifiedCount();
    }

    public List<Map> aggregateByCategory() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("category")
                        .count().as("count")
                        .avg("price").as("avgPrice")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "books", Map.class);
        return results.getMappedResults();
    }

    /** 批量插入（MongoTemplate.insert），直接写入，不经过 save() 的 upsert 逻辑 */
    public List<Book> batchInsert(List<Book> books) {
        return new ArrayList<>((Collection<? extends Book>)
                mongoTemplate.insert(books, Book.class));
    }

    /** 部分更新：只更新指定字段，不对整条文档做替换 */
    public long partialUpdate(String id, BigDecimal newPrice, Boolean active) {
        Query query = new Query(Criteria.where("id").is(id));
        Update update = new Update();
        if (newPrice != null) {
            update.set("price", newPrice);
        }
        if (active != null) {
            update.set("active", active);
        }
        return mongoTemplate.updateFirst(query, update, Book.class).getModifiedCount();
    }

    /** 条件删除：删除符合条件的所有文档 */
    public long deleteByCategory(String category) {
        Query query = new Query(Criteria.where("category").is(category));
        return mongoTemplate.remove(query, Book.class).getDeletedCount();
    }
}
