package finalProject;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaxiAgentAdapterTest {

    @Test
    void manualOrderShouldSubmitRequestToDispatchQueueWhenSubmitterIsConfigured() {
        DriverCacheService driverCache = new DriverCacheService();
        OrderIndexService orderIndex = new OrderIndexService();
        List<RideRequest> submitted = new ArrayList<>();
        TaxiAgentAdapter adapter = new TaxiAgentAdapter(driverCache, orderIndex, submitted::add);

        String result = adapter.manualOrder("UW", "NEU", 120.0, "EXPRESS_PICKUP");

        assertEquals(1, submitted.size());
        RideRequest request = submitted.get(0);
        assertEquals("UW", request.getStartLocation());
        assertEquals("NEU", request.getDestination());
        assertEquals(120.0, request.getAnticipatedDistance());
        assertEquals(RideType.EXPRESS_PICKUP, request.getRideType());
        assertTrue(result.contains("已进入派单队列"));
    }
}
