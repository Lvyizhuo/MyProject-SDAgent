# 牛客04-快手后端-Java开发一面面经

> 面试岗位：后端 Java 开发
>
> 面试公司：快手
>
> 面试形式：一面

---

## 面试问题

### 一、AI / Agent 相关（项目 + 原理）

#### 1. Agent 项目用什么做的，选型原因，Agent 框架了解吗？

**参考答案**：

**项目实现方案**：
我的项目没有使用第三方 Agent 框架（如 LangChain4j、LangChain），而是基于 **Spring AI** 自研了一套轻量级的 ReAct 框架。

**核心组件**：
- `ReActPlanningService` - 规划服务，生成执行计划
- `ToolIntentClassifier` - 工具意图分类器，参数校验拦截
- `AgentPlanParser` - 计划解析器
- `ChatService` - 计划执行器

**选型原因**：
1. **业务简单可控**：我的场景只需要 4 个工具（calculateSubsidy、webSearch、parseFile、amap-mcp），不需要复杂框架
2. **Spring AI 已有基础**：项目已用 Spring AI 做 ChatClient 和 RAG，在此基础上扩展更自然
3. **避免框架依赖**：自研更容易做降级和故障处理，LangChain4j 迭代快，API 不稳定
4. **调试友好**：自研代码完全可控，出问题容易排查

**对 Agent 框架的了解**：
- **LangChain4j**：Java 生态最成熟的 Agent 框架，支持工具调用、Memory、RAG 等
- **Spring AI**：官方提供了 `QuestionAnswerAdvisor`、`ToolCallAdvisor`，但完整 Agent 能力较弱
- **LangChain (Python)**：生态最丰富，Python 数据科学栈集成好
- **Semantic Kernel**：微软出品，多语言支持，企业级特性好

**企业级 Agent 框架通常提供**：
- 多步规划与修正（ReAct、Plan-and-Execute）
- 记忆管理（短期/长期记忆、记忆压缩）
- 工具调用与错误重试
- 多智能体协作（Multi-Agent）

---

#### 2. Agent 技术组成部分理解吗，讲讲执行链路？

**参考答案**：

**Agent 核心组成部分**：
```
┌─────────────────────────────────────────────────────────┐
│                        Agent                             │
├─────────────────────────────────────────────────────────┤
│  ┌──────────────┐    ┌──────────────┐                  │
│  │   Planning   │ →  │  Execution   │                  │
│  │   (规划)     │    │   (执行)     │                  │
│  └──────────────┘    └──────────────┘                  │
│         ↑                   ↓                            │
│  ┌──────────────┐    ┌──────────────┐                  │
│  │    Memory    │ ←  │    Tools     │                  │
│  │   (记忆)     │    │    (工具)     │                  │
│  └──────────────┘    └──────────────┘                  │
└─────────────────────────────────────────────────────────┘
```

**1. Planning（规划）**：
- 理解用户意图，分解为多步任务
- 决定是否需要调用工具，调用什么工具
- 我的项目实现：`ReActPlanningService.createPlan()`

**2. Memory（记忆）**：
- 短期记忆：对话历史（`MessageChatMemoryAdvisor` + Redis）
- 长期记忆：会话事实缓存（`SessionFactCacheService`）
- 我的项目：结构化事实（价格、地区、设备型号）单独存 Redis，TTL 7 天

**3. Tools（工具）**：
- 计算补贴、联网搜索、文件解析、地图查询
- 我的项目：`@Tool` 注解注册，Spring AI 自动调用

**4. Execution（执行）**：
- 按计划执行，每步观察结果，决定下一步
- 我的项目：`ChatService` 按 `steps` 顺序执行

---

**本项目的完整执行链路**（`ChatService.java`）：

```
1. 接收用户问题
   ↓
2. 会话事实缓存 mergeFacts() - 提取历史事实拼入提示词
   ↓
3. ReActPlanningService.createPlan() - 生成 JSON 格式计划
   ├─ summary: 问题总结
   ├─ needToolCall: 是否需要工具
   └─ steps: [ {action, toolHint, thought} ]
   ↓
4. 快捷路径判断
   ├─ 问候语 → 直接返回
   └─ 实时查询 → 直接走 webSearch
   ↓
5. ToolIntentClassifier.classify() - 工具意图分类
   ├─ 检查参数是否充足
   ├─ 不足 → block，返回澄清问题
   └─ 充足 → allow，继续执行
   ↓
6. 按计划执行 steps
   ├─ 流式 / 非流式判断
   ├─ 工具调用（Spring AI ToolCallChatOptions）
   └─ 结果收集
   ↓
7. 多级降级保护
   ├─ 流式工具调用失败 → 降级非流式
   ├─ RAG 失败 → 关闭 RAG 重试
   ├─ Spring AI 失败 → 原生 REST 调用
   └─ 全部失败 → 兜底提示
   ↓
8. 会话事实缓存 extractAndSaveFacts() - 提取新事实存 Redis
   ↓
9. 返回最终回复
```

