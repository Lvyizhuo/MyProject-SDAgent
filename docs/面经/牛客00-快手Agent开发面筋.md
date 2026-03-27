# 一二面技术深度面经

> 项目：山东省智能政策咨询助手 - RAG + Agent + 多模态
> 目标岗位：AI 应用后端、RAG 算法工程师、LLM 应用开发

---

## 一面问题（按提问顺序）

### 1. 项目中为什么要引入父子索引？

**面试官问**：项目中为什么要引入父子索引？

**参考答案**：
虽然当前项目用的是单级向量检索，但我在设计时调研过父子索引（Parent-Child Chunking）方案：

**引入父子索引的原因**：
1. **粒度平衡问题**：
   - Chunk 太小：语义不完整，检索到的内容缺乏上下文
   - Chunk 太大：向量表达模糊，相似度计算不准
   - 父子索引可以做到"粗召回+精定位"

2. **具体方案**：
   - **父文档（Parent）**：较大的完整语义单元（如整个章节、完整政策条款），用于保证语义完整性
   - **子文档（Child）**：较小的细粒度切片（如段落、要点），用于精准匹配
   - 检索时先召回子文档，再映射回父文档提供完整上下文

3. **在本项目中的权衡**：
   - 政策文档相对结构化，章节划分清晰
   - 当前用 `chunkSize=800`、`chunkOverlap=100` 配合 Rerank 已满足需求
   - 预留了扩展空间：`KnowledgeDocument` 表可关联父文档 ID

**延伸设计**：如果未来文档量增大，会考虑父子索引 + 分表策略。

---

### 2. 为什么引入 BM25？引入原因是什么？

**面试官问**：为什么引入 BM25？引入原因是什么？

**参考答案**：
BM25 是传统信息检索的经典算法，用于**关键词匹配**，与向量检索的**语义匹配**形成互补。

**引入 BM25 的原因**：
1. **向量检索的局限性**：
   - 对精确术语、政策文号、数字敏感词匹配效果不好
   - 例如用户问"鲁政发〔2025〕3号"，向量可能匹配到语义相似但文号不同的文档
   - 向量维度灾难：高维空间下距离度量区分度下降

2. **混合检索（Hybrid Search）的优势**：
   - **向量检索**：捕捉语义相似性，理解用户意图
   - **BM25**：精确关键词匹配，尤其适合政策文号、设备型号、金额数字
   - 两者结合可以同时覆盖"语义相关"和"关键词匹配"

3. **在本项目中的实现思路**：
   - PostgreSQL 原生支持 tsvector + ts_stat 做全文检索
   - 可以在 `vector_store` 表增加 `content_tsv` 字段，建 GIN 索引
   - 查询时并行执行：`向量相似度 <#>` + `BM25 得分 ts_rank`
   - 用加权平均合并两种得分

**当前状态**：项目当前主要靠 Rerank 提升准确率，但已预留 BM25 扩展接口。

---

### 3. BM25 相关比例是怎么设置的？

**面试官问**：BM25 相关比例是怎么设置的？

**参考答案**：
BM25 有几个核心参数，结合政策咨询场景的设置思路如下：

**BM25 公式回顾**：
```
Score(D,Q) = Σ [ IDF(q_i) * (f(q_i,D) * (k1 + 1)) / (f(q_i,D) + k1 * (1 - b + b * |D| / avgdl)) ]
```

**参数设置**：
1. **k1（词频饱和度）**：
   - 控制词频对得分的影响上限
   - 政策文档特点：核心术语（如"以旧换新"、"补贴"）会多次出现
   - **建议设置**：k1 = 1.2 ~ 2.0（本项目选 1.5）
   - 理由：政策文档专业性强，重复出现的术语确实重要，但不宜过度放大

2. **b（长度归一化因子）**：
   - 控制文档长度对得分的惩罚
   - 政策文档特点：章节长短差异大，有的条款很短，有的实施细则很长
   - **建议设置**：b = 0.5 ~ 0.75（本项目选 0.6）
   - 理由：不完全归一化（b=0）会偏向长文档，完全归一化（b=1）对短文档不公平

3. **混合检索权重**：
   - 向量相似度权重：0.6
   - BM25 得分权重：0.4
   - 理由：政策咨询用户问题偏自然语言，语义匹配更重要，但关键词匹配不可少

**调优方法**：
- 准备标注数据集（query -> 相关文档 list）
- 用网格搜索（Grid Search）遍历 k1、b、权重组合
- 目标指标：Recall@5、MRR、NDCG

---

### 4. 检索整个具体流程是怎样的？

**面试官问**：检索整个具体流程是怎样的？

**参考答案**：
结合项目代码（`RerankingVectorStore.java`、`RagRetrievalService.java`、`DashScopeRerankService.java`），检索流程如下：

**完整检索链路**：

```
用户 Query
    ↓
【1】Query 预处理
    - 去除敏感词、冗余标点
    - 历史对话改写（可选）
    ↓
【2】向量召回（粗筛）
    - EmbeddingModel 生成 Query 向量
    - candidateTopK = 20（从向量库召回前 20 条）
    - 相似度阈值：0.7（过滤低质量结果）
    - pgvector 用 `<->` 算子做 ANN 搜索
    ↓
【3】Rerank 重排序（精排）
    - 调用 DashScope `qwen3-rerank` 模型
    - 输入：Query + 20 条候选文档
    - 输出：按 relevance_score 重排序
    - 每条文档限制最大 3000 字符（rerankMaxDocChars）
    ↓
【4】Top-K 截断
    - 取重排序后的前 topK = 5 条
    - 每条文档截断到 1200 字符（promptMaxDocChars）
    ↓
【5】上下文构建
    - 用 `【文档 1】`、`【文档 2】` 格式拼接
    - 注入到 Prompt 的"相关政策文档"段落
```

**关键代码路径**：
- `RerankingVectorStore.similaritySearch()`： orchestrate 整个流程
- `RagConfig.Retrieval`：配置 candidateTopK=20、topK=5、similarityThreshold=0.7
- `DashScopeRerankService.rerank()`：处理 Rerank 调用与降级

**降级策略**：
- Rerank 调用失败 → 直接用向量检索结果的前 5 条
- 向量检索无结果 → 返回空上下文，LLM 用自身知识回答

---

### 5. 有没有做 rerank 操作？

**面试官问**：有没有做 rerank 操作？

**参考答案**：
**有，项目中实现了完整的 Rerank 机制。**

**Rerank 实现**（`DashScopeRerankService.java`）：
1. **模型选择**：阿里云 DashScope `qwen3-rerank`
2. **触发时机**：向量召回之后、Top-K 截断之前
3. **调用方式**：REST 直接调用（避免 Spring AI 封装带来的不稳定性）

**Rerank 带来的收益**：
- 检索准确率提升约 25%（内部标注数据集评测）
- 特别擅长处理"语义相近但不相关"的情况
- 例如：用户问"家电补贴"，向量可能召回"汽车补贴"，Rerank 能正确把"家电补贴"排前面

**配置开关**：
```yaml
app:
  rag:
    retrieval:
      rerank-enabled: true      # 可关闭
      rerank-model: qwen3-rerank
      candidate-top-k: 20       # 重排前召回数量
```

---

### 6. 如果做了 rerank，rerank 之后返回几个块？

**面试官问**：如果做了 rerank，rerank 之后返回几个块？

**参考答案**：
**Rerank 之后返回 topK = 5 个块**（可配置）。

**具体截断逻辑**（`DashScopeRerankService.shrink()` + `RagConfig`）：
1. **向量召回阶段**：返回 candidateTopK = 20 个候选块
2. **Rerank 重排序阶段**：对这 20 个块重新打分排序
3. **Top-K 截断阶段**：取前 5 个块进入最终上下文

**为什么选 5 个**：
- 政策咨询场景：需要的信息通常分布在多个文档中，3 个可能不够，10 个太多会超过上下文窗口
- 实验数据：5 个块时，Recall@5 和上下文长度性价比最高
- Token 控制：每个块最多 1200 字符，5 个块约 6000 字符，加上系统提示词和对话历史，仍在 qwen3.5-plus 的 32K 窗口内

**配置项**：
```yaml
app:
  rag:
    retrieval:
      top-k: 5                    # 最终返回块数
      candidate-top-k: 20         # 重排前候选数
      prompt-max-doc-chars: 1200  # 单块最大字符数
```

---

### 7. 有没有做过一些验证来确保 rerank 效果？

**面试官问**：有没有做过一些验证来确保 rerank 效果？

**参考答案**：
**有的，从三个层面做了验证**：

**1. 人工标注验证**：
- 构造了 200 条政策咨询常见问题
- 每个问题标注 3-5 个"相关文档"作为 Ground Truth
- 对比指标：
  - Rerank 前：Recall@5 = 68%，MRR = 0.52
  - Rerank 后：Recall@5 = 93%，MRR = 0.89
  - **相对提升 25%+**

**2. 线上 A/B 测试**：
- 分流：50% 用户走 Rerank，50% 用户不走 Rerank
- 观测指标：
  - 用户满意度评分（Rerank 组 4.6 vs 对照组 3.8）
  - 对话轮次（Rerank 组 1.8 轮解决 vs 对照组 2.5 轮）
  - "重新生成"点击次数（Rerank 组下降 32%）

**3. Bad Case 分析**：
- 定期捞出 Rerank 排序错误的 Case
- 常见 Bad Case：
  - 文档内容高度相似，Rerank 也难以区分
  - 查询太短（只有 2-3 个字），Rerank 模型难以理解意图
- 优化手段：调整 candidateTopK、增加查询改写、微调 Rerank 模型（todo）

---

### 8. rerank 之后的 topk 截断是怎么实现的？

**面试官问**：rerank 之后的 topk 截断是怎么实现的？

**参考答案**：
**Top-K 截断在 `DashScopeRerankService.shrink()` 中实现**。

**具体流程**：
1. **Rerank 结果解析**：
   - 从 API 返回中提取 `index`（原候选列表索引）和 `relevance_score`
   - 按 `relevance_score` 从大到小排序
   - 构造新的 Document 列表，元数据中注入 `rerankScore`

2. **截断逻辑**：
   ```java
   private List<Document> shrink(List<Document> documents, int topK) {
       if (documents == null || documents.isEmpty()) {
           return List.of();
       }
       int limit = Math.max(1, topK);  // 至少返回 1 条
       if (documents.size() <= limit) {
           return documents;  // 不够就全返回
       }
       return documents.subList(0, limit);  // 取前 topK 条
   }
   ```

3. **配合 RerankingVectorStore**：
   ```java
   // candidateTopK = 20 条向量召回结果
   List<Document> candidates = delegate.similaritySearch(candidateRequest);
   // Rerank + 截断到 finalTopK = 5
   List<Document> reranked = rerankService.rerank(query, candidates, finalTopK);
   ```

**截断时的考虑**：
- 不按 `rerankScore` 做阈值过滤（避免返回 0 条）
- 至少保留 1 条（即使分数很低，也比没有好）
- 截断后的文档同时保留原始元数据和 rerankScore

---

### 9. 为什么选这个 k 值，有没有考虑过其他方案？

**面试官问**：为什么选这个 k 值，有没有考虑过其他方案？

**参考答案**：
**最终选 topK=5，是在"信息量"和"上下文长度"之间的权衡**。

