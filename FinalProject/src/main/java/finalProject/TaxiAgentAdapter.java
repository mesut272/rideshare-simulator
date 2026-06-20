package finalProject;

import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

public class TaxiAgentAdapter {
    private final DriverCacheService driverCache;
    private final OrderIndexService orderIndex;
    private final Consumer<RideRequest> manualOrderSubmitter;

    public TaxiAgentAdapter(DriverCacheService driverCache, OrderIndexService orderIndex) {
        this(driverCache, orderIndex, null);
    }

    public TaxiAgentAdapter(DriverCacheService driverCache, OrderIndexService orderIndex,
                            Consumer<RideRequest> manualOrderSubmitter) {
        this.driverCache = driverCache;
        this.orderIndex = orderIndex;
        this.manualOrderSubmitter = manualOrderSubmitter;
    }

    @Tool("查询指定司机的实时位置和可用性。参数为司机ID，例如 Driver-1")
    public String getDriverStatus(String driverId) {
        // 1. 鲁棒性校验：拦截无效或空参数
        if (driverId == null || driverId.trim().isEmpty()) {
            return "错误：司机 ID 不能为空。";
        }

        // 2. 安全性拦截：模拟金融级权限校验
        // 只有特定前缀或在白名单内的司机信息可以被查询
        if (driverId.equals("Admin-Secret")) {
            return "安全性拦截：您无权查询管理账户的状态。";
        }

        Driver d = driverCache.getDriver(driverId);
        if (d == null) return "未找到该司机信息（可能由于防穿透策略，该 ID 被判定为非法）。";
        return String.format("司机 %s 目前位于 %s, 状态: %s",
                d.getDriverId(), d.getCurrentLocation(), d.isAvailable() ? "空闲可接单" : "正在忙碌");
    }

    @dev.langchain4j.agent.tool.Tool("获取当前系统中所有司机的名单及其状态快照")
    public String getAllDriversStatus() {
        java.util.Collection<Driver> allDrivers = driverCache.getAllDrivers();
        if (allDrivers.isEmpty()) return "当前没有在线司机。";

        StringBuilder sb = new StringBuilder("当前系统司机状态如下：\n");
        for (Driver d : allDrivers) {
            sb.append(String.format("- %s: 位置[%s], 状态[%s]\n",
                    d.getDriverId(), d.getCurrentLocation(), d.isAvailable() ? "空闲" : "忙碌"));
        }
        return sb.toString();
    }

    @dev.langchain4j.agent.tool.Tool("将指定司机加入实时监控名单，当其接单时立刻通知")
    public String watchDriver(String driverName) {
        RideSharingApp.WATCH_LIST.add(driverName);
        return "已将 " + driverName + " 加入实时监控名单。他一旦接单，我会立刻在控制台提醒你。";
    }

    @dev.langchain4j.agent.tool.Tool("从监控名单中移除指定司机")
    public String unwatchDriver(String driverName) {
        RideSharingApp.WATCH_LIST.remove(driverName);
        return "已停止对 " + driverName + " 的实时监控。";
    }

    @Tool("手动录入一个紧急或特定订单。参数：起点, 终点, 距离(double类型), 类型(STANDARD_PICKUP/EXPRESS_PICKUP)")
    public String manualOrder(String start, String dest, double dist, String type) {
        try {
            RideType rideType = RideType.valueOf(type.toUpperCase());
            if (dist < 0) {
                return "距离不能为负数。";
            }
            if (!CityMap.getAllLocations().contains(start) || !CityMap.getAllLocations().contains(dest)) {
                return "地点输入错误，请使用系统地图中的地点：" + CityMap.getAllLocations();
            }
            if (start.equals(dest)) {
                return "起点和终点不能相同。";
            }

            String customerId = "AI-USER-" + UUID.randomUUID().toString().substring(0, 4);

            RideRequest req = new RideRequest(customerId, start, dest, dist, LocalDateTime.now(), rideType);

            if (manualOrderSubmitter != null) {
                manualOrderSubmitter.accept(req);
                return "【AI 调度成功】紧急订单已进入派单队列，起点为：" + start;
            } else {
                orderIndex.insertOrder(req, "PENDING");
                return "【AI 录入成功】紧急订单已写入索引演示池，起点为：" + start;
            }
        } catch (IllegalArgumentException e) {
            return "车型类型输入错误，请指定为 STANDARD_PICKUP、EXPRESS_PICKUP、WAIT_AND_SAVE_PICKUP 或 ENVIR_PICKUP。";
        }
    }
}
