package finalProject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

class StrategyCompositeOrderingTest {

    private StrategyCompositeOrdering strategy;

    @BeforeEach
    void setUp() {
        strategy = new StrategyCompositeOrdering();
    }

    @Test
    void testPriorityOrdering_ExpressBeforeStandard() {
        LocalDateTime now = LocalDateTime.now();

        RideRequest standard = new RideRequest(
                "Customer1", "UW", "Airport", 400.0, now, RideType.STANDARD_PICKUP
        );

        RideRequest express = new RideRequest(
                "Customer2", "UW", "Airport", 400.0, now, RideType.EXPRESS_PICKUP
        );

        // EXPRESS (priority 1) 应该排在 STANDARD (priority 2) 前面
        assertTrue(strategy.compare(express, standard) < 0);
        assertTrue(strategy.compare(standard, express) > 0);
    }

    @Test
    void testTimeOrdering_WhenSamePriority() {
        LocalDateTime earlier = LocalDateTime.now();
        LocalDateTime later = earlier.plusSeconds(10);

        RideRequest request1 = new RideRequest(
                "Customer1", "UW", "Airport", 400.0, earlier, RideType.STANDARD_PICKUP
        );

        RideRequest request2 = new RideRequest(
                "Customer2", "UW", "Airport", 400.0, later, RideType.STANDARD_PICKUP
        );

        // 相同优先级时，早的请求应该排在前面
        assertTrue(strategy.compare(request1, request2) < 0);
        assertTrue(strategy.compare(request2, request1) > 0);
    }

    @Test
    void testDistanceOrdering_WhenSamePriorityAndTime() {
        LocalDateTime now = LocalDateTime.now();

        RideRequest shortDistance = new RideRequest(
                "Customer1", "UW", "SLU", 100.0, now, RideType.STANDARD_PICKUP
        );

        RideRequest longDistance = new RideRequest(
                "Customer2", "UW", "Airport", 400.0, now, RideType.STANDARD_PICKUP
        );

        // 相同优先级和时间时，长距离应该排在前面
        assertTrue(strategy.compare(longDistance, shortDistance) < 0);
        assertTrue(strategy.compare(shortDistance, longDistance) > 0);
    }

    @Test
    void testCompleteOrderingInPriorityQueue() {
        PriorityQueue<RideRequest> queue = new PriorityQueue<>(strategy);

        LocalDateTime baseTime = LocalDateTime.now();

        // 创建不同类型的请求
        RideRequest r1 = new RideRequest("C1", "UW", "Airport", 400.0,
                baseTime.plusSeconds(5), RideType.WAIT_AND_SAVE_PICKUP);  // Priority 3, time +5

        RideRequest r2 = new RideRequest("C2", "UW", "Airport", 400.0,
                baseTime, RideType.EXPRESS_PICKUP);  // Priority 1, time 0

        RideRequest r3 = new RideRequest("C3", "UW", "Airport", 400.0,
                baseTime.plusSeconds(2), RideType.STANDARD_PICKUP);  // Priority 2, time +2

        RideRequest r4 = new RideRequest("C4", "UW", "SLU", 100.0,
                baseTime, RideType.EXPRESS_PICKUP);  // Priority 1, time 0, shorter distance

        // 乱序添加
        queue.add(r1);
        queue.add(r2);
        queue.add(r3);
        queue.add(r4);

        // 期望顺序：r2 (Express, t=0, 400), r4 (Express, t=0, 100), r3 (Standard, t=2), r1 (Wait, t=5)
        // 但 r2 和 r4 优先级和时间相同，r2距离更长应该先出队
        assertEquals(r2, queue.poll());  // Express, distance 400
        assertEquals(r4, queue.poll());  // Express, distance 100
        assertEquals(r3, queue.poll());  // Standard
        assertEquals(r1, queue.poll());  // Wait and Save
    }

    @Test
    void testAllPriorityLevels() {
        LocalDateTime now = LocalDateTime.now();

        RideRequest express = new RideRequest("C1", "UW", "Airport", 400.0, now, RideType.EXPRESS_PICKUP);
        RideRequest standard = new RideRequest("C2", "UW", "Airport", 400.0, now, RideType.STANDARD_PICKUP);
        RideRequest waitSave = new RideRequest("C3", "UW", "Airport", 400.0, now, RideType.WAIT_AND_SAVE_PICKUP);
        RideRequest envir = new RideRequest("C4", "UW", "Airport", 400.0, now, RideType.ENVIR_PICKUP);

        // 验证优先级顺序：EXPRESS < STANDARD < WAIT_AND_SAVE < ENVIR
        assertTrue(strategy.compare(express, standard) < 0);
        assertTrue(strategy.compare(standard, waitSave) < 0);
        assertTrue(strategy.compare(waitSave, envir) < 0);
    }

    @Test
    void testEqualRequests_ShouldReturnZero() {
        LocalDateTime now = LocalDateTime.now();

        RideRequest request = new RideRequest(
                "Customer", "UW", "Airport", 400.0, now, RideType.STANDARD_PICKUP
        );

        // 同一个对象比较应该返回0
        assertEquals(0, strategy.compare(request, request));
    }
}