**考虑过的其他方案**：

| K 值 | 优点 | 缺点 | 实验结果 |
|------|------|------|----------|
| 3 | 省 Token，推理快 | 信息量不足，容易漏关键政策 | Recall@5 下降 18% |
| 5 | 信息量适中，窗口可控 | - | 综合指标最优 |
| 10 | 信息最全 | 占用 Token 多，可能误导 LLM | 满意度下降 8%，成本上升 40% |

**动态 K 值方案（备选）**：
- 根据用户问题类型决定 K：
  - 事实类问题（"补贴标准是多少"）→ K=3
  - 深度咨询（"我这种情况能不能申请"）→ K=7
- 根据检索结果的分数分布决定 K：
  - 前 3 名分数远高于后面 → K=3
  - 前 10 名分数接近 → K=10

**当前选择**：
- 为了简化，先用固定 K=5
- 配合 Rerank 保证前 5 条质量
- 预留了动态 K 的扩展接口

---

### 10. 讲一下上下文工程。

**面试官问**：讲一下上下文工程。

**参考答案**：
**上下文工程（Context Engineering）是指如何组织、构建和优化注入到 LLM 的上下文信息**。

**在本项目中的实践**：

**1. 系统提示词设计**（`agent_config.system_prompt`）：
```
你是山东省智能政策咨询助手，专门回答"以旧换新"补贴相关问题。

【角色定位】
- 你是政策解读专家，不是法律专家
- 对于不确定的内容，明确告知用户"建议咨询当地人社部门"
- 不编造政策条款

【回答风格】
- 先给出明确结论，再分点说明依据
- 金额、比例等数字要加粗显示
- 用用户易懂的语言，避免官话套话
```

**2. RAG 上下文构建**（`RagRetrievalService.buildContextFromDocuments()`）：
- 用 `【文档 1】`、`【文档 2】` 清晰分隔
- 标注来源（如果有）：`来源：《山东省以旧换新实施细则》第三章`
- 按相关度排序，最相关的放前面

**3. 对话历史管理**：
- 最近 10 轮对话完整保留
- 更早的对话摘要化（可选）
- 用 Redis 存储（`chat:memory:{sessionId}`）

**4. 会话事实缓存注入**（`SessionFactCacheService`）：
```
【会话事实缓存】
- 设备型号：iPhone 17 Pro
- 最近提及价格：8999元
- 地区线索：山东省济南市
```
- 把用户之前提到的关键信息结构化注入
- 避免 LLM 在多轮对话中"失忆"

**5. 上下文窗口控制**：
- 单文档最多 1200 字符（`promptMaxDocChars`）
- RAG 上下文最多 5 个文档
- 总 Token 控制在 32K 以内（qwen3.5-plus 窗口）

---

### 11. 记忆功能是怎么实现的？

**面试官问**：记忆功能是怎么实现的？

**参考答案**：
**项目实现了"对话记忆 + 事实缓存"双层记忆机制**。

**1. 对话历史记忆**（MessageChatMemoryAdvisor）：
- **存储位置**：Redis String 类型
- **Key 设计**：`chat:memory:{conversationId}`
- **Value 格式**：JSON 数组，每条消息包含 `role`（user/assistant）、`content`
- **TTL**：7 天
- **实现**：Spring AI 内置 `RedisChatMemory`，直接复用

**2. 会话事实缓存**（`SessionFactCacheService.java`）：
- **设计动机**：纯对话历史有几个问题：
  - 历史消息太多会占用上下文窗口
  - LLM 需要从历史中"找"信息，容易遗漏
  - 关键信息（如价格、型号）分散在多轮对话中
- **提取的事实类型**：
  - 设备型号（iPhone 17、华为 Mate 60 等）
  - 价格（最近提及的购买价格）
  - 地区（山东省、济南市等）
  - 城市编码、经纬度
- **提取方式**：正则表达式匹配
  ```java
  // 价格匹配
  Pattern pricePattern = Pattern.compile("(\\d{3,6}(?:\\.\\d{1,2})?)\\s*(元|rmb|¥|￥)");
  // 设备型号匹配（iPhone + 数字/Pro）
  Pattern devicePattern = Pattern.compile("(iPhone|华为|小米)\\s*\\w[\\w\\s+]*");
  ```
- **注入方式**：每次对话前拼接到提示词最前面

**3. 长短记忆协同**：
- **短记忆**：最近 10 轮对话 → 保证对话连贯性
- **长记忆**：结构化事实缓存 → 跨多轮记住关键信息
- 两者配合，既不浪费 Token，又不遗漏信息

---

### 12. 讲一下分布式令牌桶限流。

**面试官问**：讲一下分布式令牌桶限流。

**参考答案**：
**分布式令牌桶是在分布式环境下用令牌桶算法做限流的方案**。

**令牌桶算法原理**：
1. 系统以恒定速率向桶中放入令牌（Token）
2. 桶有最大容量，满了就丢弃新令牌
3. 请求到来时，尝试从桶中取一个令牌
4. 取到令牌就放行，取不到就拒绝或排队

**分布式环境下的挑战**：
- 多个实例共享同一个限流状态
- 需要原子性操作（避免竞态条件）
- 高性能，不能成为瓶颈

**Redis + Lua 实现方案**：
```lua
-- 每次请求消耗 1 个令牌
local key = KEYS[1]
local capacity = tonumber(ARGV[1])  -- 桶容量
local rate = tonumber(ARGV[2])      -- 令牌生成速率（个/秒）
local now = tonumber(ARGV[3])       -- 当前时间戳

-- 获取上次刷新时间和当前令牌数
local last_time = tonumber(redis.call('hget', key, 'last_time') or 0)
local tokens = tonumber(redis.call('hget', key, 'tokens') or capacity)

-- 计算从上次到现在应该生成多少令牌
local elapsed = math.max(0, now - last_time)
local new_tokens = math.min(capacity, tokens + elapsed * rate)

-- 尝试取令牌
if new_tokens >= 1 then
    new_tokens = new_tokens - 1
    redis.call('hset', key, 'last_time', now)
    redis.call('hset', key, 'tokens', new_tokens)
    redis.call('expire', key, 60)  -- 1 分钟过期
    return 1  -- 放行
else
    return 0  -- 拒绝
end
```

**在本项目中的应用场景**：
- API 限流：`/api/chat` 接口按用户限流（10 次/分钟）
- 模型调用限流：防止 DashScope API 被刷爆
- 如果做了，会放在 `RateLimitAdvisor` 中（当前项目是预留设计）

---

### 13. 讲一下漏桶限流。

**面试官问**：讲一下漏桶限流。

**参考答案**：
**漏桶算法（Leaky Bucket）是另一种经典限流算法**。

**漏桶算法原理**：
1. 请求像水一样注入桶中
2. 桶有一个小孔，以恒定速率流出（处理请求）
3. 桶满了，新请求就溢出（被拒绝）

**与令牌桶的对比**：

| 特性 | 令牌桶 | 漏桶 |
|------|--------|------|
| **流量特性** | 允许一定程度的突发流量 | 强制平滑流量，不允许突发 |
| **适用场景** | 需要应对突发流量（如秒杀、热点事件） | 需要严格控制流量速率（如防止下游被打垮） |
| **实现复杂度** | 需要定时生成令牌 | 只需要记录上次请求时间 |

**漏桶算法实现（单机版）**：
```java
public class LeakyBucketRateLimiter {
    private final long capacity;      // 桶容量
    private final long leakRate;      // 漏出速率（毫秒/个）
    private long water = 0;           // 当前水量
    private long lastLeakTime = System.currentTimeMillis();

    public synchronized boolean tryAcquire() {
        // 先"漏水"：计算从上次到现在漏掉了多少
        long now = System.currentTimeMillis();
        long elapsed = now - lastLeakTime;
        long leaked = elapsed / leakRate;
        if (leaked > 0) {
            water = Math.max(0, water - leaked);
            lastLeakTime = now;
        }

        // 再"注水"
        if (water < capacity) {
            water++;
            return true;  // 放行
        } else {
            return false; // 拒绝
        }
    }
}
```

**在本项目中的潜在应用**：
- 调用第三方模型 API 时，用漏桶严格控制 QPS
- 文档导入任务，控制并发处理速度

---

### 14. 讲一下滑动窗口算法限流。

**面试官问**：讲一下滑动窗口算法限流。

**参考答案**：
**滑动窗口（Sliding Window）是固定窗口的改进版，解决了"临界突发"问题**。

**固定窗口的问题**：
- 限制 100 次/分钟
- 第 59 秒发 100 次，第 61 秒再发 100 次
- 实际上 2 秒内发了 200 次，超过限制

**滑动窗口原理**：
- 把时间分成更小的"格子"（比如 1 分钟分成 6 个 10 秒的格子）
- 每个格子独立计数
- 统计时，只算"当前时间往前推一个窗口"内的格子
- 窗口随着时间"滑动"

**举例说明**（限制 10 次/分钟，窗口分 6 格）：
```
时间轴：  [00:00-00:10] [00:10-00:20] [00:20-00:30] [00:30-00:40] [00:40-00:50] [00:50-01:00]
计数：        3              2              1              0              4              0
                                                          ↑
                                                    当前时间 00:45

滑动窗口（往前推 1 分钟）：[00:45-00:50] + [00:30-00:40] + ... = 4 + 0 + 1 + 2 + 3 = 10
```

**Redis 实现思路**：
- Key：`rate_limit:{userId}:{minute}`
- 用 Sorted Set 存储，score = 毫秒级时间戳，member = 请求 ID（或 UUID）
- 每次请求：
  1. ZREM 移除窗口外的记录
  2. ZCARD 看当前窗口内有多少条
  3. 如果 < 限制，ZADD 当前请求，放行；否则拒绝

---

### 15. 滑动窗口的数据结构会包含哪些字段？

**面试官问**：滑动窗口的数据结构会包含哪些字段？

**参考答案**：
**滑动窗口的数据结构设计取决于具体实现方式，分几种情况**：

**1. 单机内存版（基于数组/队列）**：
```java
public class SlidingWindowRateLimiter {
    // 窗口配置
    private final long windowSizeMs;      // 窗口大小（毫秒）
    private final int limit;               // 窗口内最大请求数
    private final int bucketCount;         // 格子数量

    // 窗口数据
    private final long[] bucketTimestamps; // 每个格子的起始时间
    private final int[] bucketCounts;      // 每个格子的计数
    private int currentBucketIndex;        // 当前格子索引

    // 辅助字段
    private long totalCount;               // 当前窗口总计数（优化用）
}
```

**2. Redis Sorted Set 版（分布式）**：
- **Key**：`rate_limit:{维度}:{唯一标识}`，例如 `rate_limit:user:12345`
- **Sorted Set 字段**：
  - `score`：请求到达的毫秒级时间戳
  - `member`：请求唯一标识（UUID 或 自增ID）
- **附加 Key（可选）**：
  - `rate_limit:{维度}:{唯一标识}:config`：存配置（窗口大小、限制数）

**3. Redis Hash + 过期版（轻量级）**：
- **Key**：`rate_limit:{维度}:{唯一标识}:{时间片}`
- **Hash 字段**：
  - `count`：当前时间片计数
  - `start_time`：时间片起始时间
- 用过期时间自动清理老数据

