# SpringBoot MongoDB 接入模板

> **省流版**：MongoDB 就是一个 **存 JSON 的数据库**。你把 JSON 扔进去，它原样存起来，查出来还是 JSON。没有表结构、没有 JOIN、不用建表。三分钟看完这篇就能干活。

---

## 一、MongoDB 是什么，数据长什么样

### 1.1 核心概念一句话对比

| MySQL（你熟悉的） | MongoDB（你要用的） |
|---|---|
| 数据库 Database | 数据库 Database |
| 表 Table | **集合 Collection** |
| 行 Row | **文档 Document（就是一个 JSON）** |
| 列 Column | **字段 Field** |
| JOIN 联表查 | **嵌套！直接把子数据塞进主文档里** |
| 建表时固定字段 | **无 Schema，随时加字段** |

### 1.2 数据长这样

MySQL 存一条图书记录，通常要拆成 3 张表：

```sql
-- MySQL: books 表
id | title      | price
1  | Spring实战  | 99.00

-- MySQL: publishers 表（需要 JOIN）
book_id | name           | city
1       | 人民邮电出版社   | 北京

-- MySQL: reviews 表（又需要 JOIN）
book_id | user | rating | comment
1       | 张三  | 5      | 非常好
```

MongoDB 只有 **一个文档**：

```json
{
  "_id": "664a3f2b...",
  "title": "Spring实战",
  "author": "Craig Walls",
  "price": 99.00,
  "tags": ["Java", "Spring"],
  "publisher": {                    // ← 内嵌子文档（对应 MySQL 的 publishers 表）
    "name": "人民邮电出版社",
    "city": "北京"
  },
  "reviews": [                      // ← 内嵌数组（对应 MySQL 的 reviews 表）
    { "user": "张三", "rating": 5, "comment": "非常好" },
    { "user": "李四", "rating": 4, "comment": "不错" }
  ],
  "metadata": { "language": "中文" } // ← 动态字段，随时加，不用 ALTER TABLE
}
```

**核心区别**：MySQL 用"拆表 + 外键 + JOIN"表达数据关系，MongoDB 用"嵌套 + 数组"把相关数据直接塞进一条文档里。查一条数据不需要 JOIN 多张表，一次查询全出来。

### 1.3 MongoDB 数据类型速查

| 类型 | 示例 | 说明 |
|---|---|---|
| String | `"hello"` | 字符串 |
| Number | `99.00` / `500` | 数字（Double / Int32 / Int64 / Decimal128） |
| Boolean | `true` / `false` | 布尔 |
| Array | `["Java", "Spring"]` | 数组 |
| Object / Embedded Document | `{"name": "张三"}` | 嵌套子文档 |
| ObjectId | `ObjectId("...")` | 12 字节唯一 ID（默认主键） |
| Date | `ISODate("2025-01-01")` | 日期时间 |
| Null | `null` | 空值 |

---

## 二、怎么接入（3 步上手）

### 2.1 加依赖

```xml
<!-- pom.xml 核心依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

就这一个，Spring Boot 自动配好所有东西。

### 2.2 配连接

```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/springboot_mongdb_demo  # 本地默认端口，可不配
      # database: springboot_mongdb_demo  # 也可以用 database 字段指定库名
```

如果你本地没装 MongoDB，最快的方式是 Docker 启动：

```bash
docker run -d --name mongo -p 27017:27017 mongo:7
```

启动类加两个注解即可：

```java
@SpringBootApplication
@EnableMongoRepositories   // 扫描 Repository 接口
@EnableMongoAuditing        // 自动填充 @CreatedDate / @LastModifiedDate
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2.3 写代码 —— 两种操作方式

Spring Data MongoDB 提供了**两套 API**，分工明确：

| 方式 | 适用场景 | 特点 |
|---|---|---|
| **MongoRepository** | 标准 CRUD + 简单条件查询 | 声明式，继承接口，不写一行实现 |
| **MongoTemplate** | 复杂条件查询、批量更新、聚合统计 | 编程式，链式调用，灵活度高 |

---

## 三、方式一：MongoRepository（声明式，日常够了）

### 3.1 定义实体

