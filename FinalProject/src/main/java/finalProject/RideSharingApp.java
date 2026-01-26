package finalProject;

public class RideSharingApp {

    public static void main(String[] args) throws InterruptedException {

        // 打印配置信息
        ConfigLoader.printConfig();

        // 从配置文件加载参数
        SimulationConfig config = new SimulationConfig(
                ConfigLoader.getDriverCount(),
                ConfigLoader.getRequestIntervalMs(),
                ConfigLoader.getMaxRequests()
        );

        SimulationEngine engine = new SimulationEngine(config);
        engine.start();

        // 计算等待时间：生成时间 + 额外缓冲时间
        long generateTime = config.maxRequests * config.requestIntervalMs;
        long bufferTime = 60000L; // 60秒缓冲，让所有订单有时间完成
        long waitTime = generateTime + bufferTime;

        System.out.println("[MAIN] Will wait up to " + (waitTime / 1000) + " seconds for completion...");
        Thread.sleep(waitTime);

        // 如果还在运行，手动停止（实际上系统应该已经自动停止了）
        engine.stop();
    }
}