---

#### 3. Skills 的真实逻辑怎么写的，明白和企业的差距吗？

**参考答案**：

**我的项目中 Skills（Tools）的实现**：

项目中用 Spring AI 的 `@Tool` 注解定义工具，共 4 个：

**1. calculateSubsidy（补贴计算）**：
```java
@Tool("calculateSubsidy")
public ToolResult calculateSubsidy(
    @Property(name = "productType", description = "产品类型：家电/汽车/数码") String productType,
    @Property(name = "price", description = "购买价格，单位元") Double price
) {
    // 规则引擎：根据产品类型和价格查表计算
    // 家电：上限 500 元，10% 补贴
    // 汽车：上限 3000-10000 元，分档
    // 数码：上限 300 元，8% 补贴
}
```

**2. webSearch（联网搜索）**：
```java
@Tool("webSearch")
public ToolResult webSearch(
    @Property(name = "query", description = "搜索关键词") String query
) {
    // 调用 Tavily API 搜索
    // 结果缓存 Redis 2 小时
    // 关键词：webSearch:cache:{query}
}
```

**3. parseFile（文件解析）**：
```java
@Tool("parseFile")
public ToolResult parseFile(
    @Property(name = "fileType", description = "文件类型：invoice/device") String fileType,
    @Property(name = "fileContent", description = "文件内容或描述") String fileContent
) {
    // 发票解析：提取金额、日期、销售方
    // 设备解析：提取品牌、型号、状态
}
```

**4. amap-mcp（地图查询）**：
```java
@Tool("amap-mcp")
public ToolResult amapMcp(
    @Property(name = "action", description = "操作类型") String action,
    @Property(name = "location", description = "经纬度或地址") String location
) {
    // 调用高德地图 API
    // 查询附近以旧换新门店
}
```

---

**和企业级 Skills 的差距**：

| 维度 | 我的实现 | 企业级实现 |
|------|----------|-----------|
| **参数校验** | `ToolIntentClassifier` 简单正则 | **JSON Schema + Pydantic/Hibernate Validator** |
| **错误处理** | 直接抛异常 | **重试策略（退避指数）+ 降级 + 熔断** |
| **权限控制** | 无 | **工具级权限 + 审计日志** |
| **超时控制** | 全局超时 | **工具级别独立超时配置** |
| **结果验证** | 不验证 | **结果 Schema 校验 + LLM 验证** |
| **状态管理** | 无状态 | **支持有状态工具（会话、事务）** |
| **并行调用** | 不支持 | **并行工具调用 + 依赖图编排** |
| **成本追踪** | 无 | **Token / API Call 成本统计** |

**企业级 Skill 示例**（伪代码）：
```java
@Tool(
    name = "calculateSubsidy",
    description = "计算补贴金额",
    timeout = "5s",
    maxRetries = 3,
    retryOn = {NetworkException.class},
    costPerCall = 0.01
)
public ToolResult calculateSubsidy(
    @NotNull
    @Pattern(regexp = "家电|汽车|数码")
    @Property(name = "productType") String productType,

    @NotNull
    @Min(0)
    @Property(name = "price") Double price
) {
    try (Transaction tx = beginTransaction()) {
        auditLog.record(user, "calculateSubsidy", productType, price);
        Subsidies result = subsidyEngine.calculate(productType, price);
        metrics.recordToolCall("calculateSubsidy", success, latency);
        tx.commit();
        return ToolResult.success(result);
    } catch (Exception e) {
        metrics.recordToolError("calculateSubsidy", e);
        throw e;
    }
}
```

---

#### 4. 项目里的难点（以 Agent、RAG 为例）

**参考答案**：

---

**难点一：Spring AI 流式模式下工具调用不稳定**

**问题现象**：
- 流式 + 工具调用时，`toolInput` 或 `toolName` 经常为 null
- 概率性出现，难以复现
- GitHub Issue 上确认是 Spring AI 1.0.3 的已知 bug

**排查过程**：
1. 看 Spring AI 源码，`AbstractToolCallSupport` 中流式解析有竞态条件
2. 复现：只要计划中包含工具调用，流式模式 70% 概率失败
3. 尝试升级版本到 1.0.4，问题仍存在

