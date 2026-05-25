package com.quick.springbootredis.util;

import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis 通用工具类 —— 基于 RedisTemplate，涵盖全部常用数据类型操作。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>入参 key 统一使用 String，value 统一使用 Object（由 RedisTemplateConfig 序列化为 JSON）</li>
 *   <li>时间参数同时提供 {@code long + TimeUnit} 和 {@link Duration} 两个重载</li>
 *   <li>不吞异常，由调用方决定如何处理（除非方法名带 Safe 后缀）</li>
 *   <li>每个方法都有 Javadoc 说明对应 Redis 命令及其行为</li>
 * </ul>
 *
 * <h3>快速使用</h3>
 * <pre>{@code
 * @Autowired
 * private RedisUtil redisUtil;
 *
 * redisUtil.set("user:1001", user, Duration.ofMinutes(30));
 * User u = (User) redisUtil.get("user:1001");
 * }</pre>
 */
@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;
    private final HashOperations<String, String, Object> hashOps;
    private final ListOperations<String, Object> listOps;
    private final SetOperations<String, Object> setOps;
    private final ZSetOperations<String, Object> zSetOps;
    private final HyperLogLogOperations<String, Object> hllOps;
    private final GeoOperations<String, Object> geoOps;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate,
                     ValueOperations<String, Object> valueOps,
                     HashOperations<String, String, Object> hashOps,
                     ListOperations<String, Object> listOps,
                     SetOperations<String, Object> setOps,
                     ZSetOperations<String, Object> zSetOps,
                     HyperLogLogOperations<String, Object> hllOps,
                     GeoOperations<String, Object> geoOps) {
        this.redisTemplate = redisTemplate;
        this.valueOps = valueOps;
        this.hashOps = hashOps;
        this.listOps = listOps;
        this.setOps = setOps;
        this.zSetOps = zSetOps;
        this.hllOps = hllOps;
        this.geoOps = geoOps;
    }

    // ==================== Key 操作 ====================

    /** 判断 key 是否存在 — EXISTS key */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** 删除一个或多个 key — DEL key [key ...] */
    public Long delete(String... keys) {
        return keys.length == 1
                ? redisTemplate.delete(keys[0]) ? 1L : 0L
                : redisTemplate.delete(Arrays.asList(keys));
    }

    /** 删除匹配模式的所有 key（慎用，生产环境 key 量大时建议用 SCAN）— DEL pattern */
    public Long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    /** 设置过期时间 — EXPIRE key seconds */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /** 设置过期时间 — EXPIRE key seconds */
    public Boolean expire(String key, Duration duration) {
        return redisTemplate.expire(key, duration);
    }

    /** 设置在指定时间过期 — EXPIREAT key timestamp */
    public Boolean expireAt(String key, Date date) {
        return redisTemplate.expireAt(key, date);
    }

    /** 获取过期时间（秒）— TTL key */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    /** 获取过期时间 — TTL key */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /** 移除过期时间，Key 变为持久化 — PERSIST key */
    public Boolean persist(String key) {
        return redisTemplate.persist(key);
    }

    /** 返回 key 的类型 — TYPE key */
    public DataType type(String key) {
        return redisTemplate.type(key);
    }

    /** 模糊查询 key（慎用在生产环境，key 数量大时建议用 scan）— KEYS pattern */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /** SCAN 遍历 key（生产环境推荐替代 keys） */
    public Cursor<String> scan(String pattern, long count) {
        return redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(count).build()
        );
    }

    /** 重命名 key — RENAME oldKey newKey */
    public void rename(String oldKey, String newKey) {
        redisTemplate.rename(oldKey, newKey);
    }

    /** 当 newKey 不存在时才重命名 — RENAMENX oldKey newKey */
    public Boolean renameIfAbsent(String oldKey, String newKey) {
        return redisTemplate.renameIfAbsent(oldKey, newKey);
    }

    /** 将 key 移动到另一个数据库 — MOVE key dbIndex */
    public Boolean move(String key, int dbIndex) {
        return redisTemplate.move(key, dbIndex);
    }

    // ==================== String 操作 ====================

    /** SET key value */
    public void set(String key, Object value) {
        valueOps.set(key, value);
    }

    /** SET key value EX timeout — 原子性设值+过期 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        valueOps.set(key, value, timeout, unit);
    }

    /** SET key value EX timeout */
    public void set(String key, Object value, Duration duration) {
        valueOps.set(key, value, duration);
    }

    /** SETNX key value — 不存在才设值，返回 true 表示设置成功 */
    public Boolean setIfAbsent(String key, Object value) {
        return valueOps.setIfAbsent(key, value);
    }

    /** SETNX key value EX timeout — 不存在才设值并带过期时间 */
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return valueOps.setIfAbsent(key, value, timeout, unit);
    }

    /** SETNX key value EX timeout */
    public Boolean setIfAbsent(String key, Object value, Duration duration) {
        return valueOps.setIfAbsent(key, value, duration);
    }

    /** SET key value XX — 仅当 key 存在时才设置 */
    public Boolean setIfPresent(String key, Object value) {
        return valueOps.setIfPresent(key, value);
    }

    /** SET key value XX EX timeout */
    public Boolean setIfPresent(String key, Object value, long timeout, TimeUnit unit) {
        return valueOps.setIfPresent(key, value, timeout, unit);
    }

    /** GET key — 返回 null 表示 key 不存在 */
    public Object get(String key) {
        return valueOps.get(key);
    }

    /** MGET key [key ...] — 批量获取，不存在的 key 对应位置返回 null */
    public List<Object> multiGet(Collection<String> keys) {
        return valueOps.multiGet(keys);
    }

    /** MSET key value [key value ...] — 批量设置 */
    public void multiSet(Map<String, Object> map) {
        valueOps.multiSet(map);
    }

    /** MSETNX key value [key value ...] — 仅当所有 key 都不存在时才批量设置 */
    public Boolean multiSetIfAbsent(Map<String, Object> map) {
        return valueOps.multiSetIfAbsent(map);
    }

    /** GETSET key value — 返回旧值并设新值（Redis 6.2 前常用，新版推荐 SET key value GET） */
    public Object getAndSet(String key, Object value) {
        return valueOps.getAndSet(key, value);
    }

    /** GETDEL key — 获取值并删除 */
    public Object getAndDelete(String key) {
        return valueOps.getAndDelete(key);
    }

    /** GETEX key EX timeout — 获取值并设置/刷新过期时间 */
    public Object getAndExpire(String key, long timeout, TimeUnit unit) {
        return valueOps.getAndExpire(key, timeout, unit);
    }

    /** GETEX key EX timeout */
    public Object getAndExpire(String key, Duration duration) {
        return valueOps.getAndExpire(key, duration);
    }

    /** APPEND key value — 末尾追加，返回追加后的字符串长度 */
    public Integer append(String key, String value) {
        return valueOps.append(key, value);
    }

    /** GETRANGE key start end — 获取子串（0 -1 表示全部） */
    public String getRange(String key, long start, long end) {
        return valueOps.get(key, start, end);
    }

    /** SETRANGE key offset value — 从 offset 开始覆写 */
    public void setRange(String key, Object value, long offset) {
        valueOps.set(key, value, offset);
    }

    /** STRLEN key — 获取值的长度 */
    public Long size(String key) {
        return valueOps.size(key);
    }

    /** INCR key — 自增 1，返回递增后的值 */
    public Long incr(String key) {
        return valueOps.increment(key);
    }

    /** INCRBY key delta — 自增 delta */
    public Long incrBy(String key, long delta) {
        return valueOps.increment(key, delta);
    }

    /** INCRBYFLOAT key delta — 浮点自增 */
    public Double incrByFloat(String key, double delta) {
        return valueOps.increment(key, delta);
    }

    /** DECR key — 自减 1 */
    public Long decr(String key) {
        return valueOps.decrement(key);
    }

    /** DECRBY key delta — 自减 delta */
    public Long decrBy(String key, long delta) {
        return valueOps.decrement(key, delta);
    }

    // ==================== Hash 操作 ====================

    /** HSET key field value — 设置单个字段 */
    public void hSet(String key, String field, Object value) {
        hashOps.put(key, field, value);
    }

    /** HSET key field value [field value ...] — 批量设置字段 */
    public void hSetAll(String key, Map<String, Object> map) {
        hashOps.putAll(key, map);
    }

    /** HSETNX key field value — 仅当 field 不存在时才设值 */
    public Boolean hSetIfAbsent(String key, String field, Object value) {
        return hashOps.putIfAbsent(key, field, value);
    }

    /** HGET key field — 获取单个字段值 */
    public Object hGet(String key, String field) {
        return hashOps.get(key, field);
    }

    /** HMGET key field [field ...] */
    public List<Object> hMultiGet(String key, Collection<String> fields) {
        return hashOps.multiGet(key, fields);
    }

    /** HGETALL key — 返回整个 Hash */
    public Map<String, Object> hGetAll(String key) {
        return hashOps.entries(key);
    }

    /** HKEYS key — 返回所有字段名 */
    public Set<String> hKeys(String key) {
        return hashOps.keys(key);
    }

    /** HVALS key — 返回所有字段值 */
    public List<Object> hValues(String key) {
        return hashOps.values(key);
    }

    /** HDEL key field [field ...] — 删除一个或多个字段 */
    public Long hDelete(String key, String... fields) {
        return hashOps.delete(key, (Object[]) fields);
    }

    /** HEXISTS key field — 判断字段是否存在 */
    public Boolean hHasKey(String key, String field) {
        return hashOps.hasKey(key, field);
    }

    /** HLEN key — 返回字段数量 */
    public Long hSize(String key) {
        return hashOps.size(key);
    }

    /** HINCRBY key field delta — 字段值自增 */
    public Long hIncrBy(String key, String field, long delta) {
        return hashOps.increment(key, field, delta);
    }

    /** HINCRBYFLOAT key field delta — 字段值浮点自增 */
    public Double hIncrByFloat(String key, String field, double delta) {
        return hashOps.increment(key, field, delta);
    }

    /** HSCAN key cursor — 渐进遍历大 Hash */
    public Cursor<Map.Entry<String, Object>> hScan(String key, String pattern, long count) {
        return hashOps.scan(key,
                ScanOptions.scanOptions().match(pattern).count(count).build()
        );
    }

    // ==================== List 操作 ====================

    /** LPUSH key value [value ...] — 左端插入 */
    public Long lLeftPush(String key, Object value) {
        return listOps.leftPush(key, value);
    }

    /** LPUSH key value [value ...] */
    public Long lLeftPushAll(String key, Collection<Object> values) {
        return listOps.leftPushAll(key, values);
    }

    /** RPUSH key value [value ...] — 右端插入 */
    public Long lRightPush(String key, Object value) {
        return listOps.rightPush(key, value);
    }

    /** RPUSH key value [value ...] */
    public Long lRightPushAll(String key, Collection<Object> values) {
        return listOps.rightPushAll(key, values);
    }

    /** LPOP key — 左端弹出，列表为空返回 null */
    public Object lLeftPop(String key) {
        return listOps.leftPop(key);
    }

    /** BLPOP key timeout — 阻塞左弹出 */
    public Object lLeftPop(String key, long timeout, TimeUnit unit) {
        return listOps.leftPop(key, timeout, unit);
    }

    /** RPOP key — 右端弹出 */
    public Object lRightPop(String key) {
        return listOps.rightPop(key);
    }

    /** BRPOP key timeout — 阻塞右弹出 */
    public Object lRightPop(String key, long timeout, TimeUnit unit) {
        return listOps.rightPop(key, timeout, unit);
    }

    /** RPOPLPUSH source dest — 从 source 右弹出，压入 dest 左侧 */
    public Object lRightPopAndLeftPush(String sourceKey, String destKey) {
        return listOps.rightPopAndLeftPush(sourceKey, destKey);
    }

    /** LLEN key — 列表长度 */
    public Long lSize(String key) {
        return listOps.size(key);
    }

    /** LINDEX key index — 按下标获取元素（0 首个，-1 最后一个） */
    public Object lIndex(String key, long index) {
        return listOps.index(key, index);
    }

    /** LSET key index value — 设置指定下标的值 */
    public void lSet(String key, long index, Object value) {
        listOps.set(key, index, value);
    }

    /** LRANGE key start end — 获取子列表（0 -1 全部） */
    public List<Object> lRange(String key, long start, long end) {
        return listOps.range(key, start, end);
    }

    /** LREM key count value — 删除 count 个值为 value 的元素 */
    public Long lRemove(String key, long count, Object value) {
        return listOps.remove(key, count, value);
    }

    /** LTRIM key start end — 仅保留 [start, end] 范围内的元素 */
    public void lTrim(String key, long start, long end) {
        listOps.trim(key, start, end);
    }

    /** LINSERT key BEFORE pivot value — 在 pivot 之前插入 */
    public Long lInsertBefore(String key, Object pivot, Object value) {
        return listOps.leftPush(key, pivot, value);
    }

    // ==================== Set 操作 ====================

    /** SADD key member [member ...] — 添加元素 */
    public Long sAdd(String key, Object... members) {
        return setOps.add(key, members);
    }

    /** SREM key member [member ...] — 删除元素 */
    public Long sRemove(String key, Object... members) {
        return setOps.remove(key, (Object[]) members);
    }

    /** SPOP key — 随机弹出一个元素 */
    public Object sPop(String key) {
        return setOps.pop(key);
    }

    /** SPOP key count — 随机弹出 count 个元素 */
    public List<Object> sPop(String key, long count) {
        return setOps.pop(key, count);
    }

    /** SRANDMEMBER key — 随机获取一个元素（不移除） */
    public Object sRandomMember(String key) {
        return setOps.randomMember(key);
    }

    /** SRANDMEMBER key count — 随机获取 count 个元素 */
    public List<Object> sRandomMembers(String key, long count) {
        return setOps.randomMembers(key, count);
    }

    /** SISMEMBER key member — 判断是否是集合成员 */
    public Boolean sIsMember(String key, Object member) {
        return setOps.isMember(key, member);
    }

    /** SMISMEMBER key member [member ...] — 批量判断 */
    public Map<Object, Boolean> sIsMembers(String key, Object... members) {
        return setOps.isMember(key, (Object[]) members);
    }

    /** SMEMBERS key — 返回集合所有成员 */
    public Set<Object> sMembers(String key) {
        return setOps.members(key);
    }

    /** SCARD key — 返回集合大小 */
    public Long sSize(String key) {
        return setOps.size(key);
    }

    /** SMOVE source dest member — 将 member 从 source 移动到 dest */
    public Boolean sMove(String sourceKey, String destKey, Object member) {
        return setOps.move(sourceKey, member, destKey);
    }

    /** SINTER key [key ...] — 交集 */
    public Set<Object> sIntersect(String key, String otherKey) {
        return setOps.intersect(key, otherKey);
    }

    /** SINTER key [key ...] — 多集合交集 */
    public Set<Object> sIntersect(Collection<String> keys) {
        return setOps.intersect(keys);
    }

    /** SINTERSTORE dest key [key ...] — 交集存入 dest */
    public Long sIntersectAndStore(String destKey, String... keys) {
        return setOps.intersectAndStore(destKey, Arrays.asList(keys), destKey);
    }

    /** SUNION key [key ...] — 并集 */
    public Set<Object> sUnion(String key, String otherKey) {
        return setOps.union(key, otherKey);
    }

    /** SUNION key [key ...] — 多集合并集 */
    public Set<Object> sUnion(Collection<String> keys) {
        return setOps.union(keys);
    }

    /** SUNIONSTORE dest key [key ...] — 并集存入 dest */
    public Long sUnionAndStore(String destKey, String... keys) {
        return setOps.unionAndStore(destKey, Arrays.asList(keys), destKey);
    }

    /** SDIFF key [key ...] — 差集 */
    public Set<Object> sDifference(String key, String otherKey) {
        return setOps.difference(key, otherKey);
    }

    /** SDIFF key [key ...] — 多集合差集 */
    public Set<Object> sDifference(Collection<String> keys) {
        return setOps.difference(keys);
    }

    /** SDIFFSTORE dest key [key ...] — 差集存入 dest */
    public Long sDifferenceAndStore(String destKey, String... keys) {
        return setOps.differenceAndStore(destKey, Arrays.asList(keys), destKey);
    }

    /** SSCAN key cursor — 渐进遍历大 Set */
    public Cursor<Object> sScan(String key, String pattern, long count) {
        return setOps.scan(key,
                ScanOptions.scanOptions().match(pattern).count(count).build()
        );
    }

    // ==================== Sorted Set 操作 ====================

    /** ZADD key score member — 添加元素 */
    public Boolean zAdd(String key, Object member, double score) {
        return zSetOps.add(key, member, score);
    }

    /** ZADD key score member [score member ...] — 批量添加 */
    public Long zAdd(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        return zSetOps.add(key, tuples);
    }

    /** ZREM key member [member ...] — 删除元素 */
    public Long zRemove(String key, Object... members) {
        return zSetOps.remove(key, (Object[]) members);
    }

    /** ZINCRBY key delta member — 增加成员分数 */
    public Double zIncrScore(String key, Object member, double delta) {
        return zSetOps.incrementScore(key, member, delta);
    }

    /** ZSCORE key member — 获取成员分数 */
    public Double zScore(String key, Object member) {
        return zSetOps.score(key, member);
    }

    /** ZRANK key member — 获取排名（从小到大，0 开始） */
    public Long zRank(String key, Object member) {
        return zSetOps.rank(key, member);
    }

    /** ZREVRANK key member — 获取逆序排名（从大到小，0 开始） */
    public Long zReverseRank(String key, Object member) {
        return zSetOps.reverseRank(key, member);
    }

    /** ZCARD key — 返回集合大小 */
    public Long zSize(String key) {
        return zSetOps.size(key);
    }

    /** ZCOUNT key min max — 统计分数在 [min, max] 之间的成员数 */
    public Long zCount(String key, double min, double max) {
        return zSetOps.count(key, min, max);
    }

    /** ZRANGE key start end — 按排名正序获取 [start, end] 范围（含分数） */
    public Set<ZSetOperations.TypedTuple<Object>> zRangeWithScores(String key, long start, long end) {
        return zSetOps.rangeWithScores(key, start, end);
    }

    /** ZRANGE key start end — 按排名正序获取，不带分数 */
    public Set<Object> zRange(String key, long start, long end) {
        return zSetOps.range(key, start, end);
    }

    /** ZREVRANGE key start end — 按排名逆序获取 */
    public Set<Object> zReverseRange(String key, long start, long end) {
        return zSetOps.reverseRange(key, start, end);
    }

    /** ZREVRANGE key start end WITHSCORES */
    public Set<ZSetOperations.TypedTuple<Object>> zReverseRangeWithScores(String key, long start, long end) {
        return zSetOps.reverseRangeWithScores(key, start, end);
    }

    /** ZRANGEBYSCORE key min max WITHSCORES LIMIT offset count */
    public Set<Object> zRangeByScore(String key, double min, double max) {
        return zSetOps.rangeByScore(key, min, max);
    }

    /** ZRANGEBYSCORE key min max WITHSCORES LIMIT offset count */
    public Set<ZSetOperations.TypedTuple<Object>> zRangeByScoreWithScores(String key, double min, double max) {
        return zSetOps.rangeByScoreWithScores(key, min, max);
    }

    /** ZRANGEBYSCORE key min max WITHSCORES LIMIT offset count */
    public Set<Object> zRangeByScore(String key, double min, double max, long offset, long count) {
        return zSetOps.rangeByScore(key, min, max, offset, count);
    }

    /** ZREVRANGEBYSCORE key max min */
    public Set<Object> zReverseRangeByScore(String key, double min, double max) {
        return zSetOps.reverseRangeByScore(key, min, max);
    }

    /** ZREVRANGEBYSCORE key max min WITHSCORES */
    public Set<ZSetOperations.TypedTuple<Object>> zReverseRangeByScoreWithScores(String key, double min, double max) {
        return zSetOps.reverseRangeByScoreWithScores(key, min, max);
    }

    /** ZREMRANGEBYRANK key start end — 按排名删除 */
    public Long zRemoveRangeByRank(String key, long start, long end) {
        return zSetOps.removeRange(key, start, end);
    }

    /** ZREMRANGEBYSCORE key min max — 按分数删除 */
    public Long zRemoveRangeByScore(String key, double min, double max) {
        return zSetOps.removeRangeByScore(key, min, max);
    }

    /** ZUNIONSTORE dest numkeys key [key ...] — 并集存入 dest */
    public Long zUnionAndStore(String destKey, String... keys) {
        return zSetOps.unionAndStore(destKey, Arrays.asList(keys), destKey);
    }

    /** ZINTERSTORE dest numkeys key [key ...] — 交集存入 dest */
    public Long zIntersectAndStore(String destKey, String... keys) {
        return zSetOps.intersectAndStore(destKey, Arrays.asList(keys), destKey);
    }

    /** ZPOPMAX key [count] — 弹出分数最高的成员 */
    public ZSetOperations.TypedTuple<Object> zPopMax(String key) {
        return zSetOps.popMax(key);
    }

    /** ZPOPMIN key [count] — 弹出分数最低的成员 */
    public ZSetOperations.TypedTuple<Object> zPopMin(String key) {
        return zSetOps.popMin(key);
    }

    // ==================== Bitmap 操作 ====================

    /** SETBIT key offset value — 设置指定位的值（0 / 1） */
    public Boolean setBit(String key, long offset, boolean value) {
        return valueOps.setBit(key, offset, value);
    }

    /** GETBIT key offset — 获取指定位的值 */
    public Boolean getBit(String key, long offset) {
        return valueOps.getBit(key, offset);
    }

    /** BITCOUNT key [start end] — 统计值为 1 的 bit 数 */
    public Long bitCount(String key) {
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.bitCount(key.getBytes())
        );
    }

    /** BITCOUNT key start end — 统计字节范围内的 bit 数 */
    public Long bitCount(String key, long start, long end) {
        return (long) redisTemplate.execute(
                (RedisCallback<Long>) connection ->
                        connection.bitCount(key.getBytes(), start, end)
        );
    }

    /** BITOP operation destKey key [key ...] — 位运算（AND / OR / XOR / NOT） */
    public Long bitOp(String operation, String destKey, String... keys) {
        RedisStringCommands.BitOperation op;
        switch (operation.toUpperCase()) {
            case "AND": op = RedisStringCommands.BitOperation.AND; break;
            case "OR":  op = RedisStringCommands.BitOperation.OR;  break;
            case "XOR": op = RedisStringCommands.BitOperation.XOR; break;
            case "NOT": op = RedisStringCommands.BitOperation.NOT; break;
            default: throw new IllegalArgumentException("Unsupported bit operation: " + operation);
        }
        byte[][] keyBytes = new byte[keys.length][];
        for (int i = 0; i < keys.length; i++) {
            keyBytes[i] = keys[i].getBytes();
        }
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) connection ->
                        connection.bitOp(op, destKey.getBytes(), keyBytes)
        );
    }

    /** BITPOS key bit [start] [end] — 返回第一个值为 bit 的位置 */
    public Long bitPos(String key, boolean bit) {
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) connection ->
                        connection.bitPos(key.getBytes(), bit,
                                org.springframework.data.domain.Range.unbounded())
        );
    }

    /** BITFIELD — 位域操作 */
    public List<Long> bitField(String key, BitFieldSubCommands commands) {
        return valueOps.bitField(key, commands);
    }

    // ==================== HyperLogLog 操作 ====================

    /** PFADD key element [element ...] — 添加元素 */
    public Long pfAdd(String key, Object... elements) {
        return hllOps.add(key, elements);
    }

    /** PFCOUNT key [key ...] — 估算基数 */
    public Long pfCount(String... keys) {
        return hllOps.size(keys);
    }

    /** PFMERGE destKey key [key ...] — 合并多个 HLL */
    public Long pfMerge(String destKey, String... sourceKeys) {
        return hllOps.union(destKey, sourceKeys);
    }

    // ==================== Geo 操作 ====================

    /** GEOADD key longitude latitude member — 添加地理坐标 */
    public Long geoAdd(String key, double longitude, double latitude, Object member) {
        return geoOps.add(key, new GeoLocation<>(member, new Point(longitude, latitude)));
    }

    /** GEOADD key longitude latitude member [...] — 批量添加 */
    public Long geoAdd(String key, Map<Object, Point> memberPoints) {
        List<GeoLocation<Object>> locations = new ArrayList<>();
        for (Map.Entry<Object, Point> entry : memberPoints.entrySet()) {
            locations.add(new GeoLocation<>(entry.getKey(), entry.getValue()));
        }
        return geoOps.add(key, locations);
    }

    /** GEODIST key member1 member2 [unit] — 计算两点距离 */
    public Distance geoDist(String key, Object member1, Object member2, Metrics metric) {
        return geoOps.distance(key, member1, member2, metric);
    }

    /** GEOHASH key member [member ...] — 获取 Geohash */
    public List<String> geoHash(String key, Object... members) {
        return geoOps.hash(key, members);
    }

    /** GEOPOS key member [member ...] — 获取坐标 */
    public List<Point> geoPos(String key, Object... members) {
        return geoOps.position(key, members);
    }

    /** GEOSEARCH key FROMMEMBER member BYRADIUS radius unit — 以成员为中心搜索 */
    public GeoResults<GeoLocation<Object>> geoRadiusByMember(
            String key, Object member, double radius, Metrics metric) {
        return geoOps.radius(key,
                new Circle(new Point(0, 0), new Distance(radius, metric)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
        );
    }

    /** GEOSEARCH key FROMLONLAT longitude latitude BYRADIUS radius unit — 以坐标为中心搜索 */
    public GeoResults<GeoLocation<Object>> geoRadius(
            String key, double longitude, double latitude, double radius, Metrics metric) {
        return geoOps.radius(key,
                new Circle(new Point(longitude, latitude), new Distance(radius, metric)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
        );
    }

    /** GEOSEARCH key FROMLONLAT longitude latitude BYRADIUS radius unit — 带参数 */
    public GeoResults<GeoLocation<Object>> geoRadius(
            String key, double longitude, double latitude, double radius, Metrics metric,
            RedisGeoCommands.GeoRadiusCommandArgs args) {
        return geoOps.radius(key,
                new Circle(new Point(longitude, latitude), new Distance(radius, metric)), args);
    }

    /** GEO REMOVE key member — 删除地理坐标（底层是 ZSet，用 ZREM 实现） */
    public Long geoRemove(String key, Object... members) {
        return zSetOps.remove(key, (Object[]) members);
    }

    // ==================== Pipeline 批量操作 ====================

    /**
     * 执行 Pipeline 批量命令，减少网络往返次数，适合大批量写入。
     * <pre>{@code
     * List<Object> results = redisUtil.executePipelined(ops -> {
     *     for (int i = 0; i < 10000; i++) {
     *         ops.opsForValue().set("batch:" + i, "val" + i, Duration.ofMinutes(10));
     *     }
     *     return null;
     * });
     * }</pre>
     */
    public List<Object> executePipelined(SessionCallback<List<Object>> sessionCallback) {
        return redisTemplate.executePipelined(sessionCallback);
    }

    // ==================== 分布式锁（RedisTemplate 原生版——轻量场景） ====================

    /**
     * 使用 SETNX 实现轻量分布式锁。
     * <p>注意：此方式不包含 WatchDog 续期，仅适合短时锁场景。生产环境推荐 {@link RedisLockUtil}（Redisson）。
     *
     * @param lockKey 锁的 key
     * @param requestId 持有者标识（一般用 UUID），解锁时用作身份验证
     * @param timeout 锁的过期时间
     * @return true 加锁成功
     */
    public boolean tryLock(String lockKey, String requestId, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(valueOps.setIfAbsent(lockKey, requestId, timeout, unit));
    }

    /**
     * 释放 SETNX 锁（Lua 脚本原子校验 + 删除，防止误删他人锁）。
     */
    public boolean unlock(String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(lockKey), requestId);
        return result != null && result == 1L;
    }

    // ==================== 发布订阅 ====================

    /** PUBLISH channel message — 发布消息 */
    public void publish(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }
}
