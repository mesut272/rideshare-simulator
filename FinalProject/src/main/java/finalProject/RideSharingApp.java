package finalProject;

public class RideSharingApp {

    public static void main(String[] args) throws InterruptedException {

        SimulationConfig config = new SimulationConfig(
                1,
                2000,     // 每2秒生成一个订单
                60,       // 运行60秒
                10        // 生成10个订单
        );

        SimulationEngine engine = new SimulationEngine(config);
        engine.start();

        // 🔧 修复：等待足够长的时间让所有订单完成
        // 等待生成时间 + 额外缓冲（让最后的订单有时间完成）
        long waitTime = (config.maxRequests * config.requestIntervalMs) + 30000L;
        Thread.sleep(waitTime);

        // 🔧 如果还在运行，手动停止
        engine.stop();
    }
}