**解决方案**（多级降级）：
1. **前置检测**：`ReActPlanningService` 返回计划后，如果 `needToolCall=true`，直接不走流式，用非流式执行
2. **异常兜底**：流式执行时 `onErrorResume` 捕获异常，降级为非流式
3. **工具意图分类器**：参数不足时直接拦截，不走到工具调用那一步
4. **REST 兜底**：Spring AI 非流式也失败时，用 `RestTemplate` 直接调 DashScope API

**结果**：用户体验几乎不受影响，系统稳定性从 ~80% 提升到 99.9%

---

**难点二：RAG 检索准确率不高**

**问题现象**：
- 初期只用向量相似度搜索，Top-5 召回中经常有不相关文档
- 用户问"汽车补贴多少钱"，召回的是"家电补贴细则"

**优化方案**：
1. **向量重排序（Rerank）**：
   - 初筛：向量召回 Top-20（`candidateTopK=20`）
   - 精排：DashScope Rerank API 重新排序，返回 Top-5
   - 代码：`RerankingVectorStore.similaritySearch()`
   - 效果：准确率提升 ~25%

2. **合理的切片策略**：
   - 切片大小：1600 字符（默认 800 太小）
   - 切片重叠：300 字符（避免上下文丢失）
   - 代码：`RagConfig.Chunking`

3. **相似度阈值过滤**：
   - 阈值：0.7（`similarityThreshold=0.7`）
   - 低于阈值的文档直接丢弃，不注入提示词

4. **元数据过滤**：
   - 支持按 `category`、`source`、`publishDate` 过滤

**代码参考**（`RerankingVectorStore.java`）：
```java
@Override
public List<Document> similaritySearch(SearchRequest request) {
    // 1. 向量初筛：召回 candidateTopK=20 条
    SearchRequest candidateRequest = SearchRequest.from(request)
            .topK(Math.max(finalTopK, ragConfig.getRetrieval().getCandidateTopK()));
    List<Document> candidates = delegate.similaritySearch(candidateRequest);

    // 2. Rerank 精排：返回 Top-K=5 条
    List<Document> reranked = rerankService.rerank(
        request.getQuery(),
        candidates,
        finalTopK
    );
    return reranked;
}
```

---

**难点三：工具参数不足导致无效调用**

**问题现象**：
- 用户问"我买个手机能补多少"，LLM 直接调用 `calculateSubsidy`，但缺少 `price` 参数
- 工具抛异常，用户体验差

**解决方案**：`ToolIntentClassifier` 工具意图分类器
```java
// 在工具调用前先做规则拦截
public Classification classify(String userInput, ToolDefinition tool) {
    if (tool.name().equals("calculateSubsidy")) {
        // 检查是否有价格（\d{3,6} 元）
        boolean hasPrice = PRICE_PATTERN.matcher(userInput).find();
        boolean hasCategory = CATEGORY_PATTERN.matcher(userInput).find();
        if (!hasPrice || !hasCategory) {
            return Classification.block("请问您购买的产品价格是多少？");
        }
    }
    if (tool.name().equals("webSearch")) {
        // 检查是否有具体型号（iPhone 17 / 华为 Mate 60）
        boolean hasSpecificModel = ...;
        if (!hasSpecificModel) {
            return Classification.block("请问您想查询哪个具体型号？");
        }
    }
    return Classification.allow();
}
```

**效果**：无效工具调用降低约 40%

---

#### 5. 除此之外了解 AI 的什么？OpenCLAP、Transformer 等

**参考答案**：

---

**Transformer 架构**：
- **核心思想**：Self-Attention（自注意力），让模型看到全局上下文
- **Encoder-Decoder 结构**：
  - Encoder：把输入序列编码成向量表示
  - Decoder：自回归生成输出
- **Multi-Head Attention**：多头注意力，不同头关注不同模式
- **Layer Normalization** + **Residual Connection**：训练稳定
- **Positional Encoding**：给模型提供位置信息
- **变体**：
  - BERT：只用 Encoder，双向理解（填空式）
  - GPT：只用 Decoder，单向生成（自回归）
  - T5：Encoder-Decoder，统一成 text-to-text 格式

---

**LLM 训练流程**：
```
预训练 (Pre-training)
    ↓
指令微调 (SFT - Supervised Fine-Tuning)
    ↓
对齐训练 (RLHF / DPO)
    ↓
部署推理 (Inference)
```

- **预训练**：海量无标注数据，学习语言模型（Next Token Prediction）
- **SFT**：高质量标注数据，学会遵循指令
- **RLHF**：人类反馈强化学习，PPO 算法训练 Reward Model
- **DPO**：Direct Preference Optimization，简化版 RLHF，不用强化学习

