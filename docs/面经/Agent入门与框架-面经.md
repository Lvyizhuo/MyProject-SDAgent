# Agent 入门与框架 - 面经

> 结合山东省智能政策咨询助手项目，梳理 Agent 入门与主流框架面试题

---

## 第一部分：Agent 入门 10 题

---

### 1. 什么是大模型 Agent？它与传统的 AI 系统有什么不同？

**参考答案**（结合项目）：

#### 什么是大模型 Agent
大模型 Agent 是以 LLM 为核心大脑，具备感知、推理、决策、行动能力的智能系统。简单公式：
```
Agent = LLM + 记忆 (Memory) + 工具 (Tools) + 规划 (Planning)
```

**我的项目中的 Agent**：
- **LLM 大脑**：Qwen 3.5-plus
- **记忆**：Redis 对话记忆（7天）+ 会话事实缓存
- **工具**：webSearch（联网搜索）、calculateSubsidy（补贴计算）、parseFile（文件解析）、amap-mcp（地图服务）
- **规划**：ReAct 框架

#### 与传统 AI 系统的不同

| 维度 | 传统 AI 系统 | 大模型 Agent |
|------|-------------|-------------|
| **决策方式** | 规则/固定流程 | LLM 推理 + 动态决策 |
| **扩展性** | 加新功能需改代码 | 加新工具即可，无需改代码 |
| **适应性** | 只能处理预设场景 | 能处理开放场景 |
| **交互** | 单轮/简单多轮 | 真正的多轮对话、意图理解 |
| **我的项目** | - | ReAct 规划 + 工具调用 + 记忆 |

**我的项目举例**：
- 传统做法：用户问"补贴多少"→ 固定流程图 → 先问品类，再问价格，再计算
- Agent 做法：ReAct 规划 → 理解用户已说过的信息（事实缓存）→ 按需调用工具 → 灵活回答

---

### 2. LLM Agent 的基本架构有哪些组成部分？

**参考答案**（结合项目架构）：

#### 通用 Agent 架构
```
┌─────────────────────────────────────────────────────────────┐
│                      User Input                              │
└────────────────────┬────────────────────────────────────────┘
                     │
         ┌───────────▼───────────┐
         │   Planning（规划层）   │  ← ReActPlanningService
         │  - 理解问题            │
         │  - 生成执行计划        │
         └───────────┬───────────┘
                     │
    ┌────────────────┼────────────────┐
    │                │                │
┌───▼────┐    ┌────▼────┐   ┌─────▼─────┐
│ Memory │    │  Tools  │   │ Execution │
│(记忆层) │    │ (工具层) │   │  (执行层)  │
└───┬────┘    └────┬────┘   └─────┬─────┘
    │              │               │
    └──────────────┼───────────────┘
                   │
         ┌─────────▼─────────┐
         │   LLM (大脑层)    │  ← Qwen 3.5-plus
         └─────────┬─────────┘
                   │
         ┌─────────▼─────────┐
         │   Output (输出)    │
         └───────────────────┘
```

#### 我的项目中的具体实现

| 架构层 | 对应组件 | 代码文件 |
|--------|----------|----------|
| **Planning（规划）** | ReActPlanningService | ReActPlanningService.java |
| **Memory（记忆）** | SessionFactCacheService + RedisChatMemory | SessionFactCacheService.java |
| **Tools（工具）** | WebSearchTool、CalculateSubsidyTool 等 | tool/ 目录 |
| **Execution（执行）** | ChatService | ChatService.java |
| **LLM（大脑）** | DynamicChatClientFactory + Qwen | ChatClientConfig.java |
| **意图分类** | ToolIntentClassifier | ToolIntentClassifier.java |

---

### 3. LLM Agent 如何进行决策？能否使用具体的方法解释？

**参考答案**（结合 ReAct 实现）：

#### 常见决策方法

| 方法 | 说明 | 我的项目 |
|------|------|---------|
| **ReAct** | Reason + Act，先思考再行动 | ✅ 用了 |
| **CoT** | Chain-of-Thought，思维链 | ✅ 提示词中用了 |
| **Tree-of-Thought** | 树状搜索，多条路径 | ❌ 没用 |
| **Reflexion** | 反思 + 自我改进 | ❌ 没用 |