```java
@Data
@Builder
@Document(collection = "books")   // 对应 MongoDB 的 collection "books"
public class Book {

    @Id                             // 主键，不赋值时自动生成 ObjectId
    private String id;

    @Indexed                        // 单字段索引
    private String title;

    @Field("category")              // 显式指定 MongoDB 字段名
    private String category;

    private BigDecimal price;
    private List<String> tags;      // 数组字段
    private Publisher publisher;    // 内嵌子文档
    private List<Review> reviews;   // 内嵌数组

    @CreatedDate                    // 自动填创建时间（需要 @EnableMongoAuditing）
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate               // 自动填更新时间
    @Field("updated_at")
    private LocalDateTime updatedAt;
}
```

### 3.2 定义 Repository

继承 `MongoRepository<实体, 主键类型>`，Spring 自动生成实现类，**这个接口不用写任何实现代码**。

**重要：父接口 `MongoRepository` 已经自带了一套 CRUD 方法，不需要你自己写：**

| 继承来的方法 | 作用 |
|---|---|
| `save(entity)` | 新增或修改（有 id 则更新，无 id 则插入） |
| `saveAll(entities)` | 批量新增/修改 |
| `findById(id)` | 按主键查单个 |
| `findAll()` | 查全部 |
| `findAllById(ids)` | 按多个主键查 |
| `deleteById(id)` | 按主键删 |
| `delete(entity)` | 删指定实体 |
| `deleteAllById(ids)` | 按多个主键删 |
| `deleteAll()` | 清空整个集合 |
| `count()` | 总数 |
| `existsById(id)` | 判断是否存在 |

**所以你的接口里只需要写"上面没有的"查询方法**，增删改直接用继承的就行：

```java
public interface BookRepository extends MongoRepository<Book, String> {

    // 方法命名查询 —— Spring Data 自动根据方法名生成查询逻辑
    List<Book> findByAuthor(String author);                              // 按作者查
    List<Book> findByPriceBetween(BigDecimal min, BigDecimal max);       // 价格区间
    List<Book> findByTagsContaining(String tag);                          // 按标签查
    List<Book> findByActiveAndCategory(Boolean active, String category);  // 多条件

    // 复杂查询写 @Query，里面是 MongoDB 原生 JSON
    @Query("{ 'publisher.name': ?0, 'reviews.rating': { $gte: ?1 } }")
    List<Book> findByPublisherAndMinRating(String publisherName, int minRating);
}
```

方法命名规则举例：

| 方法名 | 等价 MongoDB 查询 |
|---|---|
| `findByAuthor("张三")` | `db.books.find({"author": "张三"})` |
| `findByPriceBetween(50, 100)` | `db.books.find({"price": {"$gte": 50, "$lte": 100}})` |
| `findByTagsContaining("Java")` | `db.books.find({"tags": "Java"})` |

`findByXxx` / `And` / `Between` / `Containing` 这些都是固定关键词，Spring Data 会自动解析。

### 3.3 使用

```java
@Service
public class BookService {
    private final BookRepository bookRepository;

    // 增
    public Book addBook(Book book) { return bookRepository.save(book); }

    // 查全部
    public List<Book> getAllBooks() { return bookRepository.findAll(); }

    // 查单个
    public Book getBook(String id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
    }

    // 改
    public Book updateBook(String id, Book book) {
        Book existing = getBook(id);
        existing.setTitle(book.getTitle());
        existing.setPrice(book.getPrice());
        return bookRepository.save(existing);
    }

    // 删
    public void deleteBook(String id) { bookRepository.deleteById(id); }
}
```

跟 JPA / MyBatis-Plus 一个套路，零学习成本。

---

## 四、方式二：MongoTemplate（编程式，复杂场景用）

当查询条件需要动态拼、或者需要批量更新、聚合统计时，用 MongoTemplate：