---

**RAG 相关技术**：
- **向量数据库**：pgvector（我项目用）、Milvus、Qdrant、Weaviate、Chroma
- **嵌入模型**：text-embedding-ada-002（OpenAI）、text-embedding-v3（DashScope）、gte、bge
- **重排序（Rerank）**：CrossEncoder、Cohere Rerank、DashScope Rerank
- **混合检索**：BM25（关键词）+ 向量（语义）+ Reciprocal Rank Fusion（RRF）
- **Parent-Child 索引**：父文档粗粒度，子文档细粒度，召回子文档返回父文档

---

**Agent 相关概念**：
- **ReAct**：Reasoning + Acting，先想再做
- **Plan-and-Execute**：先规划，再执行
- **Function Calling / Tool Calling**：函数调用
- **Memory**：记忆（ConversationSummaryMemory、VectorStoreRetrieverMemory）
- **Multi-Agent**：多个智能体协作（CAMEL、AutoGen）

---

**OpenCLAP**：
> 这个我不太熟，可能是指某个特定框架或平台？如果是指 **OpenCL**（Open Computing Language），那是异构计算框架，用于 GPU/CPU 并行计算，AI 推理框架（TensorRT、ONNX Runtime）可能用它做加速。

---

**其他**：
- **LoRA / QLoRA**：参数高效微调，不用训全量参数
- **量化**：4-bit / 8-bit 量化，减少显存占用
- **KVCache**：KV 缓存，加速自回归生成
- **Speculative Decoding**：投机解码，小模型草稿大模型验证，加速推理
- **Function Calling**：函数调用，让 LLM 可以调用外部工具
- **Context Window**：上下文窗口（GPT-4 8K/32K/128K，Qwen 32K/128K）

---

### 二、Java 后端八股（基础偏易）

#### 1. Redis 简介，Redis 为什么快？

**参考答案**：

**Redis 简介**：
- **Remote Dictionary Server**，开源的内存数据结构存储系统
- **类型**：NoSQL 数据库，Key-Value 型
- **数据结构**：String、Hash、List、Set、Sorted Set、Bitmap、HyperLogLog、Geo、Stream
- **持久化**：RDB（快照）+ AOF（日志）
- **应用场景**：缓存、会话存储、排行榜、计数器、分布式锁、消息队列

---

**Redis 为什么快**：

| 原因 | 说明 |
|------|------|
| **1. 纯内存操作** | 数据存内存，内存读写速度 ~100ns，磁盘 ~10ms |
| **2. 单线程模型** | 避免上下文切换和竞态条件，不用加锁 |
| **3. I/O 多路复用** | epoll/kqueue，单线程处理大量并发连接 |
| **4. 高效的数据结构** | SDS、跳表、哈希表、压缩列表 |
| **5. 协议简单** | RESP（Redis Serialization Protocol），轻量易解析 |

---

**详细说明**：

**1. 纯内存操作**：
- Redis 所有数据都在内存中，读写不涉及磁盘 I/O
- 内存访问速度是磁盘的 10万 ~ 100万倍
- 持久化是异步的，不影响主线程读写

**2. 单线程模型**：
- Redis 6.0 之前：完全单线程
- Redis 6.0+：主线程处理命令，多线程处理 I/O 和过期键删除
- **为什么单线程还快**：
  - CPU 不是瓶颈，瓶颈在内存和网络
  - 避免多线程的上下文切换开销（一次上下文切换 ~1-2us）
  - 避免竞态条件，不用加锁（锁竞争反而慢）

**3. I/O 多路复用**：
- `epoll`（Linux）/ `kqueue`（FreeBSD）
- 单线程可以监听多个 socket，哪个就绪处理哪个
- 没有多线程开销，并发能力强

**4. 高效的数据结构**：
- **SDS（Simple Dynamic String）**：比 C 字符串多存 length，O(1) 取长度，二进制安全
- **Hash**：压缩列表（ziplist）→ 哈希表（hashtable）
- **Sorted Set**：压缩列表 → 跳表（skiplist），O(log n) 插入删除
- **List**：快速列表（quicklist），压缩列表 + 双向链表

**5. 协议简单**：
- RESP（Redis Serialization Protocol）：
  - `+` 简单字符串，`-` 错误，`:` 整数，`$` 批量字符串，`*` 数组
  - 人类可读，解析简单，没有 XML/JSON 那么重

---