#### 我的项目中的 ReAct 决策流程（结合代码）

```java
// ReActPlanningService.java - 规划阶段
public AgentExecutionPlan createPlan(String conversationId, String userMessage) {
    // 1. 快捷路径：问候语/实时查询直接返回
    AgentExecutionPlan shortcutPlan = tryBuildShortcutPlan(conversationId, userMessage);
    if (shortcutPlan != null) return shortcutPlan;

    // 2. LLM 生成 JSON 格式计划
    String rawPlan = chatModel.call(planningPrompt).getResult().getOutput().getText();
    AgentExecutionPlan plan = planParser.parse(rawPlan, userMessage, maxSteps);

    return plan; // { summary, needToolCall, steps: [{id, action, toolHint}] }
}

// ChatService.java - 执行阶段
public ChatResponse chat(ChatRequest request) {
    // 1. 工具意图分类（前置检查）
    AgentExecutionPlan plan = planningService.createPlan(conversationId, userMessage);
    IntentDecision intentDecision = toolIntentClassifier.classify(userMessage, plan);
    plan = toolIntentClassifier.applyDecision(plan, intentDecision);

    // 2. 按需执行工具
    WebSearchTool.SearchResponse webSearchResponse = executeDirectWebSearchIfNeeded(...);

    // 3. 拼入提示词，LLM 生成最终答案
    String executionPrompt = buildExecutionPrompt(userMessage, plan, webSearchResponse);
    ChatExecutionResult result = executeChatWithFallback(...);
}
```

#### ReAct Prompt 示例
```
【用户原始问题】
iPhone 17 多少钱，补贴多少？

【ReAct执行计划】
目标：先查价格再算补贴
需要工具：true
步骤：
1. 调用 webSearch 查询 iPhone 17 价格 [toolHint=webSearch]
2. 调用 calculateSubsidy 计算补贴 [toolHint=calculateSubsidy]
3. 结合结果给出答案 [toolHint=none]
```

---

### 4. 如何让 LLM Agent 具备长期记忆能力？

**参考答案**（结合项目实现）：

#### 记忆分层架构
```
长期记忆 (Long-term Memory)
    ↓
中期记忆 (Medium-term Memory)
    ↓
短期记忆 (Short-term Memory / Working Memory)
    ↓
上下文窗口 (Context Window)
```

#### 我的项目中的记忆实现

| 记忆类型 | 实现方式 | 代码位置 | TTL |
|---------|---------|----------|-----|
| **对话记忆** | RedisChatMemoryAdvisor | ChatClientConfig.java | 7 天 |
| **事实缓存** | SessionFactCacheService | SessionFactCacheService.java | 7 天 |
| **RAG 记忆** | PostgreSQL + pgvector | RagRetrievalService.java | 永久 |

#### SessionFactCacheService 实现（关键代码）
```java
// 提取结构化事实
public SessionFacts mergeFacts(String conversationId, ChatRequest request) {
    SessionFacts existing = loadFacts(conversationId);
    SessionFacts merged = existing == null ? new SessionFacts() : existing;

    // 正则提取：价格、地区、设备型号
    extractPrice(message, merged);      // (\d{3,6}(?:\.\d{1,2})?)\s*(元|rmb|¥|￥)
    extractRegions(message, merged);    // ([\p{IsHan}]{2,}(?:省|市|区|县))
    extractDeviceModels(message, merged); // iPhone|华为|小米... + 型号

    // 存入 Redis，TTL 7 天
    saveFacts(conversationId, merged);
    return merged;
}

// 拼入提示词
public String toPromptContext(SessionFacts facts) {
    return """
        【会话事实缓存】
        - 设备型号：%s
        - 最近提及价格：%s元
        - 地区线索：%s
        """.formatted(
        String.join("、", facts.getDeviceModels()),
        facts.getLatestPrice(),
        String.join("、", facts.getRegions())
    );
}
```

#### 其他记忆方法
- **向量记忆**：把历史对话存入向量库，检索相关历史
- **摘要记忆**：用 LLM 把长历史摘要成一段
- **知识图谱记忆**：实体 + 关系结构化存储

---

### 5. LLM Agent 如何进行动态 API 调用？

**参考答案**（结合项目工具调用）：

