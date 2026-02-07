package finalProject;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load Balancing Strategy: Distributes rides evenly across drivers.
 *
 * Algorithm:
 * 1. Track how many rides each driver has completed
 * 2. Assign new ride to the driver with fewest completed rides
 * 3. Break ties by choosing first available
 *
 * Trade-offs:
 * - Pros: Even workload distribution, fair to drivers
 * - Cons: May assign far drivers, ignores location
 */
public class LoadBalancingStrategy {

    // Track ride count per driver
    private static final ConcurrentHashMap<String, AtomicInteger> driverRideCount =
            new ConcurrentHashMap<>();

    /**
     * Find the driver with the least number of completed rides.
     */
    public static Driver findLeastLoadedDriver(BlockingQueue<Driver> availableDrivers) {

        if (availableDrivers.isEmpty()) {
            return null;
        }

        // Get all available drivers
        java.util.List<Driver> tempList = new java.util.ArrayList<>();
        availableDrivers.drainTo(tempList);

        if (tempList.isEmpty()) {
            return null;
        }

        Driver leastLoadedDriver = null;
        int minRides = Integer.MAX_VALUE;

        // Find driver with minimum rides
        for (Driver driver : tempList) {
            int rideCount = driverRideCount
                    .computeIfAbsent(driver.getDriverId(), k -> new AtomicInteger(0))
                    .get();

            if (rideCount < minRides) {
                minRides = rideCount;
                leastLoadedDriver = driver;
            }
        }

        // Put non-selected drivers back
        for (Driver driver : tempList) {
            if (driver != leastLoadedDriver) {
                try {
                    availableDrivers.put(driver);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return leastLoadedDriver;
    }

    /**
     * Increment ride count when driver completes a ride.
     */
    public static void recordRideCompletion(String driverId) {
        driverRideCount
                .computeIfAbsent(driverId, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Get ride count for a driver (for debugging).
     */
    public static int getRideCount(String driverId) {
        return driverRideCount
                .getOrDefault(driverId, new AtomicInteger(0))
                .get();
    }

    /**
     * Reset all counters (for testing).
     */
    public static void reset() {
        driverRideCount.clear();
    }
}
