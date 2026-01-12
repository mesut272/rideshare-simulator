package finalProjectTest;

import finalProject.RideType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RideTypeTest {

    @Test
    void enum_containsExpectedValues() {
        RideType[] values = RideType.values();

        assertTrue(values.length > 0);
    }

    @Test
    void valueOf_returnsCorrectEnum() {
        for (RideType type : RideType.values()) {
            assertEquals(type, RideType.valueOf(type.name()));
        }
    }

    @Test
    void enum_values_areUnique() {
        RideType[] values = RideType.values();

        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotEquals(values[i], values[j]);
            }
        }
    }
}
