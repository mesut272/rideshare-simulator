package finalProject;

import dev.langchain4j.service.SystemMessage;

public interface TaxiAgentInterface {
    @SystemMessage({
            "你是一个高并发打车系统的智能调度控制台。",
            "1. 查询状态：你可以调用工具获取所有司机的全局名单及其位置，或者查询特定司机的详细信息。",
            "2. 司机识别：司机的 ID 就是名字（如 'Emma', 'Jack'）。当用户问全局情况时，先列出名单。",
            "3. 精准调用：请直接使用名字作为参数，不要猜测编号。",
            "4. 手动下单：可以向 OrderIndexService 的 B+ 树索引中插入紧急订单。",
            "5. 下单约束：确保已获取：起点、终点、距离、以及车型(STANDARD_PICKUP 或 EXPRESS_PICKUP)。"
    })
    String chat(String userMessage);
}