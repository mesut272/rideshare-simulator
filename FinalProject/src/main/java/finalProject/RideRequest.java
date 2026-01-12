package finalProject;

import java.time.LocalDateTime;

public class RideRequest {
    private String customerId;
    private String startLocation;
    private String destination;
    private double anticipatedDistance;

    // 🔧 修复：明确时间语义
    private final LocalDateTime requestTimestamp;  // 请求时间（创建时设置，不可变）
    private LocalDateTime actualStartTime;         // 实际开始时间（dispatch时设置）
    private LocalDateTime expectedCompletionTime;  // 预计完成时间（dispatch时设置）

    private RideType rideType;

    // 🔧 修复：constructor只设置请求时间
    public RideRequest(String customerId, String startLocation, String destination,
                       double anticipatedDistance, LocalDateTime requestTimestamp, RideType rideType) {
        this.customerId = customerId;
        this.startLocation = startLocation;
        this.destination = destination;
        this.anticipatedDistance = anticipatedDistance;
        this.requestTimestamp = requestTimestamp;
        this.rideType = rideType;
        this.actualStartTime = null;  // 等待dispatch
        this.expectedCompletionTime = null;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public String getDestination() {
        return destination;
    }

    public double getAnticipatedDistance() {
        return anticipatedDistance;
    }

    public LocalDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public LocalDateTime getActualStartTime() {
        return actualStartTime;
    }

    public LocalDateTime getExpectedCompletionTime() {
        return expectedCompletionTime;
    }

    public RideType getRideType() {
        return rideType;
    }

    // 🔧 新增：只允许设置一次实际开始时间
    public void setActualStartTime(LocalDateTime time) {
        if (this.actualStartTime != null) {
            throw new IllegalStateException("Start time already set!");
        }
        this.actualStartTime = time;
    }

    // 🔧 新增：只允许设置一次完成时间
    public void setExpectedCompletionTime(LocalDateTime time) {
        if (this.expectedCompletionTime != null) {
            throw new IllegalStateException("Completion time already set!");
        }
        this.expectedCompletionTime = time;
    }

    public int getPriorityLevel() {
        if (this.rideType == RideType.EXPRESS_PICKUP) {
            return 1;
        } else if (this.rideType == RideType.STANDARD_PICKUP) {
            return 2;
        } else if (this.rideType == RideType.WAIT_AND_SAVE_PICKUP) {
            return 3;
        } else {
            return 4;
        }
    }

    @Override
    public String toString() {
        return "RideRequest{" +
                "customer=" + customerId +
                ", type=" + rideType +
                ", " + startLocation + "→" + destination +
                ", distance=" + anticipatedDistance +
                "}";
    }
}