**本项目中 Redis 的使用**：
1. **对话记忆**：`chat:memory:{conversationId}`，TTL 7 天
2. **会话事实缓存**：`chat:facts:{conversationId}`，TTL 7 天
3. **联网搜索缓存**：`webSearch:cache:{query}`，TTL 120 分钟

---

#### 2. MySQL 索引了解吗？联合索引介绍下，最左匹配前缀举例说明

**参考答案**：

---

**MySQL 索引简介**：

**索引类型**：
- **按数据结构分**：
  - B+Tree 索引（默认，InnoDB）
  - Hash 索引（Memory 引擎）
  - Full-Text 索引（全文检索）
  - R-Tree 索引（地理空间）
- **按逻辑分**：
  - 主键索引（PRIMARY KEY）
  - 唯一索引（UNIQUE）
  - 普通索引（INDEX）
  - 联合索引（Composite Index）
- **按物理存储分**：
  - 聚簇索引（Clustered Index，主键就是）
  - 非聚簇索引（Secondary Index，二级索引）

**InnoDB B+Tree 索引特点**：
- 只有叶子节点存数据，非叶子节点只存索引键
- 叶子节点之间用双向链表连接，范围查询快
- 主键索引就是聚簇索引，数据和索引在一起
- 二级索引叶子节点存主键值，回表（回主键索引查）

---

**联合索引（Composite Index）**：

**定义**：多个列组合在一起的索引
```sql
-- 示例：user 表 (name, age, city) 联合索引
CREATE INDEX idx_name_age_city ON user(name, age, city);
```

**结构**：B+Tree 中，索引键按顺序排序
- 先按 name 排序
- name 相同再按 age 排序
- name、age 都相同再按 city 排序

---

**最左匹配前缀原则**：

**定义**：查询时从联合索引的**最左列开始**，**连续匹配**，遇到范围查询（>、<、BETWEEN、LIKE 'xxx%'）就停止匹配。

---

**举例说明**：

**索引**：`idx_name_age_city (name, age, city)`

| 查询语句 | 是否走索引 | 说明 |
|---------|-----------|------|
| `WHERE name = '张三'` | ✅ 全走 | 匹配最左列 name |
| `WHERE name = '张三' AND age = 25` | ✅ 全走 | 连续匹配 name、age |
| `WHERE name = '张三' AND age = 25 AND city = '济南'` | ✅ 全走 | 连续匹配三列 |
| `WHERE age = 25` | ❌ 不走 | 跳过了最左列 name |
| `WHERE city = '济南'` | ❌ 不走 | 跳过了 name、age |
| `WHERE name = '张三' AND city = '济南'` | ✅ 部分走 | 只用到 name，age 断了，city 用不到 |
| `WHERE name = '张三' AND age > 25` | ✅ 部分走 | 用到 name、age，age 是范围查询，后面的 city 用不到 |
| `WHERE name = '张三' AND age > 25 AND city = '济南'` | ✅ 部分走 | 用到 name、age，city 用不到（范围查询中断） |
| `WHERE name LIKE '张%' AND age = 25` | ✅ 部分走 | name 前缀匹配，后面 age 可能用不到（看 MySQL 优化器） |
| `WHERE name = '张三' ORDER BY age` | ✅ 走 | 索引本身有序，不用额外排序 |
| `WHERE name = '张三' ORDER BY city` | ❌ 需排序 | age 断了，city 不是有序的，需要 filesort |

---

**验证方式**：`EXPLAIN` 看 `key_len` 和 `type`

```sql
EXPLAIN SELECT * FROM user WHERE name = '张三' AND age = 25;
-- key_len: 可能是 303 (name) + 5 (age) = 308，表示两列都用到了

EXPLAIN SELECT * FROM user WHERE name = '张三' AND city = '济南';
-- key_len: 只有 303，只用到 name 列
```

---

**联合索引设计原则**：
1. **区分度高的列放前面**（区分度：COUNT(DISTINCT col) / COUNT(*)）
2. **等值查询列放前面，范围查询列放后面**
3. **尽量覆盖索引**（查询的列都在索引里，不用回表）

---

**本项目中联合索引的使用**：
```sql
-- 知识库文档表，按 folder_id + created_at 查询
CREATE INDEX idx_folder_created ON knowledge_documents(folder_id, created_at);

-- URL 导入任务表，按状态 + 创建时间查询
CREATE INDEX idx_status_created ON url_import_jobs(status, created_at);
```

---

#### 3. Java 中 ThreadLocal 了解吗，怎么使用？

**参考答案**：

---

**ThreadLocal 是什么**：
- `java.lang.ThreadLocal`，线程本地变量
- **作用**：每个线程都有自己独立的变量副本，线程之间互不影响
- **核心思想**：空间换时间，避免线程安全问题，不用加锁

