# 实战面试复盘

> 面试岗位：AI 应用后端 / RAG 算法工程师
>
> 面试形式：一面 + 手撕
>
> 问题来源：真实面试记录整理

---

## 面试流程

### 一、RAG 项目细节深挖

#### 1. Rerank 后返回几个块？有没有做一些验证？

**面试官问**：Rerank 之后返回几个块？有没有做验证来确保 Rerank 效果？

**参考答案**：

**Rerank 后返回 5 个块**（`topK=5`）。

**具体流程**：
- 向量召回阶段：`candidateTopK=20（先召回 20 条候选
- Rerank 重排序：用 `qwen3-rerank` 对这 20 条重新打分
- Top-K 截断：取前 5 条进入最终上下文

**验证方式**：
1. **人工标注验证**：
   - 构造了 200 条政策咨询问题，每条标注 3-5 个相关文档
   - Rerank 前：Recall@5 = 68%，MRR = 0.52
   - Rerank 后：Recall@5 = 93%，MRR = 0.89
   - 提升了 25% 个百分点

2. **线上 A/B 测试**：
   - 50% 用户走 Rerank，50% 不走
   - 观测指标：用户满意度（4.6 vs 3.8）、对话轮次（1.8 vs 2.5）、重新生成点击次数（下降 32%）

---

#### 2. Rerank 后的 Top-K 截断是怎么做的？为什么选这个值？有没有其他方案？

**面试官问**：Rerank 后的 Top-K 截断是怎么实现的？为什么选这个 K 值？有没有考虑过其他方案？

**参考答案**：

**截断实现**（`DashScopeRerankService.shrink()`）：
1. 解析 Rerank API 返回结果，提取 `index` 和 `relevance_score`
2. 按分数从大到小排序
3. 取前 `topK` 条，不足 `topK` 取 `min(topK, documents.size())

**代码逻辑**：
```java
private List<Document> shrink(List<Document> documents, int topK) {
    if (documents == null || documents.isEmpty()) return List.of();
    int limit = Math.max(1, topK);
    if (documents.size() <= limit) return documents;
    return documents.subList(0, limit);
}
```

**为什么选 5**：
- 实验对比：
  - K=3：省 Token，但信息量不足，Recall 降 18%
  - K=5：信息量适中，窗口可控，综合最优
  - K=10：信息全，但占用 Token 多，满意度反而降 8%
- Token 控制：每个块最多 1200 字符，5 个块约 6000 字符，加上系统提示词和对话历史，仍在 32K 窗口内

**其他方案**：
- 动态 K：根据问题类型和分数分布决定 K
  - 事实类问题 K=3，深度咨询 K=7
  - 前几名分数远高后面 → K=3，前 10 名接近 → K=10

---

#### 3. 讲一下上下文工程。记忆功能是怎么实现的？

**面试官问**：讲一下上下文工程。记忆功能是怎么实现的？

**参考答案**：

**上下文工程（Context Engineering）**：如何组织、构建和优化注入到 LLM 的上下文。

**本项目实践**：

**1. 系统提示词设计**：
- 角色定位：政策解读专家，不确定的明确告知，不编造
- 回答风格：先结论，再分点，数字加粗，用户易懂

**2. RAG 上下文构建**：
- 用【文档 1】【文档 2】清晰分隔
- 标注来源，按相关度排序

**3. 记忆功能实现**：

**双层记忆机制**：

**（1）对话历史记忆**（短记忆）：
- Redis String 类型，Key：`chat:memory:{conversationId}`
- 内容：最近 10 轮对话，JSON 数组
- TTL：7 天
- 实现：Spring AI `MessageChatMemoryAdvisor`

**（2）会话事实缓存**（长记忆/结构化记忆）：
- 设计动机：纯对话历史占窗口，LLM 找信息麻烦
- 提取事实：设备型号、价格、地区、经纬度
- 提取方式：正则匹配
  ```java
  // 价格匹配：(\d{3,6}(?:\.\d{1,2})?)\s*(元|rmb|¥|￥)
  // 设备型号：(iPhone|华为|小米)\s*\w[\w\s+]*
  ```
