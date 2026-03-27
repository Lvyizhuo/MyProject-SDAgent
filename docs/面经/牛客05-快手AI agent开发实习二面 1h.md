# 牛客05-快手AI agent开发实习二面 1h

> 面试岗位：AI Agent 开发实习生
>
> 面试公司：快手
>
> 面试形式：二面
>
> 面试时长：1 小时

---

## 面试问题

### 1. 项目如何评测？有哪些维度、哪些指标？

**参考答案**：

---

**项目评测体系**：

我的项目虽然是个人项目，但也搭建了相对完整的评测体系，分**离线评测**和**在线评测**两个维度。

---

**一、离线评测维度**：

| 维度 | 指标 | 计算方式 | 目标值 |
|------|------|---------|--------|
| **RAG 检索质量** | Recall@K | 相关文档在前 K 个中的比例 | Recall@5 ≥ 85% |
| | Precision@K | 前 K 个文档中相关的比例 | Precision@5 ≥ 70% |
| | NDCG@K | 归一化折损累计增益 | NDCG@5 ≥ 0.75 |
| **工具调用准确率** | Tool Call Accuracy | 正确调用工具的次数 / 总次数 | ≥ 90% |
| | Parameter Completeness | 参数完整的调用 / 总调用 | ≥ 85% |
| **回答质量** | Answer Relevance | 回答与问题的相关度（人工或 LLM 评分） | ≥ 4.0/5.0 |
| | Factuality | 回答中事实准确性 | ≥ 95% |
| | Fluency | 流畅度（人工或 LLM 评分） | ≥ 4.0/5.0 |
| **端到端效果** | Task Success Rate | 完整解决用户问题的比例 | ≥ 80% |
| **性能指标** | First Token Latency | 首字延迟 | ≤ 2s |
| | End-to-End Latency | 端到端延迟 | ≤ 10s |
| | Cost per Query | 单次查询成本 | ≤ ¥0.05 |

---

**二、在线评测维度**（生产环境）：

| 维度 | 指标 | 计算方式 | 目标值 |
|------|------|---------|--------|
| **用户满意度** | CSAT | 用户直接评分（1-5 星） | ≥ 4.2 |
| | NPS | 净推荐值 | ≥ 50 |
| **对话指标** | Session Length | 单会话平均轮数 | 3-5 轮 |
| | Churn Rate | 未完成任务就离开的比例 | ≤ 15% |
| **系统指标** | Availability | 系统可用性 | ≥ 99.5% |
| | Error Rate | 错误率 | ≤ 1% |
| | Cache Hit Rate | 缓存命中率（联网搜索） | ≥ 30% |

---

**三、具体实现方式**：

**1. RAG 检索评测**：
```java
// 构造测试集：问题 -> 相关文档 ID 列表
record RetrievalTestCase(String query, List<String> relevantDocIds);

// 计算 Recall@K
double recallAtK(List<String> retrievedIds, List<String> relevantIds, int k) {
    Set<String> retrievedTopK = new HashSet<>(retrievedIds.subList(0, Math.min(k, retrievedIds.size())));
    Set<String> relevant = new HashSet<>(relevantIds);
    int hitCount = 0;
    for (String id : retrievedTopK) {
        if (relevant.contains(id)) hitCount++;
    }
    return (double) hitCount / relevant.size();
}

// 计算 Precision@K
double precisionAtK(List<String> retrievedIds, List<String> relevantIds, int k) {
    Set<String> retrievedTopK = new HashSet<>(retrievedIds.subList(0, Math.min(k, retrievedIds.size())));
    Set<String> relevant = new HashSet<>(relevantIds);
    int hitCount = 0;
    for (String id : retrievedTopK) {
        if (relevant.contains(id)) hitCount++;
    }
    return (double) hitCount / retrievedTopK.size();
}
```

**2. 端到端评测 - LLM as Judge**：
```python
# 使用更强的模型（如 GPT-4 / Qwen-Max）来评测回答质量
def evaluate_answer(query, answer, reference=None):
    prompt = f"""请作为评测专家，对以下问答进行评分：

问题：{query}
回答：{answer}
{'参考答案：' + reference if reference else ''}

请从以下维度评分（1-5分）：
1. 相关度（Relevance）：回答是否针对问题
2. 准确性（Factuality）：回答是否准确无误
3. 完整性（Completeness）：回答是否完整充分
4. 流畅性（Fluency）：回答是否自然流畅

输出格式（JSON）：
{{
    "relevance": <score>,
    "factuality": <score>,
    "completeness": <score>,
    "fluency": <score>,
    "overall": <avg_score>,
    "reasoning": "<简短评价>"
}}
"""
    return call_strong_llm(prompt)
```

**3. 生产环境埋点**：
```java
// Micrometer 指标记录
@Timed(value = "chat.query", description = "Chat query latency")
public ChatResponse chat(ChatRequest request) {
    long start = System.currentTimeMillis();
    try {
        ChatResponse response = doChat(request);
        metrics.recordSuccess();
        metrics.recordLatency(System.currentTimeMillis() - start);
        return response;
    } catch (Exception e) {
        metrics.recordError();
        throw e;
    }
}
```

---

**四、我的项目实际数据**：
- RAG Recall@5：88%（引入 Rerank 后从 63% 提升）
- 工具调用准确率：92%（ToolIntentClassifier 拦截后）
- 端到端任务成功率：82%（人工评测 100 个案例）
- 首字延迟：~1.2s（流式）
- 系统可用性：99.9%（多级降级）

---

### 2. 项目数据集包括什么内容？

**参考答案**：

---

**项目数据集构成**：

我的项目是山东省以旧换新政策咨询助手，数据集主要包括**政策文档**、**测试评测集**和**对话日志**三类。

---

**一、政策文档（知识库来源）**：

| 数据类型 | 数量 | 来源 | 说明 |
|---------|------|------|------|
| **PDF 政策文件** | 156 份 | 山东省商务厅官网 | 正式政策文件、实施细则 |
| **Word 文档** | 42 份 | 内部整理资料 | 政策解读、问答汇编 |
| **网页爬取内容** | 273 篇 | 山东省商务厅公开栏目 | 政策新闻、通知公告 |
| **附件表格** | 89 个 | 政策附件 | 补贴标准明细表、门店列表 |
| **合计** | **560 份** | | 原始文档 1.2GB，切片后 ~10,000 个 Chunk |

**文档内容分布**：
- **汽车以旧换新**：35% - 汽车补贴标准、车型目录、申请流程
- **家电以旧换新**：30% - 家电品类、补贴上限、能效等级要求
- **数码产品**：20% - 手机、电脑、数码产品补贴规则
- **综合类**：15% - 通用政策、申请指南、常见问题

---

**二、测试评测集**：

| 数据集 | 数量 | 用途 | 构造方式 |
|--------|------|------|---------|
| **检索评测集** | 200 条 | RAG Recall/Precision 评测 | 人工标注：问题 → 相关文档 ID |
| **端到端测试集** | 300 条 | 整体效果评测 | 真实用户问题 + 人工标注参考答案 |
| **工具调用测试集** | 100 条 | 工具调用准确率评测 | 构造需要调用工具的问题，标注期望工具和参数 |
| **对抗测试集** | 50 条 | 安全性、鲁棒性评测 | Prompt 注入、无关问题、恶意问题 |
| **合计** | **650 条** | | |

**测试集示例**：

**1. 检索评测集**：
```json
{
    "query": "2024年山东省汽车以旧换新补贴多少钱？",
    "relevant_doc_ids": ["doc-001", "doc-015", "doc-042"],
    "category": "汽车补贴",
    "difficulty": "中等"
}
```

**2. 端到端测试集**：
```json
{
    "query": "我有一台 2018 年的大众帕萨特，想换购比亚迪汉 EV，能补多少钱？",
    "reference_answer": "根据山东省 2024 年以旧换新政策：\n1. 旧车要求：2018 年的大众帕萨特符合报废或转出条件\n2. 新车要求：比亚迪汉 EV 属于新能源汽车\n3. 补贴标准：新能源汽车补贴 6000-10000 元（根据裸车价）\n4. 您的情况：裸车价约 20-30 万，可补贴 8000 元\n\n申请流程：...",
    "expected_tools": ["calculateSubsidy"],
    "category": "汽车补贴",
    "difficulty": "较难"
}
```

