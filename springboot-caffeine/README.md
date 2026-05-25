# SpringBoot Caffeine 本地缓存接入模板

> **省流版**：在方法上加个 `@Cacheable`，第一次调用查数据库（慢），之后直接从内存拿（快 100 倍以上）。零外部依赖，不用装 Redis，三分钟集成。

---

## 一、Caffeine 是什么，为什么用它

### 1.1 一句话说清楚

你有一个方法 `getUserById(1L)`，每次调用都要查数据库，耗时 200ms。把方法加上 `@Cacheable("users")`，第一次照常查库，返回结果自动存内存。第二次调同样的参数，**直接从内存拿，耗时 < 1ms**。

```
第一次调用:  Controller → Service → DB (200ms) → 返回 + 写入 Caffeine
第二次调用:  Controller → Service → Caffeine (<1ms) → 返回
```

### 1.2 Caffeine vs Redis vs 无缓存

| | 无缓存 | Caffeine（本地） | Redis（分布式） |
|---|---|---|---|
| 速度 | 查 DB，50-500ms | **内存直读，< 1ms** | 网络 IO，1-5ms |
| 部署 | 不需要 | **不需要** | 需要装 Redis |
| 多实例共享 | — | 不支持（各自存一份） | 支持 |
| 重启丢失 | — | 是（内存） | 否（可持久化） |
| 适用场景 | — | 单机热数据、配置、字典 | 分布式 Session、跨实例共享 |

**结论**：99% 的项目里 Caffeine 够用了。比如查用户信息、商品详情、字典表这些，用 Caffeine 就行，别折腾 Redis。

### 1.3 Caffeine 核心策略

Caffeine 的缓存淘汰基于 **W-TinyLFU** 算法，比 Redis 的 LRU 更聪明。你只需要配三个参数：

| 参数 | 含义 | 示例 |
|---|---|---|
| `maximumSize` | 最多存多少条 | `500` — 超过 500 条自动踢出不常用的 |
| `expireAfterWrite` | 写入后多久过期 | `10m` — 写入 10 分钟后自动删除 |
| `expireAfterAccess` | 最后访问后多久过期 | `30m` — 30 分钟没人查就删除 |

---

## 二、怎么接入（2 步）

### 2.1 加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

`spring-boot-starter-cache` 是 Spring Cache 抽象层（提供 `@Cacheable` 等注解），`caffeine` 是底层实现。

### 2.2 配 yml + 启动类加 @EnableCaching

```yaml
# application.yml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterWrite=10m   # 全局默认：最多 500 条，10 分钟过期
```

```java
@SpringBootApplication
@EnableCaching     // 就这一行，开启缓存注解
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

到这里为止，缓存已经可用了。`spring.cache.caffeine.spec` 一行就能配好，**不需要写任何 Java 配置类**。

---

## 三、Spring Cache 三大注解

Spring Cache 是最简单的缓存使用方式 —— 只在方法上加注解，不动业务逻辑。

### 3.1 @Cacheable —— 自动缓存查询结果

```java
@Cacheable(value = "users", key = "#id")
public User getUserById(Long id) {
    // 第一次调用：执行方法体，查 DB，返回结果自动存入缓存
    // 后续调用：直接返回缓存值，方法体根本不执行
    return db.findById(id);
}
```

| 参数 | 说明 | 示例 |
|---|---|---|
| `value` | 缓存名称（必填） | `"users"` |
| `key` | 缓存键（SpEL 表达式） | `#id` — 用参数 id 作为 key |
| `condition` | 什么条件下才缓存 | `#id > 10` — 只缓存 id > 10 的结果 |
| `unless` | 什么条件下不缓存 | `#result == null` — null 不缓存 |
| `sync` | 同步加载（防缓存击穿） | `true` — 同一 key 只放一个线程去加载 |

### 3.2 @CachePut —— 更新缓存（增/改操作后刷新）

```java
@CachePut(value = "users", key = "#user.id")
public User updateUser(User user) {
    // 每次都会执行方法体，然后把返回值强制写入缓存
    db.update(user);
    return user;
}
```

和 `@Cacheable` 的区别：`@Cacheable` 是不执行方法体直接返回缓存，`@CachePut` 是**每次都执行方法体并更新缓存**。

### 3.3 @CacheEvict —— 删除缓存（删操作后清缓存）

```java
@CacheEvict(value = "users", key = "#id")
public void deleteUser(Long id) {
    db.delete(id);
    // 方法执行成功后自动清掉 key=id 的缓存
}
```

| 参数 | 说明 |
|---|---|
| `key` | 删单个 key |
| `allEntries = true` | 清空整个 `users` 缓存（慎用） |
| `beforeInvocation = true` | 方法执行前就清缓存（默认是执行后） |