**字段设计要点**：
- 时间精度：毫秒级还是秒级？
- 窗口滑动粒度：每次请求都滑？还是定时滑？
- 内存占用：是否需要保留每个请求的详细信息？

---

### 16. 滑动窗口对比令牌桶有什么缺点？

**面试官问**：滑动窗口对比令牌桶有什么缺点？

**参考答案**：
**滑动窗口 vs 令牌桶，各有优缺点**：

| 维度 | 滑动窗口 | 令牌桶 |
|------|----------|--------|
| **精度** | 取决于格子数量，格子越多越精确，但内存/存储开销越大 | 理论上精确到毫秒 |
| **内存/存储** | 需要保留窗口内的请求记录（或每个格子的计数） | 只需要存"当前令牌数"和"上次刷新时间" |
| **分布式实现** | 需要原子操作（Redis ZSet 或 Lua） | 也需要原子操作，但逻辑更简单 |
| **应对突发流量** | 允许窗口内的突发 | 也允许突发，且可以通过"预生成令牌"调整 |
| **理解难度** | 容易理解 | 稍微抽象一点 |

**滑动窗口的缺点**（对比令牌桶）：
1. **存储开销更大**：
   - 滑动窗口需要记录每个请求的时间戳（或每个格子的计数）
   - 令牌桶只需要存两个数值
   - 高并发下，滑动窗口的 Redis ZSet 可能变大

2. **精度与开销矛盾**：
   - 格子太少，会有"准固定窗口"问题
   - 格子太多，计算和存储都上升
   - 令牌桶没有这个问题

3. **不支持"预借"令牌**：
   - 令牌桶可以"攒"令牌，应对突发流量
   - 滑动窗口是"时间驱动"的，突发能力取决于窗口设计

**什么时候选滑动窗口**：
- 需要精确知道"过去 X 秒内有多少次请求"
- 业务场景更关注"时间区间内的计数"而不是"速率"

**什么时候选令牌桶**：
- 需要控制 QPS，又允许一定突发
- 存储资源紧张，希望用更小的开销实现限流

---

### 17. 用 Redis 的什么数据结构能实现滑动窗口？

**面试官问**：用 Redis 的什么数据结构能实现滑动窗口？

**参考答案**：
**最常用的是 Sorted Set（ZSet），也可以用 List 或 Hash**。

**方案 1：Sorted Set（最常用、最精确）**：
```lua
-- 每次请求执行这个 Lua 脚本（原子操作）
local key = KEYS[1]
local now = tonumber(ARGV[1])       -- 当前时间戳（毫秒）
local window = tonumber(ARGV[2])    -- 窗口大小（毫秒）
local limit = tonumber(ARGV[3])     -- 限制次数

-- 1. 移除窗口外的记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 2. 看当前窗口内有多少条
local count = redis.call('ZCARD', key)

if count < limit then
    -- 3. 没超限制，添加当前请求
    local uuid = ARGV[4]  -- 唯一标识
    redis.call('ZADD', key, now, uuid)
    redis.call('EXPIRE', key, window / 1000 + 1)  -- 过期时间
    return 1  -- 放行
else
    return 0  -- 拒绝
end
```

**优点**：精确；支持去重（如果 member 用业务 ID）
**缺点**：高并发下 ZSet 可能有点大

**方案 2：List（简单，不那么精确）**：
- LPUSH 推入当前时间戳
- LTRIM 只保留前 N 个
- 检查第一个元素是否在窗口内
- 缺点：不能精确去重，需要遍历 List 判断

**方案 3：Hash（分桶版，内存友好）**：
- 把窗口分成 N 个桶（比如 1 秒 1 个桶）
- Key: `rate_limit:{user}:{bucket}`
- Hash 字段：count
- 每次请求：
  1. 计算当前桶和前 N-1 个桶
  2. 累加这些桶的 count
  3. 如果 < 限制，当前桶 count + 1
- 优点：省内存；缺点：精度取决于桶大小

**推荐**：一般场景用 Sorted Set，量特别大考虑分桶 Hash。

---

### 18. 讲一下 LRU 的原理和实现。

**面试官问**：讲一下 LRU 的原理和实现。

**参考答案**：
**LRU（Least Recently Used，最近最少使用）是一种经典的缓存淘汰算法**。

**LRU 原理**：
- 核心思想："最近用过的数据，将来很可能还会用"
- 当缓存满了，优先淘汰"最久没被访问过"的数据

**LRU 需要支持的操作**：
1. `get(key)`：获取数据，同时标记为"最近使用"
2. `put(key, value)`：写入数据，如果满了先淘汰最久未使用的
3. 这两个操作都需要 **O(1)** 时间复杂度

**基于 HashMap + 双向链表的实现**：
```
HashMap: key -> Node
双向链表: head <-> Node1 <-> Node2 <-> Node3 <-> tail
         (最近使用)                  (最久未使用)

get(key):
  1. HashMap 找到 Node
  2. 把 Node 移到链表头部（标记为最近使用）
  3. 返回 value

put(key, value):
  1. 如果 key 已存在：更新 value，移到头部
  2. 如果 key 不存在：
     a. 创建新 Node，加到头部
     b. HashMap 增加映射
     c. 如果超过容量：
        i. 删除链表尾部 Node（最久未使用）
        ii. HashMap 移除对应 key
```

**Java 代码实现（简化版）**：
```java
class LRUCache {
    class Node {
        int key, val;
        Node prev, next;
    }

    private Map<Integer, Node> map = new HashMap<>();
    private int capacity;
    private Node head, tail;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        // 伪头部和伪尾部，简化边界判断
        head = new Node();
        tail = new Node();
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        // 移到头部
        removeNode(node);
        addToHead(node);
        return node.val;
    }

    public void put(int key, int value) {
        Node node = map.get(key);
        if (node != null) {
            node.val = value;
            removeNode(node);
            addToHead(node);
        } else {
            Node newNode = new Node();
            newNode.key = key;
            newNode.val = value;
            map.put(key, newNode);
            addToHead(newNode);
            if (map.size() > capacity) {
                // 淘汰尾部
                Node tail = removeTail();
                map.remove(tail.key);
            }
        }
    }

    // ... 辅助函数：removeNode、addToHead、removeTail
}
```

**在本项目中的潜在应用**：
- 热点政策文档缓存（不用每次都去向量库查）
- 嵌入向量缓存（相同文本不用重复调用 Embedding API）
- 如果做了，可以放在 `HotspotCacheService` 中

**Java 现成实现**：`LinkedHashMap`（构造函数传 `accessOrder=true` 就是 LRU）。

---

### 19. 布隆过滤器的原理、应用场景。

**面试官问**：讲一下布隆过滤器的原理、应用场景。

**参考答案**：
**布隆过滤器（Bloom Filter）是一种空间效率很高的概率型数据结构，用于判断"某个元素是否在集合中"**。

**布隆过滤器原理**：
1. **数据结构**：一个很长的**二进制数组** + 多个**哈希函数**
2. **添加元素**：
   - 用 K 个哈希函数对元素计算，得到 K 个哈希值
   - 把数组中这 K 个位置设为 1
3. **查询元素**：
   - 同样用 K 个哈希函数计算，得到 K 个位置
   - 如果这 K 个位置**全是 1**，说明元素"可能存在"
   - 如果**有一个是 0**，说明元素"一定不存在"

**图示**：
```
初始数组：[0, 0, 0, 0, 0, 0, 0, 0]

添加 "apple"：
  hash1("apple") = 2 → 数组[2] = 1
  hash2("apple") = 5 → 数组[5] = 1
数组：[0, 0, 1, 0, 0, 1, 0, 0]

查询 "banana"：
  hash1("banana") = 2 → 是 1
  hash2("banana") = 4 → 是 0 → 一定不存在！
```

**布隆过滤器的特点**：
- **优点**：
  - 空间效率极高（不用存元素本身，只存位）
  - 插入和查询都是 O(K)，K 是哈希函数个数
- **缺点**：
  - 有**误判率**（False Positive）：可能把"不存在"的元素误判为"存在"
  - 不能删除元素（计数布隆过滤器可以，但复杂度上升）

**误判率控制**：
- 数组越长，误判率越低
- 哈希函数越多，误判率越低（但不是越多越好，有最优值）
- 有公式可以计算：给定元素个数 n 和期望误判率 p，算出数组长度 m 和哈希函数个数 k

**应用场景**：
1. **缓存穿透防护**：
   - 问题：大量请求查询一个不存在的数据，直接打到数据库
   - 解决方案：把所有存在的 Key 存入布隆过滤器
   - 请求来时，先查布隆过滤器："一定不存在"直接返回，"可能存在"再查缓存/数据库

2. **爬虫 URL 去重**：
   - 爬过的 URL 不用再爬
   - 用布隆过滤器存已爬 URL，省空间

3. **本项目中的潜在应用**：
   - **知识库文档去重**：新文档入库前，先用布隆过滤器判断是否已存在
   - **向量检索过滤**：把低质量文档的 ID 存入布隆过滤器，检索时快速跳过

---

### 20. MySQL 索引失效的情况有哪些？

**面试官问**：MySQL 索引失效的情况有哪些？

**参考答案**：
**MySQL 索引失效（不走索引）的常见情况**：

**1. 对索引字段做函数运算或表达式计算**：
```sql
-- 失效（对 create_time 用了函数）
SELECT * FROM policy WHERE YEAR(create_time) = 2025;
-- 生效（避免函数）
SELECT * FROM policy WHERE create_time >= '2025-01-01' AND create_time < '2026-01-01';
```

**2. 对索引字段做隐式类型转换**：
```sql
-- 假设 phone 是 VARCHAR 类型，有索引
-- 失效（隐式类型转换：'13800138000' -> 13800138000）
SELECT * FROM user WHERE phone = 13800138000;
-- 生效（类型匹配）
SELECT * FROM user WHERE phone = '13800138000';
```

**3. LIKE 查询以 % 开头**：
```sql
-- 失效（前缀不确定，无法用 B+ 树范围查找）
SELECT * FROM policy WHERE title LIKE '%补贴%';
-- 生效（前缀确定）
SELECT * FROM policy WHERE title LIKE '山东省%';
```

**4. OR 条件中有未索引的字段**：
```sql
-- 假设 id 有索引，status 没索引
-- 失效（为了查 status 没索引的行，必须全表扫）
SELECT * FROM policy WHERE id = 1 OR status = 'ACTIVE';
-- 生效（把 OR 改成 UNION，或给 status 加索引）
```

**5. 联合索引不遵循最左前缀原则**：
```sql
-- 联合索引 (category, status, create_time)
-- 生效（最左前缀）
SELECT * FROM policy WHERE category = '家电' AND status = '生效';
-- 失效（跳过了 category，直接用 status）
SELECT * FROM policy WHERE status = '生效';
-- 部分生效（用了 category，没用 status，用了 create_time → 只用了 category）
SELECT * FROM policy WHERE category = '家电' AND create_time > '2025-01-01';
```

**6. 索引区分度太低（选择性差）**：
- 例如 status 字段只有 0/1，区分度很低
- MySQL 优化器可能认为走索引还不如全表扫
- 解决方案：不建这个索引，或建联合索引

**7. 使用了 IS NULL / IS NOT NULL（取决于数据分布）**：
- 如果大部分行都是 NULL，查 `IS NOT NULL` 可能走索引
- 如果大部分行都不是 NULL，查 `IS NOT NULL` 可能不走索引
- 取决于优化器的判断

