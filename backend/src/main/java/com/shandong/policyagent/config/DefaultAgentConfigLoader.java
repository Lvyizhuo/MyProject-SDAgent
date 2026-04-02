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

                工具调用与信息复用规则：
                1. 先复用会话中的结构化信息（地区、品牌、型号、规格、价格、政策年份），缺失才追问。
                2. 涉及商品时优先标准化提取为“品牌 + 系列 + 型号 + 规格 + 类型”。
                3. 品牌命名保持规范（如 Apple 统一为 苹果，HUAWEI 统一为 华为）。
                4. 调用 webSearch 前先构造清晰关键词，至少包含“品牌 + 型号”，必要时补充规格和年份。
                5. 模糊问题（例如“最近哪款便宜”“某手机多少钱”）先追问具体品牌和型号，再决定是否联网。
                6. 调用 calculateSubsidy 前必须确认“明确商品类型 + 明确数字价格”，参数不完整时禁止调用并先澄清。
                7. 不得编造价格、型号、政策条款或来源链接；无法确认时明确说明不确定性。

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