### 3.4 @Caching —— 组合多个注解

```java
@Caching(
    evict = { @CacheEvict(value = "users", key = "#user.id") },
    put   = { @CachePut(value = "users", key = "#user.id") }
)
public User saveAndRefresh(User user) {
    // 先清缓存，再执行方法，再写缓存
}
```

---

## 四、编程式 CacheManager 配置（不同缓存不同策略）

yml 只能配全局默认值。如果不同业务需要不同的过期时间，用编程式配置多个 CacheManager：

```java
@Bean("userCacheManager")
public CacheManager userCacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder()
            .initialCapacity(200)
            .maximumSize(2000)
            .expireAfterAccess(30, TimeUnit.MINUTES)   // 30 分钟无访问过期
            .expireAfterWrite(1, TimeUnit.HOURS)         // 最长活 1 小时
            .recordStats());                              // 开启统计
    return manager;
}
```

使用时指定 `cacheManager`：

```java
@Cacheable(value = "users", key = "#id", cacheManager = "userCacheManager")
public User getUserById(Long id) { ... }
```

本项目定义了 4 种 CacheManager：

| CacheManager | TTL | 适用场景 |
|---|---|---|
| `defaultCacheManager` | 写入后 10 分钟 | 通用 |
| `userCacheManager` | 访问后 30 分钟 / 最长 1 小时 | 用户信息 |
| `productCacheManager` | 写入后 2 分钟 | 商品（时效性高） |
| `foreverCacheManager` | 永不过期 | 字典、配置（靠容量淘汰） |

---

## 五、实操：curl 直接测

启动后端口号 `8088`。

### 5.1 感受缓存加速

```bash
# 先看 benchmark 对比
curl http://localhost:8088/api/benchmark/1

# 返回:
# {"firstCallMs": 520, "secondCallMs": 0, "speedUp": "520x", ...}
#              ↑ 第一次查 DB 慢         ↑ 第二次走缓存快

# 自己手动试：连续调两次
curl http://localhost:8088/api/users/1    # 第一次，控制台会打印 【缓存未命中】
curl http://localhost:8088/api/users/1    # 第二次，控制台没有任何日志，瞬间返回
```

### 5.2 各种缓存操作

```bash
# --- @Cacheable 查 ---
curl http://localhost:8088/api/users/1                  # 默认 10min TTL
curl http://localhost:8088/api/users/1/custom-ttl       # 自定义 30min TTL
curl http://localhost:8088/api/users/1/adult            # 带条件缓存
curl http://localhost:8088/api/users/1/unless-null      # null 不缓存
curl http://localhost:8088/api/users/99/unless-null     # 不存在的用户，不缓存

# 商品（2 分钟短 TTL，sync=true 防击穿）
curl http://localhost:8088/api/products/1

# --- @CachePut 增/改 ---
curl -X POST http://localhost:8088/api/users \
  -H "Content-Type: application/json" \
  -d '{"id":10,"name":"新用户","email":"new@example.com","age":25}'

curl -X PUT http://localhost:8088/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"张三已改名","email":"newemail@example.com","age":29}'

# --- @CacheEvict 删 ---
curl -X DELETE http://localhost:8088/api/users/1         # 删 DB + 清此条缓存
curl -X DELETE http://localhost:8088/api/users/cache      # 清空全部 users 缓存

# --- @Caching 组合 ---
curl -X PUT http://localhost:8088/api/users/1/refresh \
  -H "Content-Type: application/json" \
  -d '{"name":"张三刷新","email":"refresh@example.com","age":30}'
```

### 5.3 全部 API 速查

| 方法 | 路径 | 注解 | 说明 |
|---|---|---|---|
| GET | `/api/users/{id}` | `@Cacheable` | 查用户（10min TTL） |
| GET | `/api/users/{id}/custom-ttl` | `@Cacheable` | 查用户（30min TTL） |
| GET | `/api/users/{id}/adult` | `@Cacheable` + condition | 条件缓存 |
| GET | `/api/users/{id}/unless-null` | `@Cacheable` + unless | null 不缓存 |
| GET | `/api/products/{id}` | `@Cacheable` + sync | 商品（2min TTL，防击穿） |
| POST | `/api/users` | `@CachePut` | 新增 + 写缓存 |
| PUT | `/api/users/{id}` | `@CachePut` | 更新 + 刷新缓存 |
| PUT | `/api/users/{id}/refresh` | `@Caching` | 组合操作（先删再写） |
| DELETE | `/api/users/{id}` | `@CacheEvict` | 删除 + 清单个缓存 |
| DELETE | `/api/users/cache` | `@CacheEvict` allEntries | 清空全部 users 缓存 |
| GET | `/api/benchmark/{id}` | — | 对比缓存加速效果 |

