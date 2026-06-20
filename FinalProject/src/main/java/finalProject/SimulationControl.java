package finalProject;

import java.util.concurrent.atomic.AtomicBoolean;

public interface SimulationControl {
    StartResult start();

    Snapshot snapshot();

    default SimulationMapSnapshot mapSnapshot() {
        Snapshot snapshot = snapshot();
        return new SimulationMapSnapshot(snapshot.isStarted(), snapshot.isRunning(),
                snapshot.getWaitingQueueSize(), snapshot.getCompletedCount(),
                java.util.Map.of(), java.util.List.of());
    }

    default QueueSnapshot queueSnapshot(String location) {
        return new QueueSnapshot(location, java.util.List.of());
    }

    default RideRequest submitPassengerOrder(String customerId, String startLocation,
                                             String destination, RideType rideType) {
        throw new UnsupportedOperationException("Passenger orders are not supported.");
    }

    static SimulationControl forEngine(SimulationEngine engine) {
        return new EngineSimulationControl(engine);
    }

    final class StartResult extends Snapshot {
        private final boolean alreadyStarted;

        public StartResult(boolean started, boolean alreadyStarted, boolean running,
                           int waitingQueueSize, int completedCount) {
            super(started, running, waitingQueueSize, completedCount);
            this.alreadyStarted = alreadyStarted;
        }

        public boolean isAlreadyStarted() {
            return alreadyStarted;
        }
    }

    class Snapshot {
        private final boolean started;
        private final boolean running;
        private final int waitingQueueSize;
        private final int completedCount;

        public Snapshot(boolean started, boolean running, int waitingQueueSize, int completedCount) {
            this.started = started;
            this.running = running;
            this.waitingQueueSize = waitingQueueSize;
            this.completedCount = completedCount;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isRunning() {
            return running;
        }

        public int getWaitingQueueSize() {
            return waitingQueueSize;
        }

        public int getCompletedCount() {
            return completedCount;
        }
    }

    class EngineSimulationControl implements SimulationControl {
        private final SimulationEngine engine;
        private final AtomicBoolean started = new AtomicBoolean(false);

        EngineSimulationControl(SimulationEngine engine) {
            this.engine = engine;
        }

        @Override
        public StartResult start() {
            boolean alreadyStarted = !started.compareAndSet(false, true);
            if (!alreadyStarted) {
                engine.start();
            }
            return new StartResult(true, alreadyStarted, started.get() && engine.isRunning(),
                    engine.getWaitingQueueSize(), engine.getCompletedCount());
        }

        @Override
        public Snapshot snapshot() {
            boolean hasStarted = started.get();
            return new Snapshot(hasStarted, hasStarted && engine.isRunning(),
                    engine.getWaitingQueueSize(), engine.getCompletedCount());
        }

        @Override
        public SimulationMapSnapshot mapSnapshot() {
            return engine.getMapSnapshot(started.get());
        }

        @Override
        public QueueSnapshot queueSnapshot(String location) {
            return engine.getQueueSnapshot(location);
        }

        @Override
        public RideRequest submitPassengerOrder(String customerId, String startLocation,
                                                String destination, RideType rideType) {
            RideRequest request = new RideRequest(customerId, startLocation, destination,
                    CityMap.getDistance(startLocation, destination), java.time.LocalDateTime.now(), rideType);
            engine.submitManualOrder(request);
            return request;
        }
    }
}