```java
@Service
public class BookService {
    private final MongoTemplate mongoTemplate;

    // 多条件动态查询：查某分类下，价格 >= x，页数 >= y 的书，最多 20 本
    public List<Book> advancedSearch(String category, BigDecimal minPrice, int minPages) {
        Query query = new Query();
        query.addCriteria(Criteria.where("category").is(category)
                .and("price").gte(minPrice)
                .and("pages").gte(minPages));
        query.limit(20);
        return mongoTemplate.find(query, Book.class);
    }

    // 批量更新：一键把某分类下所有书的 active 设为 true
    public long batchUpdateActive(String category, boolean active) {
        Query query = new Query(Criteria.where("category").is(category));
        Update update = new Update().set("active", active);
        return mongoTemplate.updateMulti(query, update, Book.class).getModifiedCount();
    }

    // 聚合：按分类统计数量 + 平均价格
    public List<Map> aggregateByCategory() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("category")
                        .count().as("count")
                        .avg("price").as("avgPrice")
        );
        return mongoTemplate.aggregate(aggregation, "books", Map.class).getMappedResults();
    }

    // 批量插入（insert 不走 save 的 upsert 逻辑，纯写入更快）
    public List<Book> batchInsert(List<Book> books) {
        return new ArrayList<>(mongoTemplate.insert(books, Book.class));
    }

    // 部分更新：只改价格和 active，不动其他字段
    public long partialUpdate(String id, BigDecimal newPrice, Boolean active) {
        Query query = new Query(Criteria.where("id").is(id));
        Update update = new Update();
        if (newPrice != null) update.set("price", newPrice);
        if (active != null)   update.set("active", active);
        return mongoTemplate.updateFirst(query, update, Book.class).getModifiedCount();
    }

    // 条件删除：删掉某分类下所有文档
    public long deleteByCategory(String category) {
        Query query = new Query(Criteria.where("category").is(category));
        return mongoTemplate.remove(query, Book.class).getDeletedCount();
    }
}
```

---

## 五、实操：存进去、查出来（curl 直接复制用）

启动项目后 `http://localhost:8087`，下面每个命令都能直接复制执行。

### 5.1 插入一条数据（重点看 JSON 结构）

```bash
curl -X POST http://localhost:8087/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring实战",
    "author": "Craig Walls",
    "category": "技术",
    "price": 99.00,
    "pages": 500,
    "publishDate": "2022-01-15",
    "tags": ["Java", "Spring", "后端"],
    "publisher": {
      "name": "人民邮电出版社",
      "city": "北京",
      "year": 2022
    },
    "reviews": [
      {"user": "张三", "rating": 5, "comment": "非常好"}
    ],
    "metadata": {"format": "纸质", "language": "中文"},
    "active": true
  }'
```

MongoDB 返回的 `_id` 是自动生成的：

```json
{
  "id": "664a3f2b8e1b2c3d4e5f6a7b",
  "title": "Spring实战",
  "author": "Craig Walls",
  "category": "技术",
  "price": 99.00,
  "pages": 500,
  "publishDate": "2022-01-15",
  "tags": ["Java", "Spring", "后端"],
  "publisher": { "name": "人民邮电出版社", "city": "北京", "year": 2022 },
  "reviews": [{"user": "张三", "rating": 5, "comment": "非常好"}],
  "metadata": {"format": "纸质", "language": "中文"},
  "active": true
}
```

**关键点**：整个 JSON 原样存进 MongoDB，publisher 和 reviews 都在这一条文档里，不用像 MySQL 那样拆三张表分别 INSERT。

### 5.2 再造几条数据（方便后面测查询）

```bash
# 第二本
curl -X POST http://localhost:8087/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "深入理解Java虚拟机",
    "author": "周志明",
    "category": "技术",
    "price": 79.00,
    "pages": 400,
    "publishDate": "2020-06-01",
    "tags": ["Java", "JVM", "后端"],
    "publisher": {"name": "机械工业出版社", "city": "北京", "year": 2020},
    "reviews": [
      {"user": "李四", "rating": 5, "comment": "JVM圣经"},
      {"user": "王五", "rating": 4, "comment": "有点难"}
    ],
    "metadata": {"format": "纸质"},
    "active": true
  }'

# 第三本（一本小说，不同分类，用来测分类聚合）
curl -X POST http://localhost:8087/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "三体",
    "author": "刘慈欣",
    "category": "科幻",
    "price": 48.00,
    "pages": 600,
    "publishDate": "2008-01-01",
    "tags": ["科幻", "中国", "雨果奖"],
    "publisher": {"name": "重庆出版社", "city": "重庆", "year": 2008},
    "reviews": [
      {"user": "大刘粉", "rating": 5, "comment": "中国科幻巅峰"}
    ],
    "metadata": {"format": "电子版", "language": "中文"},
    "active": true
  }'
```

### 5.3 修改一条数据（全量替换）

```bash
# 拿到上面返回的 id，替换下面的 {book_id}
curl -X PUT http://localhost:8087/api/books/{book_id} \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring实战（第6版）",
    "author": "Craig Walls",
    "category": "技术",
    "price": 129.00,
    "pages": 550,
    "publishDate": "2023-12-01",
    "tags": ["Java", "Spring", "后端", "新版本"],
    "publisher": {"name": "人民邮电出版社", "city": "北京", "year": 2023},
    "active": true
  }'
```