- 注入方式：拼在提示词最前面
  ```
  【会话事实缓存】
  - 设备型号：iPhone 17 Pro
  - 最近提及价格：8999元
  ```

**两者协同**：短记忆保证对话连贯，长记忆跨多轮记住关键信息。

---

### 二、限流算法三连问

#### 4. 讲一下分布式令牌桶限流

**面试官问**：讲一下分布式令牌桶限流。

**参考答案**：

**令牌桶原理**：
1. 系统以恒定速率向桶中放令牌
2. 桶有最大容量，满了就丢弃
3. 请求来取令牌，取到就放行，取不到就拒绝

**分布式环境挑战**：多实例共享状态，需要原子操作。

**Redis + Lua 实现**：
```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])  -- 桶容量
local rate = tonumber(ARGV[2])      -- 令牌生成速率
local now = tonumber(ARGV[3])       -- 当前时间戳

local last_time = tonumber(redis.call('hget', key, 'last_time') or 0)
local tokens = tonumber(redis.call('hget', key, 'tokens') or capacity)

-- 计算新生成的令牌
local elapsed = math.max(0, now - last_time)
local new_tokens = math.min(capacity, tokens + elapsed * rate)

-- 取令牌
if new_tokens >= 1 then
    new_tokens = new_tokens - 1
    redis.call('hset', key, 'last_time', now)
    redis.call('hset', key, 'tokens', new_tokens)
    redis.call('expire', key, 60)
    return 1
else
    return 0
end
```

---

#### 5. 讲一下漏桶限流

**面试官问**：讲一下漏桶限流。

**参考答案**：

**漏桶原理**：
- 请求像水一样注入桶，桶满溢出（拒绝）
- 桶底小孔恒定速率流出（处理请求）
- 强制平滑流量，不允许突发

**与令牌桶对比**：

| 特性 | 令牌桶 | 漏桶 |
|------|--------|------|
| 流量特性 | 允许突发 | 强制平滑 |
| 适用场景 | 秒杀、热点 | 防止下游被打垮 |

**单机实现**：
```java
public class LeakyBucket {
    private long capacity, leakRate, water = 0;
    private long lastLeakTime = System.currentTimeMillis();

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long leaked = (now - lastLeakTime) / leakRate;
        if (leaked > 0) {
            water = Math.max(0, water - leaked);
            lastLeakTime = now;
        }
        if (water < capacity) {
            water++;
            return true;
        }
        return false;
    }
}
```

---

#### 6. 讲一下滑动窗口算法限流

**面试官问**：讲一下滑动窗口算法限流。滑动窗口的数据结构会包含哪些字段？滑动窗口对比令牌桶有什么缺点？用 Redis 的什么数据结构实现？

**参考答案**：

**滑动窗口原理**：
- 固定窗口问题：第 59 秒发 100 次，第 61 秒再发 100 次 → 2 秒发了 200 次
- 滑动窗口解决：把时间分成小格子（比如 6 个 10 秒），统计"当前时间往前推一个窗口"

**数据结构字段**：
```java
public class SlidingWindow {
    // 配置
    private long windowSizeMs;    // 窗口大小
    private int limit;             // 限制数
    private int bucketCount;      // 格子数

    // 数据
    private long[] bucketTimestamps;  // 每个格子起始时间
    private int[] bucketCounts;        // 每个格子计数
    private int currentBucketIndex;  // 当前格子索引
}
```

**对比令牌桶的缺点**：

| 维度 | 滑动窗口 | 令牌桶 |
|------|----------|--------|
| 存储开销 | 需要保留请求记录/每个格子计数 | 只存"令牌数+上次时间 |
| 精度 vs 开销 | 格子少了有"准固定窗口"问题，格子多了存储上升 | 理论精确到毫秒 |
| 预借令牌 | 不支持 | 支持"攒"令牌应对突发 |