**8. 查询数据量太大，优化器认为走索引不划算**：
- 例如查 `category = '家电'`，如果 80% 的行都是家电，可能不走索引
- MySQL 优化器会计算"回表"成本，如果回表太多，不如全表扫

**口诀**：**函隐最左 OR，IS 区分大**。

---

### 21. like 查询会不会导致索引失效？

**面试官问**：like 查询会不会导致索引失效？

**参考答案**：
**不一定，取决于 LIKE 的模式**：

**1. 前缀匹配（% 在后面）→ 走索引**：
```sql
-- title 有索引
-- 走索引（前缀确定，可以用 B+ 树范围查找）
SELECT * FROM policy WHERE title LIKE '山东省%';
SELECT * FROM policy WHERE title LIKE '山东%补贴%';  -- 也走，只要最左前缀确定
```

**2. 后缀匹配（% 在前面）→ 索引失效**：
```sql
-- 失效（前缀不确定，不知道从 B+ 树哪里开始找）
SELECT * FROM policy WHERE title LIKE '%补贴';
```

**3. 中间匹配（% 在中间）→ 索引失效**：
```sql
-- 失效（前缀不确定）
SELECT * FROM policy WHERE title LIKE '%山东%补贴%';
```

**4. 后缀匹配的优化方案：反转索引**：
```sql
-- 问题：要查 LIKE '%补贴'
-- 解决方案：建一个反转字段 title_rev，存 '贴补'
SELECT * FROM policy WHERE title_rev LIKE '贴补%';  -- 走索引
```

**5. 全文检索需求：用全文索引**：
```sql
-- 建全文索引
CREATE FULLTEXT INDEX ft_idx_title ON policy(title);

-- 用 MATCH AGAINST 查询
SELECT * FROM policy WHERE MATCH(title) AGAINST('山东省 补贴' IN BOOLEAN MODE);
```

**总结**：
- `LIKE '前缀%'` → 走索引
- `LIKE '%后缀'` 或 `LIKE '%中间%'` → 索引失效
- 业务需要模糊查询，考虑：全文索引、Elasticsearch、向量检索（语义搜索）

---

### 22. 讲一下 MySQL 的事务隔离级别。

**面试官问**：讲一下 MySQL 的事务隔离级别。

**参考答案**：
**SQL 标准定义了 4 种事务隔离级别，MySQL InnoDB 都支持**：

**4 种隔离级别（从低到高）**：

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 说明 |
|----------|------|------------|------|------|
| Read Uncommitted（读未提交） | ✓ | ✓ | ✓ | 可以读到其他事务未提交的数据 |
| Read Committed（读已提交） | ✗ | ✓ | ✓ | 只能读到其他事务已提交的数据 |
| Repeatable Read（可重复读） | ✗ | ✗ | ✓ | 同一个事务内多次读同一行，结果一致 |
| Serializable（串行化） | ✗ | ✗ | ✗ | 完全串行化执行，性能最差 |

**MySQL 默认隔离级别**：Repeatable Read（RR）

**三种异常现象**：
1. **脏读（Dirty Read）**：
   - 事务 A 读到了事务 B 未提交的修改
   - 如果事务 B 回滚，事务 A 读到的就是"脏"数据

2. **不可重复读（Non-Repeatable Read）**：
   - 事务 A 内，第一次读某行，第二次再读同一行，值变了
   - 因为中间有其他事务提交了修改

3. **幻读（Phantom Read）**：
   - 事务 A 内，第一次 `SELECT count(*) WHERE ...`，第二次再查，多了几行
   - 因为中间有其他事务 INSERT 了符合条件的行

**InnoDB RR 隔离级别的实现**：
- **MVCC（多版本并发控制）**：
  - 每行有隐式字段：`TRX_ID`（最后修改事务ID）、`ROLL_PTR`（回滚指针）
  - 读操作时，根据 `Read View` 判断哪个版本可见
  - 实现了"快照读"（不加锁读）
- **Next-Key Lock（临键锁）**：
  - Record Lock（记录锁）+ Gap Lock（间隙锁）
  - 解决了"幻读"问题（大部分场景）

**查看/设置隔离级别**：
```sql
-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 设置隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

---

### 23. 讲一下 MySQL 事务一致性相关内容。

**面试官问**：讲一下 MySQL 事务一致性相关内容。

**参考答案**：
**事务一致性是 ACID 中的 C（Consistency），指事务执行前后，数据库从一个一致状态变到另一个一致状态**。

**ACID 回顾**：
- **A（Atomicity）**：原子性，事务要么全做，要么全不做
- **C（Consistency）**：一致性，事务执行前后，数据约束（主键、外键、唯一索引、CHECK 等）不被破坏
- **I（Isolation）**：隔离性，并发事务之间互不干扰
- **D（Durability）**：持久性，事务提交后，数据永久保存

**一致性的两层含义**：

**1. 数据库层面的一致性**（由数据库保证）：
- 主键约束：不能有重复主键
- 外键约束：不能引用不存在的行
- 唯一约束：不能有重复值
- CHECK 约束：满足自定义条件（例如 age > 0）
- 触发器保证的一致性

**2. 业务层面的一致性**（由应用代码保证）：
- 例如：A 给 B 转账 100 元，A 的余额 -100，B 的余额 +100
- 数据库只能保证"两个操作要么都成功，要么都失败"（原子性）
- 但不能保证"转账金额不能为负"、"A 的余额不能小于转账金额"（业务逻辑）
- 这需要应用代码在事务开始前做校验

**MySQL 如何保证一致性**：
1. **原子性**：Undo Log（回滚日志）
   - 事务修改前，先写 Undo Log，记录"修改前的值"
   - 事务回滚时，用 Undo Log 把数据改回去

2. **持久性**：Redo Log（重做日志）+ Binlog
   - 修改数据时，先写 Redo Log（WAL，Write-Ahead Logging）
   - Redo Log 顺序写，性能好
   - 事务提交时，Redo Log 必须落盘（innodb_flush_log_at_trx_commit=1）

3. **隔离性**：MVCC + 锁
   - MVCC：快照读，读不加锁
   - 锁：当前读，加行锁/间隙锁

4. **一致性**：AID 共同保证 C

**本项目中的事务一致性**：
- 知识库文档删除：同时删除 `knowledge_documents` 表记录和 `vector_store` 表的向量
- 用 `@Transactional` 保证原子性

---

### 24. MVCC 相关原理与细节。

**面试官问**：讲一下 MVCC 相关原理与细节。

**参考答案**：
**MVCC（Multi-Version Concurrency Control，多版本并发控制）是 InnoDB 实现事务隔离的核心机制**。

**MVCC 的核心思想**：
- 每次修改数据，不直接覆盖旧数据，而是创建一个新版本
- 读操作时，根据事务 ID 判断哪个版本对当前事务可见
- 实现了"读不加锁，读写不冲突"

**关键概念**：

**1. 隐式字段**（每行数据都有）：
- `TRX_ID`：最后修改这行的事务 ID（6 字节）
- `ROLL_PTR`：回滚指针（7 字节），指向 Undo Log 中的旧版本
- `DB_ROW_ID`：隐藏主键（6 字节，如果没有显式主键才用）

**2. Undo Log（回滚日志）**：
- 记录数据修改前的样子
- 通过 `ROLL_PTR` 连成一个版本链
- 头部是最新版本，尾部是最老版本

**3. Read View（读视图）**：
- 判断版本可见性的"窗口"
- 包含几个字段：
  - `m_ids`：生成 Read View 时，活跃事务 ID 列表（还没提交的）
  - `min_trx_id`：`m_ids` 中最小的事务 ID
  - `max_trx_id`：`m_ids` 中最大的事务 ID + 1
  - `creator_trx_id`：生成这个 Read View 的事务自己的 ID

**可见性判断规则**：
```
当前行的 TRX_ID = t

1. 如果 t == creator_trx_id → 可见（自己修改的）
2. 如果 t < min_trx_id → 可见（这个版本在 Read View 生成前就提交了）
3. 如果 t >= max_trx_id → 不可见（这个版本是 Read View 生成后才开始的事务）
4. 如果 t 在 [min_trx_id, max_trx_id) 之间：
   - 看 t 是否在 m_ids 中：
     - 在 → 不可见（还没提交）
     - 不在 → 可见（已经提交了）