#### 动态 API 调用的几种方式

| 方式 | 说明 | 我的项目 |
|------|------|---------|
| **Function Calling** | LLM 原生支持，输出 JSON | ✅ 用了 Spring AI |
| **MCP (Model Context Protocol)** | 标准协议，工具注册与发现 | ✅ 用了 amap-mcp |
| **Prompt 解析** | 让 LLM 按格式输出，手动解析 | ❌ 没用 |

#### 我的项目中的实现

**1. Spring AI Function Calling（ChatClientConfig.java）**
```java
@Bean
public ChatClient chatClient(ChatModel chatModel, ...) {
    return ChatClient.builder(chatModel)
        .defaultSystem(systemPrompt)
        .defaultTools(
            Map.ofEntries(
                entry("webSearch", webSearchTool.webSearch()),
                entry("calculateSubsidy", calculateSubsidyTool),
                entry("parseFile", parseFileTool)
            )
        )
        .build();
}
```

**2. 工具定义（WebSearchTool.java）**
```java
@Tool("webSearch")
public SearchResponse webSearch(SearchRequest request) {
    // 参数自动从 LLM 输出解析
    String query = request.query();
    // 调用 Tavily API
    return tavilyClient.search(query);
}
```

**3. MCP 接入（Model Context Protocol）**
```java
// MCP 是标准协议，可以动态注册工具
// 我的项目用了 amap-mcp（高德地图）
// 不用写代码，配置一下就能用
```

**4. 动态 API 调用流程**
```
用户问题
    ↓
LLM 决定调用哪个工具 + 生成参数
    ↓
Spring AI 解析 JSON，调用对应函数
    ↓
工具执行，返回结果
    ↓
结果拼回提示词，LLM 生成最终答案
```

---

### 6. LLM Agent 在多模态任务中如何执行推理？

**参考答案**（结合项目多模态实现）：

#### 多模态 Agent 架构
```
文本输入 ──┐
            ├─→ VLM (视觉语言模型) ──→ 理解 ──→ 决策 ──→ 行动
图像输入 ──┘
            ↑
语音输入 ──┴─→ ASR (语音转文本)
```

#### 我的项目中的多模态实现

**1. 图片识别流程（ChatService.java）**
```java
private String buildUserMessage(ChatRequest request, SessionFacts facts) {
    // 用户上传了图片
    if (request.hasImages()) {
        // 调用 VisionService 分析
        String imageAnalysis = analyzeImages(request.getImageBase64List(), request.getImageFormat());

        // 识别结果拼入提示词
        return String.format("""
            %s
            ---
            【以下是从用户上传图片中提取的设备信息】
            %s
            ---
            请结合图片识别结果回答。如果识别出设备类型，请主动调用 calculateSubsidy。
            """, baseMessage, imageAnalysis);
    }
    return baseMessage;
}

private String analyzeImages(List<String> imageBase64List, String imageFormat) {
    // 提示词要求 VLM 输出结构化信息
    String prompt = """
        请分析图片中的设备，提取：
        - 设备类型（空调/冰箱/洗衣机/手机等）
        - 品牌
        - 型号
        - 能效等级
        """;
    return visionService.analyzeBase64Image(imageBase64, format, prompt);
}
```

**2. 多模态 API（MultiModalController.java）**
```java
@PostMapping("/transcribe")    // 语音识别
@PostMapping("/analyze-image")  // 图像分析
@PostMapping("/analyze-invoice") // 发票识别
@PostMapping("/analyze-device")  // 设备识别
```

**3. 多模态 Agent 推理模式**
| 模式 | 说明 | 我的项目 |
|------|------|---------|
| **Side-by-side** | 文本 + 图片同时输入 | ✅ 用了 |
| **Cascade** | 先图片→文本，再文本推理 | ✅ 用了 |
| **Iterative** | 多轮交互，逐步理解 | ❌ 没用 |

---

### 7. LLM Agent 有哪些局限性？

**参考答案**（结合项目踩过的坑）：

#### 我的项目中遇到的局限性