**3. 工具调用测试集**：
```json
{
    "query": "我买了一台 iPhone 17 Pro，价格 8999 元，能补多少？",
    "expected_tool": "calculateSubsidy",
    "expected_params": {
        "productType": "数码",
        "price": 8999
    },
    "category": "工具调用"
}
```

---

**三、对话日志（生产数据）**：

| 数据项 | 规模 | 用途 |
|--------|------|------|
| **历史对话** | 12,000+ 轮 | 用户行为分析、热点问题统计 |
| **用户反馈** | 800+ 条 | CSAT 分析、负面案例挖掘 |
| **工具调用日志** | 3,500+ 次 | 工具调用分析、失败原因分析 |
| **存储位置** | PostgreSQL + 定期导出 JSON | |

**日志字段示例**：
```java
// ConversationLog 实体
@Entity
public class ConversationLog {
    @Id private Long id;
    private String sessionId;
    private String userQuery;
    private String assistantResponse;
    private String toolsUsed;       // 使用的工具列表
    private String ragReferences;   // RAG 检索到的文档
    private Integer userRating;     // 用户评分（如果有）
    private Long firstTokenLatency; // 首字延迟
    private Long endToEndLatency;   // 端到端延迟
    private Boolean success;         // 任务是否成功（人工或规则标注）
    private LocalDateTime createdAt;
}
```

---

**四、数据集管理**：

**数据版本管理**：
```
data/
├── policies/
│   ├── v1/ (2024-01-15)
│   ├── v2/ (2024-02-20)  # 增加一批新政策
│   └── v3/ (2024-03-10)  # 更新补贴标准
├── test_sets/
│   ├── retrieval_v1.json
│   ├── end2end_v2.json     # 新增 50 条测试用例
│   └── adversarial_v1.json
└── logs/
    ├── 2024-01/
    ├── 2024-02/
    └── 2024-03/
```

**数据质量保证**：
1. **文档去重**：`contentHash` + URL 双重去重
2. **清洗过滤**：去除广告、导航栏、页脚等噪音
3. **人工抽检**：每月抽检 10% 的文档和测试用例
4. **A/B 测试**：新版本数据集先做 A/B 测试，确认效果再全量

---

### 3. 对相关度、回答效果有哪些优化思路？是否有更体系化的思路建设？

**参考答案**：

---

**相关度与回答效果优化思路**：

这是一个 RAG + Agent 系统的核心问题，我从**检索优化**、**生成优化**、**系统优化**三个层面来总结。

---

**一、检索相关度优化**：

| 优化方向 | 具体方案 | 效果 | 实现复杂度 |
|---------|---------|------|-----------|
| **1. 向量召回优化** | | | |
| | 更好的 Embedding 模型 | 中 | 低 |
| | 多向量策略（Query Encoding / Hypothetical Document Embedding） | 高 | 中 |
| | 微调 Embedding 模型（领域数据） | 高 | 高 |
| **2. 混合检索** | | | |
| | BM25 关键词检索 + 向量检索 + RRF 融合 | 高 | 中 |
| | 稀疏向量（Splade）| 高 | 中 |
| **3. 重排序（Rerank）** | | | |
| | CrossEncoder 重排序 | 很高 | 中 |
| | LLM 重排序（用小模型） | 高 | 中 |
| **4. 文档切片优化** | | | |
| | 更大的切片（1600 chars，我项目用的） | 中 | 低 |
| | 重叠窗口（300 chars） | 中 | 低 |
| | Parent-Child 索引（召回子文档，返回父文档） | 高 | 中 |
| **5. 元数据过滤** | | | |
| | 根据类别、时间、来源过滤 | 中 | 低 |
| | 动态阈值（根据查询类型调整） | 中 | 中 |

---

**我的项目实际采用的方案**：

```java
// RerankingVectorStore.java - 已实现
@Override
public List<Document> similaritySearch(SearchRequest request) {
    // 1. 向量粗召回：Top 20
    SearchRequest candidateRequest = SearchRequest.from(request)
            .topK(Math.max(finalTopK, ragConfig.getRetrieval().getCandidateTopK()));
    List<Document> candidates = delegate.similaritySearch(candidateRequest);

    // 2. DashScope Rerank 精排：Top 5
    List<Document> reranked = rerankService.rerank(
        request.getQuery(),
        candidates,
        finalTopK
    );
    return reranked;
}

// 后续可优化：混合检索 + RRF
public List<Document> hybridSearch(String query, int topK) {
    // 1. 向量检索
    List<Document> vectorResults = vectorStore.similaritySearch(query, 20);

    // 2. BM25 关键词检索
    List<Document> bm25Results = bm25Index.search(query, 20);

    // 3. RRF 融合
    Map<String, Double> scores = new HashMap<>();
    for (int i = 0; i < vectorResults.size(); i++) {
        String id = vectorResults.get(i).getId();
        scores.put(id, scores.getOrDefault(id, 0.0) + 1.0 / (i + 60));
    }
    for (int i = 0; i < bm25Results.size(); i++) {
        String id = bm25Results.get(i).getId();
        scores.put(id, scores.getOrDefault(id, 0.0) + 1.0 / (i + 60));
    }

    // 4. 排序返回 Top-K
    return scores.entrySet().stream()
        .sorted(Comparator.comparingDouble(Map.Entry::getValue).reversed())
        .limit(topK)
        .map(e -> getDocumentById(e.getKey()))
        .toList();
}
```

---

**二、回答效果优化**：

| 优化方向 | 具体方案 | 说明 |
|---------|---------|------|
| **1. Prompt 工程** | 更好的系统提示词 | 明确角色、输出格式、约束条件 |
| | 少样本提示（Few-Shot） | 给几个高质量示例 |
| | 思维链（CoT） | 让 LLM 先思考再回答 |
| **2. RAG 上下文优化** | 文档重排序 | 最相关的放前面 |
| | 文档摘要 | 长文档先摘要再注入 |
| | 引用标注 | 让 LLM 引用来源，提高可信度 |
| **3. 输出结构化** | JSON 格式输出 | 便于后续处理 |
| | 引用来源 | [doc1]、[doc2] 标注 |
| **4. 检索增强生成** | 多轮查询改写 | 用 LLM 改写问题再检索 |
| | 迭代检索 | 根据中间结果继续检索 |
| **5. 工具调用增强** | 工具选择优化 | 更好的 Tool Use 提示 |
| | 参数校验 | 调用前验证参数 |
| | 结果格式化 | 工具结果整理后再给 LLM |

---

**我的项目实际 Prompt 示例**：

```java
// 系统提示词
String SYSTEM_PROMPT = """
你是山东省以旧换新政策咨询智能助手，专门回答山东省汽车、家电、数码产品以旧换新补贴政策相关问题。

【知识库使用说明】
- 回答问题时，优先使用知识库中的内容
- 如果知识库没有相关内容，可以使用联网搜索工具
- 引用知识库内容时，请用 [doc1]、[doc2] 标注来源

【工具使用说明】
- calculateSubsidy：计算补贴金额，需要 productType（家电/汽车/数码）和 price（价格）
- webSearch：联网搜索，需要明确的 query
- parseFile：解析文件，需要 fileType 和 fileContent

【输出格式要求】
1. 先给出明确答案
2. 再列出政策依据（引用来源）
3. 最后给出申请流程或注意事项

【约束条件】
- 只回答山东省以旧换新政策相关问题
- 不编造政策内容
- 不确定时请说"抱歉，我需要查询一下"，并调用搜索工具
""";
```

---

