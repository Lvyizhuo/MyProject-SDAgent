# 修复管理员控制台问题实施计划

&gt; **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标：** 修复管理员控制台中的两个关键问题：1) 配置修改后右侧实时预览与调试功能正常工作，配置立即生效；2) 技能模块开关按钮在后端真实生效。

**架构：**
- 配置同步机制已存在（DynamicAgentConfigHolder + AgentConfigSyncService），需确保完整链路正常工作
- 技能开关通过 ToolStateManager 检查，需确保所有工具正确使用该管理器

**技术栈：** Spring Boot 3.4, React 19, PostgreSQL, Redis

---

## 任务 1：修复 WebSearchTool 中 ToolStateManager 可能为 null 的问题

**文件：**
- 修改: `backend/src/main/java/com/shandong/policyagent/tool/WebSearchTool.java:69-82`

**问题：** WebSearchTool 有一个备用构造函数，当使用该构造函数时 toolStateManager 为 null，可能导致技能开关检查失效。

**步骤 1：修改 WebSearchTool 构造函数**

将备用构造函数改为调用带所有参数的构造函数，确保 toolStateManager 始终被注入：

```java
public WebSearchTool(VectorStore vectorStore, ToolFailurePolicyCenter failurePolicyCenter,
                     ToolStateManager toolStateManager) {
    this.vectorStore = vectorStore;
    this.failurePolicyCenter = failurePolicyCenter != null ? failurePolicyCenter : new ToolFailurePolicyCenter();
    this.toolStateManager = toolStateManager;
    this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
}

// 移除旧的备用构造函数，让 Spring 自动注入
```

同时，在 null 检查时添加更安全的处理（如果 toolStateManager 为 null 则默认启用）：

```java
// 检查工具是否被管理员禁用
if (toolStateManager != null && !toolStateManager.isWebSearchEnabled()) {
    log.warn("联网搜索工具已被管理员禁用");
    return new SearchResponse("", List.of(), 0, "联网搜索功能当前已被管理员禁用，如需使用请联系管理员开启。");
}
```

**步骤 2：验证修复**

检查所有三个工具类（WebSearchTool、SubsidyCalculatorTool、FileParserTool）中的 toolStateManager 注入是否正确。

---

## 任务 2：为 ChatResponse 添加 usedTools 字段（用于前端显示工具调用）

**文件：**
- 修改: `backend/src/main/java/com/shandong/policyagent/model/ChatResponse.java`
- 修改: `backend/src/main/java/com/shandong/policyagent/service/ChatService.java`

**步骤 1：修改 ChatResponse 模型**

添加 `usedTools` 字段到 ChatResponse 类：

```java
/**
 * AI 回复内容
 */
private String content;

/**
 * 使用的工具列表
 */
private List<String> usedTools;

/**
 * 引用来源列表（RAG 检索结果）
 */
private List<Reference> references;
```

同时更新 @Builder 和构造函数。

**步骤 2：修改 ChatService 收集工具使用信息**

虽然我们无法直接从 ChatClient 获取工具调用信息，但可以为管理员测试接口添加一个简单的机制，或者在前端不依赖这个字段（因为当前前端代码中使用了 response.usedTools，但后端没有）。

实际上，更简单的方案是：**暂时从前端 ChatTestTab 移除对 usedTools 的依赖**，因为 Spring AI 的 ChatClient 不会直接返回工具调用列表给我们。

---

## 任务 3：修改前端 ChatTestTab 移除对 usedTools 的依赖

**文件：**
- 修改: `frontend/src/components/admin/ChatTestTab.jsx:51-60, 91-96`

**步骤 1：简化 ChatTestTab 消息处理**

由于后端 ChatResponse 没有 usedTools 字段，我们简化前端代码：

```javascript
try {
    const response = await adminApi.testAgentConfig(userText, sessionId);

    // Add a small delay for better UX
    setTimeout(() => {
        setMessages(prev => [...prev, {
            id: `ai-${Date.now()}`,
            role: 'assistant',
            content: response.content || '未返回内容'
            // 移除 usedTools
        }]);
        setLoading(false);
    }, 300);
}
```

同时移除显示工具调用的 UI：

```javascript
{/* 移除这部分
{msg.usedTools && msg.usedTools.length > 0 && (
    <div className="test-tools-used">
        <Bot size={12} style={{marginRight: '4px'}} />
        工具调用: {msg.usedTools.join(', ')}
    </div>
)}
*/}
```

---

## 任务 4：验证配置保存后立即生效的完整链路

**文件：**
- 检查: `backend/src/main/java/com/shandong/policyagent/controller/AgentConfigController.java`
- 检查: `backend/src/main/java/com/shandong/policyagent/service/AgentConfigSyncService.java`
- 检查: `backend/src/main/java/com/shandong/policyagent/config/DynamicAgentConfigHolder.java`
- 检查: `backend/src/main/java/com/shandong/policyagent/service/ChatService.java`

**验证步骤：**

1. 确认 `AgentConfigController.updateConfig()` 在保存后调用 `agentConfigSyncService.reloadFromDatabase()`
2. 确认 `AgentConfigSyncService.reloadFromDatabase()` 调用 `dynamicAgentConfigHolder.update(config)`
3. 确认 `ChatService.chat()` 在每次请求时都调用 `dynamicAgentConfigHolder.getSystemPrompt()`、`dynamicAgentConfigHolder.getModelName()`、`dynamicAgentConfigHolder.getTemperature()`
4. 确认所有三个工具在执行时都调用 `toolStateManager.isXxxEnabled()`

**当前代码已正确实现上述逻辑，此任务主要是验证和测试。**

---

## 任务 5：端到端测试

**步骤 1：启动后端服务**

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run
```

**步骤 2：启动前端服务**

```bash
cd frontend
npm install
npm run dev
```

**步骤 3：测试配置实时生效**

1. 访问管理员控制台 `/admin`
2. 使用 admin/admin 登录
3. 修改系统提示词，例如在开头添加 "[测试配置]"
4. 点击"保存"
5. 在右侧聊天测试中发送消息"你好"
6. 验证 AI 的响应反映了新的系统提示词

**步骤 4：测试技能开关**

1. 在配置面板中禁用"补贴计算器"
2. 点击"保存"
3. 在聊天测试中发送"帮我计算空调价格 3000 元的补贴"
4. 验证 AI 回复说补贴计算功能已禁用

---

## 总结

此计划修复以下问题：
1. WebSearchTool 中 ToolStateManager 可能为 null 的问题
2. 前端 ChatTestTab 对不存在的 usedTools 字段的依赖
3. 验证配置实时生效链路完整

所有修改保持最小化，不改变现有架构，仅修复发现的 bug。