| 局限性 | 具体表现 | 我的解决方案 |
|--------|---------|-------------|
| **工具调用不稳定** | Spring AI 流式 + 工具调用时 toolInput/toolName 为 null | 多级降级：流式 → 非流式 → REST |
| **参数不足** | LLM 经常参数没填全就调用工具 | ToolIntentClassifier 前置校验 |
| **幻觉** | 编造政策、编造价格 | RAG + 引用来源 + 联网搜索 |
| **上下文限制** | 对话太长塞不下 | 滑动窗口 + 事实缓存 + RAG |
| **延迟高** | ReAct 规划 + 工具调用 + 生成，太慢 | 快捷路径（问候语/实时查询直接返回） |
| **成本高** | 多轮 + 工具调用 Tokens 消耗大 | 意图分类拦截无效调用 + 缓存 |

#### 通用局限性
1. **规划能力有限**：复杂任务容易跑偏
2. **容错性差**：工具调用失败容易整段垮掉
3. **可解释性差**：不知道为什么选这个工具
4. **一致性差**：同一问题多次回答可能不一样
5. **安全风险**：Prompt 注入、工具滥用

---

### 8. 如何衡量 LLM Agent 的性能？

**参考答案**（结合项目指标）：

#### 衡量维度与指标

| 维度 | 指标 | 我的项目数值 |
|------|------|-------------|
| **效果质量** | 回答准确率 | - |
| | 工具调用准确率 | 提升 40%（意图分类器） |
| | 用户满意度 | - |
| | 幻觉率 | - |
| **效率** | 首次响应时间 (TTFT) | - |
| | 端到端延迟 | - |
| | Tokens 消耗 | 降低 40%（拦截无效调用） |
| **可靠性** | 系统可用性 | 99.9%（多级降级） |
| | 工具调用成功率 | - |
| | 崩溃率 | - |
| **成本** | 每千次对话成本 | - |
| | 平均 Tokens/对话 | - |

#### 我的项目中的监控点
```java
// ChatService.java - 耗时监控
long startTime = System.currentTimeMillis();
// ... 执行 ...
long duration = System.currentTimeMillis() - startTime;
log.info("对话完成 | conversationId={} | 耗时={}ms", conversationId, duration);

// 工具意图分类拦截日志
if (!intentDecision.allowToolCall()) {
    log.info("意图分类器拦截工具调用 | conversationId={} | tool={} | reason={}",
        conversationId, intentDecision.targetTool(), intentDecision.reason());
}
```

#### 评估方法
- **人工评估**：标注测试集，人工打分
- **自动评估**：LLM-as-a-Judge，用大模型打分
- **A/B 测试**：线上分流，对比两组指标

---

### 9. 未来 LLM Agent 可能有哪些技术突破？

**参考答案**（结合项目想做但没做的）：

#### 我的项目未来可以优化的方向（结合技术趋势）

| 方向 | 说明 | 我的项目可以怎么用 |
|------|------|------------------|
| **更好的规划** | Tree-of-Thought、Graph-of-Thought | 复杂政策问题用树状搜索 |
| **自我反思** | Reflexion、Self-Correction | 回答不好时自动反思重答 |
| **多 Agent 协作** | CrewAI、AutoGen | 政策专家 + 补贴计算专家 + 搜索专家 |
| **Memory 改进** | 记忆银行、记忆检索增强 | 历史对话向量检索，不是只塞窗口 |
| **工具进化** | 工具自动创建、工具市场 | 自动从文档中提取工具 |
| **更小更快** | 小模型 + 蒸馏 | 端侧 Agent，降低成本 |

#### 具体技术趋势
1. **Agentic Workflow**：Agent 作为工作流编排器
2. **Code as Policies**：用代码写策略，不是纯自然语言
3. **World Models**：Agent 有世界模型，能预测结果
4. **Active Learning**：Agent 主动学习，从反馈中进化
5. **标准化**：MCP 等协议统一工具生态

---

### 10. 请你设计一个 LLM Agent，用于医学问答

**参考答案**（仿照我的政策咨询项目）：

#### 医学问答 Agent 设计

