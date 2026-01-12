package finalProjectTest;

import org.junit.jupiter.api.Test;

import finalProject.CityMap;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CityMapTest {

    @Test
    void getDistance_returnsExpectedValues() {
        assertEquals(120.0, CityMap.getDistance("UW", "NEU"), 0.0001);
        assertEquals(150.0, CityMap.getDistance("UW", "SpaceNeedle"), 0.0001);
        assertEquals(100.0, CityMap.getDistance("UW", "SLU"), 0.0001);
        assertEquals(250.0, CityMap.getDistance("UW", "Bellevue"), 0.0001);
        assertEquals(400.0, CityMap.getDistance("UW", "Airport"), 0.0001);

        assertEquals(80.0, CityMap.getDistance("NEU", "SLU"), 0.0001);
        assertEquals(550.0, CityMap.getDistance("Airport", "Bellevue"), 0.0001);
    }

    @Test
    void getDistance_isSymmetricForKnownPairs() {
        assertEquals(CityMap.getDistance("UW", "NEU"), CityMap.getDistance("NEU", "UW"), 0.0001);
        assertEquals(CityMap.getDistance("SLU", "SpaceNeedle"), CityMap.getDistance("SpaceNeedle", "SLU"), 0.0001);
        assertEquals(CityMap.getDistance("Bellevue", "Airport"), CityMap.getDistance("Airport", "Bellevue"), 0.0001);
    }

    @Test
    void getAllLocations_returnsAllExpectedLocations() {
        List<String> locations = CityMap.getAllLocations();

        Set<String> actual = new HashSet<>(locations);
        Set<String> expected = Set.of("UW", "NEU", "SpaceNeedle", "SLU", "Bellevue", "Airport");

        assertEquals(expected, actual);
        assertEquals(6, locations.size());
    }

    @Test
    void getAllLocations_returnsIndependentList() {
        List<String> first = CityMap.getAllLocations();
        assertTrue(first.contains("UW"));

        first.remove("UW");

        List<String> second = CityMap.getAllLocations();
        assertTrue(second.contains("UW"));
        assertEquals(6, new HashSet<>(second).size());
    }

    @Test
    void getDistance_throwsForUnknownFromOrTo() {
        assertThrows(NullPointerException.class, () -> CityMap.getDistance("Unknown", "UW"));
        assertThrows(NullPointerException.class, () -> CityMap.getDistance("UW", "Unknown"));
    }
}
