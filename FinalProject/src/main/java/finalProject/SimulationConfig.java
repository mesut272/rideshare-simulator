package finalProject;

public class SimulationConfig {
    public final int driverCount;
    public final long requestIntervalMs;
    public final int maxRequests;

    public SimulationConfig(int driverCount, long requestIntervalMs, int maxRequests) {
        this.driverCount = driverCount;
        this.requestIntervalMs = requestIntervalMs;
        this.maxRequests = maxRequests;
    }
}

