package finalProject;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SupervisorChatServer {
    private static final Gson GSON = new Gson();

    private final TaxiAgentInterface agent;
    private final Supplier<DriverCacheService> driverCacheSupplier;
    private final SimulationControl simulationControl;
    private final HttpServer server;
    private final ExecutorService executor;

    public SupervisorChatServer(int port, TaxiAgentInterface agent, DriverCacheService driverCache) throws IOException {
        this(port, agent, driverCache, null);
    }

    public SupervisorChatServer(int port, TaxiAgentInterface agent, DriverCacheService driverCache,
                                SimulationControl simulationControl) throws IOException {
        this(port, agent, () -> driverCache, simulationControl);
    }

    public SupervisorChatServer(int port, TaxiAgentInterface agent, Supplier<DriverCacheService> driverCacheSupplier,
                                SimulationControl simulationControl) throws IOException {
        this.agent = agent;
        this.driverCacheSupplier = driverCacheSupplier;
        this.simulationControl = simulationControl;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.setExecutor(executor);
        this.server.createContext("/api/chat", this::handleChat);
        this.server.createContext("/api/drivers", this::handleDrivers);
        this.server.createContext("/api/map", this::handleMap);
        this.server.createContext("/api/queue", this::handleQueue);
        this.server.createContext("/api/orders", this::handleOrders);
        this.server.createContext("/api/simulation/start", this::handleSimulationStart);
        this.server.createContext("/api/simulation/status", this::handleSimulationStatus);
        this.server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only POST is supported."));
            return;
        }

        String message;
        try {
            JsonObject payload = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            message = payload.has("message") ? payload.get("message").getAsString().trim() : "";
        } catch (RuntimeException e) {
            sendJson(exchange, 400, error("Invalid chat request."));
            return;
        }

        if (message.isEmpty()) {
            sendJson(exchange, 400, error("Message cannot be empty."));
            return;
        }

        try {
            String reply = agent.chat(message);
            JsonObject response = new JsonObject();
            response.addProperty("reply", reply);
            sendJson(exchange, 200, response);
        } catch (RuntimeException e) {
            sendJson(exchange, 500, error("Agent request failed."));
        }
    }

    private void handleDrivers(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only GET is supported."));
            return;
        }

        JsonArray drivers = new JsonArray();
        int availableCount = 0;
        for (Driver driver : driverCacheSupplier.get().getAllDrivers()) {
            JsonObject item = new JsonObject();
            item.addProperty("driverId", driver.getDriverId());
            item.addProperty("currentLocation", driver.getCurrentLocation());
            item.addProperty("available", driver.isAvailable());
            drivers.add(item);
            if (driver.isAvailable()) {
                availableCount++;
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("total", drivers.size());
        response.addProperty("available", availableCount);
        response.add("drivers", drivers);
        sendJson(exchange, 200, response);
    }

    private void handleMap(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only GET is supported."));
            return;
        }
        if (simulationControl == null) {
            sendJson(exchange, 503, error("Simulation control is not available."));
            return;
        }

        sendJson(exchange, 200, mapSnapshotJson(simulationControl.mapSnapshot()));
    }

    private void handleQueue(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only GET is supported."));
            return;
        }
        if (simulationControl == null) {
            sendJson(exchange, 503, error("Simulation control is not available."));
            return;
        }

        String location = queryParam(exchange, "location");
        if (location == null || !CityMap.getAllLocations().contains(location)) {
            sendJson(exchange, 400, error("Valid location is required."));
            return;
        }

        sendJson(exchange, 200, queueSnapshotJson(simulationControl.queueSnapshot(location)));
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only POST is supported."));
            return;
        }
        if (simulationControl == null) {
            sendJson(exchange, 503, error("Simulation control is not available."));
            return;
        }

        try {
            JsonObject payload = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String customerId = requiredString(payload, "customerId");
            String startLocation = requiredString(payload, "startLocation");
            String destination = requiredString(payload, "destination");
            RideType rideType = RideType.valueOf(requiredString(payload, "rideType"));

            if (!CityMap.getAllLocations().contains(startLocation) || !CityMap.getAllLocations().contains(destination)) {
                sendJson(exchange, 400, error("Start and destination must be valid map locations."));
                return;
            }
            if (startLocation.equals(destination)) {
                sendJson(exchange, 400, error("Start and destination cannot be the same."));
                return;
            }

            RideRequest request = simulationControl.submitPassengerOrder(customerId, startLocation, destination, rideType);
            JsonObject response = new JsonObject();
            response.addProperty("created", true);
            response.addProperty("customerId", request.getCustomerId());
            response.addProperty("startLocation", request.getStartLocation());
            response.addProperty("destination", request.getDestination());
            response.addProperty("rideType", request.getRideType().name());
            response.addProperty("anticipatedDistance", request.getAnticipatedDistance());
            sendJson(exchange, 200, response);
        } catch (RuntimeException e) {
            sendJson(exchange, 400, error("Invalid order request."));
        }
    }

    private void handleSimulationStart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only POST is supported."));
            return;
        }
        if (simulationControl == null) {
            sendJson(exchange, 503, error("Simulation control is not available."));
            return;
        }

        sendJson(exchange, 200, simulationStartJson(simulationControl.start()));
    }

    private void handleSimulationStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("Only GET is supported."));
            return;
        }
        if (simulationControl == null) {
            sendJson(exchange, 503, error("Simulation control is not available."));
            return;
        }

        sendJson(exchange, 200, simulationSnapshotJson(simulationControl.snapshot()));
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Only GET is supported.", "text/plain; charset=utf-8");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        Map<String, String> resources = Map.of(
                "/", "supervisor/index.html",
                "/styles.css", "supervisor/styles.css",
                "/mapGeometry.js", "supervisor/mapGeometry.js",
                "/app.js", "supervisor/app.js"
        );
        String resourcePath = resources.get(requestPath);
        if (resourcePath == null) {
            sendText(exchange, 404, "Not found.", "text/plain; charset=utf-8");
            return;
        }

        try (InputStream input = SupervisorChatServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                sendText(exchange, 404, "Not found.", "text/plain; charset=utf-8");
                return;
            }
            byte[] body = input.readAllBytes();
            sendBytes(exchange, 200, body, contentTypeFor(requestPath));
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String requiredString(JsonObject payload, String field) {
        if (!payload.has(field)) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        String value = payload.get(field).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty field: " + field);
        }
        return value;
    }

    private JsonObject error(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("error", message);
        return response;
    }

    private JsonObject simulationStartJson(SimulationControl.StartResult result) {
        JsonObject response = simulationSnapshotJson(result);
        response.addProperty("alreadyStarted", result.isAlreadyStarted());
        return response;
    }

    private JsonObject simulationSnapshotJson(SimulationControl.Snapshot snapshot) {
        JsonObject response = new JsonObject();
        response.addProperty("started", snapshot.isStarted());
        response.addProperty("running", snapshot.isRunning());
        response.addProperty("waitingQueueSize", snapshot.getWaitingQueueSize());
        response.addProperty("completedCount", snapshot.getCompletedCount());
        return response;
    }

    private JsonObject mapSnapshotJson(SimulationMapSnapshot snapshot) {
        JsonObject response = new JsonObject();
        response.addProperty("started", snapshot.isStarted());
        response.addProperty("running", snapshot.isRunning());
        response.addProperty("waitingQueueSize", snapshot.getWaitingQueueSize());
        response.addProperty("completedCount", snapshot.getCompletedCount());

        JsonObject queueByLocation = new JsonObject();
        snapshot.getQueueByLocation().forEach(queueByLocation::addProperty);
        response.add("queueByLocation", queueByLocation);

        JsonArray activeTrips = new JsonArray();
        for (SimulationMapSnapshot.ActiveTripSnapshot trip : snapshot.getActiveTrips()) {
            JsonObject item = new JsonObject();
            item.addProperty("driverId", trip.getDriverId());
            item.addProperty("customerId", trip.getCustomerId());
            item.addProperty("startLocation", trip.getStartLocation());
            item.addProperty("destination", trip.getDestination());
            item.addProperty("rideType", trip.getRideType());
            item.addProperty("anticipatedDistance", trip.getAnticipatedDistance());
            item.addProperty("progressPercent", trip.getProgressPercent());
            item.addProperty("remainingSeconds", trip.getRemainingSeconds());
            activeTrips.add(item);
        }
        response.add("activeTrips", activeTrips);
        return response;
    }

    private JsonObject queueSnapshotJson(QueueSnapshot snapshot) {
        JsonObject response = new JsonObject();
        response.addProperty("location", snapshot.getLocation());
        response.addProperty("total", snapshot.getTotal());

        JsonArray passengers = new JsonArray();
        for (QueueSnapshot.PassengerSnapshot passenger : snapshot.getPassengers()) {
            JsonObject item = new JsonObject();
            item.addProperty("customerId", passenger.getCustomerId());
            item.addProperty("startLocation", passenger.getStartLocation());
            item.addProperty("destination", passenger.getDestination());
            item.addProperty("rideType", passenger.getRideType());
            item.addProperty("anticipatedDistance", passenger.getAnticipatedDistance());
            item.addProperty("waitingSeconds", passenger.getWaitingSeconds());
            passengers.add(item);
        }
        response.add("passengers", passengers);
        return response;
    }

    private void sendJson(HttpExchange exchange, int statusCode, JsonObject payload) throws IOException {
        byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, statusCode, body, "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int statusCode, String text, String contentType) throws IOException {
        sendBytes(exchange, statusCode, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void sendBytes(HttpExchange exchange, int statusCode, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private String contentTypeFor(String requestPath) {
        if (requestPath.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (requestPath.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }
}
