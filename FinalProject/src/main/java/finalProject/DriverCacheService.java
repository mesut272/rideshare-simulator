package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DriverCacheService — 模拟 Redis 二级缓存 + 延迟双删策略
 *
 * ─── 为什么需要缓存？ ────────────────────────────────────────────────────────
 * 调度系统中"热点司机"（活跃区域的司机）数据被频繁读取。
 * 若每次都查 DB，在高并发下会产生大量重复 IO。
 * 引入 Redis 缓存后，绝大多数读请求可在缓存层命中，降低 DB 压力。
 *
 * ─── 为什么需要延迟双删？ ─────────────────────────────────────────────────────
 * 当司机位置/状态发生变更时（写操作），需要同步让缓存失效，否则会读到脏数据。
 * 简单"先删缓存再写 DB"的问题：
 *   T1: 删缓存
 *   T2: 另一请求读缓存 miss → 从 DB 读到旧数据 → 重新写入缓存（脏数据回来了！）
 *   T1: 写 DB（新数据）
 * 结果：缓存里存的是旧数据。
 *
 * 延迟双删策略：
 *   Step 1: 删缓存（第一次）
 *   Step 2: 写 DB
 *   Step 3: 延迟 N ms 后再删一次缓存（第二次）
 * 第二次删除是为了清除 Step 1~2 之间被其他线程写回的脏缓存，
 * 从而保证最终一致性（eventual consistency）。
 *
 * ─── 缓存三灾及本类的应对策略 ─────────────────────────────────────────────────
 * - 缓存穿透（key 不存在）：缓存空值（NULL sentinel）
 * - 缓存击穿（热点 key 失效瞬间）：加分布式锁（见 SimulatedRedissonLock）
 * - 缓存雪崩（大量 key 同时失效）：TTL 加随机抖动
 */
public class DriverCacheService {

    private static final Logger logger = LoggerFactory.getLogger(DriverCacheService.class);

    // ─── 模拟 Redis 存储（ConcurrentHashMap = 内存 KV 存储）───────────────────
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // ─── 模拟 DB（实际项目中是 MySQL / JPA Repository）─────────────────────────
    private final ConcurrentHashMap<String, Driver> database = new ConcurrentHashMap<>();

    // 缓存 TTL：基础值 + 随机抖动，防雪崩
    private static final long BASE_TTL_MS = 10_000L;
    private static final long TTL_JITTER_MS = 2_000L;

    // 延迟双删的延迟时间（毫秒），需大于"并发读取写缓存"的最长时间
    private static final long DOUBLE_DELETE_DELAY_MS = 200L;

    // 缓存空值 sentinel，防穿透
    private static final Driver NULL_SENTINEL = new Driver("NULL", false, "NONE");

    // 分布式锁（防击穿）：每个 driverId 一把锁
    private final ConcurrentHashMap<String, SimulatedRedissonLock> locks = new ConcurrentHashMap<>();

