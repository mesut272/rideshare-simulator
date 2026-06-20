package finalProject;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.Duration;

public class RideSharingApp {

    // 在 RideSharingApp 类定义中加入
    public static final java.util.Set<String> WATCH_LIST = new java.util.concurrent.ConcurrentSkipListSet<>();
    public static void main(String[] args) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger("finalProject");
        root.setLevel(ch.qos.logback.classic.Level.ERROR);

        // 打印配置信息
        ConfigLoader.printConfig();

        // 从配置文件加载参数
        SimulationConfig config = new SimulationConfig(
                ConfigLoader.getDriverCount(),
                ConfigLoader.getRequestIntervalMs(),
                ConfigLoader.getMaxRequests()
        );

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(SecretConfig.deepSeekApiKey())
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .timeout(Duration.ofSeconds(60)) // 增加超时时间处理网络波动
                .logRequests(false)
                .logResponses(false)
                .build();

        SimulationRuntime runtime = new SimulationRuntime(config, engine ->
                AiServices.builder(TaxiAgentInterface.class)
                        .chatLanguageModel(model)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                        .tools(new TaxiAgentAdapter(engine.getDriverCache(), engine.getOrderIndex(), engine::submitManualOrder))
                        .build()
        );

        int port = Integer.getInteger("supervisor.port", 8080);
        SupervisorChatServer supervisorServer = new SupervisorChatServer(
                port, runtime::chat, runtime::driverCache, runtime);
        supervisorServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            supervisorServer.stop();
            runtime.stop();
        }));

        System.out.println("本次模拟司机名单: " + runtime.driverCache().getAllDriverIds());
        System.out.println("[SYSTEM] 监管 Agent 前端已就绪: http://localhost:" + supervisorServer.getPort() + "/");
        System.out.println("[SYSTEM] 派单模拟等待前端启动。");
    }

}
