package finalProjectTest;

import finalProject.Driver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverTest {

    @Test
    void constructor_setsFields() {
        Driver d = new Driver("D1", true);
        assertEquals("D1", d.getDriverId());
        assertTrue(d.isAvailable());
    }

    @Test
    void assignRide_makesDriverUnavailable() {
        Driver d = new Driver("D2", true);

        d.assignRide(null);

        assertFalse(d.isAvailable());
    }

    @Test
    void finishRide_makesDriverAvailable() {
        Driver d = new Driver("D3", false);

        d.finishRide();

        assertTrue(d.isAvailable());
    }

    @Test
    void toString_containsIdAndAvailability() {
        Driver d = new Driver("D4", true);
        String s = d.toString();

        assertTrue(s.contains("D4"));
        assertTrue(s.contains("true"));
    }
}
