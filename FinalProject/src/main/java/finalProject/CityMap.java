package finalProject;

import java.util.*;

public class CityMap {

    private static final Map<String, Map<String, Double>> distances = new HashMap<>();

    // The distance from each place to another is shrink into smaller amount,
    // so that it would be easier for simulation and testing.
    // Can be changed to real distance between two locations.
    static {
        distances.put("UW", Map.of(
                "NEU", 120.0,
                "SpaceNeedle", 150.0,
                "SLU", 100.0,
                "Bellevue", 250.0,
                "Airport", 400.0
        ));

        distances.put("NEU", Map.of(
                "UW", 120.0,
                "SpaceNeedle", 180.0,
                "SLU", 80.0,
                "Bellevue", 280.0,
                "Airport", 420.0
        ));

        distances.put("SpaceNeedle", Map.of(
                "UW", 150.0,
                "NEU", 180.0,
                "SLU", 60.0,
                "Bellevue", 260.0,
                "Airport", 450.0
        ));

        distances.put("SLU", Map.of(
                "UW", 100.0,
                "NEU", 80.0,
                "SpaceNeedle", 60.0,
                "Bellevue", 240.0,
                "Airport", 430.0
        ));

        distances.put("Bellevue", Map.of(
                "UW", 250.0,
                "NEU", 280.0,
                "SpaceNeedle", 260.0,
                "SLU", 240.0,
                "Airport", 550.0
        ));

        distances.put("Airport", Map.of(
                "UW", 400.0,
                "NEU", 420.0,
                "SpaceNeedle", 450.0,
                "SLU", 430.0,
                "Bellevue", 550.0
        ));
    }

    // return the distance between two given locations
    public static double getDistance(String from, String to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Locations must not be null");
        }
        if (from.equals(to)) {
            return 0.0;
        }
        Map<String, Double> routes = distances.get(from);
        if (routes == null || !routes.containsKey(to)) {
            throw new IllegalArgumentException("Unknown route: " + from + " -> " + to);
        }
        return routes.get(to);
    }

    // return a list containing all locations exist in the CityMap
    public static List<String> getAllLocations() {
        return new ArrayList<>(distances.keySet());
    }
}