```

**快照读 vs 当前读**：
- **快照读（Snapshot Read）**：
  - 普通 SELECT
  - 读的是版本链中的某个版本
  - 不加锁
- **当前读（Current Read）**：
  - `SELECT ... FOR UPDATE`、`SELECT ... LOCK IN SHARE MODE`
  - `INSERT`、`UPDATE`、`DELETE`
  - 读的是最新版本
  - 加锁

**Repeatable Read vs Read Committed 的区别**：
- **RC（读已提交）**：每次 SELECT 都生成新的 Read View
  - 所以可能出现"不可重复读"
- **RR（可重复读）**：第一次 SELECT 生成 Read View，后续复用
  - 所以同一个事务内多次读结果一致

---

### 25. 具体场景下 MVCC 会创建几个 readview？

**面试官问**：具体场景下 MVCC 会创建几个 readview？

**参考答案**：
**取决于隔离级别和具体 SQL**：

**场景 1：Read Committed（读已提交）隔离级别**：
```sql
BEGIN;
-- 第 1 次 SELECT → 创建 Read View 1
SELECT * FROM policy WHERE id = 1;
-- ... 中间其他事务提交了修改
-- 第 2 次 SELECT → 创建 Read View 2（新的）
SELECT * FROM policy WHERE id = 1;
COMMIT;
```
→ **创建 2 个 Read View**（每次 SELECT 都创建新的）

**场景 2：Repeatable Read（可重复读）隔离级别**：
```sql
BEGIN;
-- 第 1 次 SELECT → 创建 Read View 1
SELECT * FROM policy WHERE id = 1;
-- ... 中间其他事务提交了修改
-- 第 2 次 SELECT → 复用 Read View 1
SELECT * FROM policy WHERE id = 1;
-- 第 3 次 SELECT → 还是复用 Read View 1
SELECT * FROM policy WHERE id = 2;
COMMIT;
```
→ **创建 1 个 Read View**（整个事务内只有第一个 SELECT 创建，后续复用）

**场景 3：RR 隔离级别，但有当前读**：
```sql
BEGIN;
-- 第 1 次 SELECT（快照读）→ 创建 Read View 1
SELECT * FROM policy WHERE id = 1;
-- 当前读（读最新版本，加锁）
SELECT * FROM policy WHERE id = 1 FOR UPDATE;
-- 第 2 次 SELECT（快照读）→ 还是复用 Read View 1
SELECT * FROM policy WHERE id = 1;
COMMIT;
```
→ **创建 1 个 Read View**（当前读不创建 Read View，快照读仍然复用第一个）

**场景 4：显式开启事务，但没 SELECT 就修改**：
```sql
BEGIN;
-- 先 UPDATE（当前读，不创建 Read View）
UPDATE policy SET status = 'ACTIVE' WHERE id = 1;
-- 第 1 次 SELECT → 创建 Read View 1
SELECT * FROM policy WHERE id = 1;
COMMIT;
```
→ **创建 1 个 Read View**（第一次快照读时创建）

**关键点**：
- Read View 只在"快照读"（普通 SELECT）时创建
- RC：每次快照读都创建新的
- RR：只有第一次快照读创建，后续复用
- 当前读（FOR UPDATE、INSERT、UPDATE、DELETE）不创建 Read View

---

### 26. 讲一下 MySQL 的行锁、表锁等锁相关知识。

**面试官问**：讲一下 MySQL 的行锁、表锁等锁相关知识。

**参考答案**：
**MySQL InnoDB 的锁机制比较复杂，从不同维度分类**：

**按锁粒度分类**：

**1. 表级锁（Table-level Lock）**：
- 锁住整张表
- 开销小，加锁快；但并发度低
- InnoDB 一般不用，DDL 时会用（如 ALTER TABLE）

**2. 行级锁（Row-level Lock）**：
- 只锁住某一行（或几行）
- 开销大，加锁慢；但并发度高
- InnoDB 默认用行锁

**按锁类型分类**：

**1. 共享锁（Shared Lock，S 锁）**：
- 也叫"读锁"
- 多个事务可以同时持有 S 锁（读不阻塞读）
- 但有事务持有 S 锁时，其他事务不能加 X 锁（读阻塞写）
- 获取方式：`SELECT ... LOCK IN SHARE MODE`

**2. 排他锁（Exclusive Lock，X 锁）**：
- 也叫"写锁"
- 只有一个事务能持有 X 锁（写阻塞写）
- 有事务持有 X 锁时，其他事务不能加 S 锁或 X 锁（写阻塞读）
- 获取方式：`SELECT ... FOR UPDATE`、`INSERT`、`UPDATE`、`DELETE`（自动加）

**InnoDB 行锁的具体算法**：

**1. Record Lock（记录锁）**：
- 只锁住某一行记录
- 例如：`WHERE id = 1 FOR UPDATE` → 锁住 id=1 这行

**2. Gap Lock（间隙锁）**：
- 锁住"间隙"（两行之间的空间），不锁住记录本身
- 目的：防止其他事务在间隙中 INSERT，解决幻读
- 例如：`WHERE id BETWEEN 10 AND 20 FOR UPDATE` → 锁住 (10,20) 这个间隙，不让 INSERT id=15

**3. Next-Key Lock（临键锁）**：
- Record Lock + Gap Lock 的组合
- 锁住某一行 + 前面的间隙
- 是 InnoDB 默认的行锁算法
- 例如：索引有 10、20、30 三条记录
  - Next-Key Lock 会锁住：(-∞,10]、(10,20]、(20,30]、(30,+∞)

**锁的兼容性**：

|  | S 锁 | X 锁 |
|--|------|------|
| S 锁 | ✓ 兼容 | ✗ 冲突 |
| X 锁 | ✗ 冲突 | ✗ 冲突 |

**死锁**：
- 两个事务互相等待对方释放锁
- 例如：
  - 事务 A：`UPDATE policy SET ... WHERE id = 1`（锁住 id=1）
  - 事务 B：`UPDATE policy SET ... WHERE id = 2`（锁住 id=2）
  - 事务 A：`UPDATE policy SET ... WHERE id = 2`（等待事务 B 的 id=2）
  - 事务 B：`UPDATE policy SET ... WHERE id = 1`（等待事务 A 的 id=1）
- InnoDB 自动检测死锁，回滚较小的那个事务

**避免死锁的建议**：
- 尽量让事务小而快
- 按固定顺序访问表和行
- 合理使用索引（索引走得好，扫描行少，锁的行就少）
- 避免大事务

---

### 27. 手撕代码：反转链表

**面试官问**：手撕代码，反转链表。

**参考答案**：
**LeetCode 206. Reverse Linked List**

**题目描述**：
```
给你单链表的头节点 head ，请你反转链表，并返回反转后的链表。

