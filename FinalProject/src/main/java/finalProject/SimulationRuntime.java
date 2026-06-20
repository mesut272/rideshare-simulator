package finalProject;

import java.util.function.Function;

public class SimulationRuntime implements SimulationControl {
    private final SimulationConfig config;
    private final Function<SimulationEngine, TaxiAgentInterface> agentFactory;

    private SimulationEngine engine;
    private TaxiAgentInterface agent;
    private boolean started;

    public SimulationRuntime(SimulationConfig config, Function<SimulationEngine, TaxiAgentInterface> agentFactory) {
        this.config = config;
        this.agentFactory = agentFactory;
        createFreshEngine();
    }

    @Override
    public synchronized StartResult start() {
        if (started && engine.isRunning()) {
            return new StartResult(true, true, true, engine.getWaitingQueueSize(), engine.getCompletedCount());
        }

        if (started) {
            engine.stop();
            createFreshEngine();
        }

        engine.start();
        started = true;
        return new StartResult(true, false, engine.isRunning(), engine.getWaitingQueueSize(), engine.getCompletedCount());
    }

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(started, started && engine.isRunning(),
                engine.getWaitingQueueSize(), engine.getCompletedCount());
    }

    public synchronized String chat(String message) {
        return agent.chat(message);
    }

    public synchronized DriverCacheService driverCache() {
        return engine.getDriverCache();
    }

    @Override
    public synchronized SimulationMapSnapshot mapSnapshot() {
        return engine.getMapSnapshot(started);
    }

    @Override
    public synchronized QueueSnapshot queueSnapshot(String location) {
        return engine.getQueueSnapshot(location);
    }

    @Override
    public synchronized RideRequest submitPassengerOrder(String customerId, String startLocation,
                                                         String destination, RideType rideType) {
        boolean startManualRound = started && !engine.isRunning();
        if (startManualRound) {
            createFreshEngine(new SimulationConfig(config.driverCount, config.requestIntervalMs, 0));
        }

        RideRequest request = new RideRequest(customerId, startLocation, destination,
                CityMap.getDistance(startLocation, destination), java.time.LocalDateTime.now(), rideType);
        engine.submitManualOrder(request);

        if (startManualRound) {
            engine.start();
            started = true;
        }

        return request;
    }

    public synchronized void stop() {
        engine.stop();
    }

    private void createFreshEngine() {
        createFreshEngine(config);
    }

    private void createFreshEngine(SimulationConfig engineConfig) {
        engine = new SimulationEngine(engineConfig);
        agent = agentFactory.apply(engine);
        started = false;
    }
}
