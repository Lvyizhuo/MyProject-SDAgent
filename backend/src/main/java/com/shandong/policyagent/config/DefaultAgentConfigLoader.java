package com.shandong.policyagent.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DefaultAgentConfigLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取默认智能体名称
     */
    public String getDefaultName() {
        return "政策问答智能体";
    }

    /**
     * 获取默认描述
     */
    public String getDefaultDescription() {
        return "用于山东省以旧换新补贴政策咨询";
    }

    /**
     * 获取默认系统提示词
     */
    public String getDefaultSystemPrompt() {
        return """
                你是一个专业的山东省以旧换新补贴政策咨询助手。你的任务是：

                1. 准确理解用户关于补贴政策的咨询问题
                2. 基于提供的政策文档内容回答问题
                3. 当信息不足时，可以主动询问用户更多细节
                4. 对于补贴金额计算，使用提供的工具进行精确计算
                5. 保持回答准确、客观、易于理解

                请始终基于提供的事实依据回答，不要编造信息。
                """;
    }

    /**
     * 获取默认开场白
     */
    public String getDefaultGreetingMessage() {
        return """
                您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

                **我可以帮您：**
                - 查询各类产品补贴金额
                - 了解申请条件和流程
                - 计算您能获得的补贴
                - 解答政策相关疑问
                """;
    }

    /**
     * 获取默认技能配置（JSON 字符串）
     */
    public String getDefaultSkills() {
        Map<String, Object> skills = new HashMap<>();

        Map<String, Object> webSearch = new HashMap<>();
        webSearch.put("enabled", true);
        skills.put("webSearch", webSearch);

        Map<String, Object> subsidyCalculator = new HashMap<>();
        subsidyCalculator.put("enabled", true);
        skills.put("subsidyCalculator", subsidyCalculator);

        Map<String, Object> fileParser = new HashMap<>();
        fileParser.put("enabled", true);
        skills.put("fileParser", fileParser);

        try {
            return objectMapper.writeValueAsString(skills);
        } catch (JsonProcessingException e) {
            log.error("无法序列化默认技能配置", e);
            return """
                    {
                        "webSearch": {"enabled": true},
                        "subsidyCalculator": {"enabled": true},
                        "fileParser": {"enabled": true}
                    }
                    """;
        }
    }

    /**
     * 获取默认 MCP 服务器配置（空数组JSON字符串）
     */
    public String getDefaultMcpServersConfig() {
        return "[]";
    }
}