---

## 六、最佳实践 & 常见坑

### 6.1 什么时候用，什么时候不用

| 适合缓存 | 不适合缓存 |
|---|---|
| 读多写少（用户信息、商品详情） | 写多读少（日志、流水） |
| 查询耗时（复杂 SQL、外部 API） | 毫秒级简单查询 |
| 数据变化不频繁 | 实时性要求极高（比如余额） |
| 热点数据（频繁被查） | 查询条件千变万化 |

### 6.2 三个常见坑

**坑 1：类内部方法调用不走代理，缓存失效**

```java
// 错误示范
public User getById(Long id) {
    return innerGetById(id);  // this.innerGetById() 调用，不会有缓存
}

@Cacheable("users")
public User innerGetById(Long id) { ... }

// 原因：Spring AOP 拦截的是 Bean 的外部调用，类内部调用不经过代理。
// 解决：要么让调用方注入 Bean 调，要么把缓存方法放到独立类里。
```

**坑 2：null 值处理 —— 缓存穿透**

```java
// 查不到的用户，每次都会穿透缓存去查 DB
@Cacheable(value = "users", key = "#id")
public User getById(Long id) {
    return db.findById(id); // id=999 返回 null，不会被缓存！
}
// 解决：加 unless 或手动缓存一个空对象
@Cacheable(value = "users", key = "#id", unless = "#result == null")
```

**坑 3：sync = true 的作用**

```java
// 高并发下 100 个请求同时查同一个过期 key，默认 100 个全去查 DB（缓存击穿）
@Cacheable(value = "hot-data", key = "#id")
public Data getHotData(Long id) { ... }

// sync = true：只有一个线程去查 DB 重建缓存，其余 99 个等待
@Cacheable(value = "hot-data", key = "#id", sync = true)
public Data getHotData(Long id) { ... }
```

### 6.3 缓存监控

开启 `recordStats()` 后，可以通过 Actuator 查看缓存命中率：

```yaml
# 暴露 actuator 端点
management:
  endpoints:
    web:
      exposure:
        include: caches
```

```bash
curl http://localhost:8088/actuator/caches
# 能看到每个 cache 的命中次数、未命中次数、驱逐次数
```

### 6.4 生产配置模板

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=30m,recordStats

# 开启统计
management:
  endpoints:
    web:
      exposure:
        include: health,info,caches
```

---

## 七、Caffeine 常用配置参数速查

```yaml
spring:
  cache:
    caffeine:
      # 完整 spec 格式：key1=value1,key2=value2,...
      spec: maximumSize=500,expireAfterWrite=10m,expireAfterAccess=5m,recordStats
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `initialCapacity` | int | 初始容量（减少扩容开销） |
| `maximumSize` | long | 最大条目数（超过后 W-TinyLFU 自动淘汰） |
| `maximumWeight` | long | 最大权重（配合 weigher 使用） |
| `expireAfterWrite` | Duration | 写入后 N 秒/分/时过期 |
| `expireAfterAccess` | Duration | 最后访问后 N 秒/分/时过期 |
| `refreshAfterWrite` | Duration | 写入后 N 秒异步刷新（需实现 CacheLoader） |
| `recordStats` | — | 开启命中率统计 |
| `weakKeys` | — | 弱引用 key（GC 时回收） |
| `weakValues` | — | 弱引用 value |
| `softValues` | — | 软引用 value（OOM 前回收） |

---

## 八、Spring Cache SpEL 表达式速查

注解里的 `key`、`condition`、`unless` 都支持 SpEL：

| 表达式 | 说明 |
|---|---|
| `#id` | 取参数名为 id 的值 |
| `#a0` / `#p0` | 取第一个参数 |
| `#user.id` | 取参数的属性 |
| `#result` | 方法返回值（`unless` 和 `@CachePut` 的 key 中可用） |
| `#root.methodName` | 当前方法名 |
| `#root.targetClass` | 当前类 |

---

## 九、项目结构

```
springboot-caffeine/
├── pom.xml
├── application.yml
└── src/main/java/com/quick/springbootcaffeine/
    ├── SpringbootCaffeineApplication.java   # 启动类（@EnableCaching）
    ├── config/
    │   └── CacheConfig.java                 # 4 种 CacheManager（不同 TTL）
    ├── model/
    │   └── User.java                        # 用户实体
    ├── service/
    │   ├── UserService.java                 # @Cacheable / @CachePut / @CacheEvict 完整演示
    │   └── ProductService.java              # 短 TTL + sync 防击穿 演示
    └── controller/
        └── CacheController.java             # REST 接口 + benchmark 对比
```