### 5.4 往已有书籍追加书评（操作内嵌数组）

```bash
curl -X POST "http://localhost:8087/api/books/{book_id}/reviews?user=赵六&rating=4&comment=入门不错"
```

这条不用传 JSON，直接在 URL 上传参数，后端往 `reviews` 数组里追加一个元素。

### 5.5 按条件查

```bash
# 按作者查
curl "http://localhost:8087/api/books/search/author?author=周志明"

# 价格区间：50 ~ 200
curl "http://localhost:8087/api/books/search/price?min=50&max=200"

# 按标签查（tags 是数组，匹配数组里包含 "Java" 的）
curl "http://localhost:8087/api/books/search/tag?tag=Java"

# 按分类查（返回按价格从高到低排）
curl "http://localhost:8087/api/books/search/category?category=技术"

# 书名模糊匹配（正则，比如查书名里含 "Java" 的）
curl "http://localhost:8087/api/books/search/title-regex?regex=Java"

# 多条件：分类 + 上下架状态
curl "http://localhost:8087/api/books/search/active?active=true&category=技术"

# 页数大于指定值
curl "http://localhost:8087/api/books/search/pages-gt?pages=400"

# 出版社名 + 最低评分（@Query 自定义查询）
curl "http://localhost:8087/api/books/search/publisher-rating?publisher=人民邮电出版社&minRating=4"

# 只返回部分字段（投影查询，减少传输量）
curl "http://localhost:8087/api/books/search/category-brief?category=技术"

# 高级多条件：分类=技术，价格>=50，页数>=200
curl "http://localhost:8087/api/books/search/advanced?category=技术&minPrice=50&minPages=200"
```

### 5.6 批量更新 & 聚合统计 & 条件删除

```bash
# 一键把"技术"分类的书全部下架（active=false）—— updateMulti
curl -X PUT "http://localhost:8087/api/books/batch/active?category=技术&active=false"

# 部分更新：只改价格，不改其他字段 —— updateFirst + Update
curl -X PATCH "http://localhost:8087/api/books/{book_id}?price=88.00"

# 部分更新：同时改价格 + 上架状态
curl -X PATCH "http://localhost:8087/api/books/{book_id}?price=68.00&active=true"

# 批量插入（一次插多条）—— insert
curl -X POST http://localhost:8087/api/books/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"title": "书A", "author": "张三", "category": "技术", "price": 50, "pages": 300, "publisher": {"name": "出版社A"}},
    {"title": "书B", "author": "李四", "category": "科幻", "price": 40, "pages": 500, "publisher": {"name": "出版社B"}}
  ]'

# 条件删除：删掉"科幻"分类下所有书 —— remove
curl -X DELETE "http://localhost:8087/api/books/batch/category?category=科幻"

# 按分类聚合统计
curl "http://localhost:8087/api/books/aggregate/category"
```

返回结果示例：

```json
[
  {"count": 2, "avgPrice": 89.0, "_id": "技术"},
  {"count": 1, "avgPrice": 48.0, "_id": "科幻"}
]
```

### 5.7 整条 API 速查

| 方法 | 路径 | 一句话说明 |
|---|---|---|
| POST | `/api/books` | 插入一条（JSON 扔 Body 里） |
| GET | `/api/books` | 查全部 |
| GET | `/api/books/{id}` | 按 ID 查单个 |
| PUT | `/api/books/{id}` | 全量更新（JSON 扔 Body 里） |
| DELETE | `/api/books/{id}` | 按 ID 删 |
| GET | `/api/books/search/author?author=xxx` | 按作者查 |
| GET | `/api/books/search/price?min=50&max=200` | 价格区间 |
| GET | `/api/books/search/tag?tag=Java` | 按标签查 |
| GET | `/api/books/search/category?category=技术` | 按分类查（价格降序） |
| GET | `/api/books/search/title-regex?regex=Java` | 书名正则模糊查 |
| GET | `/api/books/search/active?active=true&category=技术` | 分类 + 上下架状态 |
| GET | `/api/books/search/pages-gt?pages=400` | 页数大于 |
| GET | `/api/books/search/publisher-rating?publisher=人民邮电出版社&minRating=4` | 出版社 + 最低评分 |
| GET | `/api/books/search/category-brief?category=技术` | 按分类查（只返 title/author/price） |
| GET | `/api/books/search/advanced?category=技术&minPrice=50&minPages=200` | 多条件高级查 |
| POST | `/api/books/batch` | 批量插入（数组扔 Body 里） |
| PATCH | `/api/books/{id}?price=88&active=true` | 部分更新，只改传的字段 |
| DELETE | `/api/books/batch/category?category=科幻` | 按分类条件删除 |
| PUT | `/api/books/batch/active?category=技术&active=true` | 批量更新某分类的 active |
| GET | `/api/books/aggregate/category` | 按分类聚合统计 |
| POST | `/api/books/{id}/reviews?user=张三&rating=5&comment=好` | 给一本书追评 |