**Redis 实现用 Sorted Set**：
- Key: `rate_limit:{user:123`
- ZSet: score=毫秒时间戳, member=请求 UUID
- 每次请求：
  1. ZREMRANGEBYSCORE 移除窗口外的
  2. ZCARD 看当前窗口内条数
  3. < 限制则 ZADD 当前请求，放行；否则拒绝

---

### 三、缓存与数据结构

#### 7. 讲一下 LRU 的原理和实现

**面试官问**：讲一下 LRU 的原理和实现。

**参考答案**：

**LRU（Least Recently Used）**：最近最少使用，缓存满了淘汰最久没访问的。

**需要 O(1) 实现**：**HashMap + 双向链表**

```
HashMap: key -> Node
双向链表: head <-> Node1 <-> Node2 <-> Node3 <-> tail
         (最近使用)                  (最久未使用)
```

**get 操作**：HashMap 找到 Node，移到头部

**put 操作**：已存在则更新值移到头部；不存在则创建新 Node 加头部，超过容量删尾部。

**Java 代码**（简化）**：
```java
class LRUCache {
    class Node { int key, val; Node prev, next; }

    private Map<Integer, Node> map = new HashMap<>();
    private int capacity;
    private Node head, tail;

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
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
                Node tail = removeTail();
                map.remove(tail.key);
            }
        }
    }
}
```

**Java 现成实现**：`LinkedHashMap(accessOrder=true)`。

---

#### 8. 讲一下布隆过滤器的原理、应用场景

**面试官问**：讲一下布隆过滤器的原理、应用场景。

**参考答案**：

**布隆过滤器**：空间效率很高的概率型数据结构，判断"元素是否在集合中"。

**原理**：
- 很长的二进制数组 + 多个哈希函数
- 添加：K 个哈希算 K 个位置，设为 1
- 查询：K 个位置全 1 → 可能存在；有一个 0 → 一定不存在

**特点**：
- 优点：空间极高，插入查询 O(K)
- 缺点：有误判率（False Positive），不能删除

**应用场景**：
1. **缓存穿透防护**：把存在的 Key 存入布隆过滤器，"一定不存在"直接返回
2. **爬虫 URL 去重**
3. **本项目**：知识库文档去重

---

### 四、MySQL 高频考点

#### 9. MySQL 索引失效的情况有哪些？

**面试官问**：MySQL 索引失效的情况有哪些？

**参考答案**：

**常见情况**：

1. **对索引字段做函数运算**：`YEAR(create_time) = 2025` → 失效
2. **隐式类型转换**：`phone = 13800138000`（phone 是 VARCHAR）→ 失效
3. **LIKE % 开头**：`title LIKE '%补贴%'` → 失效
4. **OR 有未索引字段**：`id = 1 OR status = 'ACTIVE'`（status 无索引）→ 失效
5. **联合索引不遵循最左前缀**：`(category, status)`，只查 `status` → 失效
6. **索引区分度太低**：status 只有 0/1 → 优化器可能不走索引
7. **查询数据量太大**：查 80% 的行 → 全表扫更划算

**口诀**：函隐最左 OR，IS 区分大。

---

#### 10. LIKE 查询会不会导致索引失效？

**面试官问**：LIKE 查询会不会导致索引失效？

**参考答案**：

**不一定**：

- `LIKE '前缀%'` → 走索引（前缀确定，可以 B+ 树范围查找）
- `LIKE '%后缀'` 或 `LIKE '%中间%'` → 失效（前缀不确定）

**优化方案**：
- 后缀匹配：建反转字段 `title_rev`，查 `LIKE '贴补%'`
- 全文检索需求：全文索引 `MATCH(title) AGAINST('...')`

---

#### 11. 讲一下 MySQL 的事务隔离级别和一致性

**面试官问**：讲一下 MySQL 的事务隔离级别和一致性。

**参考答案**：

**4 种隔离级别**：

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|----------|------|------------|------|
| Read Uncommitted | ✓ | ✓ | ✓ |
| Read Committed | ✗ | ✓ | ✓ |
| Repeatable Read | ✗ | ✗ | ✓ |
| Serializable | ✗ | ✗ | ✗ |

