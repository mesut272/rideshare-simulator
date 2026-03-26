package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SimulatedRedissonLock — 模拟 Redisson 分布式锁的核心机制
 *
 * 真实 Redisson 在 Redis 中通过一段 Lua 脚本原子性地完成：
 *   1. 判断锁是否存在（EXIST key）
 *   2. 若不存在，则 SET key threadId EX ttl
 * 这两步在 Redis 里是原子的，因为 Redis 单线程执行 Lua 脚本。
 *
 * 本类用 Java synchronized + volatile 模拟同等语义：
 *   - tryLock()     → 对应 Lua SET NX EX
 *   - unlock()      → 对应 Lua 先校验持有者再 DEL
 *   - WatchDog 线程 → 对应 Redisson 的后台续期任务（每 ttl/3 续一次）
 *
 * 为什么需要 WatchDog？
 *   业务执行时间不确定，若锁 TTL 到期但业务未完成，锁会被其他节点抢走，
 *   导致并发写入——WatchDog 定期续期，避免这个问题。
 */
public class SimulatedRedissonLock {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedRedissonLock.class);

    // 锁的超时时间（毫秒）。真实 Redisson 默认 30s。
    private static final long DEFAULT_TTL_MS = 5_000L;
    // WatchDog 续期间隔 = TTL / 3，保证在 TTL 到期前一定能续上
    private static final long WATCHDOG_INTERVAL_MS = DEFAULT_TTL_MS / 3;

    private final String lockKey;          // 锁名，等价于 Redis key
    private volatile String holderId;      // 当前持锁线程 id（null = 未加锁）
    private volatile long expireAt;        // 锁的过期时间戳（System.currentTimeMillis）

    private final ScheduledExecutorService watchdogScheduler;
    private ScheduledFuture<?> watchdogTask;

    private final AtomicBoolean watchdogRunning = new AtomicBoolean(false);

    public SimulatedRedissonLock(String lockKey) {
        this.lockKey = lockKey;
        // 守护线程池：JVM 退出时自动回收，不阻塞关闭
        this.watchdogScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "watchdog-" + lockKey);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 尝试加锁（非阻塞）。
     *
     * 模拟 Lua 脚本原子逻辑：
     *   if (redis.call('exists', KEYS[1]) == 0) then
     *       redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
     *       return 1
     *   end
     *   return 0
     *
     * @param requesterId 请求者标识（模拟线程 id / 节点 id）
     * @return true = 加锁成功；false = 锁已被持有
     */
    public synchronized boolean tryLock(String requesterId) {
        long now = System.currentTimeMillis();

        // 锁已被持有且未过期 → 加锁失败
        if (holderId != null && now < expireAt) {
            logger.debug("[LOCK] '{}' held by {}, requester {} failed",
                    lockKey, holderId, requesterId);
            return false;
        }

        // 加锁成功：设置持有者 + 过期时间
        holderId = requesterId;
        expireAt = now + DEFAULT_TTL_MS;
        logger.info("[LOCK] '{}' acquired by {} (TTL={}ms)", lockKey, requesterId, DEFAULT_TTL_MS);

        startWatchdog(requesterId);
        return true;
    }

    /**
     * 阻塞式加锁，最多等待 waitTimeMs 毫秒。
     */
    public boolean lock(String requesterId, long waitTimeMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitTimeMs;
        while (System.currentTimeMillis() < deadline) {
            if (tryLock(requesterId)) {
                return true;
            }
            Thread.sleep(50); // 轮询间隔，真实 Redisson 用 Redis pub/sub 通知
        }
        logger.warn("[LOCK] '{}' timed out for requester {} after {}ms",
                lockKey, requesterId, waitTimeMs);
        return false;
    }

    /**
     * 释放锁。
     *
     * 模拟 Lua 脚本原子逻辑（先校验持有者，再删除），
     * 防止误删他人的锁（例如自己的锁因超时被 WatchDog 续期失败后，
     * 恰好有另一个节点加锁成功，此时不能误删）：
     *   if (redis.call('get', KEYS[1]) == ARGV[1]) then
     *       return redis.call('del', KEYS[1])
     *   end
     *   return 0
     *
     * @param requesterId 释放者标识，必须与加锁者一致
     */
    public synchronized void unlock(String requesterId) {
        if (!requesterId.equals(holderId)) {
            logger.warn("[LOCK] '{}' unlock failed: caller={} holder={} (not owner)",
                    lockKey, requesterId, holderId);
            return;
        }
        holderId = null;
        expireAt = 0;
        stopWatchdog();
        logger.info("[LOCK] '{}' released by {}", lockKey, requesterId);
    }

    // ─── WatchDog ────────────────────────────────────────────────────────────

    /**
     * 启动 WatchDog：每隔 WATCHDOG_INTERVAL_MS 自动续期。
     *
     * 真实 Redisson 的 WatchDog 会在加锁时启动一个后台 Timer，
     * 定期调用 PEXPIRE key ttl 重置过期时间。
     * 当 unlock() 被调用时，WatchDog 取消续期任务。
     */
    private void startWatchdog(String ownerId) {
        if (watchdogRunning.compareAndSet(false, true)) {
            watchdogTask = watchdogScheduler.scheduleAtFixedRate(() -> {
                synchronized (SimulatedRedissonLock.this) {
                    // 只有当前 owner 的锁才续期
                    if (ownerId.equals(holderId)) {
                        expireAt = System.currentTimeMillis() + DEFAULT_TTL_MS;
                        logger.debug("[WATCHDOG] '{}' renewed for {} (new expiry +{}ms)",
                                lockKey, ownerId, DEFAULT_TTL_MS);
                    } else {
                        // owner 已变更，停止续期
                        stopWatchdog();
                    }
                }
            }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void stopWatchdog() {
        if (watchdogRunning.compareAndSet(true, false) && watchdogTask != null) {
            watchdogTask.cancel(false);
            watchdogTask = null;
            logger.debug("[WATCHDOG] '{}' stopped", lockKey);
        }
    }

    public void shutdown() {
        stopWatchdog();
        watchdogScheduler.shutdownNow();
    }

    // ─── 状态查询（供调试使用）───────────────────────────────────────────────

    public synchronized boolean isLocked() {
        return holderId != null && System.currentTimeMillis() < expireAt;
    }

    public synchronized String getHolder() {
        return holderId;
    }

    @Override
    public String toString() {
        return "SimulatedRedissonLock{key='" + lockKey + "', holder=" + holderId + "}";
    }
}
