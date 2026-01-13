package finalProject;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CityMapTest {

    @Test
    void testGetDistance_KnownLocations() {
        // 测试已知的距离
        assertEquals(120.0, CityMap.getDistance("UW", "NEU"));
        assertEquals(150.0, CityMap.getDistance("UW", "SpaceNeedle"));
        assertEquals(400.0, CityMap.getDistance("UW", "Airport"));
        assertEquals(60.0, CityMap.getDistance("SpaceNeedle", "SLU"));
    }

    @Test
    void testGetDistance_Symmetry() {
        // 测试距离的对称性：A到B的距离应该等于B到A的距离
        assertEquals(
                CityMap.getDistance("UW", "NEU"),
                CityMap.getDistance("NEU", "UW")
        );

        assertEquals(
                CityMap.getDistance("Bellevue", "Airport"),
                CityMap.getDistance("Airport", "Bellevue")
        );

        assertEquals(
                CityMap.getDistance("SLU", "SpaceNeedle"),
                CityMap.getDistance("SpaceNeedle", "SLU")
        );
    }

    @Test
    void testGetAllLocations_ShouldReturnAllSixLocations() {
        List<String> locations = CityMap.getAllLocations();

        assertEquals(6, locations.size());
        assertTrue(locations.contains("UW"));
        assertTrue(locations.contains("NEU"));
        assertTrue(locations.contains("SpaceNeedle"));
        assertTrue(locations.contains("SLU"));
        assertTrue(locations.contains("Bellevue"));
        assertTrue(locations.contains("Airport"));
    }

    @Test
    void testGetAllLocations_ShouldReturnNewList() {
        // 验证每次返回的是新的List，不会互相影响
        List<String> list1 = CityMap.getAllLocations();
        List<String> list2 = CityMap.getAllLocations();

        assertNotSame(list1, list2);  // 不是同一个对象
        assertEquals(list1, list2);    // 但内容相同
    }

    @Test
    void testGetDistance_AllPairsArePositive() {
        // 测试所有距离都是正数
        List<String> locations = CityMap.getAllLocations();

        for (String from : locations) {
            for (String to : locations) {
                if (!from.equals(to)) {
                    double distance = CityMap.getDistance(from, to);
                    assertTrue(distance > 0,
                            "Distance from " + from + " to " + to + " should be positive");
                }
            }
        }
    }

    @Test
    void testGetDistance_ShortestPath() {
        // 验证最短的距离
        double shortestDistance = Double.MAX_VALUE;
        String shortestPair = "";

        List<String> locations = CityMap.getAllLocations();
        for (String from : locations) {
            for (String to : locations) {
                if (!from.equals(to)) {
                    double distance = CityMap.getDistance(from, to);
                    if (distance < shortestDistance) {
                        shortestDistance = distance;
                        shortestPair = from + " to " + to;
                    }
                }
            }
        }

        // SpaceNeedle 到 SLU 应该是最短的（60.0）
        assertEquals(60.0, shortestDistance);
        assertTrue(shortestPair.contains("SpaceNeedle") && shortestPair.contains("SLU"));
    }

    @Test
    void testGetDistance_LongestPath() {
        // 验证最长的距离
        double longestDistance = 0;
        String longestPair = "";

        List<String> locations = CityMap.getAllLocations();
        for (String from : locations) {
            for (String to : locations) {
                if (!from.equals(to)) {
                    double distance = CityMap.getDistance(from, to);
                    if (distance > longestDistance) {
                        longestDistance = distance;
                        longestPair = from + " to " + to;
                    }
                }
            }
        }

        // Bellevue 到 Airport 应该是最长的（550.0）
        assertEquals(550.0, longestDistance);
        assertTrue(longestPair.contains("Bellevue") && longestPair.contains("Airport"));
    }

    @Test
    void testGetDistance_SpecificRoutes() {
        // 测试特定的重要路线
        assertEquals(80.0, CityMap.getDistance("NEU", "SLU"));
        assertEquals(280.0, CityMap.getDistance("NEU", "Bellevue"));
        assertEquals(240.0, CityMap.getDistance("SLU", "Bellevue"));
        assertEquals(550.0, CityMap.getDistance("Bellevue", "Airport"));
    }
}