---

## 六、索引

索引直接影响查询性能。在实体字段上加 `@Indexed` 即可创建单字段索引：

```java
@Indexed
private String title;
```

复合索引用 `@CompoundIndex`：

```java
@CompoundIndex(def = "{'category': 1, 'price': -1}")
public class Book { ... }
```

生产环境建议用 MongoDB Compass 或 mongo shell 检查索引情况：

```javascript
db.books.getIndexes()
```

---

## 七、Spring Data MongoDB vs MySQL（JPA）对应关系速查

| JPA / MySQL | Spring Data MongoDB | 说明 |
|---|---|---|
| `@Entity` | `@Document` | 标识实体 |
| `@Table` | `@Document(collection = "xxx")` | 指定表/集合名 |
| `@Id` | `@Id` | 主键（MongoDB 默认 ObjectId） |
| `@Column` | `@Field("xxx")` | 指定字段名 |
| `@GeneratedValue` | 无需，MongoDB 自动生成 | 主键生成策略 |
| `@OneToMany` / `@ManyToOne` | **直接内嵌 List/对象** | MongoDB 不需要外键 |
| `@JoinTable` | **不需要** | 关联表用内嵌数组替代 |
| `JpaRepository` | `MongoRepository` | Repository 接口 |
| `@Query(nativeQuery = true, value = "SELECT")` | `@Query(value = "{...}")` | 原生查询 |
| `@CreatedDate` / `@LastModifiedDate` | 一样 | 审计注解，需配合 `@EnableMongoAuditing` |
| `@Transactional` | 不支持多文档事务（4.0+ 副本集支持有限） | MongoDB 默认单文档原子性 |
| 建表语句 DDL | **不需要** | MongoDB 自动建集合，无 Schema |

---

## 八、常见坑

1. **事务**：MongoDB 4.0 才引入多文档事务，且必须部署副本集。单机模式下删东西不会回滚。设计数据模型时尽量把需要原子操作的字段放在一个文档里（MongoDB 单文档操作天然原子性）。

2. **不要用 MongoDB 替代 MySQL 的全部场景**：MongoDB 适合文档型数据（CMS、日志、用户画像、物联网数据），不适合强事务、多表 JOIN 的场景（订单系统、金融账本这种还是用 MySQL）。

3. **ObjectId 排序可替代时间排序**：ObjectId 前 4 字节是时间戳，按 `_id` 降序排列等价于按创建时间降序，不需要额外的时间索引。

4. **内存占用**：MongoDB 默认尽可能占用空闲内存做缓存。Docker 部署时建议限制容器内存，否则会吞掉宿主机所有空闲内存。

---

## 九、项目结构

```
springboot-mongdb/
├── pom.xml
├── application.yml
└── src/main/java/com/quick/springbootmongdb/
    ├── SpringbootMongdbApplication.java   # 启动类（含 @EnableMongoRepositories 和 @EnableMongoAuditing）
    ├── model/
    │   ├── Book.java                      # 图书实体（展示内嵌子文档、内嵌数组、Map 动态字段）
    │   ├── Publisher.java                 # 出版社（内嵌子文档）
    │   └── Review.java                    # 书评（内嵌数组元素）
    ├── repository/
    │   └── BookRepository.java            # Repository 接口（继承 MongoRepository，零实现）
    ├── service/
    │   └── BookService.java               # 演示 Repository 和 MongoTemplate 两种操作方式
    └── controller/
        └── BookController.java            # REST 接口（CRUD + 高级查询 + 聚合 + 内嵌文档操作）
```
