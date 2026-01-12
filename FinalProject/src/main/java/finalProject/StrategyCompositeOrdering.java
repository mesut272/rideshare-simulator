package finalProject;

public class StrategyCompositeOrdering implements RideRequestOrderingStrategy {

    @Override
    public int compare(RideRequest a, RideRequest b) {
        // 1) Priority: smaller number = higher priority
        int byPriority = Integer.compare(a.getPriorityLevel(), b.getPriorityLevel());
        if (byPriority != 0) {
            return byPriority;
        }

        // 2) Time: earlier request first
        int byTime = a.getRequestTimestamp().compareTo(b.getRequestTimestamp());
        if (byTime != 0) {
            return byTime;
        }

        // 3) Distance: larger distance first
        return Double.compare(b.getAnticipatedDistance(), a.getAnticipatedDistance());
    }
}

