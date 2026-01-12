package finalProjectTest;

import finalProject.RideRequest;
import finalProject.RideRequestGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RideRequestGeneratorTest {

    @Test
    void generateRequest_returnsNonNullRequest() {
        RideRequestGenerator gen = new RideRequestGenerator();
        RideRequest req = gen.generateRequest();

        assertNotNull(req);
    }

    @Test
    void generateRequest_hasDifferentStartAndDestination() {
        RideRequestGenerator gen = new RideRequestGenerator();

        for (int i = 0; i < 100; i++) {
            RideRequest req = gen.generateRequest();
            assertNotEquals(
                    req.getStartLocation(),
                    req.getDestination(),
                    "Start and destination should be different"
            );
        }
    }

    @Test
    void generateRequest_setsCustomerName() {
        RideRequestGenerator gen = new RideRequestGenerator();
        RideRequest req = gen.generateRequest();

        assertNotNull(req.getCustomerId());
        assertFalse(req.getCustomerId().isBlank());
    }

    @Test
    void generateRequest_setsPositiveDistance() {
        RideRequestGenerator gen = new RideRequestGenerator();
        RideRequest req = gen.generateRequest();

        assertTrue(req.getAnticipatedDistance() > 0);
    }

    @Test
    void generateRequest_setsExpectedCompletionTime() {
        RideRequestGenerator gen = new RideRequestGenerator();
        RideRequest req = gen.generateRequest();

        assertNotNull(req.getExpectedCompletionTime());
    }
}
