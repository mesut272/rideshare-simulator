package finalProject;

/**
 * Represents a driver in the ride-sharing system.
 * Each driver has a location and availability status.
 */
public class Driver {
    private String driverId;
    private boolean isAvailable;
    private String currentLocation;

    public Driver(String driverId, boolean isAvailable, String currentLocation) {
        this.driverId = driverId;
        this.isAvailable = isAvailable;
        this.currentLocation = currentLocation;
    }

    public boolean isAvailable() {
        return this.isAvailable;
    }

    public String getDriverId() {
        return this.driverId;
    }

    public String getCurrentLocation() {
        return this.currentLocation;
    }

    public void setCurrentLocation(String location) {
        this.currentLocation = location;
    }

    /**
     * Assign a ride to this driver.
     * Driver becomes unavailable and moves to the pickup location.
     */
    public void assignRide(RideRequest rideRequest) {
        this.isAvailable = false;
        // 司机接单后，移动到客户的起始位置
        this.currentLocation = rideRequest.getStartLocation();
    }

    /**
     * Finish a ride.
     * Driver becomes available and is now at the drop-off location.
     */
    public void finishRide(String dropOffLocation) {
        this.isAvailable = true;
        // 完成订单后，司机在目的地
        this.currentLocation = dropOffLocation;
    }

    @Override
    public String toString() {
        return driverId + " (at " + currentLocation + ")";
    }
}
