package finalProject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimulationMapSnapshot {
    private final boolean started;
    private final boolean running;
    private final int waitingQueueSize;
    private final int completedCount;
    private final Map<String, Integer> queueByLocation;
    private final List<ActiveTripSnapshot> activeTrips;

    public SimulationMapSnapshot(boolean started, boolean running, int waitingQueueSize, int completedCount,
                                 Map<String, Integer> queueByLocation,
                                 List<ActiveTripSnapshot> activeTrips) {
        this.started = started;
        this.running = running;
        this.waitingQueueSize = waitingQueueSize;
        this.completedCount = completedCount;
        this.queueByLocation = Collections.unmodifiableMap(queueByLocation);
        this.activeTrips = Collections.unmodifiableList(activeTrips);
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

    public Map<String, Integer> getQueueByLocation() {
        return queueByLocation;
    }

    public List<ActiveTripSnapshot> getActiveTrips() {
        return activeTrips;
    }

    public static class ActiveTripSnapshot {
        private final String driverId;
        private final String customerId;
        private final String startLocation;
        private final String destination;
        private final String rideType;
        private final double anticipatedDistance;
        private final double progressPercent;
        private final long remainingSeconds;

        public ActiveTripSnapshot(String driverId, String customerId, String startLocation, String destination,
                                  String rideType, double anticipatedDistance, double progressPercent,
                                  long remainingSeconds) {
            this.driverId = driverId;
            this.customerId = customerId;
            this.startLocation = startLocation;
            this.destination = destination;
            this.rideType = rideType;
            this.anticipatedDistance = anticipatedDistance;
            this.progressPercent = progressPercent;
            this.remainingSeconds = remainingSeconds;
        }

        public String getDriverId() {
            return driverId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public String getStartLocation() {
            return startLocation;
        }

        public String getDestination() {
            return destination;
        }

        public String getRideType() {
            return rideType;
        }

        public double getAnticipatedDistance() {
            return anticipatedDistance;
        }

        public double getProgressPercent() {
            return progressPercent;
        }

        public long getRemainingSeconds() {
            return remainingSeconds;
        }
    }
}
