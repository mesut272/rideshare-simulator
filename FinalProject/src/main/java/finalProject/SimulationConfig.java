package finalProject;

public class SimulationConfig {
    public final int driverCount;
    public final long requestIntervalMs;
    public final int runtimeSeconds;

    public final int maxRequests;


    public SimulationConfig(int driverCount,
                            long requestIntervalMs,
                            int runtimeSeconds,
                            int maxRequests) {
        this.driverCount = driverCount;
        this.requestIntervalMs = requestIntervalMs;
        this.runtimeSeconds = runtimeSeconds;
        this.maxRequests = maxRequests;
    }
}

