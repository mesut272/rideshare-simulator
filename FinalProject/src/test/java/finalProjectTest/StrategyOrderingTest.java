package finalProjectTest;

import finalProject.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class StrategyOrderingTest {

    private RideRequest req(String id,
                            double distance,
                            LocalDateTime requestTime,
                            LocalDateTime finishTime,
                            RideType type) {
        return new RideRequest(
                id,
                "UW",
                "NEU",
                distance,
                requestTime,
                finishTime,
                type
        );
    }

    @Test
    void timeOrdering_comparesByRequestTimestamp() {
        Comparator<RideRequest> cmp = new StrategyTimeOrdering();

        LocalDateTime t1 = LocalDateTime.now();
        LocalDateTime t2 = t1.plusSeconds(5);

        RideRequest early = req("A", 100, t1, t1.plusSeconds(10), RideType.STANDARD_PICKUP);
        RideRequest late  = req("B", 100, t2, t2.plusSeconds(10), RideType.STANDARD_PICKUP);

        assertTrue(cmp.compare(early, late) < 0, "earlier requestTimestamp should come first");
        assertTrue(cmp.compare(late, early) > 0, "later requestTimestamp should come after");
        assertEquals(0, cmp.compare(early, early));
    }

    @Test
    void priorityOrdering_comparesByDerivedPriorityLevelFromRideType() {
        Comparator<RideRequest> cmp = new StrategyPriorityLevelOrdering();

        LocalDateTime t = LocalDateTime.now();

        RideRequest express = req("E", 100, t, t.plusSeconds(10), RideType.EXPRESS_PICKUP);
        RideRequest waitSave = req("W", 100, t, t.plusSeconds(10), RideType.WAIT_AND_SAVE_PICKUP);

        assertEquals(1, express.getPriorityLevel());
        assertEquals(3, waitSave.getPriorityLevel());

        assertTrue(cmp.compare(express, waitSave) < 0, "priority 1 should be ordered before priority 3");
        assertTrue(cmp.compare(waitSave, express) > 0, "priority 3 should be ordered after priority 1");
        assertEquals(0, cmp.compare(express, express));
    }

    @Test
    void distanceOrdering_sortsByAnticipatedDistanceDescending() {
        Comparator<RideRequest> cmp = new StrategyDistanceOrdering();

        LocalDateTime t = LocalDateTime.now();

        RideRequest shortRide = req("S", 50,  t, t.plusSeconds(10), RideType.STANDARD_PICKUP);
        RideRequest longRide  = req("L", 200, t, t.plusSeconds(10), RideType.STANDARD_PICKUP);

        assertTrue(cmp.compare(longRide, shortRide) < 0, "longer distance should be ordered before shorter distance");
        assertTrue(cmp.compare(shortRide, longRide) > 0, "shorter distance should be ordered after longer distance");
        assertEquals(0, cmp.compare(shortRide, shortRide));
    }
}
