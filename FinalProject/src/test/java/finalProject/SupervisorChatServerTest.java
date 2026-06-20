package finalProject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorChatServerTest {
    private SupervisorChatServer server;

    @BeforeEach
    void clearDriverCache() {
        new DriverCacheService().clearForTesting();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void chatApiForwardsMessageToAgentAndReturnsJsonReply() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        server = new SupervisorChatServer(0, message -> "收到: " + message, driverCache);
        server.start();

        HttpResponse<String> response = postJson("/api/chat", "{\"message\":\"查看所有司机\"}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"reply\":\"收到: 查看所有司机\""));
    }

    @Test
    void driversApiReturnsDriverSnapshotAndAvailabilityCounts() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        driverCache.initDriver(new Driver("Emma", true, "UW"));
        driverCache.initDriver(new Driver("Jack", false, "Bellevue"));
        server = new SupervisorChatServer(0, message -> message, driverCache);
        server.start();

        HttpResponse<String> response = get("/api/drivers");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"total\":2"));
        assertTrue(response.body().contains("\"available\":1"));
        assertTrue(response.body().contains("\"driverId\":\"Emma\""));
        assertTrue(response.body().contains("\"currentLocation\":\"UW\""));
        assertTrue(response.body().contains("\"available\":true"));
        assertTrue(response.body().contains("\"driverId\":\"Jack\""));
        assertTrue(response.body().contains("\"currentLocation\":\"Bellevue\""));
        assertTrue(response.body().contains("\"available\":false"));
    }

    @Test
    void rootServesSupervisorConsoleHtml() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        server = new SupervisorChatServer(0, message -> message, driverCache);
        server.start();

        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("监管 Agent"));
        assertTrue(response.body().contains("实时派单地图"));
        assertTrue(response.body().contains("启动派单模拟"));
        assertTrue(response.body().contains("app.js"));
        assertTrue(response.body().contains("styles.css"));
    }

    @Test
    void chatApiRejectsEmptyMessages() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        server = new SupervisorChatServer(0, message -> message, driverCache);
        server.start();

        HttpResponse<String> response = postJson("/api/chat", "{\"message\":\"   \"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Message cannot be empty."));
    }

    @Test
    void chatApiReturnsServerErrorWhenAgentFails() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        server = new SupervisorChatServer(0, message -> {
            throw new IllegalStateException("model unavailable");
        }, driverCache);
        server.start();

        HttpResponse<String> response = postJson("/api/chat", "{\"message\":\"查看所有司机\"}");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("Agent request failed."));
    }

    @Test
    void simulationStartApiStartsControlOnceAndReturnsStatus() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        FakeSimulationControl simulation = new FakeSimulationControl();
        server = new SupervisorChatServer(0, message -> message, driverCache, simulation);
        server.start();

        HttpResponse<String> first = postJson("/api/simulation/start", "{}");
        HttpResponse<String> second = postJson("/api/simulation/start", "{}");

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());
        assertEquals(1, simulation.startCalls.get());
        assertTrue(first.body().contains("\"started\":true"));
        assertTrue(first.body().contains("\"running\":true"));
        assertTrue(second.body().contains("\"alreadyStarted\":true"));
    }

    @Test
    void simulationStatusApiReturnsQueueAndCompletionState() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        FakeSimulationControl simulation = new FakeSimulationControl();
        simulation.waitingQueueSize = 7;
        simulation.completedCount = 3;
        server = new SupervisorChatServer(0, message -> message, driverCache, simulation);
        server.start();

        HttpResponse<String> response = get("/api/simulation/status");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"started\":false"));
        assertTrue(response.body().contains("\"running\":false"));
        assertTrue(response.body().contains("\"waitingQueueSize\":7"));
        assertTrue(response.body().contains("\"completedCount\":3"));
    }

    @Test
    void mapApiReturnsLiveDispatchSnapshot() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        FakeSimulationControl simulation = new FakeSimulationControl();
        simulation.mapSnapshot = new SimulationMapSnapshot(
                true,
                true,
                3,
                4,
                Map.of("UW", 2, "Airport", 1),
                List.of(new SimulationMapSnapshot.ActiveTripSnapshot(
                        "Emma", "Alice", "UW", "NEU", "EXPRESS_PICKUP", 120.0, 42.5, 12
                ))
        );
        server = new SupervisorChatServer(0, message -> message, driverCache, simulation);
        server.start();

        HttpResponse<String> response = get("/api/map");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"waitingQueueSize\":3"));
        assertTrue(response.body().contains("\"completedCount\":4"));
        assertTrue(response.body().contains("\"queueByLocation\""));
        assertTrue(response.body().contains("\"UW\":2"));
        assertTrue(response.body().contains("\"driverId\":\"Emma\""));
        assertTrue(response.body().contains("\"customerId\":\"Alice\""));
        assertTrue(response.body().contains("\"startLocation\":\"UW\""));
        assertTrue(response.body().contains("\"destination\":\"NEU\""));
        assertTrue(response.body().contains("\"anticipatedDistance\":120.0"));
        assertTrue(response.body().contains("\"progressPercent\":42.5"));
    }

    @Test
    void queueApiReturnsPassengersForLocation() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        FakeSimulationControl simulation = new FakeSimulationControl();
        simulation.queueSnapshot = new QueueSnapshot("UW", List.of(
                new QueueSnapshot.PassengerSnapshot("Alice", "UW", "NEU", "EXPRESS_PICKUP", 120.0, 8)
        ));
        server = new SupervisorChatServer(0, message -> message, driverCache, simulation);
        server.start();

        HttpResponse<String> response = get("/api/queue?location=UW");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"location\":\"UW\""));
        assertTrue(response.body().contains("\"total\":1"));
        assertTrue(response.body().contains("\"customerId\":\"Alice\""));
        assertTrue(response.body().contains("\"destination\":\"NEU\""));
    }

    @Test
    void orderApiAddsPassengerOrderToSimulation() throws Exception {
        DriverCacheService driverCache = new DriverCacheService();
        FakeSimulationControl simulation = new FakeSimulationControl();
        server = new SupervisorChatServer(0, message -> message, driverCache, simulation);
        server.start();

        HttpResponse<String> response = postJson("/api/orders",
                "{\"customerId\":\"Alice\",\"startLocation\":\"UW\",\"destination\":\"NEU\",\"rideType\":\"EXPRESS_PICKUP\"}");

        assertEquals(200, response.statusCode());
        assertEquals("Alice", simulation.lastOrder.getCustomerId());
        assertEquals("UW", simulation.lastOrder.getStartLocation());
        assertEquals("NEU", simulation.lastOrder.getDestination());
        assertEquals(RideType.EXPRESS_PICKUP, simulation.lastOrder.getRideType());
        assertTrue(response.body().contains("\"created\":true"));
        assertTrue(response.body().contains("\"customerId\":\"Alice\""));
    }

    @Test
    void engineSimulationControlReportsNotRunningBeforeStart() {
        SimulationEngine engine = new SimulationEngine(new SimulationConfig(1, 1, 0));
        SimulationControl control = SimulationControl.forEngine(engine);

        SimulationControl.Snapshot snapshot = control.snapshot();

        assertTrue(snapshot.isStarted() == false);
        assertTrue(snapshot.isRunning() == false);
        engine.stop();
    }

    @Test
    void simulationRuntimeStartsFreshRoundAfterPreviousRoundCompletes() throws Exception {
        SimulationRuntime runtime = new SimulationRuntime(
                new SimulationConfig(1, 1, 0),
                engine -> message -> "drivers=" + engine.getDriverCache().getAllDriverIds().size()
        );

        SimulationControl.StartResult first = runtime.start();
        waitUntilStopped(runtime);
        SimulationControl.StartResult second = runtime.start();

        assertTrue(first.isStarted());
        assertTrue(second.isStarted());
        assertTrue(second.isAlreadyStarted() == false);
        assertEquals("drivers=1", runtime.chat("status"));
        runtime.stop();
    }

    @Test
    void simulationRuntimeDispatchesManualOrderAfterPreviousRoundCompletes() throws Exception {
        SimulationRuntime runtime = new SimulationRuntime(
                new SimulationConfig(1, 1, 0),
                engine -> message -> "ok"
        );

        runtime.start();
        waitUntilStopped(runtime);
        Thread.sleep(300L);

        runtime.submitPassengerOrder("ManualAfterStop", "SpaceNeedle", "SLU", RideType.EXPRESS_PICKUP);

        waitUntilActiveTrip(runtime);
        SimulationMapSnapshot snapshot = runtime.mapSnapshot();

        assertEquals(1, snapshot.getActiveTrips().size());
        assertEquals("SpaceNeedle", snapshot.getActiveTrips().get(0).getStartLocation());
        assertEquals("SLU", snapshot.getActiveTrips().get(0).getDestination());
        runtime.stop();
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getPort() + path);
    }

    private void waitUntilStopped(SimulationControl control) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (control.snapshot().isStarted() && !control.snapshot().isRunning()) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("simulation did not stop in time");
    }

    private void waitUntilActiveTrip(SimulationControl control) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (!control.mapSnapshot().getActiveTrips().isEmpty()) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("manual order did not become an active trip");
    }

    private static class FakeSimulationControl implements SimulationControl {
        private final AtomicInteger startCalls = new AtomicInteger(0);
        private boolean started = false;
        private boolean running = false;
        private int waitingQueueSize = 0;
        private int completedCount = 0;
        private RideRequest lastOrder = null;
        private SimulationMapSnapshot mapSnapshot = new SimulationMapSnapshot(
                false, false, 0, 0, Map.of(), List.of());
        private QueueSnapshot queueSnapshot = new QueueSnapshot("UW", List.of());

        @Override
        public synchronized StartResult start() {
            if (started) {
                return new StartResult(true, true, running, waitingQueueSize, completedCount);
            }
            startCalls.incrementAndGet();
            started = true;
            running = true;
            return new StartResult(true, false, running, waitingQueueSize, completedCount);
        }

        @Override
        public synchronized Snapshot snapshot() {
            return new Snapshot(started, running, waitingQueueSize, completedCount);
        }

        @Override
        public synchronized SimulationMapSnapshot mapSnapshot() {
            return mapSnapshot;
        }

        @Override
        public synchronized QueueSnapshot queueSnapshot(String location) {
            return queueSnapshot;
        }

        @Override
        public synchronized RideRequest submitPassengerOrder(String customerId, String startLocation,
                                                             String destination, RideType rideType) {
            lastOrder = new RideRequest(customerId, startLocation, destination,
                    CityMap.getDistance(startLocation, destination), java.time.LocalDateTime.now(), rideType);
            return lastOrder;
        }
    }
}
