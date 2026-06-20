package finalProject;

import java.util.Collections;
import java.util.List;

public class QueueSnapshot {
    private final String location;
    private final int total;
    private final List<PassengerSnapshot> passengers;

    public QueueSnapshot(String location, List<PassengerSnapshot> passengers) {
        this.location = location;
        this.total = passengers.size();
        this.passengers = Collections.unmodifiableList(passengers);
    }

    public String getLocation() {
        return location;
    }

    public int getTotal() {
        return total;
    }

    public List<PassengerSnapshot> getPassengers() {
        return passengers;
    }

    public static class PassengerSnapshot {
        private final String customerId;
        private final String startLocation;
        private final String destination;
        private final String rideType;
        private final double anticipatedDistance;
        private final long waitingSeconds;

        public PassengerSnapshot(String customerId, String startLocation, String destination, String rideType,
                                 double anticipatedDistance, long waitingSeconds) {
            this.customerId = customerId;
            this.startLocation = startLocation;
            this.destination = destination;
            this.rideType = rideType;
            this.anticipatedDistance = anticipatedDistance;
            this.waitingSeconds = waitingSeconds;
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

        public long getWaitingSeconds() {
            return waitingSeconds;
        }
    }
}
