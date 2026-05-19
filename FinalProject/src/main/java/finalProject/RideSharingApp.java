package finalProject;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class RideSharingApp {

    // 在 RideSharingApp 类定义中加入
    public static final java.util.Set<String> WATCH_LIST = new java.util.concurrent.ConcurrentSkipListSet<>();
    public static void main(String[] args) throws InterruptedException {
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

        SimulationEngine engine = new SimulationEngine(config);
        engine.start();

        new Thread(() -> {
            try {
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .apiKey("REDACTED_API_KEY")
                        .baseUrl("https://api.deepseek.com")
                        .modelName("deepseek-chat")
                        .timeout(Duration.ofSeconds(60)) // 增加超时时间处理网络波动
                        .logRequests(false)
                        .logResponses(false)
                        .build();

                TaxiAgentInterface agent = AiServices.builder(TaxiAgentInterface.class)
                        .chatLanguageModel(model)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                        .tools(new TaxiAgentAdapter(engine.getDriverCache(), engine.getOrderIndex()))
                        .build();

                Scanner scanner = new Scanner(System.in);
                System.out.println("本次模拟司机名单: " + engine.getDriverCache().getAllDriverIds());
                System.out.println("\n[SYSTEM] 系统正在运行中，AI 助手已就绪。");

                while (true) {
                    System.out.print("\nAI 指令 > ");
                    String input = scanner.nextLine();
                    // 使用异步方式调用，保证 AI 思考时，控制台依然能打印 Engine 的派单日志
                    CompletableFuture.supplyAsync(() -> agent.chat(input))
                            .thenAccept(res -> System.out.println("\n[AI]: " + res));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

}