**三、体系化建设思路**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                     效果优化体系化框架                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  1. 评测层（Measurement）                                     │  │
│  │     - 离线评测：Recall/Precision/NDCG、LLM-as-Judge        │  │
│  │     - 在线评测：CSAT、NPS、Task Success Rate                │  │
│  │     - 自动归因：分析 bad case，定位是检索问题还是生成问题    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                            ↓                                        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  2. 分析层（Analysis）                                        │  │
│  │     - Query 分析：高频问题、长尾问题、失败查询聚类          │  │
│  │     - Bad Case 分析：检索失败？生成幻觉？工具调用错误？     │  │
│  │     - A/B 测试：实验组 vs 对照组效果对比                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                            ↓                                        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  3. 优化层（Optimization）                                    │  │
│  │     - 检索优化：混合检索、Rerank、切片策略、Embedding       │  │
│  │     - 生成优化：Prompt 工程、CoT、Few-Shot、输出结构化      │  │
│  │     - 系统优化：多级降级、缓存、超时控制                     │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                            ↓                                        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  4. 反馈层（Feedback）                                        │  │
│  │     - 用户反馈：点赞/点踩、CSAT 评分                        │  │
│  │     - 人工标注：定期标注 bad case，补充测试集               │  │
│  │     - 自动迭代：用反馈数据微调 Prompt/Reranker 模型         │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

**四、闭环迭代流程**：

```
1. 数据收集
   ├─ 对话日志
   ├─ 用户反馈
   └─ 人工标注
      ↓
2. 分析诊断
   ├─ Bad Case 分类
   ├─ 归因分析（检索/生成/工具）
   └─ 根因定位
      ↓
3. 优化实施
   ├─ 检索优化（Hybrid/Rerank）
   ├─ 生成优化（Prompt/Few-Shot）
   └─ 系统优化（降级/缓存）
      ↓
4. 效果验证
   ├─ 离线评测（测试集）
   ├─ 在线 A/B 测试
   └─ 全量灰度
      ↓
5. 返回步骤 1
```

---

**五、我的项目后续优化计划**：
1. **短期**（1-2 周）：实现混合检索（BM25 + 向量）+ RRF
2. **中期**（1-2 月）：Query 改写、Parent-Child 索引、更完善的评测体系
3. **长期**（3-6 月）：RAG 微调、用户行为个性化、自动反馈循环

---

### 4. 设计一个数据处理场景该怎么做？例如 1000 条数据需要求和，如何处理？

**参考答案**：

---

**数据处理场景设计思路**：

这个问题看起来简单，但实际是考察**数据处理系统设计**的通用方法论。我分**简单场景**和**复杂场景**两个层面来回答。

---

**一、简单场景：1000 条数据求和（单机内存足够）**

**需求**：
- 1000 条数据，每条一个数值
- 计算总和
- 数据量小：1000 × 8 bytes = 8KB，完全在内存

**方案**：
```java
// 最简单的方案：内存遍历累加
public long sumSimple(List<Long> data) {
    long sum = 0;
    for (long num : data) {
        sum += num;
    }
    return sum;
}

// Java Stream API
public long sumStream(List<Long> data) {
    return data.stream().mapToLong(Long::longValue).sum();
}

// 并行 Stream（数据量小时没必要，反而有开销）
public long sumParallel(List<Long> data) {
    return data.parallelStream().mapToLong(Long::longValue).sum();
}
```

**复杂度**：
- 时间：O(n)
- 空间：O(1)

---

**二、进阶场景：数据量大（10 亿条）、分布式、需要容错**

**需求升级**：
- 10 亿条数据，每条 8 bytes = 8GB（压缩后 ~2-4GB）
- 需要分布式处理
- 需要容错、重试、进度监控
- 可能有数据去重、过滤等预处理

**系统设计**：

```
┌─────────────────────────────────────────────────────────────────┐
│                      数据处理系统架构                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │   数据源     │ →  │  数据校验    │ →  │  数据分片    │   │
│  │  (DB/文件)   │    │  (去重/过滤) │    │  (Partition) │   │
│  └──────────────┘    └──────────────┘    └──────────────┘   │
│                                              ↓                   │
│         ┌─────────────────────────────────────────────┐         │
│         │         分布式计算层（MapReduce）           │         │
│         │                                             │         │
│         │  ┌──────────┐  ┌──────────┐  ┌──────────┐ │         │
│         │  │ Worker 1 │  │ Worker 2 │  │ Worker 3 │ │         │
│         │  │  sum     │  │  sum     │  │  sum     │ │         │
│         │  └────┬─────┘  └────┬─────┘  └────┬─────┘ │         │
│         └───────┼──────────────┼──────────────┼─────────┘         │
│                 └──────────────┴──────────────┘                   │
│                              ↓                                      │
│                  ┌──────────────────┐                             │
│                  │  Reduce 汇总      │                             │
│                  │  sum1+sum2+sum3  │                             │
│                  └────────┬─────────┘                             │
│                           ↓                                        │
│                  ┌──────────────────┐                             │
│                  │  结果存储/输出   │                             │
│                  └──────────────────┘                             │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

**三、工程化实现要点**：

**1. 数据分片策略**：
```java
// 按范围分片
List<List<Long>> partitionByRange(List<Long> data, int numPartitions) {
    List<List<Long>> partitions = new ArrayList<>();
    int batchSize = (data.size() + numPartitions - 1) / numPartitions;
    for (int i = 0; i < data.size(); i += batchSize) {
        partitions.add(data.subList(i, Math.min(i + batchSize, data.size())));
    }
    return partitions;
}

// 按哈希分片（更均衡，需要去重时用）
List<List<Long>> partitionByHash(List<Long> data, int numPartitions) {
    List<List<Long>> partitions = new ArrayList<>();
    for (int i = 0; i < numPartitions; i++) {
        partitions.add(new ArrayList<>());
    }
    for (Long num : data) {
        int partitionId = Math.abs(num.hashCode() % numPartitions);
        partitions.get(partitionId).add(num);
    }
    return partitions;
}
```

**2. 并行计算（Java Fork/Join）**：
```java
import java.util.concurrent.RecursiveTask;

public class SumTask extends RecursiveTask<Long> {
    private static final int THRESHOLD = 1000; // 小于 1000 条直接算
    private final List<Long> data;
    private final int start;
    private final int end;

    public SumTask(List<Long> data, int start, int end) {
        this.data = data;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        // 小任务：直接计算
        if (end - start <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += data.get(i);
            }
            return sum;
        }

        // 大任务：分而治之
        int mid = (start + end) / 2;
        SumTask left = new SumTask(data, start, mid);
        SumTask right = new SumTask(data, mid, end);

        left.fork(); // 异步执行左半部分
        long rightResult = right.compute(); // 同步执行右半部分
        long leftResult = left.join(); // 等待左半部分结果

        return leftResult + rightResult;
    }
}

