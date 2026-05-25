# SpringBoot Redis 开箱即用工具包

> 每次开新项目都要从零写 Redis 工具类？这个模块提供了 **RedisTemplate** 和 **Redisson** 双轨封装的完整 Redis 接入模板，覆盖全部开发场景，拷走即用。

## 目录

- [快速开始](#快速开始)
- [配置详解](#配置详解)
- [RedisTemplate vs Redisson —— 什么时候用哪个](#redistemplate-vs-redisson--什么时候用哪个)
- [工具类 API 手册](#工具类-api-手册)
  - [RedisUtil —— RedisTemplate 封装](#redisutil--redistemplate-封装)
  - [RedisLockUtil —— Redisson 分布式锁封装](#redislockutil--redisson-分布式锁封装)
- [常见场景示例](#常见场景示例)
- [常见问题](#常见问题)

---

## 快速开始

### 1. 依赖

将 `pom.xml` 中的依赖拷贝到自己项目：

```xml
<!-- Spring Boot Data Redis (Lettuce + 连接池) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

<!-- Redisson -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.46.0</version>
</dependency>

<!-- Jackson（序列化用，一般已有） -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

> **版本说明**：Spring Boot 3.5.x 基于 Spring Data Redis 3.5.x，底层 Lettuce 客户端。redisson-spring-boot-starter 版本需与 Spring Boot 兼容，3.46.0 兼容 3.5.x。

### 2. 配置

将 `application.yml` 复制到 `src/main/resources/`，修改连接信息：

```yaml
spring:
  data:
    redis:
      host: localhost      # 改成你的 Redis 地址
      port: 6379
      password:            # 有密码就填
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          enabled: true
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 2000ms
```

### 3. 拷贝工具类

将以下文件拷贝到自己项目的对应包路径下：

| 文件 | 作用 |
|------|------|
| `config/RedisTemplateConfig.java` | 配置 RedisTemplate 序列化规则 + 拆分 Operations Bean |
| `config/RedissonConfig.java` | 配置 RedissonClient（单机/哨兵自动切换） |
| `util/RedisUtil.java` | RedisTemplate 通用工具类，覆盖全部数据类型 |
| `util/RedisLockUtil.java` | Redisson 分布式锁工具类（可重入/公平/读写/信号量/闭锁/联锁） |

### 4. 注入即用

```java
@Service
public class UserService {

    @Autowired
    private RedisUtil redisUtil;          // 通用缓存操作

    @Autowired
    private RedisLockUtil lockUtil;       // 分布式锁

    @Autowired
    private RedissonClient redissonClient; // Redisson 原生客户端（高级场景）

    public User getUser(Long userId) {
        String key = "user:" + userId;
        User cached = (User) redisUtil.get(key);
        if (cached != null) return cached;

        User user = loadFromDB(userId);
        redisUtil.set(key, user, Duration.ofMinutes(30));
        return user;
    }

    public void updateUser(Long userId, User user) {
        lockUtil.executeWithLock("lock:user:" + userId, () -> {
            redisUtil.set("user:" + userId, user, Duration.ofMinutes(30));
            // ... 其他需要互斥的操作
        });
    }
}
```

---

## 配置详解

### application.yml 完整配置项

```yaml
spring:
  data:
    redis:
      # ===== 单机模式 =====
      host: localhost
      port: 6379
      password:
      database: 0        # 0-15
      timeout: 3000ms    # 连接超时
      client-type: lettuce

      # ===== 哨兵模式（启用时注释掉 host/port） =====
      # sentinel:
      #   master: mymaster
      #   nodes: 127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381

      # ===== 集群模式（启用时注释掉 host/port） =====
      # cluster:
      #   nodes: 127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002
      #   max-redirects: 3

      lettuce:
        pool:
          enabled: true
          max-active: 16    # 最大活跃连接
          max-idle: 8       # 最大空闲连接
          min-idle: 4       # 最小空闲（预热连接数）
          max-wait: 2000ms  # 等待连接超时（超时抛异常）
```

### 连接池参数调优指南

| 参数 | 建议值 | 说明 |
|------|--------|------|
| `max-active` | 业务并发数 × 1.5 | 太小排队，太大浪费资源 |
| `max-idle` | max-active 的 50% | 突发流量缓冲 |
| `min-idle` | 4-8 | 预热连接，避免冷启动延迟 |
| `max-wait` | 2-5 秒 | 拿不到连接时的最大等待，超时抛异常 |

### IDEA 实时校验生效

`spring-boot-starter-data-redis` 的自动配置类 `RedisAutoConfiguration` 会自动创建 `RedisTemplate<String, String>` 和 `StringRedisTemplate`，本模块的 `RedisTemplateConfig` 将其覆盖为 `RedisTemplate<String, Object>` 并使用 JSON 序列化。

---

## RedisTemplate vs Redisson —— 什么时候用哪个

| 维度 | RedisTemplate | Redisson |
|------|---------------|----------|
| **定位** | Spring Data 官方对 Redis 命令的薄封装 | 更高层的分布式对象抽象 |
| **编程模型** | 与 Redis 命令一一对应（`opsForValue().set()` → `SET`） | 分布式 Java 对象（`RMap`、`RQueue` 像用本地集合） |
| **分布式锁** | 需自己写 Lua 脚本 + WatchDog | 开箱即用 `RLock`，WatchDog 自动续期 |
| **序列化** | 可配置（本模块用 Jackson JSON） | 默认 Kryo/FST 二进制 |
| **连接方式** | 由 Lettuce/Jedis 管理 | 自带 Netty 连接池 |
| **适合场景** | 缓存读写、计数器、排行榜等常规操作 | 分布式锁、分布式集合、限流、Pub/Sub |

**一句话结论**：
- **缓存、计数器、排行榜** → `RedisUtil`（基于 RedisTemplate）
- **分布式锁、信号量、分布式集合** → `RedisLockUtil` / 直接注入 `RedissonClient`
- 两者可以在同一个项目中共存，本模块就是这样配置的

---

## 工具类 API 手册

### RedisUtil —— RedisTemplate 封装

#### Key 操作

```java
// 判断 key 是否存在
Boolean exists = redisUtil.hasKey("user:1001");

// 删除 key
redisUtil.delete("key1", "key2", "key3");

// 批量删除（按 pattern 匹配，慎用于生产环境大 key 量场景）
redisUtil.deleteByPattern("temp:*");

// 设置过期
redisUtil.expire("user:1001", 30, TimeUnit.MINUTES);
redisUtil.expire("user:1001", Duration.ofHours(1));

// 在指定时间过期
redisUtil.expireAt("coupon:123", someDate);

// 查看剩余 TTL（秒）
Long ttl = redisUtil.getExpire("user:1001");

// 移除过期（变持久化）
redisUtil.persist("user:1001");

// 查看 key 类型
DataType type = redisUtil.type("user:1001"); // STRING, HASH, LIST...

// SCAN 遍历（生产推荐替代 keys）
Cursor<String> cursor = redisUtil.scan("user:*", 100);
while (cursor.hasNext()) {
    String key = cursor.next();
    // ...
}
```

#### String 操作 —— 最常用

```java
// 基本读写
redisUtil.set("user:1001", user);
User u = (User) redisUtil.get("user:1001");

// 带过期时间（原子操作，推荐）
redisUtil.set("token:abc", token, Duration.ofMinutes(30));

// SETNX —— 分布式锁 / 去重
Boolean success = redisUtil.setIfAbsent("order:12345", "processing", Duration.ofSeconds(10));
if (success) { /* 拿到锁，处理订单 */ }

// SET XX —— 仅更新已存在的 key
redisUtil.setIfPresent("stock:sku001", 999);

// 批量操作
Map<String, Object> batch = Map.of("a", 1, "b", 2, "c", 3);
redisUtil.multiSet(batch);
List<Object> values = redisUtil.multiGet(List.of("a", "b", "c"));

// GETSET / GETDEL
Object old = redisUtil.getAndSet("counter", 0);  // 取旧值设新值
Object val = redisUtil.getAndDelete("temp:key");   // 取后删除

// 计数器
redisUtil.incr("pv:article:1");            // +1
redisUtil.incrBy("pv:article:1", 10);      // +10
redisUtil.incrByFloat("price", 0.5);       // +0.5
redisUtil.decr("stock:sku001");            // -1
redisUtil.decrBy("stock:sku001", 5);       // -5

// 子串操作
String sub = redisUtil.getRange("content", 0, 99); // 前 100 字符
Long len = redisUtil.size("content");               // STRLEN
```

#### Hash 操作 —— 对象缓存首选

```java
// 设置字段
redisUtil.hSet("user:1001", "name", "张三");
redisUtil.hSet("user:1001", "age", 28);

// 批量设置
Map<String, Object> fields = new HashMap<>();
fields.put("name", "张三");
fields.put("age", 28);
fields.put("email", "zhangsan@example.com");
redisUtil.hSetAll("user:1001", fields);

// 获取
String name = (String) redisUtil.hGet("user:1001", "name");
List<Object> list = redisUtil.hMultiGet("user:1001", List.of("name", "email"));
Map<String, Object> all = redisUtil.hGetAll("user:1001");

// 递增
redisUtil.hIncrBy("user:1001", "loginCount", 1);

// 判断字段是否存在
Boolean hasEmail = redisUtil.hHasKey("user:1001", "email");

// 删除字段
redisUtil.hDelete("user:1001", "tmpField1", "tmpField2");

// SCAN 遍历大 Hash
Cursor<Map.Entry<String, Object>> cursor = redisUtil.hScan("big:hash", "*", 100);
```

> **注意**：`hGetAll` 对特大 Hash 会阻塞，生产环境建议用 `hScan` 或限制单次返回数量。

#### List 操作 —— 消息队列、时间线

```java
// 左右插入
redisUtil.lLeftPush("timeline", event);        // LPUSH —— 最新消息在左边
redisUtil.lRightPush("task:queue", task);      // RPUSH —— 任务队列尾部追加

// 批量插入
redisUtil.lLeftPushAll("timeline", eventList);

// 弹出（阻塞版适合做消费者）
Object task = redisUtil.lRightPop("task:queue");                        // RPOP
Object task = redisUtil.lRightPop("task:queue", 30, TimeUnit.SECONDS);  // BRPOP 阻塞等待

// 可靠队列：从 processing 队列移到 done 队列
Object done = redisUtil.lRightPopAndLeftPush("task:processing", "task:done");

// 范围获取
List<Object> timeline = redisUtil.lRange("timeline", 0, 19);  // 最近 20 条

// 按索引获取/修改
Object first = redisUtil.lIndex("timeline", 0);   // 第一条
redisUtil.lSet("timeline", 0, newValue);           // 更新第一条

// 删除指定值
redisUtil.lRemove("timeline", 1, someEvent);       // 删除 1 个匹配项

// 裁剪（只保留最近 100 条）
redisUtil.lTrim("timeline", 0, 99);
```

#### Set 操作 —— 标签、去重、关系计算

```java
// 添加 / 删除
redisUtil.sAdd("tags:article:1", "Java", "Spring", "Redis");
redisUtil.sRemove("tags:article:1", "Redis");

// 随机获取（抽奖场景）
Object winner = redisUtil.sPop("lottery:pool");           // 弹出 1 个
List<Object> winners = redisUtil.sPop("lottery:pool", 3);  // 弹出 3 个

// 成员判断
Boolean isMember = redisUtil.sIsMember("blacklist", userId);

// 集合运算
Set<Object> common = redisUtil.sIntersect("user:1:friends", "user:2:friends"); // 共同好友
Set<Object> all = redisUtil.sUnion("group:1:members", "group:2:members");      // 全部成员
Set<Object> diff = redisUtil.sDifference("setA", "setB");                      // A 有 B 无

// 交集写入新集合
redisUtil.sIntersectAndStore("common:friends", "user:1:friends", "user:2:friends");
```

#### Sorted Set 操作 —— 排行榜、延迟队列

```java
// 添加成员（按分数排序）
redisUtil.zAdd("leaderboard", "player:1001", 9999.0);

// 获取分数
Double score = redisUtil.zScore("leaderboard", "player:1001");

// 加分
redisUtil.zIncrScore("leaderboard", "player:1001", 100.0);

// 排名（0 开始，从小到大）
Long rank = redisUtil.zRank("leaderboard", "player:1001");
Long revRank = redisUtil.zReverseRank("leaderboard", "player:1001"); // 从大到小

// TOP N（从大到小取前 10）
Set<Object> top10 = redisUtil.zReverseRange("leaderboard", 0, 9);
// TOP N 带分数
Set<ZSetOperations.TypedTuple<Object>> top10WithScores =
        redisUtil.zReverseRangeWithScores("leaderboard", 0, 9);

// 按分数范围取
Set<Object> range = redisUtil.zRangeByScore("leaderboard", 8000, 10000);

// 分页：每页 20，取第 3 页
Set<Object> page3 = redisUtil.zRangeByScore("leaderboard", 0, Double.MAX_VALUE, 40, 20);

// 统计分数范围内的人数
Long count = redisUtil.zCount("leaderboard", 9000, 10000);

// 弹出最高分 / 最低分
ZSetOperations.TypedTuple<Object> max = redisUtil.zPopMax("leaderboard");
```

#### Bitmap 操作 —— 签到、布隆过滤

```java
// 用户签到（offset = 一年中的第几天）
redisUtil.setBit("sign:user:1001:2026", dayOfYear, true);

// 查看某天是否签到
Boolean signed = redisUtil.getBit("sign:user:1001:2026", dayOfYear);

// 签到天数统计
Long totalSignDays = redisUtil.bitCount("sign:user:1001:2026");

// 多用户签到交集（全勤天数）
redisUtil.bitOp("AND", "sign:all:full", "sign:user:1:2026", "sign:user:2:2026");
```

#### Geo 操作 —— LBS 附近的人/店

```java
// 添加地理位置
redisUtil.geoAdd("shops", 116.404, 39.915, "shop:1"); // 北京
redisUtil.geoAdd("shops", 121.473, 31.230, "shop:2"); // 上海

// 计算两点距离
Distance dist = redisUtil.geoDist("shops", "shop:1", "shop:2", Metrics.KILOMETERS);

// 获取坐标
List<Point> points = redisUtil.geoPos("shops", "shop:1", "shop:2");

// 获取 Geohash
List<String> hashes = redisUtil.geoHash("shops", "shop:1");

// 搜索半径 5km 内的店铺
GeoResults<GeoLocation<Object>> nearby = redisUtil.geoRadius(
        "shops", 116.404, 39.915, 5.0, Metrics.KILOMETERS
);
```

#### HyperLogLog 操作 —— UV 统计

```java
// 记录访问用户
redisUtil.pfAdd("uv:page:home", userId1, userId2, userId3);

// UV 估算（标准误差约 0.81%）
Long uv = redisUtil.pfCount("uv:page:home");

// 合并多天 UV
redisUtil.pfMerge("uv:page:home:week", "uv:day:1", "uv:day:2", "uv:day:3");
```

#### Pipeline 批量操作 —— 高性能写入

当需要大批量写入时，用 Pipeline 将多次网络往返合并：

```java
List<Object> results = redisUtil.executePipelined(ops -> {
    for (int i = 0; i < 10000; i++) {
        ops.opsForValue().set("batch:" + i, "val" + i, Duration.ofMinutes(10));
    }
    return null;
});
```

#### 发布订阅

```java
redisUtil.publish("channel:order", orderEvent);
```

---

### RedisLockUtil —— Redisson 分布式锁封装

#### 可重入锁 —— 最常用（推荐模板方法）

```java
// 方式一：模板方法（自动加锁/解锁 + WatchDog 续期）—— 最推荐
String result = lockUtil.executeWithLock("lock:order:1001", () -> {
    // 执行业务，锁在此期间被 WatchDog 持续续期
    return doSomething();
});

// 方式二：tryLock 版，拿不到锁直接放弃
String result = lockUtil.executeWithTryLock("lock:order:1001", 5, TimeUnit.SECONDS, () -> {
    return doSomething();
});
if (result == null) {
    // 没拿到锁，走降级逻辑
}

// 方式三：手动控制（适合需要更精细控制的场景）
RLock lock = lockUtil.getLock("lock:order:1001");
try {
    // tryLock(等待时间, 持有时间, 时间单位)
    // 持有时间不传 → WatchDog 自动续期
    // 持有时间传了 → 到期自动释放，无 WatchDog
    if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
        // 业务逻辑
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    lockUtil.unlock(lock);
}
```

#### 公平锁 —— 按申请顺序排队

```java
RLock fairLock = lockUtil.getFairLock("lock:resource");
fairLock.lock();
try {
    // 业务
} finally {
    fairLock.unlock();
}
```

#### 读写锁 —— 读多写少场景

```java
RReadWriteLock rwLock = lockUtil.getReadWriteLock("cache:config");

// 读锁（共享——多个线程可同时持有）
rwLock.readLock().lock();
try {
    // 读取操作
} finally {
    rwLock.readLock().unlock();
}

// 写锁（排他——与读锁和其他写锁互斥）
rwLock.writeLock().lock();
try {
    // 写入操作
} finally {
    rwLock.writeLock().unlock();
}
```

#### 信号量 —— 限流

```java
RSemaphore semaphore = lockUtil.getSemaphore("limit:api:sendSms");
semaphore.trySetPermits(100);  // 初始化：最多 100 并发

// 获取许可
if (semaphore.tryAcquire(3, TimeUnit.SECONDS)) {
    try {
        sendSms(phone);
    } finally {
        semaphore.release();
    }
} else {
    throw new RuntimeException("系统繁忙，请稍后重试");
}
```

#### 闭锁 —— 分布式任务协调

```java
// 主节点：创建闭锁，等待 5 个子任务
RCountDownLatch latch = lockUtil.getCountDownLatch("task:import");
latch.trySetCount(5);
latch.await(10, TimeUnit.MINUTES);  // 阻塞等待

// 每个工作节点完成后：
lockUtil.getCountDownLatch("task:import").countDown();
```

#### 联锁 —— 高可靠性（多 Redis 节点同时加锁）

```java
RLock multiLock = lockUtil.getMultiLock("lock:redis1", "lock:redis2", "lock:redis3");
multiLock.lock();
try {
    // 当且仅当 3 个 Redis 节点全部加锁成功才算持有锁
    // 即使一个节点宕机，锁依然有效
} finally {
    multiLock.unlock();
}
```

---

## 常见场景示例

### 场景 1：缓存穿透防护（布隆过滤器思路 + 空值缓存）

```java
public User getUser(Long userId) {
    String key = "user:" + userId;
    User user = (User) redisUtil.get(key);
    if (user != null) {
        return "NULL_USER".equals(user.getUsername()) ? null : user;
    }
    user = loadFromDB(userId);
    if (user == null) {
        // 缓存空对象防穿透，短 TTL
        redisUtil.set(key, new User("NULL_USER"), Duration.ofMinutes(5));
    } else {
        redisUtil.set(key, user, Duration.ofMinutes(30));
    }
    return user;
}
```

### 场景 2：缓存击穿防护（分布式锁 + 互斥加载）

```java
public User getUserWithMutex(Long userId) {
    String cacheKey = "user:" + userId;
    String lockKey = "lock:user:" + userId;

    User user = (User) redisUtil.get(cacheKey);
    if (user != null) return user;

    // 只有一个线程去加载 DB，其余等待
    return lockUtil.executeWithTryLock(lockKey, 10, TimeUnit.SECONDS, () -> {
        // 双重检查
        User u = (User) redisUtil.get(cacheKey);
        if (u != null) return u;

        u = loadFromDB(userId);
        redisUtil.set(cacheKey, u, Duration.ofMinutes(30));
        return u;
    });
}
```

### 场景 3：接口限流（滑动窗口）

```java
public boolean isRateLimited(String userId, int maxRequests, int windowSeconds) {
    String key = "rate:" + userId;
    Long count = redisUtil.incr(key);
    if (count == 1) {
        redisUtil.expire(key, windowSeconds, TimeUnit.SECONDS);
    }
    return count > maxRequests;
}

// Controller 中
@GetMapping("/api/data")
public Result getData(@RequestParam String userId) {
    if (isRateLimited(userId, 10, 60)) {
        return Result.fail("请求过于频繁，请稍后再试");
    }
    return Result.ok(doGetData());
}
```

### 场景 4：排行榜实时更新

```java
// 用户得分时更新排行榜
public void updateScore(String playerId, double score) {
    redisUtil.zAdd("leaderboard:daily", playerId, score);
}

// 获取 Top 10 排行榜
public List<LeaderboardVO> getTop10() {
    Set<ZSetOperations.TypedTuple<Object>> top10 =
            redisUtil.zReverseRangeWithScores("leaderboard:daily", 0, 9);
    return top10.stream().map(t -> new LeaderboardVO(
            (String) t.getValue(), t.getScore()
    )).toList();
}

// 获取当前玩家排名
public Long getMyRank(String playerId) {
    Long rank = redisUtil.zReverseRank("leaderboard:daily", playerId);
    return rank == null ? null : rank + 1; // 排名从 1 开始
}
```

### 场景 5：分布式 Session 共享

```java
@Component
public class SessionService {

    @Autowired
    private RedisUtil redisUtil;

    public void saveSession(String sessionId, Map<String, Object> sessionData) {
        redisUtil.hSetAll("session:" + sessionId, sessionData);
        redisUtil.expire("session:" + sessionId, Duration.ofMinutes(30));
    }

    public Map<String, Object> getSession(String sessionId) {
        return redisUtil.hGetAll("session:" + sessionId);
    }

    // 续期（每次请求时调用）
    public void refreshSession(String sessionId) {
        redisUtil.expire("session:" + sessionId, Duration.ofMinutes(30));
    }

    public void removeSession(String sessionId) {
        redisUtil.delete("session:" + sessionId);
    }
}
```

### 场景 6：订单防重（幂等性）

```java
public Result createOrder(OrderDTO dto) {
    String idempotentKey = "order:idempotent:" + dto.getRequestId();

    // SETNX 原子保证只有一个请求能创建
    Boolean acquired = redisUtil.setIfAbsent(idempotentKey, "1", Duration.ofMinutes(5));
    if (!acquired) {
        return Result.fail("重复提交，请勿重复操作");
    }
    // 处理订单...
}
```

### 场景 7：消息队列（可靠消费 + 重试）

```java
// 生产者
public void produce(String task) {
    redisUtil.lLeftPush("task:queue", task);
}

// 消费者
@Component
public class TaskConsumer {

    @Autowired
    private RedisUtil redisUtil;

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        while (true) {
            // RPOPLPUSH：原子地将任务从 queue 移到 processing
            String task = (String) redisUtil.lRightPopAndLeftPush("task:queue", "task:processing");
            if (task == null) break;

            try {
                processTask(task);
                // 处理成功，从 processing 移除
                redisUtil.lRemove("task:processing", 1, task);
            } catch (Exception e) {
                // 失败的任务留在 processing 中，由监控脚本处理重试
                log.error("Task failed: {}", task, e);
            }
        }
    }
}
```

---

## 常见问题

### Q1：为什么 Key 在 Redis 里是 `\xAC\xED\x00\x05` 开头的乱码？

默认的 JdkSerializationRedisSerializer 会把 key 序列化成 Java 二进制。本模块的 `RedisTemplateConfig` 已配置 key 使用 String 序列化，不会再出现这个问题。

### Q2：get 返回的对象怎么转成我的类型？

因为 Value 用 Jackson 序列化为 JSON，存入时是 `{"name":"张三","age":28}`。读取时会自动反序列化——前提是**你存取的是同一个类的实例**，Jackson 通过 `@class` 属性记录类型信息。

```java
// 存入 User 对象
redisUtil.set("user:1", new User("张三", 28));

// 取出来强转即可（Jackson 自动根据 @class 反序列化为 User）
User u = (User) redisUtil.get("user:1");
```

如果要用 `StringRedisTemplate` 存取纯字符串，直接注入 `StringRedisTemplate` 即可。

### Q3：Redisson 锁的 WatchDog 机制是什么？

不传 `leaseTime` 时，Redisson 默认锁持有 30 秒，后台每 10 秒自动续期到 30 秒。只要业务线程不挂，锁就永远不会过期。如果**传入 leaseTime**，到期自动释放，WatchDog 不启动。

**推荐**：用 `executeWithLock(lockKey, supplier)` 模板方法——它会走 WatchDog，业务执行完自动释放，不会因为业务超时而提前释放锁。

### Q4：Redisson 和 Lettuce 的连接池会冲突吗？

不会。Redisson 自带 Netty 连接池，spring-boot-starter-data-redis 底层是 Lettuce，两套连接完全独立。Connections 数 = Lettuce 连接池数 + Redisson Netty 连接数，注意 Redis maxclients 上限。

### Q5：哨兵/集群模式下需要改代码吗？

不需要。`RedisTemplate`（Lettuce 驱动）会自动根据 `application.yml` 中的配置切换单机/哨兵/集群。`RedissonConfig` 中通过 `@ConditionalOnProperty` 自动选择合适的 Bean。业务工具类不需要任何改动。

### Q6：Pipeline 和事务有什么区别？

- **Pipeline**：批量发送命令，减少 RTT，但不保证原子性（本模块通过 `executePipelined` 支持）
- **事务（MULTI/EXEC）**：保证原子性，但性能略低

一般大批量写入用 Pipeline，需要原子性用 Lua 脚本或 Redisson 的事务。

### Q7：生产环境为什么不要用 KEYS 命令？

`KEYS pattern` 会阻塞 Redis 整个实例遍历所有 key，key 数量大时会导致毫秒级阻塞。用 `SCAN`（本模块 `scan()` 方法）代替。

### Q8：如何配置多数据源（多个 Redis 实例）？

参考 `RedisTemplateConfig` 创建第二套 Bean，使用 `@Qualifier` 区分。Redisson 同理。

---

## 文件清单

```
springboot-redis/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/quick/springbootredis/
        │   ├── SpringbootRedisApplication.java
        │   ├── config/
        │   │   ├── RedisTemplateConfig.java
        │   │   └── RedissonConfig.java
        │   └── util/
        │       ├── RedisUtil.java
        │       └── RedisLockUtil.java
        └── resources/
            └── application.yml
```

---

## 版本兼容

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Spring Data Redis | 3.5.x（随 Boot 管理） |
| Lettuce | 6.5.x（随 Boot 管理） |
| Redisson | 3.46.0 |
| Java | 17+ |
