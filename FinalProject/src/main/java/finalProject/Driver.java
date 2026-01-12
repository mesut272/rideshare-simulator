package finalProject;

public class Driver{
    private String driverId;
    private boolean isAvailable;

    public Driver(String driverId, boolean isAvailable) {
        this.driverId = driverId;
        this.isAvailable = isAvailable;
    }

    public boolean isAvailable(){
        return this.isAvailable;
    }

    public String getDriverId(){
        return this.driverId;
    }

    public void assignRide(RideRequest rideRequest) {
        this.isAvailable = false;
    }

    public void finishRide(){
        this.isAvailable = true;
    }

    @Override
    public String toString() {
        return driverId;
    }
}