**MySQL 默认**：Repeatable Read（RR）

**三种异常**：
- **脏读**：读到未提交的修改
- **不可重复读**：同一事务内两次读同一行，值变了
- **幻读**：同一事务内两次查，多了几行

**一致性（Consistency）**：事务执行前后，数据库从一个一致状态变到另一个一致状态。
- 数据库层面：主键、外键、唯一约束
- 业务层面：应用代码保证（A 转账 B，余额不能负）

---

#### 12. 讲一下 MVCC，详细追问

**面试官问**：讲一下 MVCC 相关原理与细节。这种情况会创造几个 Read View？

**参考答案**：

**MVCC（Multi-Version Concurrency Control）**：

**核心思想**：每次修改不覆盖旧数据，创建新版本；读操作根据事务 ID 判断可见性。

**关键概念**：
- **隐式字段**：`TRX_ID`（最后修改事务 ID）、`ROLL_PTR`（回滚指针，指向 Undo Log 旧版本）
- **Undo Log**：版本链，头部最新，尾部最老
- **Read View**：判断可见性的窗口
  - `m_ids`：活跃事务 ID 列表
  - `min_trx_id`：最小活跃 ID
  - `max_trx_id`：最大活跃 ID + 1
  - `creator_trx_id`：生成者自己的 ID

**可见性规则**：
1. `t == creator_trx_id` → 可见（自己改的）
2. `t < min_trx_id` → 可见（Read View 生成前就提交了）
3. `t >= max_trx_id` → 不可见（Read View 生成后才开始的）
4. `t` 在 `[min_trx_id, max_trx_id)` 之间：看 `t` 在 `m_ids` 中 → 不可见（还没提交），不在 → 可见（已提交）

**Read View 创建几个？**

| 隔离级别 | Read View 创建时机 |
|----------|------------------|
| Read Committed | 每次 SELECT 都创建新的 |
| Repeatable Read | 第一次 SELECT 创建，后续复用 |

**场景**：
- RR 隔离级别，BEGIN → SELECT（创建 Read View 1）→ 其他事务提交 → SELECT（复用 Read View 1）→ **创建 1 个**
- RC 隔离级别，每次 SELECT 都创建新的 → **创建多个**

---

#### 13. 讲一下 MySQL 的锁

**面试官问**：讲一下 MySQL 的行锁、表锁等锁相关知识。

**参考答案**：

**按粒度**：表级锁、行级锁

**按类型**：
- **共享锁（S 锁）**：读锁，多个事务可同时持有，读阻塞写
- **排他锁（X 锁）**：写锁，只有一个能持有，写阻塞读/写

**InnoDB 行锁算法**：
- **Record Lock**：只锁住某一行
- **Gap Lock**：锁住间隙（两行之间），防止 INSERT，解决幻读
- **Next-Key Lock**：Record + Gap，默认算法，锁住某一行 + 前面间隙

**死锁**：两个事务互相等对方释放锁，InnoDB 自动检测，回滚较小的。

---

### 五、手撕代码

#### 14. 反转链表

**面试官问**：手撕代码，反转链表。

**参考答案**：

**LeetCode 206**

**双指针迭代（推荐）**：
```java
class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode curr = head;

        while (curr != null) {
            ListNode nextTemp = curr.next;
            curr.next = prev;
            prev = curr;
            curr = nextTemp;
        }

        return prev;
    }
}
```

**时间 O(n)，空间 O(1)。

---

## 面试复盘总结

**本场面试考点分布**：
- RAG 项目：4 题（Rerank、Top-K、上下文工程、记忆）
- 限流算法：3 题（令牌桶、漏桶、滑动窗口）
- 缓存/数据结构：2 题（LRU、布隆过滤器）
- MySQL：5 题（索引、隔离、MVCC、锁）
- 手撕：1 题（反转链表）

**建议**：
1. RAG 一定要准备数据支撑（Recall、MRR、满意度）
2. 限流三个算法必须熟练（原理、实现、对比）
3. MySQL MVCC / 锁 / 索引是必考题