---

**数据结构**：
```
Thread
  └─ threadLocals (ThreadLocalMap)
        └─ Entry[]
              ├─ Entry(key=ThreadLocal1, value=value1)
              ├─ Entry(key=ThreadLocal2, value=value2)
              └─ ...
```

- `Thread` 类有一个 `threadLocals` 成员变量（ThreadLocalMap）
- `ThreadLocalMap` 是一个类似 HashMap 的结构，key 是 `ThreadLocal` 对象，value 是线程本地值
- `Entry` 继承 `WeakReference<ThreadLocal>`，key 是弱引用

---

**核心方法**：

| 方法 | 作用 |
|------|------|
| `set(T value)` | 设置当前线程的线程本地变量值 |
| `get()` | 获取当前线程的线程本地变量值 |
| `remove()` | 移除当前线程的线程本地变量值（**重要，防止内存泄漏**） |
| `initialValue()` | 初始值，默认 null，可重写 |

---

**使用示例**：

**示例 1：用户上下文传递**
```java
public class UserContext {
    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    public static void setUser(User user) {
        CURRENT_USER.set(user);
    }

    public static User getUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove(); // 必须清理！
    }
}

// 使用
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        User user = parseUserFromToken(request);
        UserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, ...) {
        UserContext.clear(); // 清理！
    }
}

// Service 层直接用
public class SomeService {
    public void doSomething() {
        User user = UserContext.getUser(); // 不用传参
    }
}
```

**示例 2：SimpleDateFormat 线程安全**
```java
// SimpleDateFormat 非线程安全，用 ThreadLocal 包装
public class DateUtil {
    private static final ThreadLocal<SimpleDateFormat> SDF =
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

    public static String format(Date date) {
        return SDF.get().format(date);
    }
}
```

**示例 3：事务上下文 / 数据库连接**
```java
// Spring 的 TransactionSynchronizationManager 内部就是用 ThreadLocal
// Mybatis 的 SqlSessionHolder 也是用 ThreadLocal
```

---

**本项目中的使用**：
- **Spring Security 上下文**：`SecurityContextHolder` 内部用 ThreadLocal 存认证信息
- **TransactionSynchronizationManager**：Spring 事务同步管理器，用 ThreadLocal 存事务资源

---

#### 4. Java 中 ThreadLocal 的内存泄漏问题讲一下

**参考答案**：

---

**什么是内存泄漏**：
- 不再使用的对象无法被 GC 回收，占用内存
- 严重时会导致 OOM（OutOfMemoryError）

---

**ThreadLocal 内存泄漏的原因**：

```
Thread（线程）
  └─ threadLocals (ThreadLocalMap)
        └─ Entry[]
              └─ Entry(key=ThreadLocal（弱引用）, value=Object（强引用）)
                    ↑                    ↑
                 WeakReference         强引用
```

**关键点**：
1. **Entry 的 key 是弱引用**（`WeakReference<ThreadLocal>`）
   - ThreadLocal 对象没有其他强引用时，GC 会回收它
   - key 变成 null
2. **Entry 的 value 是强引用**
   - value 不会被 GC，因为 Thread → ThreadLocalMap → Entry → value
   - 只要线程不结束，value 就一直占内存
3. **线程池场景**：线程生命周期很长（甚至和程序同生命周期），value 永远不会回收

---

**泄漏链路**：
```
Thread（存活）
    ↓ 强引用
ThreadLocalMap
    ↓ 强引用
Entry[]
    ↓ 强引用
Entry（key = null，因为 ThreadLocal 被 GC 了）
    ↓ 强引用
Value（泄漏了！）
```

---

**为什么 key 要设计成弱引用**：
- 如果 key 是强引用：ThreadLocal 对象不用了也不会被 GC，泄漏更严重
- 弱引用是"尽人事听天命"：至少 ThreadLocal 对象能被回收，只是 value 泄漏

---

**如何避免内存泄漏**：

**1. 手动调用 remove()（最重要！）**
```java
try {
    threadLocal.set(value);
    // do something
} finally {
    threadLocal.remove(); // 必须在 finally 中，确保一定执行
}
```

**2. 不要在 ThreadLocal 存大对象**
- 如果必须存，用完尽快 remove

**3. 线程池场景下尤其要注意**
- 线程会复用，用完必须清理，否则下一个任务会用到上一个任务的值（脏数据 + 内存泄漏）

---

