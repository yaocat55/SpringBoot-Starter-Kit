package com.quick.springbootredis.util;

import org.redisson.api.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁工具类 —— 支持可重入锁、公平锁、读写锁、信号量、闭锁。
 * <p>
 * Redisson 的 RLock 实现了 {@link java.util.concurrent.locks.Lock} 接口，
 * 用法与 JDK 原生的 ReentrantLock 基本一致。核心优势：
 * <ul>
 *   <li><b>WatchDog 自动续期</b>：lock 不传 leaseTime 时会每 10 秒自动续期 30 秒，防止业务执行超时锁被释放</li>
 *   <li><b>可重入</b>：同一线程可多次获取同一把锁</li>
 *   <li><b>Redis 主从切换不丢锁</b>（RedLock / MultiLock）</li>
 *   <li><b>异步 / 响应式 API</b></li>
 * </ul>
 *
 * <h3>快速使用</h3>
 * <pre>{@code
 * @Autowired
 * private RedisLockUtil lockUtil;
 *
 * // 带自动续期的锁（推荐）
 * lockUtil.executeWithLock("order:1001", () -> {
 *     // 业务逻辑
 *     return result;
 * });
 *
 * // 手动控制加锁/解锁
 * RLock lock = lockUtil.getLock("order:1001");
 * if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
 *     try { ... } finally { lock.unlock(); }
 * }
 * }</pre>
 */
@Component
public class RedisLockUtil {

    private final RedissonClient redissonClient;

    public RedisLockUtil(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // ==================== 可重入锁 (Reentrant Lock) —— 最常用 ====================

    /** 获取可重入锁实例 */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 加锁（阻塞等待，WatchDog 自动续期）。
     * 使用后必须调用 {@link #unlock(RLock)} 释放。
     */
    public void lock(String lockKey) {
        redissonClient.getLock(lockKey).lock();
    }

    /**
     * 加锁（阻塞等待，固定持有时间，无 WatchDog）。
     *
     * @param leaseTime 锁自动释放时间（到期自动释放，不续期）
     */
    public void lock(String lockKey, long leaseTime, TimeUnit unit) {
        redissonClient.getLock(lockKey).lock(leaseTime, unit);
    }

    /**
     * 尝试加锁（非阻塞），WatchDog 自动续期。
     *
     * @param waitTime 最大等待时间
     * @return true 加锁成功
     */
    public boolean tryLock(String lockKey, long waitTime, TimeUnit unit) throws InterruptedException {
        return redissonClient.getLock(lockKey).tryLock(waitTime, unit);
    }

    /**
     * 尝试加锁（非阻塞），无 WatchDog。
     *
     * @param waitTime  最大等待时间
     * @param leaseTime 锁持有时间（到期自动释放）
     * @return true 加锁成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return redissonClient.getLock(lockKey).tryLock(waitTime, leaseTime, unit);
    }

    /** 解锁 */
    public void unlock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /** 按 key 解锁 */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 在锁保护下执行业务逻辑（模板方法，自动加锁/解锁 + WatchDog）。
     * <pre>{@code
     * String result = lockUtil.executeWithLock("order:1001", () -> {
     *     return doSomething();
     * });
     * }</pre>
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在锁保护下执行（无返回值） */
    public void executeWithLock(String lockKey, Runnable runnable) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            runnable.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 在锁保护下执行业务逻辑（tryLock 版本，拿不到锁直接返回 null）。
     */
    public <T> T executeWithTryLock(String lockKey, long waitTime, TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, unit);
            if (acquired) {
                return supplier.get();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 公平锁 (Fair Lock) ====================

    /** 获取公平锁实例（按申请顺序排队获取） */
    public RLock getFairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    // ==================== 读写锁 (ReadWrite Lock) ====================

    /**
     * 获取读锁（共享锁——多个读锁可同时持有，与写锁互斥）。
     * <pre>{@code
     * RReadWriteLock rwLock = lockUtil.getReadWriteLock("resource:key");
     * rwLock.readLock().lock();
     * try { ... } finally { rwLock.readLock().unlock(); }
     * }</pre>
     */
    public RReadWriteLock getReadWriteLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey);
    }

    // ==================== 信号量 (Semaphore) ====================

    /**
     * 获取信号量（控制同时访问资源的线程数）。
     * <pre>{@code
     * RSemaphore semaphore = lockUtil.getSemaphore("limit:key");
     * semaphore.trySetPermits(10);  // 初始化，最多 10 个并发
     * semaphore.acquire();          // 获取许可
     * try { ... } finally { semaphore.release(); }
     * }</pre>
     */
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    // ==================== 闭锁 (CountDownLatch) ====================

    /**
     * 获取分布式闭锁（等待一组操作全部完成）。
     * <pre>{@code
     * RCountDownLatch latch = lockUtil.getCountDownLatch("task:latch");
     * latch.trySetCount(5);  // 等待 5 个任务
     * // 在其他节点完成任务后: latch.countDown();
     * latch.await();          // 阻塞等待全部完成
     * }</pre>
     */
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    // ==================== 联锁 (MultiLock) ====================

    /**
     * 获取联锁（同时锁定多把锁，全部锁定才算成功——降低主从切换丢锁概率）。
     * <pre>{@code
     * RLock multiLock = lockUtil.getMultiLock("lock:node1", "lock:node2", "lock:node3");
     * multiLock.lock();
     * try { ... } finally { multiLock.unlock(); }
     * }</pre>
     */
    public RLock getMultiLock(String... lockKeys) {
        RLock[] locks = new RLock[lockKeys.length];
        for (int i = 0; i < lockKeys.length; i++) {
            locks[i] = redissonClient.getLock(lockKeys[i]);
        }
        return redissonClient.getMultiLock(locks);
    }
}
