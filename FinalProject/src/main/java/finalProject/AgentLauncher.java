package finalProject;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public class AgentLauncher {
    public static void main(String[] args) {
        // 1. 初始化后端组件
        DriverCacheService driverCache = new DriverCacheService();
        OrderIndexService orderIndex = new OrderIndexService();

        // 手动注入测试数据
        driverCache.updateDriver(new Driver("Driver-101", true, "Northeastern University"));

        // 2. 配置 AI (关键：我在这里直接关掉了库自带的打印代码)
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey("REDACTED_API_KEY")
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .timeout(Duration.ofSeconds(60)) // 增加超时时间处理网络波动
                .logRequests(false)
                .logResponses(false)
                .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 3. 绑定适配器
        TaxiAgentInterface agent = AiServices.builder(TaxiAgentInterface.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .tools(new TaxiAgentAdapter(driverCache, orderIndex))
                .build();


        ExecutorService aiExecutor = Executors.newFixedThreadPool(5);

        // 4. 开始对话
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n=== 智能调度后台已就绪 (已手动载入测试数据) ===");
        while (true) {
            System.out.print("指令 > ");
            String input = scanner.nextLine();
            if ("exit".equalsIgnoreCase(input)) break;

            // 2. 将同步调用改为 CompletableFuture 异步编排
            CompletableFuture.supplyAsync(() -> agent.chat(input), aiExecutor)
                    .orTimeout(12, TimeUnit.SECONDS) // 鲁棒性：设置超时
                    .thenAccept(response -> {
                        System.out.println("\n[AI 异步回复]: " + response);
                        System.out.print("指令 > "); // 重新打印提示符
                    })
                    .exceptionally(ex -> {
                        System.err.println("\n[系统异常]: 调度链路超时或错误，已触发降级保护。");
                        return null;
                    });
        }
    }
}