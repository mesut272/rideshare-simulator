package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The core engine that drives the ride-sharing simulation.
 * It manages three main concurrent loops:
 * 1. Request Generation (Producer)
 * 2. Dispatching (Consumer/Scheduler)
 * 3. Ride Completion (Monitor)
 *
 * 新增组件：
 * - SimulatedRedissonLock：模拟分布式锁防重复派单
 * - DriverCacheService：Redis 二级缓存 + 延迟双删策略
 * - DriverStateManager：CAS 乐观锁司机状态机
 * - OrderIndexService：B+ 树复合索引 + EXPLAIN 分析
 */
public class SimulationEngine {

    private static final Logger logger = LoggerFactory.getLogger(SimulationEngine.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("metrics");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SimulationConfig config;

    // Thread-safe queues
    private final PriorityBlockingQueue<RideRequest> waitingQueue;
    private final PriorityBlockingQueue<ActiveRide> activeRequests;
    private final BlockingQueue<Driver> availableDrivers;

    // Atomic flags and counters for thread safety
    private final AtomicBoolean generatorDone = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger dispatchedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);

    // Performance metrics (using volatile or synchronized for updates)
    private long totalWaitTimeSeconds = 0;
    private long totalRideDurationSeconds = 0;
    private long maxWaitTimeSeconds = 0;
    private long minWaitTimeSeconds = Long.MAX_VALUE;

    // To avoid spamming logs for the same waiting request
    private final java.util.Set<String> waitingNotified = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ─── 新增：并发安全组件 ────────────────────────────────────────────────────
    // 分布式锁：防止高并发下同一订单被派给多个司机
    private final SimulatedRedissonLock dispatchLock = new SimulatedRedissonLock("dispatch-global-lock");
    // 司机缓存：Redis 二级缓存 + 延迟双删
    private final DriverCacheService driverCacheService = new DriverCacheService();
    // 司机状态管理器：CAS 乐观锁，按 driverId 维护
    private final ConcurrentHashMap<String, DriverStateManager> driverStateManagers = new ConcurrentHashMap<>();
    // 订单索引服务：模拟 B+ 树复合索引 + EXPLAIN
    private final OrderIndexService orderIndexService = new OrderIndexService();

    private final ExecutorService executor;

    public SimulationEngine(SimulationConfig config) {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(3); // 3 dedicated threads for the 3 loops

        // Initialize queues with strategies
        RideRequestOrderingStrategy orderingStrategy = new StrategyCompositeOrdering();
        this.waitingQueue = new PriorityBlockingQueue<>(11, orderingStrategy::compare);

        // Active rides sorted by completion time (earliest first)
        this.activeRequests = new PriorityBlockingQueue<>(
                11,
                (a, b) -> a.getRequest().getExpectedCompletionTime()
                        .compareTo(b.getRequest().getExpectedCompletionTime())
        );

        this.availableDrivers = new LinkedBlockingQueue<>();

        // Initialize drivers
        for (int i = 0; i < config.driverCount; i++) {
            String driverName = Name.randomName();
            String startLocation = CityMap.getAllLocations().get(
                    new java.util.Random().nextInt(CityMap.getAllLocations().size())
            );
            Driver driver = new Driver(driverName, true, startLocation);
            availableDrivers.add(driver);

            // 新增：初始化每个司机的 CAS 状态管理器 + 缓存
            driverStateManagers.put(driverName,
                    new DriverStateManager(driverName, DriverStateManager.DriverState.AVAILABLE));
            driverCacheService.initDriver(driver);
        }
    }

    public void start() {
        logger.info("=".repeat(70));
        logger.info("Simulation started at {}", LocalDateTime.now().format(TIME_FORMATTER));
        logger.info("Config: Drivers={}, Max Requests={}, Interval={}ms",
                config.driverCount, config.maxRequests, config.requestIntervalMs);
        logger.info("Strategy: {}", ConfigLoader.getDispatchStrategy());
        logger.info("=".repeat(70));

        // Submit the three core loops to the thread pool
        executor.submit(this::requestGeneratorLoop);
        executor.submit(this::dispatchLoop);
        executor.submit(this::completionLoop);
    }