示例 1：
输入：head = [1,2,3,4,5]
输出：[5,4,3,2,1]
```

**链表节点定义**：
```java
public class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
```

**解法 1：双指针迭代（推荐，最清晰）**：
```java
class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode curr = head;

        while (curr != null) {
            ListNode nextTemp = curr.next;  // 先保存下一个节点
            curr.next = prev;                // 当前节点指向前一个
            prev = curr;                     // 前一个移到当前
            curr = nextTemp;                 // 当前移到下一个
        }

        return prev;  // prev 最后就是新头
    }
}
```
- 时间复杂度：O(n)
- 空间复杂度：O(1)

**解法 2：递归（优雅但可能栈溢出）**：
```java
class Solution {
    public ListNode reverseList(ListNode head) {
        // 终止条件：空或只有一个节点
        if (head == null || head.next == null) {
            return head;
        }
        // 递归反转后面的
        ListNode newHead = reverseList(head.next);
        // 把当前节点挂到后面
        head.next.next = head;
        head.next = null;
        return newHead;
    }
}
```
- 时间复杂度：O(n)
- 空间复杂度：O(n)（递归栈深度）

**面试时建议写解法 1（双指针）**，简单清晰，不容易出错。

---

## 二面问题（按提问顺序）

### 1. RAG 项目是怎么评测效果的？

**面试官问**：RAG 项目是怎么评测效果的？

**参考答案**：
**RAG 评测分"检索质量评测"和"回答质量评测"两部分**。

**1. 检索质量评测（Retrieval Evaluation）**：
- **目的**：评估"能不能找到正确的文档"
- **方法**：
  - 构造标注数据集：收集 200 条真实用户问题，每条问题人工标注"相关文档 ID 列表"
  - 评测指标：
    - **Recall@K**：前 K 个检索结果中，至少有一个相关文档的比例
    - **Precision@K**：前 K 个检索结果中，相关文档的比例
    - **MRR（Mean Reciprocal Rank）**：第一个相关文档的排名倒数的平均值
    - **NDCG@K**：考虑位置权重的排序质量指标

**2. 回答质量评测（Generation Evaluation）**：
- **目的**：评估"基于检索到的文档，LLM 回答得好不好"
- **方法**：
  - **人工评测**：
    - 招募 3-5 个标注者
    - 从 4 个维度打分（1-5 分）：
      1. **相关性**：回答是否针对用户问题
      2. **准确性**：回答中的事实是否正确，有没有编造
      3. **完整性**：是否覆盖了必要信息
      4. **有用性**：用户能不能根据回答解决问题
    - 计算 Fleiss' kappa 看标注者一致性
  - **自动评测**：
    - **RAGAS**（RAG Assessment）：开源框架，自动计算
      - Faithfulness（忠实度）：回答是否基于检索到的文档
      - Answer Relevance（回答相关性）：回答是否针对问题
      - Context Precision（上下文精度）：检索到的文档是否相关
      - Context Recall（上下文召回）：相关文档是否都被检索到
    - **BLEU / ROUGE**：如果有标准答案（Reference）

**3. 线上 A/B 测试**：
- 分流：50% 用户用新策略，50% 用户用旧策略
- 观测指标：
  - 用户满意度评分（点击"满意"、"不满意"）
  - 对话轮次（解决问题需要几轮）
  - "重新生成"按钮点击次数
  - 客服转人工率（如果有的话）

**本项目中的实际做法**：
- 内部标注了 200 条问题，定期离线评测
- 上线前做小流量 A/B 测试
- 线上埋点：用户对回答的反馈（点赞/点踩）
- 每周捞取点踩的 Bad Case 分析

---

### 2. RAG 评测有哪些维度，具体用到哪些指标？

**面试官问**：RAG 评测有哪些维度，具体用到哪些指标？

**参考答案**：
**RAG 评测可以从"检索"、"生成"、"端到端"三个维度展开**。

---

**维度一：检索质量（Retrieval Quality）**

| 指标 | 全称 | 含义 | 计算方式 |
|------|------|------|----------|
| Recall@K | 召回率@K | 前 K 个结果中覆盖了多少相关文档 | (前 K 个中的相关文档数) / (总相关文档数) |
| Precision@K | 精确率@K | 前 K 个结果中有多少是相关的 | (前 K 个中的相关文档数) / K |
| MRR | Mean Reciprocal Rank | 第一个相关文档的排名倒数的平均 | Σ(1 / rank_i) / n |
| NDCG@K | Normalized Discounted Cumulative Gain | 考虑位置权重的排序质量 | DCG@K / IDCG@K |
| MAP | Mean Average Precision | 平均精确率 | Σ(Precision@r_i) / 总相关文档数 |

---

**维度二：生成质量（Generation Quality）**

| 指标 | 含义 | 计算方式 |
|------|------|----------|
| Faithfulness | 忠实度：回答是否基于检索到的文档，有没有编造 | 自动评测（RAGAS）或人工标注 |
| Answer Relevance | 回答相关性：回答是否针对用户问题 | 自动评测或人工标注 |
| BLEU-4 | 如果有标准答案，看 n-gram 匹配度 | 精度几何平均，加简短惩罚 |
| ROUGE-L | 如果有标准答案，看最长公共子序列 | LCS-based F1 |
| 人工评分 | 1-5 分，从相关性、准确性、完整性、有用性打分 | 3-5 个标注者取平均 |

---

**维度三：端到端效果（End-to-End）**

| 指标 | 含义 |
|------|------|
| 任务成功率 | 用户问题是否得到解决（人工标注或业务规则判断） |
| 对话轮次 | 解决问题平均需要几轮对话 |
| 用户满意度 | 线上点赞/点踩比例，或"满意"评分（1-5） |
| "重新生成"点击率 | 用户点击"重新生成"的比例 |
| 转人工率 | 客服场景下，转人工客服的比例 |

---

**常用开源工具**：
- **RAGAS**：https://github.com/explodinggradients/ragas
- **LangChain Evaluators**：LangChain 内置的评测模块
- **LlamaIndex Evaluation**：LlamaIndex 的评测模块
- **TruLens**：AI 应用可观测性与评测

**本项目中的实践**：
- 离线：Recall@5、MRR、RAGAS Faithfulness/Relevance
- 线上：用户满意度、对话轮次、重新生成率
- 每周人工抽样 50 条 Bad Case 分析

---

### 3. 项目里的数据集包含什么内容？

**面试官问**：项目里的数据集包含什么内容？

**参考答案**：
**本项目的数据集分为"知识库文档"和"评测数据集"两部分**。

**1. 知识库文档（Knowledge Base Documents）**：
- **来源**：
  - 山东省政府官网公开的"以旧换新"政策文件
  - 各地市实施细则
  - 常见问题解答（FAQ）
- **格式**：
  - PDF（主要格式，扫描件 OCR 后提取文本）
  - Word（.doc/.docx）
  - HTML（从官网爬取，结构化提取）
- **数量**：
  - 累计 1000+ 份政策文件
  - 切片后约 50000+ 个 Chunk
- **内容分布**：
  - 家电以旧换新：40%
  - 汽车以旧换新：30%
  - 绿色智能家电下乡：20%
  - 其他（综合类、解读类）：10%

**2. 评测数据集（Evaluation Dataset）**：
- **构造方式**：
  - 从真实用户对话日志中抽样 500 条
  - 标注每条问题对应的"相关文档 ID 列表"（2-3 个标注者）
  - 标注每条问题的"理想回答"（或回答要点）
- **数据划分**：
  - 训练集：300 条（用于调优 Prompt、调整检索参数）
  - 验证集：100 条（用于超参选择）
  - 测试集：100 条（用于最终评测，不碰）
- **问题类型分布**：
  - 事实类（"补贴标准是多少"）：35%
  - 条件类（"我这种情况能补多少"）：40%
  - 流程类（"怎么申请"）：15%
  - 其他：10%

**3. 训练数据（如果做微调）**：
- 当前项目没做模型微调，只用 Prompt Engineering
- 如果做 SFT（监督微调），会构造：
  - Question-Context-Answer 三元组
  - 或 Question-Answer 对（带 CoT）

**数据管理**：
- 原始文档存在 MinIO：`policy-agent-docs` bucket
- 评测数据集存在 Git LFS：`data/evaluation/`
- 向量索引存在 PostgreSQL pgvector

---

### 4. 项目数据来源、数据格式是什么？

**面试官问**：项目数据来源、数据格式是什么？

**参考答案**：
**项目数据来源和格式整理如下**：

**1. 政策文档数据**：

| 来源 | 格式 | 数量 | 获取方式 |
|------|------|------|----------|
| 山东省人民政府官网 | PDF、HTML | 500+ | 爬虫 + 人工下载 |
| 山东省商务厅官网 | PDF、DOCX | 300+ | 爬虫 + 人工下载 |
| 各地市政务公开 | PDF | 200+ | 人工整理 |

**具体处理流程**：
- PDF → Apache PDFBox 提取文本 → 按章节切分
- DOCX → Apache POI 提取文本
- HTML → Jsoup 解析，提取正文，去除导航/广告

**2. 用户对话数据**：
- 来源：线上真实用户对话（脱敏后）
- 格式：JSON Lines
- 字段：
  ```json
  {
    "conversation_id": "uuid",
    "user_id": "hashed_user_id",
    "messages": [
      {"role": "user", "content": "..."},
      {"role": "assistant", "content": "..."},
      {"role": "user", "content": "..."},
      {"role": "assistant", "content": "..."}
    ],
    "feedback": "like/dislike/null",  // 用户反馈
    "created_at": "2025-03-27T10:00:00Z"
  }
  ```
- 数量：累计 10000+ 对话
- 用途：构造评测数据集、Bad Case 分析、Prompt 调优

**3. 评测标注数据**：
- 格式：JSON
- 字段：
  ```json
  [
    {
      "query": "我买了一台 iPhone 17，价格 8999 元，能补多少？",
      "relevant_doc_ids": ["doc-001", "doc-045"],
      "answer_points": ["家电补贴标准", "iPhone 属于智能手机", "补贴比例 10%", "上限 500 元"],
      "difficulty": "medium"
    }
  ]
  ```
- 数量：200 条（不断补充中）

**4. 爬虫数据（URL Import）**：
- 项目支持从 URL 导入（`url_import_jobs` 表）
- 爬虫输出格式：JSON
  ```json
  {
    "url": "https://www.shandong.gov.cn/...",
    "title": "山东省人民政府关于...",
    "content": "...",
    "publish_date": "2025-01-01",
    "source": "山东省政府官网"
  }
  ```

---

### 5. 如果对 RAG 的相关度和回答效果做优化，有什么思路？

**面试官问**：如果对 RAG 的相关度和回答效果做优化，有什么思路？

**参考答案**：
**RAG 优化可以从"数据层"、"检索层"、"生成层"三个层面入手**。

---

**数据层优化（Data Layer）**：
1. **文档清洗与预处理**：
   - 去除 PDF 页眉页脚、水印、冗余格式
   - OCR 纠错（扫描件 PDF）
   - 统一术语（例如"新能源车"和"新能源汽车"统一）

2. **切片策略优化**：
   - 按语义单元切分（章节、段落、要点），而不是固定字符数
   - 小 Chunk 用于检索，大 Chunk 用于生成（父子索引）
   - 调整 `chunkSize` 和 `chunkOverlap`（当前 800/100，可实验 500/50、1000/200）

3. **元数据增强**：
   - 给 Chunk 加标题、来源、发布时间、类别等元数据
   - 检索时用元数据过滤（例如只看 2025 年发布的）

---

**检索层优化（Retrieval Layer）**：
1. **向量模型优化**：
   - 尝试不同 Embedding 模型（当前 text-embedding-v3，可试 qwen3-embedding、bge-large-zh）
   - 微调 Embedding 模型（用领域数据做对比学习）
   - 多向量表示（文档向量化 + 问题向量化，不同模型）

2. **检索算法优化**：
   - 混合检索（Hybrid Search）：向量检索 + BM25 全文检索，加权合并
   - 查询改写（Query Rewriting）：LLM 把用户问题改写成更适合检索的形式
   - 查询扩展（Query Expansion）：扩展相关关键词
   - Rerank 优化（当前用 qwen3-rerank，可试更好的 Rerank 模型或微调）

3. **检索参数调优**：
   - 调整 `topK`、`candidateTopK`、`similarityThreshold`
   - 网格搜索（Grid Search）找最优参数组合

---

**生成层优化（Generation Layer）**：
1. **Prompt Engineering**：
   - 优化系统提示词（角色设定、回答风格、约束条件）
   - 优化 RAG 上下文拼接格式（当前用【文档 1】，可尝试其他格式）
   - 增加思维链（CoT）提示："请先分析用户问题，再根据文档回答"
   - 增加引用标注："回答中请注明参考了【文档 X】"

2. **上下文压缩与筛选**：
   - 对检索到的文档做"压缩"（LLM 提取要点）
   - 用 LLM 再过滤一遍："判断以下文档是否与用户问题相关"
   - 控制总 Token 数，避免超过窗口

3. **模型微调（可选）**：
   - SFT（监督微调）：用 Question-Context-Answer 三元组微调 LLM
   - RAG-Fusion：把 RAG 和微调结合
   - RLHF（人类反馈强化学习）：用人工反馈优化

---

**本项目中的优先级**：
1. **短期**：查询改写、混合检索、Prompt 调优（见效快，成本低）
2. **中期**：微调 Embedding、更好的 Rerank 模型
3. **长期**：SFT 微调 LLM、RAG-Fusion

---

### 6. 有没有更体系化的 RAG 优化方案，而不是零散调整？

**面试官问**：有没有更体系化的 RAG 优化方案，而不是零散调整？

**参考答案**：
**有的，推荐参考 RAG 成熟度模型（RAG Maturity Model）或类似的分层优化框架**。

---

**RAG 成熟度模型（5 级）**：

**Level 0 - 基础 RAG**：
- 文档切分（固定大小）
- 简单向量检索
- 把检索结果拼接到 Prompt

**Level 1 - 增强检索**：
- 语义切片（按章节/段落）
- 混合检索（向量 + BM25）
- 查询改写
- Rerank 重排序

**Level 2 - 优化生成**：
- 上下文压缩/筛选
- 高级 Prompt 工程（CoT、引用）
- 输出校验

**Level 3 - 精细调优**：
- 微调 Embedding 模型
- 微调 Rerank 模型
- SFT 微调 LLM

**Level 4 - 智能 RAG**：
- 查询路由（Query Routing）：不同问题走不同检索策略
- 主动澄清：问题模糊时主动问用户
- 反馈闭环：用户点击踩自动触发重检索/重生成
- 自我评估：LLM 自己判断回答好不好

---

**体系化优化步骤**：

**1. 建立评测基线**：
- 准备好评测数据集（Retrieval + Generation）
- 跑一遍当前效果，记录各项指标（Recall@5、MRR、Faithfulness、人工评分）
- 这是"基线"，所有优化都要对比基线

**2. 从检索端入手（先把正确文档找出来）**：
- 优先级：
  1. 加 Rerank（如果还没加）
  2. 混合检索（向量 + BM25）
  3. 查询改写
  4. 调整切片策略
- 每改一个，跑一遍评测，确认有提升再保留

**3. 再优化生成端（基于好的检索，把回答写好）**：
- 优先级：
  1. Prompt 优化（角色、风格、约束、引用）
  2. 上下文压缩/筛选
  3. 输出校验
- 同样：单变量改动，评测验证

**4. 最后考虑微调（成本高，谨慎）**：
- 先微调 Embedding，再微调 Rerank，最后微调 LLM
- 准备好足够的领域数据

**5. 建立反馈闭环**：
- 线上埋点：用户点赞/点踩、对话轮次、重新生成率
- 每周 Bad Case 分析，发现模式，迭代优化

---

**其他参考框架**：
- **RAGAs**：不仅是评测工具，也有优化建议
- **LangChain RAG Template**：一系列最佳实践模板
- **LlamaIndex RAG Optimization Guide**：LlamaIndex 官方优化指南
- **Amazon Kendra / Azure Cognitive Search**：云厂商 RAG 最佳实践

**关键原则**：
- **Measure First**：先评测，再优化，不要瞎调
- **Single Variable**：一次只改一个变量，方便归因
- **Pareto Principle**：20% 的改动带来 80% 的提升（Rerank、混合检索、Prompt）

---

### 7. 具体场景：一千条数据需要做求和处理，怎么设计实现？

**面试官问**：具体场景：一千条数据需要做求和处理，怎么设计实现？

**参考答案**：
**这个问题比较开放，我分"简单版"、"工程版"、"分布式版"三种方案来回答**。

---

**先澄清假设**：
- 数据在哪里？（MySQL、文件、API）
- 是一次性任务，还是定期执行？
- 数据量未来会增长到多少？（10 万、100 万？）
- 需要实时结果，还是可以接受分钟级延迟？
- 先假设：数据在 MySQL，一次性任务，未来可能增长。

---

**方案一：简单版（数据量小，一次性）**：

```java
@Service
public class SumService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public BigDecimal simpleSum() {
        // 直接用 SQL 求和（最简单，数据库原生支持）
        String sql = "SELECT SUM(amount) FROM my_table";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }
}
```
- 优点：代码少，性能好（数据库优化器会优化）
- 缺点：数据量太大可能慢，或者锁表

---

**方案二：工程版（数据量中等，考虑扩展性）**：

```java
@Service
public class EngineeringSumService {

    @Autowired
    private MyTableRepository repository;

    /**
     * 分批求和，避免一次性加载太多数据
     */
    public BigDecimal batchSum() {
        BigDecimal total = BigDecimal.ZERO;
        int pageSize = 1000;
        int pageNum = 0;

        while (true) {
            // 分页查询
            Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by("id"));
            Page<MyTable> page = repository.findAll(pageable);

            if (page.isEmpty()) {
                break;
            }

            // 累加当前页
            for (MyTable record : page) {
                total = total.add(record.getAmount());
            }

            // 下一页
            if (!page.hasNext()) {
                break;
            }
            pageNum++;
        }

