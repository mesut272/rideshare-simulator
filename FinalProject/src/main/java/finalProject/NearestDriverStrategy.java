package finalProject;

import java.util.concurrent.BlockingQueue;
import java.util.ArrayList;
import java.util.List;

/**
 * Nearest Driver Strategy: Assigns rides to the closest available driver.
 *
 * Algorithm:
 * 1. For each new ride request
 * 2. Find all available drivers
 * 3. Calculate distance from each driver's current location to pickup location
 * 4. Assign to the nearest driver
 *
 * Trade-offs:
 * - Pros: Minimizes pickup time, better customer experience
 * - Cons: May ignore ride priority, longer trips might wait longer
 */
public class NearestDriverStrategy {

    /**
     * Find the nearest available driver to the pickup location.
     *
     * @param availableDrivers Queue of available drivers
     * @param pickupLocation The customer's pickup location
     * @return The nearest driver, or null if none available
     */
    public static Driver findNearestDriver(
            BlockingQueue<Driver> availableDrivers,
            String pickupLocation) {

        if (availableDrivers.isEmpty()) {
            return null;
        }

        Driver nearestDriver = null;
        double minDistance = Double.MAX_VALUE;

        // 🔧 修复：从队列中取出所有司机进行比较
        List<Driver> tempList = new ArrayList<>();
        availableDrivers.drainTo(tempList);

        if (tempList.isEmpty()) {
            return null;
        }

        // 找到最近的司机
        for (Driver driver : tempList) {
            double distance = CityMap.getDistance(
                    driver.getCurrentLocation(),
                    pickupLocation
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearestDriver = driver;
            }
        }

        // 🔧 关键修复：把所有非选中的司机放回队列
        for (Driver driver : tempList) {
            if (driver != nearestDriver) {
                try {
                    availableDrivers.put(driver);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 如果被中断，尝试添加但不阻塞
                    availableDrivers.offer(driver);
                }
            }
        }

        // 返回最近的司机（不放回队列，因为要被派单）
        return nearestDriver;
    }

    /**
     * Calculate the pickup time based on distance.
     * Assumes average speed of 60 units per minute.
     */
    public static long calculatePickupTimeSeconds(String from, String to) {
        double distance = CityMap.getDistance(from, to);
        return Math.round(distance / 60.0);
    }
}
