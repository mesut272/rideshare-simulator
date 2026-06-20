package finalProject;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    @Test
    void simulationShouldFinishWithoutManualStopWhenAllRequestsComplete() throws InterruptedException {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(2, 1L, 1));

        engine.start();
        boolean finished = engine.awaitCompletion(45, TimeUnit.SECONDS);

        assertTrue(finished, "simulation should finish within timeout");
        assertEquals(1, engine.getCompletedCount());
        assertFalse(engine.isRunning());
    }

    @Test
    void submitManualOrderShouldAddRequestToWaitingQueue() {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1L, 0));
        RideRequest request = new RideRequest(
                "ManualCustomer",
                "UW",
                "NEU",
                120.0,
                java.time.LocalDateTime.now(),
                RideType.EXPRESS_PICKUP
        );

        engine.submitManualOrder(request);

        assertEquals(1, engine.getWaitingQueueSize());
        engine.stop();
    }

    @Test
    void mapSnapshotCountsWaitingRequestsByStartLocation() {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1L, 0));
        engine.submitManualOrder(new RideRequest(
                "Manual-1", "UW", "NEU", 120.0, LocalDateTime.now(), RideType.EXPRESS_PICKUP));
        engine.submitManualOrder(new RideRequest(
                "Manual-2", "UW", "SLU", 100.0, LocalDateTime.now(), RideType.STANDARD_PICKUP));
        engine.submitManualOrder(new RideRequest(
                "Manual-3", "Airport", "Bellevue", 550.0, LocalDateTime.now(), RideType.STANDARD_PICKUP));

        SimulationMapSnapshot snapshot = engine.getMapSnapshot(false);

        assertEquals(3, snapshot.getWaitingQueueSize());
        assertEquals(2, snapshot.getQueueByLocation().get("UW"));
        assertEquals(1, snapshot.getQueueByLocation().get("Airport"));
        assertEquals(0, snapshot.getActiveTrips().size());
        engine.stop();
    }

    @Test
    void mapSnapshotIncludesActiveTripRouteAndProgress() throws InterruptedException {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1L, 0));
        engine.submitManualOrder(new RideRequest(
                "Manual-1", "UW", "NEU", 120.0, LocalDateTime.now(), RideType.EXPRESS_PICKUP));

        engine.start();
        Thread.sleep(150L);

        SimulationMapSnapshot snapshot = engine.getMapSnapshot(true);

        assertEquals(1, snapshot.getActiveTrips().size());
        SimulationMapSnapshot.ActiveTripSnapshot trip = snapshot.getActiveTrips().get(0);
        assertEquals("UW", trip.getStartLocation());
        assertEquals("NEU", trip.getDestination());
        assertTrue(trip.getProgressPercent() >= 0.0);
        assertTrue(trip.getProgressPercent() <= 100.0);
        assertTrue(trip.getRemainingSeconds() >= 0);
        engine.stop();
    }

    @Test
    void dispatchedRideUsesSlowerRideDuration() throws InterruptedException {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1L, 0));
        RideRequest request = new RideRequest(
                "Manual-1", "UW", "NEU", 120.0, LocalDateTime.now(), RideType.EXPRESS_PICKUP);
        engine.submitManualOrder(request);

        engine.start();
        long deadline = System.currentTimeMillis() + 1000L;
        while (request.getExpectedCompletionTime() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }

        assertNotNull(request.getExpectedCompletionTime(), "request should be dispatched");
        long seconds = java.time.Duration.between(
                request.getActualStartTime(), request.getExpectedCompletionTime()).getSeconds();
        assertEquals(6L, seconds);
        engine.stop();
    }

    @Test
    void queueSnapshotReturnsPassengersWaitingAtLocation() {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1L, 0));
        engine.submitManualOrder(new RideRequest(
                "Alice", "UW", "NEU", 120.0, LocalDateTime.now().minusSeconds(5), RideType.EXPRESS_PICKUP));
        engine.submitManualOrder(new RideRequest(
                "Bob", "Airport", "Bellevue", 550.0, LocalDateTime.now(), RideType.STANDARD_PICKUP));

        QueueSnapshot snapshot = engine.getQueueSnapshot("UW");

        assertEquals("UW", snapshot.getLocation());
        assertEquals(1, snapshot.getTotal());
        assertEquals("Alice", snapshot.getPassengers().get(0).getCustomerId());
        assertEquals("UW", snapshot.getPassengers().get(0).getStartLocation());
        assertEquals("NEU", snapshot.getPassengers().get(0).getDestination());
        assertEquals("EXPRESS_PICKUP", snapshot.getPassengers().get(0).getRideType());
        assertTrue(snapshot.getPassengers().get(0).getWaitingSeconds() >= 0);
        engine.stop();
    }
}