```
┌─────────────────────────────────────────────────────────────┐
│                    医学问答 Agent                          │
├─────────────────────────────────────────────────────────────┤
│  【Memory（记忆）】                                        │
│  - 患者病历记忆（长期）                                     │
│  - 对话历史记忆（7天）                                      │
│  - 医学知识图谱（RAG）                                      │
├─────────────────────────────────────────────────────────────┤
│  【Tools（工具）】                                          │
│  - medical_search: 医学文献检索（PubMed/知网）             │
│  - symptom_check: 症状自查（结构化知识库）                  │
│  - drug_interaction: 药物相互作用查询                      │
│  - guideline_query: 诊疗指南查询                           │
├─────────────────────────────────────────────────────────────┤
│  【Planning（规划）】ReAct + 反思                          │
│  1. 理解患者问题                                            │
│  2. 检索医学文献/指南                                       │
│  3. 结合病历给出建议                                        │
│  4. 必要时建议就医（免责）                                  │
├─────────────────────────────────────────────────────────────┤
│  【Safety（安全）】                                         │
│  - 意图分类器：拒绝诊断，只给建议                           │
│  - 免责声明："不能替代执业医师"                             │
│  - 敏感词过滤：拒绝处方药推荐                                │
└─────────────────────────────────────────────────────────────┘
```

#### 核心流程（仿照我的政策咨询项目）
```java
// 1. 意图分类：是咨询还是要诊断？如果是诊断，拦截
MedicalIntentDecision decision = medicalIntentClassifier.classify(userQuestion);
if (decision.isDiagnosisRequest()) {
    return "抱歉，我不能给出诊断建议，请咨询专业医生。";
}

// 2. ReAct 规划
MedicalAgentPlan plan = medicalPlanningService.createPlan(patientId, userQuestion);

// 3. 工具调用
MedicalSearchResponse literature = medicalSearchTool.search(plan.getQuery());
DrugInteractionCheck interaction = drugInteractionTool.check(plan.getDrugs());

// 4. 生成回答 + 免责声明
String answer = medicalLLM.generate(patientHistory, literature, interaction);
return answer + "\n\n【免责声明】以上建议仅供参考，不能替代执业医师诊断。";
```

---

## 第二部分：主流 Agent 框架 10 题

---

### 1. LangChain 的核心组件有哪些？

**参考答案**（对比我的项目实现）：

#### LangChain 核心组件

| LangChain 组件 | 我的项目对应实现 | 说明 |
|--------------|-----------------|------|
| **LLM/ChatModel** | ChatClient + Qwen | 大语言模型封装 |
| **PromptTemplate** | 系统提示词 + 少样本 | 提示词模板 |
| **Memory** | RedisChatMemory + SessionFactCache | 对话记忆 |
| **Tools/Toolkits** | WebSearchTool、CalculateSubsidyTool | 工具封装 |
| **Agents** | ReActPlanningService + ChatService | Agent 执行逻辑 |
| **Chains** | ChatService 中的执行链路 | 链编排 |
| **Retrievers** | RagRetrievalService | 检索器 |
| **Document Loaders** | DocumentLoaderService | 文档加载 |
| **Text Splitters** | TextSplitterService | 文本切分 |
| **Vector Stores** | MultiVectorStoreService | 向量存储 |

#### 我的项目为什么没用 LangChain？
- 想自己控制细节，学习目的
- Spring AI 更贴合 Spring Boot 生态
- 但 LangChain 的很多思想借鉴了

---

### 2. LangChain Agent 的主要类型有哪些？

**参考答案**（对比我的 ReAct）：

#### LangChain Agent 类型

| Agent 类型 | 说明 | 我的项目用了吗？ |
|-----------|------|-----------------|
| **ReAct Agent** | Reason + Act，先思考再行动 | ✅ 用了（自己实现） |
| **OpenAI Functions Agent** | 利用 OpenAI Function Calling | ❌ 没用，用 Spring AI |
| **Structured Chat Agent** | 支持多输入工具 | ✅ 我的工具支持多参数 |
| **Zero-shot Agent** | 零样本直接调用 | ❌ 没用 |
| **Conversational Agent** | 带记忆的对话 Agent | ✅ Redis 记忆 |
| **Plan-and-Execute** | 先规划再执行 | ✅ ReAct 就是这种 |