**ThreadLocalMap 的自清理机制**：
- `get()`、`set()`、`remove()` 时会扫描 key == null 的 Entry，清理 value
- 但这是"撞大运"式的清理，如果不调用这些方法，还是会泄漏
- 不能依赖自清理，必须手动 `remove()`

---

**错误示例**：
```java
public class UserInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(...) {
        UserContext.setUser(user);
        return true;
    }

    // ❌ 没有 afterCompletion，没有 clear！内存泄漏
}
```

**正确示例**：
```java
public class UserInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(...) {
        UserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(...) {
        UserContext.clear(); // ✅ 清理
    }
}
```

---

#### 5. Java 中 Synchronized 和 ReentrantLock 区别

**参考答案**：

---

**共同点**：
- 都是可重入锁（Reentrant）：同一个线程可以多次获取同一把锁
- 都用于解决线程安全问题

---

**区别对比表**：

| 维度 | Synchronized | ReentrantLock |
|------|-------------|----------------|
| **层次** | Java 关键字，JVM 层面 | Java 类，API 层面（`java.util.concurrent`） |
| **锁释放** | 自动释放：代码块执行完 / 异常 | 手动释放：必须 `unlock()` 在 finally 中 |
| **锁获取** | 阻塞式获取，不可中断 | 可中断（`lockInterruptibly()`）、可超时（`tryLock(timeout)`） |
| **公平锁** | 非公平，不可选择 | 默认非公平，可构造公平锁（`new ReentrantLock(true)`） |
| **条件变量** | 没有，用 `wait()` / `notify()` | 有 `Condition`，支持多个条件队列 |
| **性能** | Java 6 后优化了，差不多 | 差不多，竞争激烈时可能稍好 |
| **可重入** | 支持 | 支持 |
| **锁状态** | 不可查询 | 可查询（`isLocked()`、`getHoldCount()`） |

---

**详细说明**：

---

**1. 用法区别**：

**Synchronized**：
```java
// 方式 1：同步代码块
synchronized (lockObject) {
    // 临界区
}

// 方式 2：同步实例方法
public synchronized void doSomething() {
    // 锁是 this
}

// 方式 3：同步静态方法
public static synchronized void doSomething() {
    // 锁是 Class 对象
}
```

**ReentrantLock**：
```java
ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // 临界区
} finally {
    lock.unlock(); // 必须手动释放！
}
```

---

**2. 可中断获取锁**：

**ReentrantLock**：
```java
ReentrantLock lock = new ReentrantLock();

try {
    lock.lockInterruptibly(); // 等待时可以被中断
    try {
        // do something
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    // 处理中断
    Thread.currentThread().interrupt();
}
```

**Synchronized**：无法中断，只能一直等

---

**3. 超时获取锁**：

**ReentrantLock**：
```java
ReentrantLock lock = new ReentrantLock();

if (lock.tryLock(3, TimeUnit.SECONDS)) { // 等 3 秒，拿不到就放弃
    try {
        // do something
    } finally {
        lock.unlock();
    }
} else {
    // 超时处理
}
```

**Synchronized**：做不到

---

**4. 公平锁**：

**ReentrantLock**：
```java
ReentrantLock fairLock = new ReentrantLock(true); // 公平锁
ReentrantLock unfairLock = new ReentrantLock(false); // 非公平（默认）
```
- 公平：先等待的先获取锁（FIFO）
- 非公平：后来的线程可能插队（性能好，因为唤醒线程有开销，插队直接用）

**Synchronized**：只有非公平，不可选

---

**5. Condition 条件变量**：

**ReentrantLock**：
```java
ReentrantLock lock = new ReentrantLock();
Condition notFull = lock.newCondition(); // 队列不满
Condition notEmpty = lock.newCondition(); // 队列不空

// 生产者
lock.lock();
try {
    while (queue.isFull()) {
        notFull.await(); // 等待 notFull 条件
    }
    queue.add(item);
    notEmpty.signal(); // 唤醒 notEmpty 等待的线程
} finally {
    lock.unlock();
}

// 消费者
lock.lock();
try {
    while (queue.isEmpty()) {
        notEmpty.await(); // 等待 notEmpty 条件
    }
    Item item = queue.take();
    notFull.signal(); // 唤醒 notFull 等待的线程
    return item;
} finally {
    lock.unlock();
}
```

**Synchronized**：只能用 `wait()` / `notify()` / `notifyAll()`，只能有一个条件队列

---

**如何选择**：
- 优先用 **Synchronized**：简单，不容易出错，Java 6+ 性能不差
- 需要高级特性时用 **ReentrantLock**：可中断、可超时、公平锁、Condition

---

### 三、算法手撕

