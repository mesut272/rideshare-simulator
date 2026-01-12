package finalProjectTest;

import finalProject.Name;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameTest {

    @Test
    void randomName_returnsNonNullNonEmptyString() {
        String n = Name.randomName();
        assertNotNull(n);
        assertFalse(n.isBlank());
    }

    @Test
    void randomName_returnsOneOfTheNamesInList() throws Exception {
        Field f = Name.class.getDeclaredField("names");
        f.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) f.get(null);

        assertNotNull(names);
        assertFalse(names.isEmpty());
        for (int i = 0; i < 200; i++) {
            String n = Name.randomName();
            assertTrue(names.contains(n), "randomName returned a value not in names list: " + n);
        }
    }

    @Test
    void randomName_canProduceDifferentValues_overManyCalls() {
        String first = Name.randomName();
        boolean sawDifferent = false;

        for (int i = 0; i < 500; i++) {
            if (!Name.randomName().equals(first)) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent, "Expected at least one different name over many calls");
    }
}