#### 我的 ReAct 实现 vs LangChain ReAct
```java
// 我的实现更简单，更贴合业务
// ReActPlanningService.java
public AgentExecutionPlan createPlan(String conversationId, String userMessage) {
    // 1. 快捷路径
    AgentExecutionPlan shortcutPlan = tryBuildShortcutPlan(conversationId, userMessage);
    if (shortcutPlan != null) return shortcutPlan;

    // 2. LLM 生成 JSON 计划
    String rawPlan = chatModel.call(PLANNER_PROMPT + userMessage);
    return planParser.parse(rawPlan);
}
```

---

### 3. LlamaIndex 如何与 LangChain 结合？

**参考答案**（对比我的 RAG 实现）：

#### LlamaIndex 是什么
- 原名 GPT Index，专注于 RAG
- 核心概念：Index（索引）、Nodes（节点）、Retrievers（检索器）
- 优势：文档处理能力强，支持复杂 RAG 策略

#### 我的项目中的 RAG vs LlamaIndex
| 我的实现 | LlamaIndex 对应 |
|---------|----------------|
| DocumentLoaderService | SimpleDirectoryReader |
| TextSplitterService | TokenTextSplitter |
| MultiVectorStoreService | VectorStoreIndex |
| RagRetrievalService | RetrieverQueryEngine |

#### LangChain + LlamaIndex 结合方式
```python
# Python 示例（我的项目是 Java，思路类似）
from langchain.llms import OpenAI
from llama_index import VectorStoreIndex, SimpleDirectoryReader
from llama_index.langchain_helpers.agents import create_llama_agent

# 1. LlamaIndex 构建索引
documents = SimpleDirectoryReader("./data").load_data()
index = VectorStoreIndex.from_documents(documents)

# 2. 作为 Tool 接入 LangChain Agent
query_engine = index.as_query_engine()
tool = create_llama_agent(
    query_engine=query_engine,
    name="policy_knowledge_base",
    description="查询政策知识库"
)

# 3. LangChain Agent 使用
agent = initialize_agent(
    tools=[tool],
    llm=OpenAI(),
    agent=AgentType.OPENAI_FUNCTIONS
)
```

---

### 4. AutoGPT 如何实现自主决策？

**参考答案**（对比我的 Agent）：

#### AutoGPT 核心思想
- 完全自主，目标驱动
- 自我提示（Self-prompting）
- 长期记忆 + 短期记忆
- 工具使用（搜索、文件、代码执行）

#### AutoGPT 自主决策循环
```
1. 理解目标（User Goal）
    ↓
2. 生成任务列表（Tasks）
    ↓
3. 执行任务（Execute）
    ├─→ 搜索信息
    ├─→ 读写文件
    ├─→ 执行代码
    └─→ ...
    ↓
4. 反思（Reflect）
    ├─→ 评估结果
    ├─→ 调整计划
    └─→ 生成下一步
    ↓
5. 回到 3，直到目标完成
```

#### 我的项目 vs AutoGPT
| 维度 | 我的政策咨询 Agent | AutoGPT |
|------|-------------------|---------|
| **自主性** | 半自主（ReAct 框架） | 完全自主 |
| **目标设定** | 每次对话一个小目标 | 长期大目标 |
| **记忆** | 7 天会话 + 事实缓存 | 向量记忆 + 短视记忆 |
| **工具** | 4 个特定工具 | 通用工具（搜索/文件/代码） |
| **安全性** | 高（限制工具，意图分类） | 低（可能执行危险操作） |

---

### 5. BabyAGI 如何进行任务管理？

**参考答案**（对比我的规划）：

#### BabyAGI 核心机制
- **任务列表**：待办任务队列
- **任务创建**：根据结果创建新任务
- **任务优先级**：重新排列任务优先级
- **向量记忆**：任务结果存入向量库

#### BabyAGI 任务管理循环
```
┌─────────────────────────────────────────┐
│         Objective (目标)                │
│    "查清楚山东以旧换新补贴政策"          │
└─────────────┬───────────────────────────┘
              │
    ┌─────────▼──────────┐
    │  Task Creation     │  ← 创建任务
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │ Task Prioritization │ ← 优先级排序
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   Execution Agent   │ ← 执行任务
    └─────────┬──────────┘
              │
    ┌─────────▼──────────┐
    │   Memory (Vector)  │ ← 存入向量记忆
    └─────────┬──────────┘
              │
    └─────────┬──────────┘
              │
    回到 Task Creation，循环直到目标完成
```