    /**
     * Gracefully stops the simulation engine.
     * Ensures all threads are shut down properly and prints final stats.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return; // Already stopped
        }

        logger.info("Stopping simulation engine...");

        // Graceful shutdown sequence
        executor.shutdown();
        try {
            // Wait a bit for existing tasks to finish
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Force shutdown if stuck
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        printSummary();
    }

    // --- Core Loops ---

    private void requestGeneratorLoop() {
        while (running.get() && createdCount.get() < config.maxRequests) {
            try {
                RideRequest request = RideRequestGenerator.generateRequest();
                waitingQueue.put(request);

                int count = createdCount.incrementAndGet();

                if (ConfigLoader.isDetailedLoggingEnabled()) {
                    logger.info("[CREATED #{}] at {}", count, request.getRequestTimestamp().format(TIME_FORMATTER));
                }

                Thread.sleep(config.requestIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Unexpected error in Generator Loop", e);
            }
        }

        generatorDone.set(true);
        logger.info("[GENERATOR] Finished creating {} requests.", createdCount.get());
    }

    private void dispatchLoop() {
        String strategy = ConfigLoader.getDispatchStrategy();

        while (running.get() || !waitingQueue.isEmpty()) {
            try {
                // Check if we are done
                if (generatorDone.get() && waitingQueue.isEmpty()) {
                    Thread.sleep(100); // Small pause to ensure consistency
                    continue;
                }

                // Use poll with timeout to prevent busy-waiting
                RideRequest request = waitingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (request == null) {
                    continue;
                }

                Driver driver;
                // Strategy Selection
                if ("NEAREST_DRIVER".equals(strategy)) {
                    driver = NearestDriverStrategy.findNearestDriver(availableDrivers, request.getStartLocation());
                } else if ("LOAD_BALANCING".equals(strategy)) {
                    driver = LoadBalancingStrategy.findLeastLoadedDriver(availableDrivers);
                } else {
                    driver = availableDrivers.poll(); // Default: Any available driver
                }

                // If no driver found, put request back and wait
                if (driver == null) {
                    handleNoDriverAvailable(request);
                    continue;
                }

                // Driver found - Process Dispatch
                processDispatch(request, driver, strategy);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Critical: Catch generic exceptions so the thread doesn't die silently
                logger.error("[DISPATCHER] Critical error processing request", e);
            }
        }
        logger.info("[DISPATCHER] Finished. Dispatched total: {}", dispatchedCount.get());
    }

    private void completionLoop() {
        String strategy = ConfigLoader.getDispatchStrategy();

        while (running.get() || !activeRequests.isEmpty()) {
            try {
                // Auto-stop condition: generator done, waiting empty, active empty
                if (generatorDone.get() && waitingQueue.isEmpty() && activeRequests.isEmpty()) {
                    // We don't call stop() here to avoid race conditions, we just break.
                    // Main app should call stop().
                    break;
                }

                ActiveRide nextRide = activeRequests.peek();
                if (nextRide == null) {
                    Thread.sleep(100);
                    continue;
                }

                // Check if the ride should be completed (Current Time >= Expected Completion)
                if (!nextRide.getRequest().getExpectedCompletionTime().isAfter(LocalDateTime.now())) {
                    completeRide(strategy);
                } else {
                    // Small sleep to prevent hammering CPU
                    Thread.sleep(50);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("[COMPLETION] Critical error completing ride", e);
            }
        }
    }

    // --- Helper Methods ---

    private void handleNoDriverAvailable(RideRequest request) throws InterruptedException {
        String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();

        // Log warning only once per request
        if (!waitingNotified.contains(requestId)) {
            waitingNotified.add(requestId);
            logger.warn("[WAITING] Customer {} waiting. Queue size: {}",
                    request.getCustomerId(), waitingQueue.size() + 1);
        }

        waitingQueue.put(request); // Put back in queue
        Thread.sleep(200); // Wait a bit before retrying
    }

    private void processDispatch(RideRequest request, Driver driver, String strategy) {
        String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();
        String threadId = Thread.currentThread().getName();
        waitingNotified.remove(requestId);

        // ─── 新增：分布式锁防重复派单 ───────────────────────────────────────────
        // 模拟 Redisson 分布式锁：高并发下多个 dispatcher 线程可能同时派单给同一司机，
        // 通过 Lua 脚本原子性加锁，保证同一时刻只有一个线程能成功派单。
        boolean lockAcquired;
        try {
            lockAcquired = dispatchLock.lock(threadId, 500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (!lockAcquired) {
            // 获取锁超时，将请求放回队列重试（offer 不抛受检异常）
            waitingQueue.offer(request);
            return;
        }

        try {
            // ─── 新增：CAS 乐观锁校验司机状态 ──────────────────────────────────
            // 防止在获取 driver 对象到实际派单之间，司机状态已被其他线程改变
            DriverStateManager stateManager = driverStateManagers.get(driver.getDriverId());
            if (stateManager != null) {
                boolean casSuccess = stateManager.tryMarkBusy(requestId);
                if (!casSuccess) {
                    // CAS 失败：司机已被其他线程抢走，请求放回队列
                    logger.warn("[DISPATCH] CAS failed for driver {}, re-queuing request {}",
                            driver.getDriverId(), requestId);
                    availableDrivers.offer(driver);
                    waitingQueue.offer(request);
                    return;
                }
            }

            LocalDateTime actualStart = LocalDateTime.now();
            request.setActualStartTime(actualStart);

            // Calculate Times
            long pickupTime = 0;
            if ("NEAREST_DRIVER".equals(strategy)) {
                pickupTime = NearestDriverStrategy.calculatePickupTimeSeconds(
                        driver.getCurrentLocation(), request.getStartLocation());
            }

            long rideDuration = Math.round(request.getAnticipatedDistance() / 60.0);
            long totalTime = pickupTime + rideDuration;

            request.setExpectedCompletionTime(actualStart.plusSeconds(totalTime));

            // Update Driver State
            driver.assignRide(request);

            // ─── 新增：延迟双删更新司机缓存 ──────────────────────────────────────
            // 司机接单后位置变更，触发缓存失效（延迟双删保证最终一致性）
            driverCacheService.updateDriver(driver);

            // ─── 新增：订单写入索引（模拟 DB 写入 + B+ 树索引维护）──────────────
            orderIndexService.insertOrder(request, "IN_PROGRESS");

            // Add to active rides
            activeRequests.put(new ActiveRide(request, driver));
            int count = dispatchedCount.incrementAndGet();

            if (ConfigLoader.isDetailedLoggingEnabled()) {
                logger.info("[DISPATCHED #{}] Driver {} -> Customer {}",
                        count, driver.getDriverId(), request.getCustomerId());
            }
        } finally {
            // 确保锁一定被释放（WatchDog 防止因业务超时导致死锁）
            dispatchLock.unlock(threadId);
        }
    }

    private void completeRide(String strategy) throws InterruptedException {
        ActiveRide ride = activeRequests.poll();
        if (ride != null) {
            RideRequest req = ride.getRequest();
            Driver driver = ride.getDriver();
            String rideId = req.getCustomerId() + "-" + req.getRequestTimestamp();

            driver.finishRide(req.getDestination());

            if ("LOAD_BALANCING".equals(strategy)) {
                LoadBalancingStrategy.recordRideCompletion(driver.getDriverId());
            }

            // ─── 新增：CAS 标记司机回到 AVAILABLE ──────────────────────────────
            DriverStateManager stateManager = driverStateManagers.get(driver.getDriverId());
            if (stateManager != null) {
                stateManager.tryMarkAvailable(rideId);
            }

            // ─── 新增：司机完单后位置更新，触发缓存延迟双删 ──────────────────────
            driverCacheService.updateDriver(driver);

            // ─── 新增：更新订单状态为 COMPLETED（含 B+ 树索引维护）───────────────
            orderIndexService.insertOrder(req, "COMPLETED");

            availableDrivers.put(driver); // Return driver to pool
            int count = completedCount.incrementAndGet();

            updateMetrics(req);

            if (ConfigLoader.isDetailedLoggingEnabled()) {
                logger.info("[COMPLETED #{}] Customer {} arrived at {}",
                        count, req.getCustomerId(), req.getDestination());
            }
        }
    }

    private synchronized void updateMetrics(RideRequest req) {
        long waitSeconds = java.time.Duration.between(
                req.getRequestTimestamp(), req.getActualStartTime()).getSeconds();

        totalWaitTimeSeconds += waitSeconds;
        maxWaitTimeSeconds = Math.max(maxWaitTimeSeconds, waitSeconds);
        minWaitTimeSeconds = Math.min(minWaitTimeSeconds, waitSeconds);

        long rideSeconds = java.time.Duration.between(
                req.getActualStartTime(), req.getExpectedCompletionTime()).getSeconds();

        totalRideDurationSeconds += rideSeconds;
    }

    private void printSummary() {
        logger.info("\n" + "=".repeat(70));
        logger.info("=== Simulation Summary ===");
        logger.info("Created:    {}", createdCount.get());
        logger.info("Dispatched: {}", dispatchedCount.get());
        logger.info("Completed:  {}", completedCount.get());

        if (createdCount.get() == dispatchedCount.get() && dispatchedCount.get() == completedCount.get()) {
            logger.info("✓ OK: All requests processed successfully.");
        } else {
            logger.error("✗ ERROR: Count mismatch detected!");
        }

        logger.info("");
        logger.info("=== Performance Metrics ===");
        if (completedCount.get() > 0) {
            double avgWait = totalWaitTimeSeconds / (double) completedCount.get();
            double avgRide = totalRideDurationSeconds / (double) completedCount.get();
            long minWait = (minWaitTimeSeconds == Long.MAX_VALUE) ? 0 : minWaitTimeSeconds;

            logger.info("Average wait time:      {} seconds", String.format("%.2f", avgWait));
            logger.info("Max wait time:          {} seconds", maxWaitTimeSeconds);
            logger.info("Min wait time:          {} seconds", minWait);
            logger.info("Average ride duration:  {} seconds", String.format("%.2f", avgRide));

            // Also log to metrics file for CSV analysis if needed
            metricsLogger.info("SUMMARY,{},{},{},{},{}",
                    createdCount.get(), String.format("%.2f", avgWait), maxWaitTimeSeconds, minWait, String.format("%.2f", avgRide));
        } else {
            logger.warn("No completed rides to analyze.");
        }
        logger.info("=".repeat(70));

        // ─── 新增：打印索引优化效果 + 缓存命中统计 ──────────────────────────────
        orderIndexService.printOptimizationReport();
        driverCacheService.printStats();

        // ─── 示例：演示有索引 vs 无索引的查询对比 ───────────────────────────────
        logger.info("[DEMO] Running index comparison query...");
        orderIndexService.queryWithFullScan("COMPLETED", "UW");
        orderIndexService.queryWithIndex("COMPLETED", "UW");
        orderIndexService.printOptimizationReport();

        // 关闭新增服务
        dispatchLock.shutdown();
        driverCacheService.shutdown();
    }

    // --- Accessors ---

    public int getCompletedCount() {
        return completedCount.get();
    }
}