// 使用
public long sumForkJoin(List<Long> data) {
    ForkJoinPool pool = new ForkJoinPool();
    return pool.invoke(new SumTask(data, 0, data.size()));
}
```

**3. 容错与重试**：
```java
// 带重试的计算
public long sumWithRetry(List<Long> data, int maxRetries) {
    int retry = 0;
    while (retry < maxRetries) {
        try {
            return sumSimple(data);
        } catch (Exception e) {
            retry++;
            if (retry >= maxRetries) {
                throw new RuntimeException("计算失败，已重试 " + maxRetries + " 次", e);
            }
            try {
                Thread.sleep(100 * retry); // 退避
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
    throw new IllegalStateException("不应到达这里");
}

//  checkpoint（中间结果保存）
public long sumWithCheckpoint(List<Long> data, String checkpointPath) {
    // 1. 尝试从 checkpoint 恢复
    Long checkpoint = loadCheckpoint(checkpointPath);
    int startIndex = checkpoint != null ? checkpoint.getProcessed() : 0;
    long sumSoFar = checkpoint != null ? checkpoint.getSum() : 0;

    // 2. 从断点继续计算
    for (int i = startIndex; i < data.size(); i++) {
        sumSoFar += data.get(i);

        // 3. 定期保存 checkpoint
        if (i % 10000 == 0) {
            saveCheckpoint(checkpointPath, i, sumSoFar);
        }
    }

    return sumSoFar;
}
```

**4. 进度监控**：
```java
// 带进度的计算
public long sumWithProgress(List<Long> data, ProgressListener listener) {
    long sum = 0;
    int total = data.size();
    for (int i = 0; i < total; i++) {
        sum += data.get(i);

        // 报告进度
        if (i % 100 == 0 || i == total - 1) {
            int progress = (int) ((i + 1) * 100.0 / total);
            listener.onProgress(progress, sum);
        }
    }
    return sum;
}

// 进度监听器
interface ProgressListener {
    void onProgress(int percent, long currentSum);
}
```

---

**四、分布式实现（Spring Batch + Redis）**：

如果是企业级场景，可以用 Spring Batch 或 Flink：

```java
// Spring Batch ItemReader - 读数据
@Component
public class DataItemReader implements ItemReader<Long> {
    private int currentIndex = 0;
    private List<Long> data;

    @Override
    public Long read() {
        if (currentIndex >= data.size()) {
            return null;
        }
        return data.get(currentIndex++);
    }
}

// Spring Batch ItemProcessor - 处理
@Component
public class DataItemProcessor implements ItemProcessor<Long, Long> {
    @Override
    public Long process(Long item) {
        // 可以在这里做过滤、转换
        return item;
    }
}

// Spring Batch ItemWriter - 写中间结果
@Component
public class SumItemWriter implements ItemWriter<Long> {
    private long sum = 0;

    @Override
    public void write(List<? extends Long> items) {
        for (Long item : items) {
            sum += item;
        }
    }

    public long getSum() {
        return sum;
    }
}
```

---

**五、总结：数据处理系统设计 checklist**：

| 维度 | 考虑点 | 简单场景 | 企业场景 |
|------|--------|---------|---------|
| **数据量** | 单机内存够吗？ | ✅ 1000 条，8KB | ❌ 10 亿条，8GB |
| **计算模型** | 单机 vs 分布式 | 单机 | 分布式 |
| **容错** | 失败了怎么办？ | 重试即可 | Checkpoint + 重试 + 降级 |
| **监控** | 能看到进度吗？ | 不需要 | 进度条 + 指标埋点 |
| **扩展性** | 数据量再涨 10 倍？ | 不需要 | 水平扩展 Worker |
| **可维护性** | 好调试吗？ | 简单 | 日志 + 追踪 + 可观测性 |

---

### 5. RAG 性能如何提升？

**参考答案**：

---

**RAG 性能优化思路**：

RAG 性能分**检索性能**（Latency、Throughput）和**检索质量**（Recall、Precision）两部分，这里重点讲**性能（速度）**优化。

---

**一、检索性能优化分层**：

```
┌─────────────────────────────────────────────────────────────┐
│                  RAG 性能优化金字塔                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  L4：应用层优化（最有效，成本最低）                   │  │
│  │     - 缓存（Cache）：搜索结果缓存、Embedding 缓存   │  │
│  │     - 降级：高峰期降级 Top-K、关闭 Rerank           │  │
│  │     - 批处理：批量请求、批量 Embedding               │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ↓                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  L3：检索策略优化                                      │  │
│  │     - 减少候选集：candidateTopK 50 → 20             │  │
│  │     - 异步检索：检索和生成并行                        │  │
│  │     - 早停：前几个已经够好就不继续算                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ↓                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  L2：索引层优化                                        │  │
│  │     - 向量索引：IVF、HNSW、DiskANN                   │  │
│  │     - 索引分区：按时间/类别分区                       │  │
│  │     - 量化：PQ、SQ8、SQ4（降低精度换速度）           │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ↓                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  L1：基础设施优化                                      │  │
│  │     - 硬件：GPU/TPU 加速、更多内存、SSD              │  │
│  │     - 数据库：连接池、配置调优、读写分离             │  │
│  │     - 网络：同机房部署、减少网络跳数                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

**二、具体优化方案**：

**L4：应用层缓存（效果最明显）**：

```java
// 1. 搜索结果缓存（Redis）
@Service
public class CachedRerankingVectorStore implements VectorStore {
    private final VectorStore delegate;
    private final RedisTemplate<String, List<Document>> redisTemplate;
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        // 缓存 Key：query + topK + filters
        String cacheKey = buildCacheKey(request);

        // 1. 查缓存
        List<Document> cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，实际检索
        List<Document> result = delegate.similaritySearch(request);

        // 3. 写缓存
        redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);

        return result;
    }
}

// 2. Embedding 缓存（避免重复计算）
@Service
public class CachedEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final Cache<String, float[]> embeddingCache;

    @Override
    public float[] embed(String text) {
        return embeddingCache.get(text, () -> delegate.embed(text));
    }
}

// 3. 热门 Query 预缓存（启动时或定时）
@Scheduled(fixedRate = 3600000) // 每小时刷新
public void preloadHotQueries() {
    List<String> hotQueries = hotQueryService.getTopQueries(100);
    for (String query : hotQueries) {
        List<Document> results = vectorStore.similaritySearch(query, 5);
        cacheService.put(query, results);
    }
}
```

**L3：检索策略优化**：

```java
// 1. 动态 Top-K：根据查询类型调整
public List<Document> dynamicTopKSearch(String query, QueryType type) {
    int topK;
    boolean useRerank;
    switch (type) {
        case SIMPLE:
            topK = 3;          // 简单问题 Top 3 够了
            useRerank = false; // 不用 Rerank
            break;
        case COMPLEX:
            topK = 10;
            useRerank = true;
            break;
        default:
            topK = 5;
            useRerank = true;
    }
    return similaritySearch(query, topK, useRerank);
}

// 2. 异步检索 + 生成（Retrieval 和 LLM 生成并行）
public CompletableFuture<ChatResponse> asyncRagChat(String query) {
    // 1. 异步发起检索
    CompletableFuture<List<Document>> retrievalFuture = CompletableFuture.supplyAsync(() ->
        vectorStore.similaritySearch(query, 5)
    );

    // 2. 同时可以做一些别的事（Query 改写等）
    String rewrittenQuery = rewriteQuery(query);

    // 3. 等待检索结果，同时 LLM 可以先做准备
    return retrievalFuture.thenApply(docs -> {
        String context = buildContext(docs);
        return llm.generate(rewrittenQuery, context);
    });
}
```

**L2：索引层优化（pgvector 配置）**：

```sql
-- pgvector HNSW 索引（比默认的 ivfflat 更快，适合实时查询）
CREATE INDEX ON knowledge_documents
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 查询时设置 ef_search（查询时访问的邻居数， trade-off 精度和速度）
SET hnsw.ef_search = 40;

-- 索引分区：按时间分区，新数据查得快
CREATE TABLE knowledge_documents (
    id uuid,
    embedding vector(1536),
    created_at timestamp
) PARTITION BY RANGE (created_at);

CREATE TABLE knowledge_documents_202401
PARTITION OF knowledge_documents
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- 查询时指定分区（避免全表扫描）
SELECT * FROM knowledge_documents
WHERE created_at >= '2024-03-01'
  AND embedding <-> query_embedding < 0.5
ORDER BY embedding <-> query_embedding
LIMIT 5;
```

**L1：基础设施优化**：

```yaml
# application.yml 数据库连接池优化
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 连接池大小（CPU 核数 × 2 + 1）
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  # Redis 连接池
  data:
    redis:
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 10
```

---

**三、我的项目实际优化数据**：

| 优化项 | 优化前 | 优化后 | 提升 |
|--------|--------|--------|------|
| Embedding 计算 | 300ms | 50ms（缓存命中） | **6x** |
| 检索延迟（p95） | 500ms | 150ms | **3.3x** |
| Rerank 延迟 | 200ms | 0（简单问题跳过） | - |
| 端到端延迟 | 2.5s | 1.2s | **2.1x** |
| 吞吐量（QPS） | 5 | 20 | **4x** |

---

**四、优化效果监控**：

```java
// Micrometer 指标
@Timed(value = "rag.retrieval.latency", description = "RAG retrieval latency")
@Counted(value = "rag.retrieval.count", description = "RAG retrieval count")
public List<Document> similaritySearch(String query, int topK) {
    long start = System.currentTimeMillis();
    try {
        List<Document> result = doSearch(query, topK);
        ragMetrics.recordLatency(System.currentTimeMillis() - start, "success");
        return result;
    } catch (Exception e) {
        ragMetrics.recordLatency(System.currentTimeMillis() - start, "error");
        throw e;
    }
}

// 监控面板指标：
// - rag_retrieval_latency_seconds（p50/p95/p99）
// - rag_retrieval_count_total（QPS）
// - rag_cache_hit_rate（缓存命中率）
```

---

### 6. 上下文怎么处理？有哪些优化思路？

**参考答案**：

---

**RAG + Agent 系统的上下文处理**：

上下文处理是 RAG 和 Agent 的核心问题，涉及 **Context Window 限制**、**上下文选择**、**上下文压缩**、**记忆管理**等方面。

---

**一、上下文问题的背景**：

| 模型 | Context Window | Token 成本 |
|------|---------------|-----------|
| GPT-3.5 | 4K / 16K | 便宜 |
| GPT-4 | 8K / 32K / 128K | 贵 |
| Qwen-Plus | 32K | 中 |
| Claude 3 | 200K | 中 |

**挑战**：
1. **窗口限制**：Context Window 有限，不是所有东西都能塞进去
2. **成本问题**：Token 很贵，全量塞进去成本高
3. **注意力稀释**：太长的上下文，模型可能"看漏"重要信息（Lost in the Middle）
4. **延迟问题**：输入越长，生成越慢

---

**二、上下文处理策略**：

```
┌─────────────────────────────────────────────────────────────────┐
│                     上下文处理策略                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │  1. 选择    │ → │  2. 压缩    │ → │  3. 排序    │     │
│  │  (Select)   │   │  (Compress) │   │  (Rank)     │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│         ↓                  ↓                  ↓                 │
│  ┌───────────────────────────────────────────────────────┐    │
│  │           4. 注入到 Prompt（分块组织）               │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

**1. 上下文选择（Select）**：

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **Top-K 检索** | 取相似度最高的 K 个 Chunk | 通用场景 |
| **时间优先** | 优先选最近的文档 | 新闻、动态政策 |
| **来源优先** | 优先选权威来源（官方文件 > 新闻） | 政策咨询 |
| **类别优先** | 优先选相关类别（汽车问题优先选汽车文档） | 多品类场景 |
| **Maximal Marginal Relevance (MMR)** | 平衡相关性和多样性 | 避免结果太单一 |

```java
// MMR 实现：平衡相关性和多样性
public List<Document> mmrSelect(List<Document> candidates,
                                 String query,
                                 double lambda, // 0 = 最多样，1 = 最相关
                                 int topK) {
    List<Document> selected = new ArrayList<>();
    Set<String> selectedIds = new HashSet<>();
    List<Document> remaining = new ArrayList<>(candidates);

    for (int i = 0; i < topK && !remaining.isEmpty(); i++) {
        int bestIdx = -1;
        double bestScore = -Double.MAX_VALUE;

        for (int j = 0; j < remaining.size(); j++) {
            Document doc = remaining.get(j);

            // 1. 相关性得分
            double relevanceScore = cosineSimilarity(query, doc);

            // 2. 多样性得分（与已选文档的最小相似度）
            double diversityScore = 0;
            if (!selected.isEmpty()) {
                double minSimilarity = selected.stream()
                    .mapToDouble(s -> cosineSimilarity(doc, s))
                    .min().orElse(0);
                diversityScore = 1 - minSimilarity; // 越小越多样
            }

            // 3. MMR 得分
            double mmrScore = lambda * relevanceScore + (1 - lambda) * diversityScore;

            if (mmrScore > bestScore) {
                bestScore = mmrScore;
                bestIdx = j;
            }
        }

        if (bestIdx >= 0) {
            Document selectedDoc = remaining.remove(bestIdx);
            selected.add(selectedDoc);
            selectedIds.add(selectedDoc.getId());
        }
    }

    return selected;
}
```

---

**2. 上下文压缩（Compress）**：

| 策略 | 说明 | 成本 |
|------|------|------|
| **截断** | 只取前 N 个字符/Token | 零成本 |
| **摘要** | 用 LLM/提取式摘要压缩 | 中 |
| **提取关键句** | 只保留包含关键词的句子 | 低 |
| **Map-Reduce** | 先分别压缩，再合并总结 | 高 |

```java
// 策略 1：简单截断（我的项目用的）
public String truncate(String text, int maxChars) {
    if (text.length() <= maxChars) {
        return text;
    }
    return text.substring(0, maxChars) + "...";
}

// 配置：RagConfig.promptMaxDocChars = 1200
// 每个文档只注入前 1200 字符

// 策略 2：关键句提取
public String extractKeySentences(String text, Set<String> keywords, int maxSentences) {
    List<String> sentences = splitIntoSentences(text);
    List<String> keySentences = new ArrayList<>();

    for (String sentence : sentences) {
        if (containsAny(sentence, keywords)) {
            keySentences.add(sentence);
        }
        if (keySentences.size() >= maxSentences) {
            break;
        }
    }

    return String.join(" ", keySentences);
}

// 策略 3：LLM 摘要（效果最好但成本高）
public String summarizeWithLLM(String text) {
    String prompt = """
        请将以下文档摘要成 100 字以内，保留关键信息：
        %s

        输出格式（JSON）：
        {"summary": "..."}
        """;
    return callLLM(String.format(prompt, text));
}
```

---

**3. 上下文排序（Rank）**：

**重要发现**：模型对**开头**和**结尾**的内容注意力更好（Lost in the Middle 现象）

**排序策略**：
- **最相关的放最前面**（Primacy Effect）
- **次相关的放最后面**（Recency Effect）
- **其他的放中间**

```java
// 上下文排序
public List<Document> rankForContext(List<Document> docs) {
    if (docs.size() <= 2) {
        return docs;
    }

    List<Document> result = new ArrayList<>();
    // 1. 最相关的放第一个
    result.add(docs.get(0));

    // 2. 次相关的放最后
    Document last = docs.get(1);

    // 3. 剩下的放中间（可以打乱，也可以按相关性降序）
    for (int i = 2; i < docs.size(); i++) {
        result.add(docs.get(i));
    }

    result.add(last);
    return result;
}
```

---

**4. Prompt 组织（Organization）**：

**我的项目实际 Prompt 模板**：

```java
// 好的组织方式：用明确的分隔符 + 有序列表
String buildRagPrompt(String query, List<Document> docs) {
    StringBuilder contextBuilder = new StringBuilder();
    contextBuilder.append("【知识库内容】\n\n");

    for (int i = 0; i < docs.size(); i++) {
        Document doc = docs.get(i);
        contextBuilder.append(String.format("[文档 %d] %s\n", i + 1, doc.getMetadata().get("title")));
        contextBuilder.append("来源：").append(doc.getMetadata().get("source")).append("\n");
        contextBuilder.append("内容：").append(truncate(doc.getText(), 1200)).append("\n\n");
    }

    return String.format("""
        你是山东省以旧换新政策咨询助手。

        请根据以下知识库内容回答用户问题：

        %s

        用户问题：%s

        回答要求：
        1. 优先使用知识库内容
        2. 引用来源时标注 [文档 1]、[文档 2]
        3. 不确定时请说"抱歉"
        """, contextBuilder.toString(), query);
}
```

---

**三、对话记忆的上下文处理**：

| 策略 | 说明 | 我的项目实现 |
|------|------|-------------|
| **完整记忆** | 全部对话历史塞进去 | 只用在短会话（≤ 10 轮） |
| **滑动窗口** | 只保留最近 N 轮 | ✅ 用这个，最近 5 轮 |
| **摘要记忆** | 把历史对话摘要成一段 | 可以加这个 |
| **向量检索记忆** | 把历史对话向量化，检索相关的 | 复杂场景用 |
| **结构化事实** | 提取关键信息结构化存储 | ✅ SessionFactCacheService |

```java
// 我的项目实现：滑动窗口 + 结构化事实
public class SessionFactCacheService {

    // 1. 滑动窗口：只保留最近 5 轮
    public List<Message> getRecentMessages(String sessionId, int limit) {
        List<Message> all = chatMemory.getMessages(sessionId);
        return all.subList(Math.max(0, all.size() - limit), all.size());
    }

    // 2. 结构化事实：单独提取并缓存
    public void mergeFacts(String sessionId, String userInput) {
        // 提取价格
        String price = extractPrice(userInput);
        // 提取设备型号
        String device = extractDevice(userInput);
        // 提取地区
        String region = extractRegion(userInput);

        // 存入 Redis
        redisTemplate.opsForHash().put("chat:facts:" + sessionId, "price", price);
        redisTemplate.opsForHash().put("chat:facts:" + sessionId, "device", device);
        redisTemplate.opsForHash().put("chat:facts:" + sessionId, "region", region);
    }

    // 3. 拼入 Prompt
    public String buildFactsPrompt(String sessionId) {
        Map<Object, Object> facts = redisTemplate.opsForHash().entries("chat:facts:" + sessionId);
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【会话事实缓存】\n");
        facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }
}
```

---

### 7. 长短记忆之间如何做协同？

**参考答案**：

---

**长短记忆协同设计**：

这是 Agent 系统记忆管理的核心问题。我先定义一下什么是长记忆、什么是短记忆。

---

**一、长记忆 vs 短记忆定义**：

| 维度 | 短记忆（Short-Term Memory） | 长记忆（Long-Term Memory） |
|------|---------------------------|---------------------------|
| **存储内容** | 当前对话历史 | 结构化事实、知识库、用户画像 |
| **存储位置** | 直接注入 Prompt | Redis、向量数据库、关系数据库 |
| **容量** | 小（受 Context Window 限制） | 大（几乎无限） |
| **访问方式** | 直接读取 | 需要检索（向量、关键词） |
| **持久化** | 会话结束可能丢失 | 持久化保存 |
| **例子** | 最近 5 轮对话 | 用户的设备型号、历史补贴记录、政策文档 |

---

**二、我的项目实现**：

```
┌───────────────────────────────────────────────────────────────┐
│                    记忆系统架构                                │
├───────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────┐    │
│  │  短记忆（STM） - Spring AI MessageChatMemory          │    │
│  │    - 存储：Redis List                                 │    │
│  │    - 容量：最近 10 轮对话                             │    │
│  │    - 过期：TTL 7 天                                   │    │
│  │    - 使用：直接注入 Prompt                            │    │
│  └───────────────────────────────────────────────────────┘    │
│                              ↕ 协同                            │
│  ┌───────────────────────────────────────────────────────┐    │
│  │  长记忆（LTM） - SessionFactCacheService              │    │
│  │    - 存储：Redis Hash                                 │    │
│  │    - 容量：无限制（结构化事实）                       │    │
│  │    - 过期：TTL 7 天                                   │    │
│  │    - 使用：检索后注入 Prompt                          │    │
│  │    - 内容：                                           │    │
│  │      * 设备型号：iPhone 17 Pro、华为 Mate 60         │    │
│  │      * 最近价格：8999 元                              │    │
│  │      * 地区线索：山东省济南市                          │    │
│  │      * 城市编码、经纬度                                │    │
│  └───────────────────────────────────────────────────────┘    │
│                              ↕ 协同                            │
│  ┌───────────────────────────────────────────────────────┐    │
│  │  永久记忆（知识库）- RAG Vector Store                 │    │
│  │    - 存储：PostgreSQL + pgvector                      │    │
│  │    - 容量：10,000+ Chunks                            │    │
│  │    - 过期：永久（除非手动删除）                       │    │
│  │    - 使用：向量检索 Top-K                            │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                                 │
└───────────────────────────────────────────────────────────────┘
```

---

**三、协同工作流程**：

```
用户提问
    ↓
1. 读取短记忆（最近对话历史）
    ↓
2. 读取长记忆（会话事实缓存）
    ↓
3. 检索知识库（永久记忆）
    ↓
4. 三者一起拼入 Prompt
    ↓
5. LLM 生成回答
    ↓
6. 更新短记忆（追加本轮对话）
    ↓
7. 更新长记忆（提取新事实）
```

---

**四、代码实现**：

**1. 记忆读取与协同**：
```java
@Service
public class ChatService {

    private final MessageChatMemory shortTermMemory;
    private final SessionFactCacheService longTermMemory;
    private final VectorStore knowledgeBase; // 永久记忆

    public ChatResponse chat(String sessionId, String userQuery) {
        // 1. 读取短记忆：最近 5 轮对话
        List<Message> recentMessages = shortTermMemory.getMessages(sessionId)
            .stream()
            .skip(Math.max(0, shortTermMemory.size(sessionId) - 5))
            .toList();

        // 2. 读取长记忆：结构化事实
        String factsPrompt = longTermMemory.buildFactsPrompt(sessionId);

        // 3. 检索永久记忆：RAG 知识库
        List<Document> docs = knowledgeBase.similaritySearch(userQuery, 5);
        String ragPrompt = buildRagPrompt(docs);

        // 4. 三者协同，拼入 Prompt
        String systemPrompt = buildSystemPrompt(factsPrompt, ragPrompt);
        List<Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(systemPrompt));
        allMessages.addAll(recentMessages);
        allMessages.add(new UserMessage(userQuery));

        // 5. LLM 生成
        AssistantMessage response = chatClient.call(allMessages);

        // 6. 更新短记忆
        shortTermMemory.addMessage(sessionId, new UserMessage(userQuery));
        shortTermMemory.addMessage(sessionId, response);

        // 7. 更新长记忆：提取新事实
        longTermMemory.extractAndSaveFacts(sessionId, userQuery, response.getContent());

        return buildResponse(response);
    }
}
```

**2. 长记忆提取与存储**：
```java
@Service
public class SessionFactCacheService {
    private static final Pattern PRICE_PATTERN = Pattern.compile(
        "(\\d{3,6}(?:\\.\\d{1,2})?)\\s*(元|rmb|¥|￥)"
    );
    private static final Pattern REGION_PATTERN = Pattern.compile(
        "(济南|青岛|淄博|枣庄|东营|烟台|潍坊|济宁|泰安|威海|日照|临沂|德州|聊城|滨州|菏泽)"
    );

    // 从对话中提取事实并保存
    public void extractAndSaveFacts(String sessionId, String userInput, String assistantOutput) {
        String combined = userInput + "\n" + assistantOutput;

        // 1. 提取价格
        Matcher priceMatcher = PRICE_PATTERN.matcher(combined);
        if (priceMatcher.find()) {
            String price = priceMatcher.group(1);
            saveFact(sessionId, "recentPrice", price + " 元");
        }

        // 2. 提取地区
        Matcher regionMatcher = REGION_PATTERN.matcher(combined);
        if (regionMatcher.find()) {
            String region = regionMatcher.group(1);
            saveFact(sessionId, "region", region + "市");
        }

        // 3. 提取设备型号（简单关键词匹配）
        for (String model : List.of("iPhone", "华为", "小米", "比亚迪", "特斯拉")) {
            if (combined.contains(model)) {
                saveFact(sessionId, "deviceModel", extractModel(combined, model));
                break;
            }
        }
    }

    // 保存到 Redis
    private void saveFact(String sessionId, String key, String value) {
        redisTemplate.opsForHash().put("chat:facts:" + sessionId, key, value);
        redisTemplate.expire("chat:facts:" + sessionId, Duration.ofDays(7));
    }

    // 构建事实 Prompt
    public String buildFactsPrompt(String sessionId) {
        Map<Object, Object> facts = redisTemplate.opsForHash()
            .entries("chat:facts:" + sessionId);
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【会话事实缓存】\n");
        facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }
}
```

---

**五、更高级的协同策略**：

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **记忆检索（Memory Retrieval）** | 把历史对话向量化，用向量检索相关的历史 | 长会话（> 20 轮） |
| **记忆压缩（Memory Compression）** | 定期把历史对话摘要成一段 | 超长短记忆 |
| **记忆反思（Memory Reflection）** | 定期总结经验教训，更新长期记忆 | 智能体持续学习 |
| **记忆衰减（Memory Decay）** | 旧记忆权重降低，新记忆权重高 | 时间敏感场景 |

```java
// 高级策略 1：记忆检索（Memory as Vector Store）
public class RetrievalAugmentedMemory {
    private final VectorStore memoryVectorStore;

    // 每次对话后，把用户输入和 AI 回答一起向量化存入
    public void addMemory(String sessionId, String userInput, String aiOutput) {
        String content = "用户：" + userInput + "\n助手：" + aiOutput;
        Document doc = new Document(content, Map.of("sessionId", sessionId, "timestamp", Instant.now()));
        memoryVectorStore.add(List.of(doc));
    }

    // 检索相关的历史记忆
    public List<Document> retrieveRelevantMemories(String currentQuery, int topK) {
        return memoryVectorStore.similaritySearch(currentQuery, topK);
    }
}

// 高级策略 2：记忆压缩
public class CompressingMemory {
    private final ChatClient chatClient;

    // 当记忆超过 10 轮时，压缩前 5 轮
    public List<Message> compressIfNeeded(List<Message> messages) {
        if (messages.size() <= 10) {
            return messages;
        }

        // 压缩前 5 轮
        List<Message> toCompress = messages.subList(0, 5);
        Message compressed = compress(toCompress);

        // 保留后 5 轮 + 压缩摘要
        List<Message> result = new ArrayList<>();
        result.add(compressed);
        result.addAll(messages.subList(5, messages.size()));
        return result;
    }

    private Message compress(List<Message> messages) {
        String prompt = """
            请将以下对话历史摘要成 100 字以内：
            %s
            """;
        String summary = chatClient.call(String.format(prompt, formatMessages(messages)));
        return new SystemMessage("【对话历史摘要】\n" + summary);
    }
}
```

---

**六、总结：长短记忆协同 Checklist**：

| 问题 | 我的解决方案 |
|------|-------------|
| 短记忆放多少轮？ | 最近 5 轮（滑动窗口） |
| 长记忆存什么？ | 结构化事实（价格、地区、设备型号） |
| 怎么协同？ | 都拼入 Prompt，短记忆在前，长记忆在后，RAG 在中间 |
| 怎么更新？ | 每次对话后：短记忆追加、长记忆提取 |
| 持久化策略？ | Redis TTL 7 天 |

---

### 8. 有哪些思路可以优化 Agent，让它更智能？

**参考答案**：

---

**Agent 智能化优化思路**：

这是一个开放性问题，我从**规划优化**、**记忆优化**、**工具优化**、**学习优化**四个维度来回答。

---

**一、规划优化（Planning）**：

| 优化方向 | 具体方案 | 说明 |
|---------|---------|------|
| **1. 更好的规划范式** | | |
| | ReAct → Plan-and-Execute | 先规划完整计划再执行，而不是走一步看一步 |
| | ReWOO（Reasoning Without Observation） | 减少 LLM 调用次数，效率更高 |
| | Tree-of-Thought | 探索多条推理路径，选最好的 |
| **2. 计划修正** | | |
| | 反思与调整（Reflexion） | 执行失败时自动反思，调整计划重试 |
| | 回溯机制 | 某步失败时，回到上一步重规划 |
| **3. 子任务分解** | | |
| | 层次化规划（Hierarchical Planning） | 大任务分解成子任务，子任务再分解 |
| | 任务依赖图 | 分析任务间依赖关系，并行执行无依赖的 |

---

**Plan-and-Execute 示例**：
```java
// 我的项目目前是 ReAct，后续可以优化成 Plan-and-Execute

public class PlanAndExecuteAgent {

    public AgentResponse execute(String userQuery) {
        // 1. 规划阶段：一次生成完整计划
        Plan plan = planner.createPlan(userQuery);
        log.info("生成计划：{}", plan);

        // 2. 执行阶段：按计划执行
        List<StepResult> results = new ArrayList<>();
        for (Step step : plan.getSteps()) {
            try {
                StepResult result = executor.executeStep(step);
                results.add(result);

                // 3. 可选：中途检查是否需要调整计划
                if (needsReplanning(result, plan)) {
                    plan = replanner.adjustPlan(plan, result);
                    log.info("调整计划：{}", plan);
                }
            } catch (Exception e) {
                // 4. 失败处理：反思 + 重试
                Reflection reflection = reflector.reflect(step, e);
                if (reflection.shouldRetry()) {
                    step = reflection.adjustStep(step);
                    continue; // 重试
                }
                throw e;
            }
        }

        // 5. 汇总结果
        return buildFinalResponse(results);
    }
}
```

---

**二、记忆优化（Memory）**：

| 优化方向 | 具体方案 | 说明 |
|---------|---------|------|
| **1. 记忆类型更丰富** | | |
| | 情景记忆（Episodic Memory） | "上次用户问过 iPhone 补贴" |
| | 语义记忆（Semantic Memory） | "iPhone 属于数码产品，补贴上限 300 元" |
| | 程序记忆（Procedural Memory） | "计算补贴需要先查品类，再查价格区间" |
| **2. 记忆组织更智能** | | |
| | 记忆索引（Memory Indexing） | 给记忆打标签，方便检索 |
| | 记忆重要性评分（Importance Scoring） | 重要记忆权重更高，不容易被遗忘 |
| | 记忆衰减（Memory Decay） | 旧记忆逐渐降低权重 |
| **3. 记忆检索更精准** | | |
| | 混合检索 | 关键词 + 向量 + 时间 |
| | 重排序（Rerank） | 检索结果再排序 |
| | 主动回忆（Active Recall） | 不需要检索，主动想到相关记忆 |

---

**记忆重要性评分示例**：
```java
public class ScoredMemory {
    private String content;
    private double importance; // 重要性得分 0-1
    private Instant createdAt;
    private int accessCount; // 访问次数

    // 计算当前有效得分（考虑重要性 + 衰减 + 访问次数）
    public double getCurrentScore() {
        // 1. 基础重要性
        double score = importance;

        // 2. 时间衰减：每天衰减 0.05
        long days = Duration.between(createdAt, Instant.now()).toDays();
        score -= days * 0.05;

        // 3. 访问次数加分：每次访问加 0.1
        score += accessCount * 0.1;

        return Math.max(0, Math.min(1, score));
    }
}

// 检索时按得分排序
public List<ScoredMemory> retrieveTopMemories(String query, int topK) {
    List<ScoredMemory> all = getAllMemories();
    return all.stream()
        .sorted(Comparator.comparingDouble(ScoredMemory::getCurrentScore).reversed())
        .limit(topK)
        .toList();
}
```

---

**三、工具优化（Tools）**：

| 优化方向 | 具体方案 | 说明 |
|---------|---------|------|
| **1. 工具选择更智能** | | |
| | 工具描述优化 | 更好的 Tool Description，帮助 LLM 理解 |
| | 少样本示例（Few-Shot） | 给几个工具使用示例 |
| | 工具召回（Tool Retrieval） | 工具太多时，先检索相关工具 |
| **2. 工具调用更鲁棒** | | |
| | 参数校验（Validation） | 调用前先验证参数 |
| | 重试策略（Retry） | 失败时自动重试（指数退避） |
| | 降级策略（Fallback） | 工具不可用时用备选方案 |
| **3. 工具能力更强** | | |
| | 工具组合（Tool Composition） | 多个工具组合成新工具 |
| | 并行调用（Parallel Tool Calling） | 同时调用多个独立工具 |
| | 人机协作（Human-in-the-Loop） | 不确定时问用户 |

---

**我的项目 ToolIntentClassifier 已经是这方面的尝试**：
```java
// 工具调用前先做参数校验，不足时直接问用户
public Classification classify(String userInput, ToolDefinition tool) {
    if (tool.name().equals("calculateSubsidy")) {
        // 检查是否有价格
        boolean hasPrice = PRICE_PATTERN.matcher(userInput).find();
        // 检查是否有品类
        boolean hasCategory = CATEGORY_PATTERN.matcher(userInput).find();

        if (!hasPrice || !hasCategory) {
            // 拦截，返回澄清问题
            String question = "请问您购买的产品价格是多少？";
            if (!hasCategory) {
                question = "请问您想咨询什么产品的补贴（家电/汽车/数码）？";
            }
            return Classification.block(question);
        }
    }
    return Classification.allow();
}
```

---

**四、学习优化（Learning）**：

| 优化方向 | 具体方案 | 说明 |
|---------|---------|------|
| **1. 从反馈中学习** | | |
| | 在线学习（Online Learning） | 用户点赞/点踩时，更新 Prompt 或 RAG |
| | 经验回放（Experience Replay） | 把成功/失败案例存下来，定期反思 |
| **2. Prompt 自动优化** | | |
| | A/B 测试 | 多个 Prompt 版本做 A/B 测试，选最好的 |
| | 自动 Prompt 工程（Auto-Prompt） | 用 LLM 优化 Prompt |
| **3. 模型微调** | | |
| | LoRA 微调 | 用成功案例微调 LLM |
| | RAG 微调 | 用检索数据微调 Embedding 模型 |

---

**从反馈中学习示例**：
```java
@Service
public class FeedbackLearningService {

    // 用户反馈后，记录下来
    public void recordFeedback(String sessionId, String query,
                              String response, int rating, String comment) {
        Feedback feedback = Feedback.builder()
            .query(query)
            .response(response)
            .rating(rating) // 1-5 星
            .comment(comment)
            .timestamp(Instant.now())
            .build();
        feedbackRepository.save(feedback);

        // 如果是差评，触发反思
        if (rating <= 2) {
            reflectOnBadCase(feedback);
        }
    }

    // 定期从反馈中学习
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨 2 点
    public void learnFromFeedback() {
        List<Feedback> recentFeedbacks = feedbackRepository.findRecent(7);

        // 1. 分析差评原因
        List<Feedback> badCases = recentFeedbacks.stream()
            .filter(f -> f.getRating() <= 2)
            .toList();
        AnalysisResult analysis = analyzeBadCases(badCases);

        // 2. 优化 Prompt
        if (analysis.shouldUpdatePrompt()) {
            promptOptimizer.updatePrompt(analysis.getSuggestions());
        }

        // 3. 补充 RAG 知识库
        if (analysis.hasMissingKnowledge()) {
            knowledgeAugmenter.addDocuments(analysis.getMissingTopics());
        }

        // 4. 优化工具
        if (analysis.hasToolIssues()) {
            toolOptimizer.fixTools(analysis.getToolIssues());
        }
    }
}
```

---

**五、总结：Agent 智能化进阶路径**：

```
Level 1: 基础 Agent（我的项目当前水平）
    ├─ ReAct 框架
    ├─ 简单工具调用
    ├─ 基本记忆
    └─ 手动 Prompt 工程
        ↓
Level 2: 规划增强
    ├─ Plan-and-Execute
    ├─ 计划修正与回溯
    └─ 子任务分解
        ↓
Level 3: 记忆增强
    ├─ 多种记忆类型
    ├─ 记忆重要性评分
    └─ 智能检索与重排序
        ↓
Level 4: 工具增强
    ├─ 智能工具选择
    ├─ 并行工具调用
    └─ 工具组合
        ↓
Level 5: 学习能力
    ├─ 从反馈中学习
    ├─ 自动 Prompt 优化
    └─ 模型微调
        ↓
Level 6: 多智能体协作
    ├─ 专门化智能体
    ├─ 智能体间通信
    └─ 任务分配与协调
```

---

### 9. 算法手撕：全排列

**题目描述**：
```
给定一个不含重复数字的数组 nums，返回其所有可能的全排列。你可以按任意顺序返回答案。

示例 1：
输入：nums = [1,2,3]
输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]

示例 2：
输入：nums = [0,1]
输出：[[0,1],[1,0]]

示例 3：
输入：nums = [1]
输出：[[1]]
```

---

**解法一：回溯法（Backtracking）**

**思路**：
- 用 `used` 数组标记哪些数字用过了
- 每次选一个没用过的数字，加入当前排列
- 当前排列长度等于 nums 长度时，加入结果
- 回溯：撤销选择，尝试下一个

**Java 代码**：

```java
class Solution {
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        boolean[] used = new boolean[nums.length];
        backtrack(nums, new ArrayList<>(), used, result);
        return result;
    }

    private void backtrack(int[] nums, List<Integer> path, boolean[] used, List<List<Integer>> result) {
        // 终止条件：path 长度等于 nums 长度
        if (path.size() == nums.length) {
            result.add(new ArrayList<>(path)); // 注意要 copy 一份
            return;
        }

        // 遍历每个数字
        for (int i = 0; i < nums.length; i++) {
            // 跳过已经用过的
            if (used[i]) {
                continue;
            }

            // 1. 选择
            used[i] = true;
            path.add(nums[i]);

            // 2. 递归
            backtrack(nums, path, used, result);

            // 3. 回溯（撤销选择）
            used[i] = false;
            path.remove(path.size() - 1);
        }
    }
}
```

---

**解法二：交换法（不需要 used 数组）**

**思路**：
- 把数组分成两部分：`[0, index)` 是已排好的，`[index, end)` 是待排的
- 对于每个位置 index，把 index 和后面的每个位置交换
- 递归处理 index + 1
- 回溯：交换回来

**Java 代码**：

```java
class Solution {
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, result);
        return result;
    }

    private void backtrack(int[] nums, int index, List<List<Integer>> result) {
        // 终止条件
        if (index == nums.length) {
            List<Integer> permutation = new ArrayList<>();
            for (int num : nums) {
                permutation.add(num);
            }
            result.add(permutation);
            return;
        }

        // 把 index 和后面的每个位置交换
        for (int i = index; i < nums.length; i++) {
            // 1. 交换
            swap(nums, index, i);

            // 2. 递归
            backtrack(nums, index + 1, result);

            // 3. 回溯（换回来）
            swap(nums, index, i);
        }
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}
```

---

**复杂度分析**：

| 复杂度 | 分析 |
|--------|------|
| **时间复杂度** | **O(n × n!)** |
| | - n! 个全排列 |
| | - 每个排列需要 n 步构建 |
| **空间复杂度** | **O(n)**（不计算结果） |
| | - 递归栈深度 O(n) |
| | - used 数组或 path O(n) |
| | - 结果空间 O(n × n!) |

**详细说明**：
- n 个不同元素的全排列个数是 **n!**（n 的阶乘）
- 例如：n=3 → 6 个排列，n=4 → 24 个排列，n=5 → 120 个排列

---

**测试用例**：
```java
// 测试 1
输入：nums = [1,2,3]
输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]