#### 我的 ReActPlanningService vs BabyAGI
```java
// 我的简化版任务管理
// ReActPlanningService.java
public AgentExecutionPlan createPlan(String conversationId, String userMessage) {
    // 1. 生成固定步骤计划（最多 4 步）
    // {
    //   "summary": "目标...",
    //   "needToolCall": true,
    //   "steps": [
    //     {"id": 1, "action": "...", "toolHint": "webSearch"},
    //     {"id": 2, "action": "...", "toolHint": "calculateSubsidy"}
    //   ]
    // }

    // 2. 按顺序执行，不动态创建新任务
    // 不像 BabyAGI 会根据结果动态创建任务
}
```

---

### 6. CrewAI 如何管理多个 Agent 之间的协作？

**参考答案**（对比我的单 Agent）：

#### CrewAI 核心概念
- **Crew**：团队，多个 Agent 组成
- **Agent**：单个智能体，有角色、目标、工具
- **Task**：任务，分配给特定 Agent
- **Process**：流程，Sequential（顺序）、Hierarchical（层级）

#### CrewAI 多 Agent 协作示例（政策咨询场景）
```python
# Python 示例
from crewai import Agent, Task, Crew

# Agent 1: 政策检索专家
policy_expert = Agent(
    role="政策检索专家",
    goal="精准检索相关政策文件",
    backstory="你是山东省政策研究专家，熟悉各类补贴政策...",
    tools=[policy_search_tool]
)

# Agent 2: 补贴计算专家
subsidy_expert = Agent(
    role="补贴计算专家",
    goal="准确计算补贴金额",
    backstory="你是补贴计算专家，精通以旧换新补贴规则...",
    tools=[calculate_subsidy_tool]
)

# Agent 3: 用户沟通专家
communication_expert = Agent(
    role="用户沟通专家",
    goal="用通俗易懂的语言解释政策",
    backstory="你是用户沟通专家，擅长把专业政策转化为大白话..."
)

# 任务定义
task1 = Task(
    description="检索 iPhone 相关补贴政策",
    agent=policy_expert
)
task2 = Task(
    description="计算补贴金额",
    agent=subsidy_expert
)
task3 = Task(
    description="给用户通俗易懂的解释",
    agent=communication_expert
)

# 团队协作
crew = Crew(
    agents=[policy_expert, subsidy_expert, communication_expert],
    tasks=[task1, task2, task3],
    process=Process.sequential  # 顺序执行
)

result = crew.kickoff()
```

#### 我的项目 vs CrewAI
| 维度 | 我的项目 | CrewAI |
|------|---------|--------|
| **Agent 数量** | 1 个 | 多个（Crew） |
| **角色分工** | 无（全能） | 有（专家角色） |
| **协作方式** | 单 Agent 串行 | 多 Agent 协作（顺序/层级） |
| **实现复杂度** | 简单 | 复杂 |

---

### 7. LangChain 如何支持 API 调用？

**参考答案**（对比我的 Spring AI 工具调用）：

#### LangChain API 调用方式

| 方式 | 说明 | 我的项目对应 |
|------|------|-------------|
| **@tool 装饰器** | 函数上贴装饰器 | ✅ Spring AI @Tool |
| **StructuredTool** | 带参数 schema 的工具 | ✅ 我的工具自动推断 schema |
| **APIChain** | 自动调用 OpenAPI/Swagger API | ❌ 没用 |
| **Requests** | 直接发 HTTP 请求 | ✅ WebSearchTool 用了 |

#### LangChain @tool 示例（对比我的实现）
```python
# LangChain Python
from langchain.tools import tool

@tool
def web_search(query: str) -> str:
    """搜索网络信息"""
    return tavily_client.search(query)

# 我的 Java 实现（Spring AI）
@Tool("webSearch")
public SearchResponse webSearch(SearchRequest request) {
    return tavilyClient.search(request.query());
}
```

#### APIChain 示例（OpenAPI/Swagger）
```python
# LangChain 可以自动解析 OpenAPI/Swagger
from langchain.chains import APIChain
from langchain.llms import OpenAI

api_docs = """
GET /api/policies - 查询政策列表
GET /api/policies/{id} - 查询单个政策
"""

chain = APIChain.from_llm_and_api_docs(
    llm=OpenAI(),
    api_docs=api_docs
)
chain.run("查一下山东以旧换新政策")
```

