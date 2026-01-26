package finalProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulationEngine {

    private static final Logger logger = LoggerFactory.getLogger(SimulationEngine.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("metrics");

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
        logger.info("=".repeat(70));
        logger.info("Simulation started at {}", LocalDateTime.now().format(TIME_FORMATTER));
        logger.info("Config: Drivers={}, Max Requests={}, Interval={}ms",
                config.driverCount, config.maxRequests, config.requestIntervalMs);
        logger.info("=".repeat(70));

        executor.submit(this::requestGeneratorLoop);
        executor.submit(this::dispatchLoop);
        executor.submit(this::completionLoop);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        logger.info("\n" + "=".repeat(70));
        logger.info("=== Simulation Summary ===");
        logger.info("Created:    {}", createdCount.get());
        logger.info("Dispatched: {}", dispatchedCount.get());
        logger.info("Completed:  {}", completedCount.get());

        if (createdCount.get() == dispatchedCount.get()
                && dispatchedCount.get() == completedCount.get()) {
            logger.info("✓ OK: All requests processed");
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

            metricsLogger.info("Simulation Summary: Created={}, Dispatched={}, Completed={}",
                    createdCount.get(), dispatchedCount.get(), completedCount.get());
            metricsLogger.info("Avg Wait: {}s, Max Wait: {}s, Min Wait: {}s, Avg Ride: {}s",
                    String.format("%.2f", avgWait), maxWaitTimeSeconds, minWait, String.format("%.2f", avgRide));
        } else {
            logger.warn("No completed rides to analyze");
        }
        logger.info("=".repeat(70));

        executor.shutdownNow();
    }

    private void requestGeneratorLoop() {
        while (running.get() && createdCount.get() < config.maxRequests) {
            try {
                RideRequest request = RideRequestGenerator.generateRequest();
                waitingQueue.put(request);

                int count = createdCount.incrementAndGet();

                if (ConfigLoader.isDetailedLoggingEnabled()) {
                    logger.info("[CREATED #{}] at {}", count, request.getRequestTimestamp().format(TIME_FORMATTER));
                    logger.debug("  Customer: {}", request.getCustomerId());
                    logger.debug("  Type: {}", request.getRideType());
                    logger.debug("  Route: {} → {}", request.getStartLocation(), request.getDestination());
                    logger.debug("  Distance: {} units", request.getAnticipatedDistance());
                }

                Thread.sleep(config.requestIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        generatorDone.set(true);
        logger.info("[GENERATOR] Finished creating {} requests at {}",
                createdCount.get(), LocalDateTime.now().format(TIME_FORMATTER));
    }

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
                    String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();
                    if (!waitingNotified.contains(requestId)) {
                        waitingNotified.add(requestId);
                        int queueSize = waitingQueue.size() + 1;
                        logger.warn("[WAITING] Customer {} is in the waiting queue (Position: {} in queue)",
                                request.getCustomerId(), queueSize);
                        logger.debug("  Reason: No available drivers");
                        logger.debug("  Request type: {}", request.getRideType());
                    }

                    waitingQueue.put(request);
                    Thread.sleep(200);
                    continue;
                }

                String requestId = request.getCustomerId() + "-" + request.getRequestTimestamp();
                waitingNotified.remove(requestId);

                LocalDateTime actualStart = LocalDateTime.now();
                request.setActualStartTime(actualStart);

                long durationSeconds = Math.round(request.getAnticipatedDistance() / 60.0);
                request.setExpectedCompletionTime(actualStart.plusSeconds(durationSeconds));

                ActiveRide ride = new ActiveRide(request, driver);
                activeRequests.put(ride);

                int count = dispatchedCount.incrementAndGet();

                if (ConfigLoader.isDetailedLoggingEnabled()) {
                    logger.info("[DISPATCHED #{}] at {}", count, actualStart.format(TIME_FORMATTER));
                    logger.debug("  Driver: {}", driver.getDriverId());
                    logger.debug("  Customer: {}", request.getCustomerId());
                    logger.debug("  Request time: {}", request.getRequestTimestamp().format(TIME_FORMATTER));
                    logger.debug("  Start time: {}", actualStart.format(TIME_FORMATTER));
                    logger.debug("  Expected completion: {}", request.getExpectedCompletionTime().format(TIME_FORMATTER));

                    long waitSeconds = java.time.Duration.between(
                            request.getRequestTimestamp(),
                            actualStart
                    ).getSeconds();
                    logger.debug("  Wait time: {} seconds", waitSeconds);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.info("[DISPATCHER] Finished dispatching {} rides at {}",
                dispatchedCount.get(), LocalDateTime.now().format(TIME_FORMATTER));
    }

    private void completionLoop() {
        while (running.get() || !generatorDone.get() || !waitingQueue.isEmpty() || !activeRequests.isEmpty()) {

            if (generatorDone.get() && waitingQueue.isEmpty() && activeRequests.isEmpty()) {
                logger.info("[ENGINE] All rides completed at {}", LocalDateTime.now().format(TIME_FORMATTER));
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

                        long waitSeconds = java.time.Duration.between(
                                req.getRequestTimestamp(),
                                req.getActualStartTime()
                        ).getSeconds();

                        synchronized (this) {
                            totalWaitTimeSeconds += waitSeconds;
                            maxWaitTimeSeconds = Math.max(maxWaitTimeSeconds, waitSeconds);
                            minWaitTimeSeconds = Math.min(minWaitTimeSeconds, waitSeconds);
                        }

                        long rideSeconds = java.time.Duration.between(
                                req.getActualStartTime(),
                                req.getExpectedCompletionTime()
                        ).getSeconds();

                        synchronized (this) {
                            totalRideDurationSeconds += rideSeconds;
                        }

                        if (ConfigLoader.isDetailedLoggingEnabled()) {
                            logger.info("[COMPLETED #{}] at {}", count, LocalDateTime.now().format(TIME_FORMATTER));
                            logger.debug("  Driver: {}", ride.getDriver().getDriverId());
                            logger.debug("  Customer: {}", req.getCustomerId());
                            logger.debug("  Started at: {}", req.getActualStartTime().format(TIME_FORMATTER));
                            logger.debug("  Completed at: {}", req.getExpectedCompletionTime().format(TIME_FORMATTER));
                            logger.debug("  Wait time: {} seconds", waitSeconds);
                            logger.debug("  Ride duration: {} seconds", rideSeconds);
                        }
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