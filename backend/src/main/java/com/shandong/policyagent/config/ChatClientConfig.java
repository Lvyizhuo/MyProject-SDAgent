package com.shandong.policyagent.config;

import com.shandong.policyagent.advisor.LoggingAdvisor;
import com.shandong.policyagent.advisor.RedisChatMemory;
import com.shandong.policyagent.advisor.ReReadingAdvisor;
import com.shandong.policyagent.advisor.SecurityAdvisor;
import com.shandong.policyagent.rag.RagConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            你是山东省政策咨询智能助手，专门为山东省居民提供以旧换新政策的咨询服务。
            当前日期：2026年2月。
            
            你的职责:
            1. 准确解读国家和山东省各地市的以旧换新补贴政策
            2. 帮助用户计算可获得的补贴金额
            3. 指导用户如何申请补贴
            
            回答要求:
            - 使用通俗易懂的语言
            - 如果涉及具体金额或日期请务必准确
            - 如果不确定请明确告知用户
            - 回答应简洁明了重点突出
            - 如果检索到相关政策文档，请基于文档内容回答，并标注信息来源
            - 【重要】不要对产品发布时间做任何猜测或声明，不要说"截至XX年"这类话
            
            处理未知产品型号的规则:
            - 用户询问的具体产品型号（如iPhone17、小米15等）可能是最新款或尚未发布的型号
            - 不要因为不认识某个型号就说"该产品尚未发布"
            - 应该根据产品品类（如"手机"）来回答补贴政策，而不是纠结于具体型号
            - 例如：用户问"iPhone17能补贴多少"，应理解为"购买手机能补贴多少"
            - 主动调用 calculateSubsidy 工具，将产品类型设为"手机"来计算补贴
            
            图片识别结果处理规则:
            - 当用户消息中包含"【以下是从用户上传图片中提取的设备信息】"时，说明系统已自动识别了用户上传的家电图片
            - 【必须】根据识别结果中的设备类型，主动调用 calculateSubsidy 工具计算补贴金额
            - 不要再反问用户"请告诉我您的家电类型"，识别结果已经提供了设备类型信息
            - 如果识别结果中缺少购买价格，可以询问用户新家电的购买价格
            - 如果识别结果标注"未识别"某些信息，可以就那些未识别的信息补充询问
            
            可用工具（请主动使用）:
            - calculateSubsidy: 用于精确计算补贴金额。当用户询问具体商品的补贴金额时【必须】调用此工具。
              支持的商品类型：空调、冰箱、洗衣机、电视、热水器、微波炉、油烟机、洗碗机、燃气灶、净水器、手机、平板、智能手表、手环。
              当用户提到具体品牌型号时（如iPhone、华为手机、小米平板等），请识别其品类并调用工具计算。
              当图片识别结果中包含设备类型时，直接使用识别出的类型调用此工具。
            - parseFile: 用于解析用户上传的发票或旧机参数文件，提取关键信息
            - webSearch: 联网搜索工具，用于获取最新的实时信息。以下场景【必须】主动调用此工具：
              1. 用户询问具体产品的市场价格（如"iPhone17多少钱"、"华为手机价格"）
              2. 用户要求查询最新的政策变化或新闻动态
              3. 用户明确要求联网搜索
              4. 需要获取实时数据才能准确回答用户问题时
              调用时将用户的问题转化为有效的搜索关键词，例如用户问"iPhone17 256GB优惠价格"，搜索关键词应为"iPhone 17 256GB 价格"。
            """;

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate) {
        return RedisChatMemory.builder()
                .redisTemplate(redisTemplate)
                .ttlDays(7)
                .build();
    }

    @Bean
    public SecurityAdvisor securityAdvisor() {
        return SecurityAdvisor.builder()
                .order(10)
                .build();
    }

    @Bean
    public ReReadingAdvisor reReadingAdvisor() {
        return ReReadingAdvisor.builder()
                .order(50)
                .build();
    }

    @Bean
    public LoggingAdvisor loggingAdvisor() {
        return LoggingAdvisor.builder()
                .order(90)
                .logTokenUsage(true)
                .logRetrievedDocs(true)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  VectorStore vectorStore,
                                  RagConfig ragConfig,
                                  ChatMemory chatMemory,
                                  SecurityAdvisor securityAdvisor,
                                  ReReadingAdvisor reReadingAdvisor,
                                  LoggingAdvisor loggingAdvisor) {
        RagConfig.Retrieval retrievalConfig = ragConfig.getRetrieval();

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(retrievalConfig.getTopK())
                        .similarityThreshold(retrievalConfig.getSimilarityThreshold())
                        .build())
                .build();

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        securityAdvisor,
                        memoryAdvisor,
                        reReadingAdvisor,
                        qaAdvisor,
                        loggingAdvisor
                )
                .defaultToolNames("calculateSubsidy", "parseFile", "webSearch")
                .build();
    }
}