---

### 8. 如何优化 LLM Agent 的性能？

**参考答案**（结合我的项目优化）：

#### 我的项目中的优化清单

| 优化方向 | 具体手段 | 效果 |
|---------|---------|------|
| **规划优化** | 快捷路径（问候语/实时查询直接返回） | 延迟降低 |
| **工具调用优化** | 意图分类器前置校验 | 无效调用降 40% |
| **缓存优化** | 联网搜索缓存（Redis 120分钟） | 成本降低 |
| **记忆优化** | 事实缓存 + 滑动窗口 | 上下文更准 |
| **降级优化** | 流式 → 非流式 → REST | 可用性 99.9% |
| **Prompt 优化** | 结构化提示词 + 少样本 | 准确率提升 |
| **模型优化** | 动态模型选择 + 绑定 | 成本/效果平衡 |

#### 通用优化方法
```
1. Prompt 优化
   ├─ 结构化输出（JSON）
   ├─ 少样本示例（Few-shot）
   └─ 思维链（CoT）

2. 记忆优化
   ├─ 向量检索历史（不是全塞）
   ├─ 摘要压缩
   └─ 遗忘机制（FIFO/LRU）

3. 工具优化
   ├─ 前置校验
   ├─ 结果缓存
   └─ 并行调用

4. 模型优化
   ├─ 大小模型搭配（小模型分类，大模型生成）
   ├─ 微调/蒸馏
   └─ 量化（INT4/INT8）

5. 架构优化
   ├─ 快捷路径
   ├─ 降级策略
   └─ 流式输出
```

---

### 9. LLM Agent 在企业应用中的典型场景有哪些？

**参考答案**（结合我的政策咨询项目）：

#### 企业 Agent 典型场景

| 场景 | 说明 | 类似我的项目中的 |
|------|------|-----------------|
| **客户服务/客服** | 7x24 小时智能客服，回答常见问题 | ✅ 政策咨询就是客服 |
| **知识管理/问答** | 企业知识库问答，RAG + Agent | ✅ 政策知识库 |
| **文档处理** | 合同审查、文档摘要、信息抽取 | ✅ 发票识别、文档摘要 |
| **数据分析** | 自然语言查数据库、生成报表 | ❌ 我的项目没做 |
| **DevOps/运维** | 日志分析、故障排查、自动修复 | ❌ 我的项目没做 |
| **内容生成** | 文案写作、邮件撰写、报告生成 | ❌ 我的项目没做 |
| **RPA 增强** | Agent 驱动 RPA，更灵活的自动化 | ❌ 我的项目没做 |

#### 我的政策咨询项目在企业中的定位
```
山东省商务厅
    ↓
政策文件（PDF/Word/网页）
    ↓
RAG 知识库（PostgreSQL + pgvector）
    ↓
Agent（ReAct + 工具 + 记忆）
    ↓
用户（消费者）咨询
    ├─→ 补贴计算（calculateSubsidy）
    ├─→ 联网搜索（webSearch）
    ├─→ 图片识别（多模态）
    └─→ 地图服务（amap-mcp）
```

---

## 总结：Agent 面试准备要点

### 必知必会
1. **Agent 基本概念**：LLM + Memory + Tools + Planning
2. **ReAct 框架**：先思考再行动，Reason + Act
3. **记忆分层**：短期/中期/长期，向量记忆
4. **工具调用**：Function Calling、MCP、前置校验
5. **主流框架**：LangChain 核心组件、LlamaIndex、AutoGPT/BabyAGI/CrewAI 思想

### 结合项目亮点（我的政策咨询）
- **意图分类器**：降无效调用 40%，面试官爱听
- **多级降级**：可用性 99.9%，工程能力体现
- **事实缓存**：结构化记忆，不是纯对话历史
- **ReAct 规划**：自己实现，不是调包，能讲清楚细节

### 面试技巧
- 先讲通用概念，再结合自己项目
- 说清楚"为什么这么做"，不是只说"做了什么"
- 讲踩过的坑（比如 Spring AI 流式工具调用 Bug），怎么解决的
