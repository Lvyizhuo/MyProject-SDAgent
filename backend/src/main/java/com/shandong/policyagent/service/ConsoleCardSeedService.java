package com.shandong.policyagent.service;

import com.shandong.policyagent.entity.*;
import com.shandong.policyagent.repository.AgentInstanceRepository;
import com.shandong.policyagent.repository.ModelProviderRepository;
import com.shandong.policyagent.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多智能体 + 工具定义初始化。
 * 启动时若库为空则灌入默认数据，避免前端 404 / 500。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleCardSeedService {

    private final AgentInstanceRepository agentRepository;
    private final ToolDefinitionRepository toolRepository;
    private final ModelProviderRepository modelRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedTools();
        seedAgents();
        seedDefaultSystemPrompts();
    }

    /**
     * 兜底：对老版本未填 systemPrompt 的 agent，灌入默认提示词，
     * 避免管理员打开「智能体编辑」面板时 textarea 是空的。
     */
    private void seedDefaultSystemPrompts() {
        String defaultPrompt = "你是山东省智能政策咨询助手，专注于解答以旧换新、补贴申领、"
                + "购车资格、地市政策差异等问题。请用准确、简洁、有条理的中文回答。"
                + "当用户问题超出政策范围时，礼貌告知并建议联系当地主管部门。";
        String defaultGreeting = "您好！我是山东省智能政策咨询助手，请问有什么可以帮您？";
        for (AgentInstance a : agentRepository.findAll()) {
            boolean changed = false;
            if (a.getSystemPrompt() == null || a.getSystemPrompt().isBlank()) {
                a.setSystemPrompt(defaultPrompt);
                changed = true;
            }
            if (a.getGreetingMessage() == null || a.getGreetingMessage().isBlank()) {
                a.setGreetingMessage(defaultGreeting);
                changed = true;
            }
            if (changed) {
                agentRepository.save(a);
            }
        }
    }

    private void seedTools() {
        if (toolRepository.count() > 0) {
            return;
        }
        log.info("初始化默认工具定义");

        toolRepository.save(ToolDefinition.builder()
                .code("web_search").name("联网搜索")
                .description("允许智能体搜索实时政策和产品价格，基于 Tavily Search API。")
                .category("web").icon("Globe")
                .isEnabled(true).dailyCalls(2100L).usedByAgentCount(3)
                .build());

        toolRepository.save(ToolDefinition.builder()
                .code("subsidy_calculator").name("补贴计算器")
                .description("提供山东省以旧换新补贴金额的精确计算，覆盖 17 地市 + 3 品类。")
                .category("calc").icon("Calculator")
                .isEnabled(true).dailyCalls(1100L).usedByAgentCount(2)
                .build());

        toolRepository.save(ToolDefinition.builder()
                .code("file_parser").name("文件解析")
                .description("允许解析用户上传的发票、旧机参数表、PDF 扫描件。")
                .category("parse").icon("FileText")
                .isEnabled(true).dailyCalls(387L).usedByAgentCount(1)
                .build());

        toolRepository.save(ToolDefinition.builder()
                .code("sandbox_py").name("代码沙箱")
                .description("为数据分析 agent 提供 Python 代码执行沙箱，限制网络与文件访问。")
                .category("external").icon("Code2")
                .isEnabled(false).dailyCalls(0L).usedByAgentCount(0)
                .build());
    }

    private void seedAgents() {
        if (agentRepository.count() > 0) {
            return;
        }
        log.info("初始化默认智能体实例");

        List<ModelProvider> providers = modelRepository.findAll();
        Long qwenId = providers.stream()
                .filter(p -> "qwen-72b".equalsIgnoreCase(p.getModelName()))
                .map(ModelProvider::getId).findFirst().orElse(null);
        Long deepseekId = providers.stream()
                .filter(p -> p.getModelName() != null && p.getModelName().toLowerCase().contains("deepseek"))
                .map(ModelProvider::getId).findFirst().orElse(null);
        Long gpt4oId = providers.stream()
                .filter(p -> p.getModelName() != null && p.getModelName().toLowerCase().contains("gpt-4o"))
                .map(ModelProvider::getId).findFirst().orElse(null);
        Long bgeVlId = providers.stream()
                .filter(p -> p.getType() == ModelType.VISION)
                .map(ModelProvider::getId).findFirst().orElse(null);
        Long whisperId = providers.stream()
                .filter(p -> p.getType() == ModelType.AUDIO)
                .map(ModelProvider::getId).findFirst().orElse(null);

        // 1. 政策咨询助手（默认）
        AgentInstance policyAssistant = AgentInstance.builder()
                .name("政策咨询助手").code("policy-assistant")
                .description("山东省以旧换新政策主入口，绑定 Qwen-72B 长文本模型，覆盖家电/汽车/数码品类。")
                .category("chat").icon("MessageSquare")
                .status("published").isDefault(true).version("3.2")
                .dailyCalls(2847L)
                .build();
        addBinding(policyAssistant, "LLM", qwenId, qwenId != null ? "Qwen-72B" : "Qwen-72B");
        addBinding(policyAssistant, "VISION", bgeVlId, bgeVlId != null ? "BGE-VL" : "BGE-VL");
        agentRepository.save(policyAssistant);

        // 2. 补贴计算专家
        AgentInstance subsidyExpert = AgentInstance.builder()
                .name("补贴计算专家").code("subsidy-calc-expert")
                .description("精确计算山东省 17 地市家电/汽车/数码补贴金额，调用函数工具避免幻觉。")
                .category("support").icon("Calculator")
                .status("published").isDefault(false).version("2.1")
                .dailyCalls(1124L)
                .build();
        addBinding(subsidyExpert, "LLM", deepseekId, deepseekId != null ? "DeepSeek-V3" : "DeepSeek-V3");
        addBinding(subsidyExpert, "FUNCTION", null, "subsidy-tool");
        agentRepository.save(subsidyExpert);

        // 3. 合同审查助手
        AgentInstance contractReviewer = AgentInstance.builder()
                .name("合同审查助手").code("contract-reviewer")
                .description("上传合同 PDF，自动抽取关键条款并标记风险点，绑定 BGE-VL 处理扫描件。")
                .category("workflow").icon("FileSearch")
                .status("published").isDefault(false).version("1.4")
                .dailyCalls(387L)
                .build();
        addBinding(contractReviewer, "LLM", qwenId, qwenId != null ? "Qwen-72B" : "Qwen-72B");
        addBinding(contractReviewer, "VISION", bgeVlId, bgeVlId != null ? "BGE-VL" : "BGE-VL");
        agentRepository.save(contractReviewer);

        // 4. 数据分析助手（草稿）
        AgentInstance dataAnalyst = AgentInstance.builder()
                .name("数据分析助手").code("data-analyst")
                .description("自然语言查询销量/用户/政策命中数据，自动生成可视化图表与解读。")
                .category("workflow").icon("BarChart3")
                .status("draft").isDefault(false).version("0.3")
                .dailyCalls(0L)
                .build();
        addBinding(dataAnalyst, "LLM", gpt4oId, gpt4oId != null ? "GPT-4o" : "GPT-4o");
        addBinding(dataAnalyst, "FUNCTION", null, "sandbox-py");
        agentRepository.save(dataAnalyst);

        // 5. 语音接待助手
        AgentInstance voiceReception = AgentInstance.builder()
                .name("语音接待助手").code("voice-reception")
                .description("电话热线 7×24 自动接待，ASR + TTS 全流程，接入呼叫中心系统。")
                .category("support").icon("Mic")
                .status("published").isDefault(false).version("2.0")
                .dailyCalls(512L)
                .build();
        addBinding(voiceReception, "LLM", qwenId, qwenId != null ? "Qwen-72B" : "Qwen-72B");
        addBinding(voiceReception, "AUDIO", whisperId, whisperId != null ? "Whisper" : "Whisper");
        agentRepository.save(voiceReception);
    }

    private void addBinding(AgentInstance agent, String role, Long modelId, String displayName) {
        AgentModelBinding binding = AgentModelBinding.builder()
                .agent(agent).role(role).modelId(modelId)
                .displayName(displayName)
                .build();
        agent.getBindings().add(binding);
    }
}
