package finalProject;

public class ActiveRide {

    private final RideRequest request;
    private final Driver driver;

    public ActiveRide(RideRequest request, Driver driver) {
        this.request = request;
        this.driver = driver;
    }

    public RideRequest getRequest() {
        return request;
    }

    public Driver getDriver() {
        return driver;
    }

    @Override
    public String toString() {
        return "ActiveRide{" +
                "request=" + request +
                ", driver=" + driver.getDriverId() +
                '}';
    }
}