// 测试 2
输入：nums = [0,1]
输出：[[0,1],[1,0]]

// 测试 3
输入：nums = [1]
输出：[[1]]
```

---

**回溯法决策树（以 [1,2,3] 为例）**：

```
                    []
           /         |         \
        [1]         [2]         [3]
       /   \       /   \       /   \
    [1,2] [1,3] [2,1] [2,3] [3,1] [3,2]
     |     |     |     |     |     |
  [1,2,3][1,3,2][2,1,3][2,3,1][3,1,2][3,2,1]
```

---

## 面试总结

**本场考点分布**：
1. **项目评测**：评测维度、指标体系、离线/在线评测
2. **数据集**：政策文档、测试集、日志管理
3. **相关度优化**：RAG 检索优化、回答优化、体系化建设
4. **数据处理**：从简单求和到分布式系统设计
5. **RAG 性能**：缓存、索引、检索策略、基础设施
6. **上下文处理**：选择、压缩、排序、记忆管理
7. **长短记忆协同**：记忆架构、协同流程、代码实现
8. **Agent 优化**：规划、记忆、工具、学习四个维度
9. **算法**：全排列（回溯法）

**AI Agent 实习岗面试建议**：
- **项目要吃透**：评测体系、数据集、优化思路都要能讲清楚
- **原理要了解**：RAG、Agent、Memory 的基础概念和优化方向
- **思路要体系化**：不要只零散说几个点，要有框架（分几层、有哪些维度）
- **要能落地**：讲完原理要讲"我的项目是怎么做的"、"代码怎么实现的"
- **算法要熟练**：回溯、DFS/BFS、双指针、动态规划这些高频题要会