#### 1. 最长回文子串 - 中心扩散法实现 + 时间复杂度分析

**题目描述**：
```
给定一个字符串 s，找到 s 中最长的回文子串。

示例 1：
输入：s = "babad"
输出："bab"（或 "aba"）

示例 2：
输入：s = "cbbd"
输出："bb"

示例 3：
输入：s = "a"
输出："a"
```

---

**中心扩散法思路**：
- 回文子串可以是**奇数长度**（中心是一个字符）或**偶数长度**（中心是两个字符之间）
- 枚举每个可能的中心位置，向两边扩散，直到不再是回文
- 记录最长的那个

---

**Java 代码实现**：

```java
class Solution {
    public String longestPalindrome(String s) {
        if (s == null || s.length() < 1) {
            return "";
        }

        int start = 0; // 最长回文的起始下标
        int end = 0;   // 最长回文的结束下标

        for (int i = 0; i < s.length(); i++) {
            // 情况 1：奇数长度，中心是 i
            int len1 = expandAroundCenter(s, i, i);
            // 情况 2：偶数长度，中心是 i 和 i+1
            int len2 = expandAroundCenter(s, i, i + 1);

            int len = Math.max(len1, len2);

            // 更新最长回文的起止下标
            if (len > end - start) {
                start = i - (len - 1) / 2;
                end = i + len / 2;
            }
        }

        // substring 左闭右开，所以 end + 1
        return s.substring(start, end + 1);
    }

    // 从 left 和 right 向两边扩散，返回回文长度
    private int expandAroundCenter(String s, int left, int right) {
        while (left >= 0 && right < s.length()
               && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        // 注意：退出循环时 left 和 right 已经越界或不相等了
        // 所以长度是 right - left - 1
        // 例如：left=0, right=0 → left=-1, right=1 → 长度 1 - (-1) - 1 = 1
        return right - left - 1;
    }
}
```

---

**时间复杂度分析**：

| 复杂度 | 分析 |
|--------|------|
| **时间复杂度** | **O(n²)** |
| **空间复杂度** | **O(1)** |

**详细说明**：

**时间复杂度 O(n²)**：
- 外层循环：枚举中心，O(n)
- 内层 `expandAroundCenter`：每次扩散最多 O(n)
- 总共：n × n = O(n²)

**空间复杂度 O(1)**：
- 只用到几个变量（start、end、len1、len2 等）
- 没有用额外的数组

---

**其他解法对比**：

| 解法 | 时间复杂度 | 空间复杂度 | 说明 |
|------|-----------|-----------|------|
| **中心扩散** | O(n²) | O(1) | 简单，面试常写这个 |
| **暴力枚举** | O(n³) | O(1) | 枚举所有子串，判断是否回文（O(n)），太慢 |
| **动态规划** | O(n²) | O(n²) | dp[i][j] 表示 s[i..j] 是否回文，空间大 |
| **Manacher** | O(n) | O(n) | 最优但复杂，面试一般不要求写 |

---

**测试用例**：
```java
// 测试 1
s = "babad"
输出："bab" 或 "aba"

// 测试 2
s = "cbbd"
输出："bb"

// 测试 3
s = "a"
输出："a"

// 测试 4
s = "ac"
输出："a" 或 "c"

// 测试 5
s = "aaaaa"
输出："aaaaa"
```

---

## 面试总结

**本场考点分布**：
1. **AI / Agent**：Agent 架构选型、执行链路、Skills 实现、项目难点、Transformer 原理
2. **Java 后端**：Redis 原理、MySQL 索引、ThreadLocal、Synchronized vs ReentrantLock
3. **算法**：最长回文子串（中心扩散法）

**Java 后端面试建议**：
- **Redis 是重点**：为什么快、数据结构、应用场景、持久化、缓存雪崩/击穿/穿透
- **MySQL 索引**：B+Tree、聚簇索引、联合索引最左匹配、explain 分析
- **并发**：ThreadLocal、synchronized、ReentrantLock、线程池、CAS、AQS
- **JVM**：内存区域、GC 算法、CMS/G1、类加载、双亲委派
- **Spring**：IOC、AOP、Bean 生命周期、事务、Spring Boot 自动装配
- **分布式**：CAP、BASE、分布式锁、分布式事务、消息队列
- **算法**：LeetCode Hot 100，链表、树、DP、双指针、回溯

**AI + Java 岗位加分项**：
- 能够把 AI 原理和项目实际结合（Rerank 为什么能提效）
- 懂 AI 系统工程化（降级、重试、缓存）
- 基础扎实（Java/MySQL/Redis/Spring）的同时有 AI 项目经验