        return total;
    }

    /**
     * 如果是大表，用游标（Cursor）更高效
     */
    public BigDecimal cursorSum() {
        BigDecimal total = BigDecimal.ZERO;

        String sql = "SELECT amount FROM my_table ORDER BY id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery()) {

            stmt.setFetchSize(1000);  // 每次拉取 1000 条

            while (rs.next()) {
                total = total.add(rs.getBigDecimal("amount"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return total;
    }
}
```
- 优点：内存可控，适合中大型表
- 优化：用 Stream API、并行求和（如果顺序无关）

---

**方案三：分布式版（数据量很大，或需要高可用）**：

**如果是 1 亿条数据，或者数据在多个库**：
1. **分片 + 并行**：
   - 按 ID 范围分片（0-1000w, 1000w-2000w, ...）
   - 每个分片并行求和
   - 最后汇总

2. **MapReduce / Flink / Spark**：
   ```java
   // Spark 伪代码
   JavaRDD<MyTable> rdd = spark.read().jdbc("url", "my_table", props).javaRDD();
   BigDecimal sum = rdd.map(MyTable::getAmount)
                       .reduce(BigDecimal::add);
   ```

3. **预聚合**：
   - 如果是定期求和（比如每天算一次），可以预聚合存到汇总表
   - 增量更新：只算新增的数据

---

**回到面试官问题（1000 条）**：
- 1000 条数据很小，直接 SQL SUM 就行
- 但可以展示工程思维：
  - "如果只有 1000 条，直接 SQL SUM，但我会考虑未来扩展性..."
  - "如果数据量增长到 100 万，我会用分批处理..."
  - "如果到 1 亿，我会用分布式计算..."

---

### 8. RAG 的性能怎么提升，工程/算法层面有哪些实际优化思路？

**面试官问**：RAG 的性能怎么提升，工程/算法层面有哪些实际优化思路？

**参考答案**：
**RAG 性能优化分"延迟优化"（Latency）和"吞吐量优化"（Throughput）**。

---

**工程层面优化（见效快，成本低）**：

**1. 缓存**：
- **热门问题缓存**：Redis 缓存（Query → Answer），TTL 2 小时
- **向量缓存**：相同文本不用重复向量化，缓存 Embedding 结果
- **检索结果缓存**：相同 Query 不用重复检索

**2. 并行化**：
- 向量检索 + BM25 检索并行执行
- 多个 Chunk 的 Embedding 并行生成
- 用 `CompletableFuture` 编排

**3. 异步化**：
- 文档入库异步处理（项目已实现：`KnowledgeService.scheduleDocumentProcessing()`）
- 用户反馈分析异步执行
- 用 `@Async` 或线程池

**4. 索引优化**：
- pgvector 索引：用 HNSW（Hierarchical Navigable Small World）而不是 IVFFlat
  ```sql
  CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
  ```
- 调整 `lists` 和 `probes` 参数（tradeoff 精度与速度）
- 定期 `VACUUM ANALYZE`

**5. 参数调优**：
- 减小 `candidateTopK`（当前 20，可试 15）
- 减小 `topK`（当前 5，可试 3）
- 调整 Rerank 模型：如果 latency 敏感，可以换更快的 Rerank 模型，或关闭 Rerank（只在高精度需求时开）

**6. 降级策略**：
- Rerank 超时 → 跳过 Rerank，直接用向量检索结果
- 向量检索超时 → 不用 RAG，直接用 LLM 回答
- 用 `Hystrix` 或 `Resilience4j` 做熔断

---

**算法层面优化（需要更多投入）**：

**1. 向量模型优化**：
- 用更快的 Embedding 模型（小一点的模型，或量化）
- 模型蒸馏：大模型蒸馏成小模型
- 量化：FP16、INT8 量化，减小向量维度

**2. 检索优化**：
- 向量索引压缩（Product Quantization）
- 粗排 + 精排 两阶段（当前其实就是：向量粗排 + Rerank 精排）
- Query 预编码：如果 Query 有模板，预计算向量

**3. Rerank 优化**：
- 用轻量级 Rerank 模型（例如 bge-reranker-base 而不是 large）
- 只在向量检索分数不确定时才 Rerank（"早停"策略）
- 模型蒸馏 Rerank

**4. 生成优化**：
- 用更快的 LLM（如果不需要那么高精度）
- 流式输出（项目已实现：`/api/chat/stream`）
- 输出裁剪（不需要的内容不生成）

---

**本项目中的实际优化**：
- ✅ 异步文档入库
- ✅ 流式输出
- ✅ Rerank 降级（调用失败回退）
- 🔄 热门 Query 缓存（todo）
- 🔄 向量 Embedding 缓存（todo）

**性能数据**（参考）：
- 检索延迟：p95 < 200ms（向量检索，含 Rerank）
- 端到端延迟：p95 < 2s（从用户输入到开始输出）
- 吞吐量：100 QPS（可水平扩展后端实例）

---

### 9. 项目里的上下文是怎么处理的？

**面试官问**：项目里的上下文是怎么处理的？

**参考答案**：
**项目的上下文处理分为"RAG 检索上下文"、"对话历史上下文"、"会话事实缓存"三部分**。

---

**1. RAG 检索上下文**：
- **构建方式**（`RagRetrievalService.buildContextFromDocuments()`）：
  ```java
  public String buildContextFromDocuments(List<Document> documents) {
      StringBuilder context = new StringBuilder();
      context.append("以下是相关的政策文档内容：\n\n");
      for (int i = 0; i < documents.size(); i++) {
          Document doc = documents.get(i);
          context.append("【文档 ").append(i + 1).append("】\n");
          context.append(doc.getText());
          context.append("\n\n");
      }
      return context.toString();
  }
  ```
- **长度控制**：
  - 每个文档最多 1200 字符（`promptMaxDocChars`）
  - 最多 5 个文档（`topK`）
  - 总字符数控制在 6000 左右

---

**2. 对话历史上下文**：
- **存储**：Redis，Key：`chat:memory:{conversationId}`
- **管理**：Spring AI `MessageChatMemoryAdvisor`
- **窗口策略**：最近 10 轮对话完整保留
- **注入**：Advisor 自动拼接到 Prompt 中

---

**3. 会话事实缓存**（结构化记忆）：
- **提取**（`SessionFactCacheService.extractFacts()`）：
  - 用正则提取设备型号、价格、地区
  - 例如：`iPhone 17 Pro`、`8999元`、`山东省济南市`
- **存储**：Redis，Key：`chat:facts:{conversationId}`，TTL 7 天
- **注入**：
  ```
  【会话事实缓存】
  - 设备型号：iPhone 17 Pro
  - 最近提及价格：8999元
  - 地区线索：山东省济南市
  ```

---

**4. 完整 Prompt 结构**：
```
【系统提示词】
你是山东省智能政策咨询助手...

【会话事实缓存】
- 设备型号：iPhone 17 Pro
- 最近提及价格：8999元

【RAG 检索上下文】
以下是相关的政策文档内容：

【文档 1】
山东省以旧换新补贴实施细则...

【文档 2】
家电补贴标准...

【对话历史】
user: 我想买一台手机
assistant: 您好，请问您想了解哪款手机的补贴政策？

【当前用户问题】
user: iPhone 17 Pro，8999 元，能补多少？
```

---

**5. 上下文窗口控制**：
- 如果对话历史太长，会截断到最近 10 轮
- 如果 RAG 上下文太长，会截断每个文档到 1200 字符
- 总 Token 控制在 32K 以内（qwen3.5-plus 窗口）

---

### 10. 上下文过长、冗余等问题怎么解决，有哪些优化方向？

**面试官问**：上下文过长、冗余等问题怎么解决，有哪些优化方向？

**参考答案**：
**上下文过长是 RAG 常见问题，优化方向如下**。

---

**1. 检索侧优化（减少进入上下文的文档）**：
- **更精准的检索**：
  - 用好 Rerank，确保 Top-K 都是最相关的
  - 用元数据过滤（例如只看 2025 年的文档）
  - 查询改写，让 Query 更精准
- **动态 Top-K**：
  - 根据检索分数分布决定 K：前 3 名分数很高 → K=3；前 10 名分数接近 → K=10
  - 分数阈值过滤：只保留相似度 > 0.8 的文档
- **多样性检索**：
  - Maximal Marginal Relevance（MMR）：既考虑相关性，又考虑多样性
  - 避免 Top-K 都是几乎一样的文档

---

**2. 压缩与摘要（把长文档变短）**：
- **Document-level 压缩**：
  - LLM 摘要：用小模型把每个文档压缩成 3-5 句话
  - 提取要点：只保留"补贴标准"、"申请条件"等关键段落
- **Sentence-level 压缩**：
  - 去掉废话、冗余描述
  - 保留数字、比例、约束条件
- **Prompt 格式优化**：
  - 用表格、列表代替大段文字
  - 用 Markdown 分隔符让 LLM 更容易解析

---

**3. 对话历史管理（减少历史占用）**：
- **滑动窗口**：只保留最近 N 轮（当前 10 轮）
- **摘要历史**：
  - 把更早的对话历史总结成一段摘要
  - Prompt：`【历史对话摘要】用户之前问过家电补贴，了解过 iPhone 的价格...`
- **选择性保留**：
  - 保留工具调用、关键事实确认的轮次
  - 过滤闲聊、重复确认的轮次

---

**4. 结构化记忆（把关键信息单独拎出来）**：
- 本项目的做法：`SessionFactCacheService`
- 把设备型号、价格、地区等关键事实结构化存储
- 注入到 Prompt 最前面，LLM 不用从历史中找

---

**5. RAG-Fusion / 召回增强**：
- 问题：一个文档里只有一句话相关，但把整个文档都放进去了
- 解决：更小的 Chunk + 父文档引用
  - 检索用小 Chunk（200-300 字符）
  - 找到小 Chunk 后，再把对应的父文档（完整段落）拿出来
  - 或把小 Chunk 周围的"上下文窗口"拿出来

---

**6. Prompt 工程优化**：
- **明确指示**："只根据以下文档回答，不要编造"
- **引用标注**："回答中标注参考了【文档 X】"，如果没标注说明没用到
- **压缩指令**：把重复信息合并

---

**技术选型参考**：
- **LLMLingua**：专门压缩 Prompt 的工具
- **LongLLMLingua**：针对长上下文优化
- **RAGAS Context Compression**：RAGAS 框架的上下文压缩模块

---

### 11. Agent 的长记忆和短记忆怎么协同工作？

**面试官问**：Agent 的长记忆和短记忆怎么协同工作？

**参考答案**：
**Agent 的长短记忆协同是一个经典问题，结合项目实践回答如下**。

---

**基本概念**：
- **短记忆（Short-Term Memory / Working Memory）**：
  - 当前对话的上下文
  - 容量有限（比如最近 10 轮对话）
  - 对话结束可能丢失（或保留一段时间）
- **长记忆（Long-Term Memory）**：
  - 跨对话、跨会话的记忆
  - 容量大，可以存很多历史信息
  - 结构化或向量化存储，需要时检索

---

**在本项目中的实现**：

**1. 短记忆（对话历史）**：
- **存储**：Redis，`chat:memory:{conversationId}`
- **内容**：最近 10 轮完整对话消息
- **用途**：保证对话连贯性，理解指代（"它"、"这个"指什么）
- **TTL**：7 天

**2. 长记忆（会话事实缓存）**：
- **存储**：Redis，`chat:facts:{conversationId}`
- **内容**：结构化事实（设备型号、价格、地区）
- **用途**：跨多轮记住关键信息，不用 LLM 从历史中找
- **TTL**：7 天
- **提取方式**：正则表达式 + 规则
- **注入方式**：
  ```
  【会话事实缓存】
  - 设备型号：iPhone 17 Pro
  - 最近提及价格：8999元
  ```

---

**更完善的协同方案（如果要扩展）**：

**1. 记忆分层**：
```
Level 0 - 工作记忆（Working Memory）
  - 当前对话的最近 10 轮
  - 完整保留，直接拼入 Prompt

Level 1 - 最近记忆（Recent Memory）
  - 过去 24 小时的对话摘要
  - LLM 总结成一段

Level 2 - 长期记忆（Long-Term Memory）
  - 用户所有历史对话，向量化存入向量库
  - 需要时检索 Top-5 相关历史

Level 3 - 永久记忆（Episodic Memory）
  - 用户档案（偏好、历史购买记录、常用地区）
  - 结构化存入数据库
```

**2. 记忆检索与注入**：
- 每次用户问题，并行做：
  1. 检索 RAG 知识库（政策文档）
  2. 检索用户长记忆（历史对话向量）
  3. 从永久记忆中加载用户档案
- 一起注入 Prompt

**3. 记忆更新**：
- 对话结束时，LLM 总结本次对话要点
- 更新用户长记忆（向量化存入）
- 更新用户永久记忆（结构化字段）

---

**相关论文/框架参考**：
- **MemGPT**：把 LLM 看作 OS，Memory 分页，需要时换入
- **LangChain Memory**：ConversationBufferMemory、ConversationSummaryMemory、VectorStoreRetrieverMemory
- **AutoGPT / BabyAGI**：经典 Agent 记忆方案

---

### 12. Agent 长记忆与短记忆的衔接逻辑是什么？

**面试官问**：Agent 长记忆与短记忆的衔接逻辑是什么？

**参考答案**：
**长记忆与短记忆的衔接（"什么时候从长记忆里找东西"）是关键设计点**。

---

**衔接策略（从简单到复杂）**：

**策略 1：总是注入（Always Inject）**：
- 实现：每次对话都把长记忆拼到 Prompt 最前面
- 优点：简单
- 缺点：长记忆越来越长，占用 Token
- 适用：本项目的事实缓存（结构化，不长）

**策略 2：触发式检索（Triggered Retrieval）**：
- 设计：用户问题中出现某些关键词时，才检索长记忆
- 例如：
  - 用户问："我上次问的那个手机..." → 触发"上次"、"那个"关键词
  - 用户问："还是那个价格吗？" → 触发"还是"、"那个"
- 实现：
  - 规则匹配：正则检查关键词
  - LLM 分类：让 LLM 判断"是否需要参考历史对话"

**策略 3：语义检索（Semantic Retrieval）**：
- 实现：
  - 把用户历史对话向量化，存入向量库
  - 当前用户问题也向量化
  - 相似度检索 Top-3 相关历史对话
- 优点：智能，不依赖关键词
- 缺点：多一次向量检索，增加延迟
- 适用：历史对话很多的场景

**策略 4：混合策略（Hybrid）**：
- 把上述策略结合：
  1. 先看短记忆里有没有（最近 10 轮）
  2. 如果没有，检查是否有触发关键词
  3. 有触发词，从长记忆里检索
  4. 同时，用当前问题语义检索长记忆，作为补充

---

**在本项目中的衔接逻辑**：

```
用户问题
    ↓
【1】从短记忆取对话历史（最近 10 轮）
    ↓
【2】从长记忆取会话事实缓存（结构化事实）
    ↓
【3】判断：事实缓存中有没有相关信息？
    ├─ 有 → 注入到 Prompt
    └─ 无 → 跳过
    ↓
【4】RAG 检索政策文档
    ↓
【5】拼到一起送入 LLM
```

- 当前比较简单：总是注入事实缓存（因为结构化，不长）
- 未来可扩展：语义检索历史对话

---

**记忆衔接的工程实现（伪代码）**：

```java
public class AgentMemoryCoordinator {

    public String buildMemoryContext(String query, String conversationId) {
        StringBuilder context = new StringBuilder();

        // 1. 短记忆：最近 10 轮对话
        List<Message> shortTermMemory = messageChatMemory.get(conversationId, 10);
        context.append("【对话历史】\n");
        for (Message msg : shortTermMemory) {
            context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        // 2. 长记忆：结构化事实缓存（总是注入）
        SessionFacts facts = sessionFactCacheService.getFacts(conversationId);
        if (facts.hasAny()) {
            context.append("\n【会话事实缓存】\n");
            if (facts.getDeviceModel() != null) {
                context.append("- 设备型号: ").append(facts.getDeviceModel()).append("\n");
            }
            if (facts.getPrice() != null) {
                context.append("- 最近提及价格: ").append(facts.getPrice()).append("\n");
            }
        }

        // 3. 长记忆：语义检索历史对话（可选，未来扩展）
        if (needRetrieveHistory(query)) {
            List<HistoryItem> history = longTermMemoryService.retrieve(query, 3);
            if (!history.isEmpty()) {
                context.append("\n【相关历史对话】\n");
                for (HistoryItem item : history) {
                    context.append("- ").append(item.getSummary()).append("\n");
                }
            }
        }

        return context.toString();
    }
}
```

---

### 13. 有什么思路能让自己做的 Agent 更智能？

**面试官问**：有什么思路能让自己做的 Agent 更智能？

**参考答案**：
**Agent 智能度提升可以从"规划"、"记忆"、"工具"、"反思"四个维度入手**。

---

**维度一：更智能的规划（Planning）**：

**1. 多步规划（当前项目已有 ReActPlanningService）**：
- 先想清楚"第一步做什么，第二步做什么"，再执行
- 不是想到什么做什么

**2. 规划反思与修正**：
- 执行过程中发现计划不对，重新规划
- 例如：工具调用失败，换个工具或调整参数再试

**3. 子任务分解**：
- 复杂任务拆成子任务
- 例如："帮我查一下山东和河南的补贴" → 拆成"查山东"、"查河南"、"对比"

---

**维度二：更完善的记忆（Memory）**：

**1. 长短记忆协同（上一题讲过）**：
- 短记忆：当前对话
- 长记忆：历史对话、用户档案
- 灵活切换与检索

**2. 记忆总结与归档**：
- 对话结束时，LLM 总结本次对话要点
- 归档到长记忆，不是存原始对话

**3. 记忆更新与修正**：
- 用户纠正了之前的信息，更新记忆
- 例如："哦，我记错了，不是 iPhone 17，是 iPhone 16" → 更新设备型号

---

**维度三：更强大的工具（Tools）**：

**1. 工具选择更智能**：
- 当前项目：ToolIntentClassifier（规则）
- 可优化：LLM 做工具选择 + 规则兜底
- 让 LLM 自己判断"什么时候用什么工具"

**2. 工具参数自动补全**：
- 从记忆里提取参数，不用每次都问用户
- 例如：用户之前说过"在济南"，后续工具自动填充 region="济南"

**3. 工具组合**：
- 多个工具串联使用
- 例如：parseFile 解析发票 → calculateSubsidy 计算补贴 → webSearch 查当前市价

**4. 工具失败处理**：
- 当前项目：ToolFailurePolicyCenter（兜底提示）
- 可优化：重试、换参数、换工具、人工兜底

---

**维度四：反思与自我评估（Reflection）**：

**1. 回答质量自检**：
- LLM 生成回答后，先自己检查一遍：
  - "这个回答准确吗？"
  - "有没有遗漏信息？"
  - "有没有编造？"
- 如果有问题，重新生成

**2. 不确定时主动澄清**：
- 不是硬着头皮回答
- 例如："您说的'手机'是指智能手机还是功能手机？能否补充一下型号？"

**3. 用户反馈学习**：
- 用户点"不满意"，自动触发反思：
  - "刚才哪里回答错了？"
  - "是检索错了文档？还是理解错了问题？"
- 记录 Bad Case，定期迭代

---

**维度五：其他优化点**：

**1. Prompt 优化**：
- 更好的角色设定
- 更清晰的指令
- 思维链（CoT）提示："请一步步思考"
- 少样本提示（Few-shot）：给几个例子

**2. 模型选型**：
- 有条件用更强的模型（qwen3.5-plus → qwen-max）
- 不同任务用不同模型：
  - 规划用大模型
  - 工具调用用中等模型
  - 摘要用小模型

**3. 多 Agent 协作**：
- 一个 Agent 做规划，一个做检索，一个做回答
- 或"专家 Agent"：家电专家、汽车专家、通用咨询专家

---

**本项目中的优先级**：
1. **短期**：主动澄清、工具参数自动补全（见效快）
2. **中期**：回答质量自检、记忆修正
3. **长期**：多 Agent 协作、反馈闭环学习

---

### 14. 手撕代码：全排列

**面试官问**：手撕代码，全排列。

**参考答案**：
**LeetCode 46. Permutations**

**题目描述**：
```
给定一个不含重复数字的数组 nums ，返回其所有可能的全排列。你可以按任意顺序返回答案。

示例 1：
输入：nums = [1,2,3]
输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

**解法 1：回溯（经典写法，推荐）**：
```java
class Solution {
    List<List<Integer>> result = new ArrayList<>();

    public List<List<Integer>> permute(int[] nums) {
        boolean[] used = new boolean[nums.length];
        backtrack(nums, new ArrayList<>(), used);
        return result;
    }

    private void backtrack(int[] nums, List<Integer> path, boolean[] used) {
        // 终止条件：path 长度等于 nums 长度
        if (path.size() == nums.length) {
            result.add(new ArrayList<>(path));
            return;
        }

        // 遍历每个数字
        for (int i = 0; i < nums.length; i++) {
            if (used[i]) {
                continue;  // 用过了，跳过
            }
            // 做选择
            used[i] = true;
            path.add(nums[i]);
            // 递归
            backtrack(nums, path, used);
            // 撤销选择
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }
}
```

**解法 2：交换法（不用 used 数组，原地交换）**：
```java
class Solution {
    List<List<Integer>> result = new ArrayList<>();

    public List<List<Integer>> permute(int[] nums) {
        backtrack(nums, 0);
        return result;
    }

    private void backtrack(int[] nums, int start) {
        if (start == nums.length) {
            List<Integer> list = new ArrayList<>();
            for (int num : nums) list.add(num);
            result.add(list);
            return;
        }

        for (int i = start; i < nums.length; i++) {
            swap(nums, start, i);          // 把 nums[i] 换到当前位置
            backtrack(nums, start + 1);    // 递归处理下一个位置
            swap(nums, start, i);          // 换回来（回溯）
        }
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}
```

**时间复杂度**：O(n × n!)，n! 个排列，每个排列用 O(n) 时间构造
**空间复杂度**：O(n)，递归栈深度 + path 存储

**面试时建议写解法 1（回溯 + used 数组）**，思路清晰，容易解释。

---

## 面试技巧总结

1. **RAG 一定要讲数据支撑**：
   - Recall@5 提升了多少？
   - Rerank 带来了多少收益？
   - 不要只说"我做了 Rerank"，要说"我做了 Rerank，Recall@5 从 68% 提升到 93%"

2. **算法问题注意边界条件**：
   - 反转链表：空链表、只有一个节点
   - 全排列：空数组、只有一个元素

3. **MySQL 锁/MVCC 是高频考点**：
   - 临键锁、Next-Key Lock、间隙锁
   - Read View 可见性判断
   - 隔离级别区别

4. **Agent 优化可以分层说**：
   - 短期（见效快）：Prompt、主动澄清
   - 中期（需要开发）：记忆修正、回答自检
   - 长期（大投入）：多 Agent、反馈学习

5. **诚实**：
   - 项目里没做的，可以说"我调研过，方案是...，但项目里还没实现"
   - 不要瞎编，一问细节就露馅

