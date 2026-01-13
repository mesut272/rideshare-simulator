package finalProject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RideRequestTest {

    private RideRequest request;
    private LocalDateTime requestTime;

    @BeforeEach
    void setUp() {
        requestTime = LocalDateTime.now();
        request = new RideRequest(
                "TestCustomer",
                "UW",
                "Airport",
                400.0,
                requestTime,
                RideType.STANDARD_PICKUP
        );
    }

    @Test
    void testConstructor_ShouldInitializeFieldsCorrectly() {
        assertEquals("TestCustomer", request.getCustomerId());
        assertEquals("UW", request.getStartLocation());
        assertEquals("Airport", request.getDestination());
        assertEquals(400.0, request.getAnticipatedDistance());
        assertEquals(requestTime, request.getRequestTimestamp());
        assertEquals(RideType.STANDARD_PICKUP, request.getRideType());

        // 实际开始时间和完成时间应该是null（还没dispatch）
        assertNull(request.getActualStartTime());
        assertNull(request.getExpectedCompletionTime());
    }

    @Test
    void testSetActualStartTime_ShouldSetTimeSuccessfully() {
        LocalDateTime startTime = LocalDateTime.now();
        request.setActualStartTime(startTime);

        assertEquals(startTime, request.getActualStartTime());
    }

    @Test
    void testSetActualStartTime_ShouldThrowExceptionWhenSetTwice() {
        LocalDateTime firstTime = LocalDateTime.now();
        LocalDateTime secondTime = LocalDateTime.now().plusSeconds(10);

        request.setActualStartTime(firstTime);

        // 尝试第二次设置应该抛出异常
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            request.setActualStartTime(secondTime);
        });

        assertEquals("Start time already set!", exception.getMessage());
    }

    @Test
    void testSetExpectedCompletionTime_ShouldSetTimeSuccessfully() {
        LocalDateTime completionTime = LocalDateTime.now().plusSeconds(100);
        request.setExpectedCompletionTime(completionTime);

        assertEquals(completionTime, request.getExpectedCompletionTime());
    }

    @Test
    void testSetExpectedCompletionTime_ShouldThrowExceptionWhenSetTwice() {
        LocalDateTime firstTime = LocalDateTime.now().plusSeconds(100);
        LocalDateTime secondTime = LocalDateTime.now().plusSeconds(200);

        request.setExpectedCompletionTime(firstTime);

        // 尝试第二次设置应该抛出异常
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            request.setExpectedCompletionTime(secondTime);
        });

        assertEquals("Completion time already set!", exception.getMessage());
    }

    @Test
    void testGetPriorityLevel_ExpressPickup() {
        RideRequest expressRequest = new RideRequest(
                "Customer", "UW", "Airport", 400.0,
                LocalDateTime.now(), RideType.EXPRESS_PICKUP
        );
        assertEquals(1, expressRequest.getPriorityLevel());
    }

    @Test
    void testGetPriorityLevel_StandardPickup() {
        RideRequest standardRequest = new RideRequest(
                "Customer", "UW", "Airport", 400.0,
                LocalDateTime.now(), RideType.STANDARD_PICKUP
        );
        assertEquals(2, standardRequest.getPriorityLevel());
    }

    @Test
    void testGetPriorityLevel_WaitAndSavePickup() {
        RideRequest waitRequest = new RideRequest(
                "Customer", "UW", "Airport", 400.0,
                LocalDateTime.now(), RideType.WAIT_AND_SAVE_PICKUP
        );
        assertEquals(3, waitRequest.getPriorityLevel());
    }

    @Test
    void testGetPriorityLevel_EnvirPickup() {
        RideRequest envirRequest = new RideRequest(
                "Customer", "UW", "Airport", 400.0,
                LocalDateTime.now(), RideType.ENVIR_PICKUP
        );
        assertEquals(4, envirRequest.getPriorityLevel());
    }

    @Test
    void testRequestTimestamp_ShouldBeImmutable() {
        LocalDateTime originalTime = request.getRequestTimestamp();

        // 设置实际开始时间不应该影响请求时间
        request.setActualStartTime(LocalDateTime.now().plusSeconds(10));

        assertEquals(originalTime, request.getRequestTimestamp());
    }
}
