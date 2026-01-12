package finalProject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class SimulationEngine {

    private final SimulationConfig config;
    private final PriorityBlockingQueue<RideRequest> waitingQueue;
    private final PriorityBlockingQueue<ActiveRide> activeRequests;
    private final BlockingQueue<Driver> availableDrivers;
    private final AtomicBoolean generatorDone = new AtomicBoolean(false);
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger dispatchedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private long totalWaitTimeSeconds = 0;
    private long totalRideDurationSeconds = 0;
    private long maxWaitTimeSeconds = 0;
    private long minWaitTimeSeconds = Long.MAX_VALUE;

    // 🔧 新增：防止重复打印等待信息
    private final java.util.Set<String> waitingNotified = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SimulationEngine(SimulationConfig config) {
        this.config = config;

        RideRequestOrderingStrategy orderingStrategy = new StrategyCompositeOrdering();
        this.waitingQueue = new PriorityBlockingQueue<>(11, orderingStrategy::compare);

        this.activeRequests = new PriorityBlockingQueue<>(
                11,
                (a, b) -> a.getRequest().getExpectedCompletionTime()
                        .compareTo(b.getRequest().getExpectedCompletionTime())
        );

        this.availableDrivers = new LinkedBlockingQueue<>();

        for (int i = 0; i < config.driverCount; i++) {
            availableDrivers.add(new Driver(Name.randomName(), true));
        }
    }

    public void start() {
        System.out.println("=".repeat(70));
        System.out.println("[ENGINE] Simulation started at " + LocalDateTime.now().format(TIME_FORMATTER));
        System.out.println("[CONFIG] Drivers=" + config.driverCount
                + ", Max Requests=" + config.maxRequests
                + ", Interval=" + config.requestIntervalMs + "ms");
        System.out.println("=".repeat(70));
        System.out.println();

        executor.submit(this::requestGeneratorLoop);
        executor.submit(this::dispatchLoop);
        executor.submit(this::completionLoop);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("=== Simulation Summary ===");
        System.out.println("Created:    " + createdCount.get());
        System.out.println("Dispatched: " + dispatchedCount.get());
        System.out.println("Completed:  " + completedCount.get());

        if (createdCount.get() == dispatchedCount.get()
                && dispatchedCount.get() == completedCount.get()) {
            System.out.println("✓ OK: All requests processed");
        } else {
            System.out.println("✗ ERROR: Count mismatch detected!");
        }

        // 🔧 新增：显示统计数据
        System.out.println();
        System.out.println("=== Performance Metrics ===");
        if (completedCount.get() > 0) {
            double avgWait = totalWaitTimeSeconds / (double) completedCount.get();
            double avgRide = totalRideDurationSeconds / (double) completedCount.get();
            long minWait = (minWaitTimeSeconds == Long.MAX_VALUE) ? 0 : minWaitTimeSeconds;

            System.out.println("Average wait time:      " + String.format("%.2f", avgWait) + " seconds");
            System.out.println("Max wait time:          " + maxWaitTimeSeconds + " seconds");
            System.out.println("Min wait time:          " + minWait + " seconds");
            System.out.println("Average ride duration:  " + String.format("%.2f", avgRide) + " seconds");
        } else {
            System.out.println("No completed rides to analyze");
        }
        System.out.println("=".repeat(70));

        executor.shutdownNow();
    }

    // ========================
    // Thread 1: 生成订单
    // ========================
    private void requestGeneratorLoop() {
        while (running.get() && createdCount.get() < config.maxRequests) {
            try {
                RideRequest request = RideRequestGenerator.generateRequest();
                waitingQueue.put(request);

                int count = createdCount.incrementAndGet();

                // 🔧 新增：显示详细时间信息
                System.out.println("[CREATED #" + count + "] at " + request.getRequestTimestamp().format(TIME_FORMATTER));
                System.out.println("  Customer: " + request.getCustomerId());
                System.out.println("  Type: " + request.getRideType());
                System.out.println("  Route: " + request.getStartLocation() + " → " + request.getDestination());
                System.out.println("  Distance: " + request.getAnticipatedDistance() + " units");
                System.out.println();

                Thread.sleep(config.requestIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        generatorDone.set(true);
        System.out.println("[GENERATOR] Finished creating " + createdCount.get() + " requests at "
                + LocalDateTime.now().format(TIME_FORMATTER));
        System.out.println();
    }

    // ========================
    // Thread 2: 派单
    // ========================
    private void dispatchLoop() {
        while (running.get() || !generatorDone.get() || !waitingQueue.isEmpty()) {
            try {
                RideRequest request = waitingQueue.poll();
                if (request == null) {
                    Thread.sleep(50);
                    continue;
                }

                Driver driver = availableDrivers.poll();
                if (driver == null) {
                    // 🔧 修复：只在第一次进入等待队列时打印
                    String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();
                    if (!waitingNotified.contains(requestId)) {
                        waitingNotified.add(requestId);
                        int queueSize = waitingQueue.size() + 1; // +1 因为当前request还没放回
                        System.out.println("[WAITING] Customer " + request.getCustomerId()
                                + " is in the waiting queue (Position: " + queueSize + " in queue)");
                        System.out.println("  Reason: No available drivers");
                        System.out.println("  Request type: " + request.getRideType());
                        System.out.println();
                    }

                    waitingQueue.put(request);
                    Thread.sleep(200); // 🔧 增加等待时间，减少CPU占用
                    continue;
                }

                // 🔧 派发时从通知集合中移除
                String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();
                waitingNotified.remove(requestId);

                LocalDateTime actualStart = LocalDateTime.now();
                request.setActualStartTime(actualStart);

                long durationSeconds = Math.round(request.getAnticipatedDistance() / 60.0);
                request.setExpectedCompletionTime(actualStart.plusSeconds(durationSeconds));

                ActiveRide ride = new ActiveRide(request, driver);
                activeRequests.put(ride);

                int count = dispatchedCount.incrementAndGet();

                // 显示详细时间信息
                System.out.println("[DISPATCHED #" + count + "] at " + actualStart.format(TIME_FORMATTER));
                System.out.println("  Driver: " + driver.getDriverId());
                System.out.println("  Customer: " + request.getCustomerId());
                System.out.println("  Request time: " + request.getRequestTimestamp().format(TIME_FORMATTER));
                System.out.println("  Start time: " + actualStart.format(TIME_FORMATTER));
                System.out.println("  Expected completion: " + request.getExpectedCompletionTime().format(TIME_FORMATTER));

                // 计算等待时间
                long waitSeconds = java.time.Duration.between(
                        request.getRequestTimestamp(),
                        actualStart
                ).getSeconds();
                System.out.println("  Wait time: " + waitSeconds + " seconds");
                System.out.println();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[DISPATCHER] Finished dispatching " + dispatchedCount.get() + " rides at "
                + LocalDateTime.now().format(TIME_FORMATTER));
        System.out.println();
    }

    // ========================
    // Thread 3: 完成处理
    // ========================
    private void completionLoop() {
        while (running.get() || !generatorDone.get() || !waitingQueue.isEmpty() || !activeRequests.isEmpty()) {

            if (generatorDone.get() && waitingQueue.isEmpty() && activeRequests.isEmpty()) {
                System.out.println("[ENGINE] All rides completed at " + LocalDateTime.now().format(TIME_FORMATTER));
                stop();
                break;
            }

            try {
                ActiveRide next = activeRequests.peek();
                if (next == null) {
                    Thread.sleep(50);
                    continue;
                }

                if (!next.getRequest().getExpectedCompletionTime().isAfter(LocalDateTime.now())) {
                    ActiveRide ride = activeRequests.poll();
                    if (ride != null) {
                        availableDrivers.put(ride.getDriver());

                        int count = completedCount.incrementAndGet();
                        RideRequest req = ride.getRequest();

                        // 🔧 新增：计算并累积等待时间
                        long waitSeconds = java.time.Duration.between(
                                req.getRequestTimestamp(),
                                req.getActualStartTime()
                        ).getSeconds();

                        synchronized (this) {
                            totalWaitTimeSeconds += waitSeconds;
                            maxWaitTimeSeconds = Math.max(maxWaitTimeSeconds, waitSeconds);
                            minWaitTimeSeconds = Math.min(minWaitTimeSeconds, waitSeconds);
                        }

                        // 🔧 新增：计算并累积行程时长
                        long rideSeconds = java.time.Duration.between(
                                req.getActualStartTime(),
                                req.getExpectedCompletionTime()
                        ).getSeconds();

                        synchronized (this) {
                            totalRideDurationSeconds += rideSeconds;
                        }

                        // 显示详细完成信息
                        System.out.println("[COMPLETED #" + count + "] at " + LocalDateTime.now().format(TIME_FORMATTER));
                        System.out.println("  Driver: " + ride.getDriver().getDriverId());
                        System.out.println("  Customer: " + req.getCustomerId());
                        System.out.println("  Started at: " + req.getActualStartTime().format(TIME_FORMATTER));
                        System.out.println("  Completed at: " + req.getExpectedCompletionTime().format(TIME_FORMATTER));
                        System.out.println("  Wait time: " + waitSeconds + " seconds");
                        System.out.println("  Ride duration: " + rideSeconds + " seconds");
                        System.out.println();
                    }
                } else {
                    Thread.sleep(50);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}