    // 延迟双删使用的单线程调度器
    private final ScheduledExecutorService delayedDeleteScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-delayed-delete");
                t.setDaemon(true);
                return t;
            });

    // 统计指标
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    // ─── 公共 API ─────────────────────────────────────────────────────────────

    /**
     * 初始化：将 Driver 预加载到"DB"
     */
    public void initDriver(Driver driver) {
        database.put(driver.getDriverId(), driver);
    }

    /**
     * 读取司机信息（缓存优先）。
     *
     * 流程：
     *   1. 查缓存 → 命中且有效 → 返回
     *   2. 缓存 miss → 加分布式锁防止缓存击穿
     *   3. 锁内再查一次缓存（double-check，防止加锁期间其他线程已回填）
     *   4. 查 DB → 回填缓存 → 返回
     *   5. DB 也没有 → 缓存空值 sentinel，防止穿透
     */
    public Driver getDriver(String driverId) {
        // Step 1: 查缓存
        CacheEntry entry = cache.get(driverId);
        if (entry != null && !entry.isExpired()) {
            if (entry.value == NULL_SENTINEL) {
                logger.debug("[CACHE] Hit null-sentinel for driverId={}", driverId);
                cacheHits.incrementAndGet();
                return null; // 穿透保护：DB 中也没有，不用查了
            }
            cacheHits.incrementAndGet();
            logger.debug("[CACHE] Hit for driverId={}", driverId);
            return entry.value;
        }

        cacheMisses.incrementAndGet();
        logger.debug("[CACHE] Miss for driverId={}, querying DB...", driverId);

        // Step 2: 加锁防击穿
        SimulatedRedissonLock lock = locks.computeIfAbsent(
                driverId, k -> new SimulatedRedissonLock("driver-lock:" + k));

        String threadId = Thread.currentThread().getName();
        try {
            boolean acquired = lock.lock(threadId, 1000L);
            if (!acquired) {
                // 获取锁超时，降级直接查 DB（保证可用性）
                logger.warn("[CACHE] Lock timeout for driverId={}, fallback to DB", driverId);
                return database.get(driverId);
            }

            // Step 3: Double-check（加锁成功后再查一次缓存）
            CacheEntry recheck = cache.get(driverId);
            if (recheck != null && !recheck.isExpired() && recheck.value != NULL_SENTINEL) {
                return recheck.value;
            }

            // Step 4: 查 DB
            Driver driver = database.get(driverId);

            // Step 5: 回填缓存（包括空值）
            Driver toCache = (driver != null) ? driver : NULL_SENTINEL;
            long ttl = BASE_TTL_MS + (long) (Math.random() * TTL_JITTER_MS); // 抖动防雪崩
            cache.put(driverId, new CacheEntry(toCache, System.currentTimeMillis() + ttl));

            if (driver == null) {
                logger.info("[CACHE] driverId={} not in DB, cached null-sentinel", driverId);
            } else {
                logger.info("[CACHE] driverId={} loaded from DB and cached (TTL={}ms)", driverId, ttl);
            }
            return driver;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return database.get(driverId); // 降级
        } finally {
            lock.unlock(threadId);
        }
    }

    /**
     * 更新司机信息（延迟双删策略保证缓存与 DB 的最终一致性）。
     *
     * 时序：
     *   [第一次删缓存] → [写 DB] → [delay 200ms] → [第二次删缓存]
     *
     * 为什么要延迟？
     *   若写 DB 期间有并发读请求将旧数据重新写入缓存，
     *   等写 DB 完成后立刻删缓存也无法保证：
     *   "最后一次并发读" 可能在"立刻删"之后再次写入缓存。
     *   延迟 200ms 覆盖了大多数并发读的完成时间，第二次删除可将脏数据清除。
     */
    public void updateDriver(Driver driver) {
        String driverId = driver.getDriverId();

        // Step 1: 第一次删缓存
        cache.remove(driverId);
        logger.info("[CACHE] First delete for driverId={} (before DB write)", driverId);

        // Step 2: 写 DB（模拟）
        database.put(driverId, driver);
        logger.info("[DB] Updated driverId={} location={}", driverId, driver.getCurrentLocation());

        // Step 3: 延迟 DOUBLE_DELETE_DELAY_MS 后，第二次删缓存
        delayedDeleteScheduler.schedule(() -> {
            cache.remove(driverId);
            logger.info("[CACHE] Delayed second delete for driverId={} ({}ms after DB write)",
                    driverId, DOUBLE_DELETE_DELAY_MS);
        }, DOUBLE_DELETE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 打印缓存命中率统计
     */
    public void printStats() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (hits * 100.0 / total);
        logger.info("[CACHE STATS] Hits={} Misses={} Total={} HitRate={:.1f}%",
                hits, misses, total, hitRate);
    }

    public void shutdown() {
        delayedDeleteScheduler.shutdownNow();
        locks.values().forEach(SimulatedRedissonLock::shutdown);
    }

    /**
     * 获取当前系统中所有的司机对象（直接从 DB 读取）。
     * 用于 AI 全局情况查询，绕过缓存，确保获取的是最全的数据列表。
     */
    public java.util.Collection<Driver> getAllDrivers() {
        return database.values();
    }

    /**
     * 获取当前系统中所有司机的 ID 列表。
     */
    public java.util.Set<String> getAllDriverIds() {
        return database.keySet();
    }

    void clearForTesting() {
        cache.clear();
        database.clear();
    }

    // ─── 内部类：缓存条目（含 TTL）────────────────────────────────────────────

    private static class CacheEntry {
        final Driver value;
        final long expireAt; // System.currentTimeMillis() 时间戳

        CacheEntry(Driver value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
