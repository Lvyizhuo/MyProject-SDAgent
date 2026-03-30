# MiniMax-Agent大模型二面面经

> 面试岗位：Agent 大模型工程师
>
> 面试公司：MiniMax
>
> 面试形式：二面

---

## 面试问题

### 一、ReAct 框架局限性与规划范式对比

#### 1. 深入剖析 ReAct 框架的局限性，并在此基础上，详细解释 Plan-Then-Act、ReAct + 轻规划以及 Tree/Graph Planning（如 ToT、LATS）这三种范式的核心区别、适用场景和各自的优缺点。

**参考答案**：

---

**ReAct 框架的局限性**：

| 局限性 | 说明 |
|--------|------|
| **线性规划陷阱** | 每次只规划一步，缺乏全局视野，容易走弯路 |
| **无回溯机制** | 一旦某步走错，无法回到之前的状态重新选择 |
| **重复思考** | 相似场景反复推理，浪费 Token |
| **错误累积** | 某一步推理错误会传导到后续步骤 |
| **无分支探索** | 无法并行探索多个可能的解题路径 |
| **无价值评估** | 不知道当前路径的“好坏”，无法提前剪枝 |

---

**三种规划范式对比**：

| 范式 | 核心思想 | 执行流程 | 适用场景 | 优点 | 缺点 |
|------|----------|----------|----------|------|------|
| **Plan-Then-Act** | 先规划完整计划，再按计划执行 | 1. 理解任务<br>2. 生成完整步骤列表<br>3. 按顺序执行 | 任务路径清晰、步骤确定的场景（如数据处理 Pipeline） | 步骤明确，易于调试，可提前检查计划合理性 | 缺乏对执行中意外的适应，计划出错需全盘重来 |
| **ReAct + 轻规划** | 先做一个粗略的多步计划，然后每走几步就重新审视和调整计划 | 1. 生成高层计划（3-5 步）<br>2. ReAct 方式逐步执行<br>3. 定期重计划 | 大多数实际业务场景（RAG + 工具调用、多步查询） | 平衡了规划的全局观与执行的灵活性 | 仍然是线性探索，无分支 |
| **Tree/Graph Planning**<br>（ToT / LATS） | 将问题建模为树/图，多个分支并行探索，用评估函数剪枝，选最优路径 | 1. 生成多个候选下一步<br>2. 评估每个候选的价值<br>3. 选择 top-K 继续扩展<br>4. 回溯选最优解 | 推理题、数学题、代码生成等需要探索最优解的场景 | 可以找到全局最优解，有回溯，能剪枝 | 计算成本高，Token 消耗大，延迟高 |

---

**我的项目中的实践**：

```
当前采用：ReAct + 轻规划

1. 先生成 3-5 步的粗略计划（AgentPlan）
2. 每执行 1-2 步后，检查是否需要调整计划
3. 保留 ReAct 的灵活性，避免 Plan-Then-Act 的僵化
4. 不使用 Tree/Graph Planning（业务场景不需要极致最优解，控制成本）
```

---

### 二、思维链（CoT）与规划（Planning）的本质区别

#### 2. 请阐述“思维链”（Chain-of-Thought, CoT）与“规划”（Planning）的本质区别。为什么说 CoT 仅仅是“将推理过程写出来”，而 Planning 是生成一个“可执行的任务表”？请用具体例子说明。

**参考答案**：

---

**本质区别**：

| 维度 | 思维链（CoT） | 规划（Planning） |
|------|---------------|------------------|
| **核心目标** | 帮助模型“想清楚”，提高单次输出的准确性 | 生成可执行的步骤序列，调度工具/动作完成复杂任务 |
| **输出内容** | 自然语言的推理过程（“因为…所以…”） | 结构化的任务列表（JSON/YAML，含 action/input/expected_output） |
| **是否可执行** | 不可直接执行，只是“思考过程” | 可被解析器直接执行，每一步对应工具调用或子任务 |
| **反馈闭环** | 无反馈，一次写完 | 有反馈，执行结果影响后续规划 |
| **状态追踪** | 不追踪中间状态 | 显式追踪任务状态（待办/进行中/已完成/失败） |
| **错误处理** | 出错就整体错了 | 支持单步重试、回滚、重规划 |

---

**具体例子对比**：

**任务**：计算“山东省购买 3000 元家电的以旧换新补贴金额，同时查询附近门店”

---

**CoT 输出示例**（只是想清楚，不可执行）：

```
用户问的是山东省家电以旧换新补贴和门店查询。
首先我需要回忆一下补贴政策：家电是按价格的 10% 补贴，上限 500 元。
3000 元的 10% 是 300 元，没超过 500，所以补贴 300 元。
然后需要查附近门店，这一步需要调用地图工具。
假设用户在济南，那么可以查济南的门店。
综合起来，补贴是 300 元，门店可以用地图查。
```

→ 这只是“把推理过程写出来”，**无法被程序解析执行**。

---

**Planning 输出示例**（结构化任务表，可执行）：

```json
{
  "planId": "plan_001",
  "summary": "计算补贴并查询门店",
  "steps": [
    {
      "stepId": "step_1",
      "action": "calculateSubsidy",
      "description": "计算家电补贴金额",
      "input": {
        "productType": "家电",
        "price": 3000,
        "region": "山东省"
      },
      "expectedOutput": {
        "subsidyAmount": 300
      },
      "onSuccess": "go(step_2)",
      "onFailure": "retry(3) or fail"
    },
    {
      "stepId": "step_2",
      "action": "webSearch",
      "description": "获取用户位置",
      "input": {
        "query": "用户当前位置"
      },
      "expectedOutput": {
        "city": "济南市"
      },
      "onSuccess": "go(step_3)",
      "onFailure": "useDefault(济南市)"
    },
    {
      "stepId": "step_3",
      "action": "amap-mcp",
      "description": "查询附近门店",
      "input": {
        "action": "searchNearby",
        "keyword": "以旧换新门店",
        "city": "${step_2.output.city}"
      },
      "expectedOutput": {
        "stores": ["..."]
      },
      "onSuccess": "done",
      "onFailure": "skip"
    }
  ]
}
```

→ 这是“可执行的任务表”，**每一步都可以被程序解析并调用对应的工具**。

---

**关键差异总结**：

| 差异点 | CoT | Planning |
|--------|-----|----------|
| 变量引用 | 无 | `${step_2.output.city}` 支持变量传递 |
| 条件分支 | 无 | `onSuccess` / `onFailure` 定义分支逻辑 |
| 重试策略 | 无 | `retry(3)` 显式定义重试 |
| 状态机 | 无 | `go()` / `done()` / `fail()` 定义状态流转 |
| 结构解析 | 需 LLM 重新理解 | 直接 JSON Schema 解析 |

---

### 三、复杂任务的鲁棒规划机制与容错策略

#### 3. 在处理一个需要多步工具调用的复杂任务（例如“调研三篇关于 RAG+RL 的论文并输出中文总结”）时，如何设计一个鲁棒的规划机制来应对中间步骤的失败（如某个 API 调用超时或返回数据格式错误）？请描述具体的重试、回滚或重规划策略。

**参考答案**：

---

**任务分析**：

```
目标：调研三篇 RAG+RL 论文并输出中文总结

步骤分解：
1. webSearch 搜索 "RAG+RL survey 2024"
2. 从结果中筛选 3 篇相关论文
3. 对每篇论文：
   3.1 webSearch 搜索论文标题 + "pdf"
   3.2 parseFile 解析论文内容
   3.3 LLM 总结论文
4. 合并三篇总结，输出最终报告
```

---

**鲁棒规划架构设计**：

```
┌─────────────────────────────────────────────────────────────┐
│                    Plan Orchestrator                          │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Step Executor                                          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │  Retry   │→ │ Fallback │→ │ Rollback │            │  │
│  │  └──────────┘  └──────────┘  └──────────┘            │  │
│  └───────────────────────────────────────────────────────┘  │
│                            ↓                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  State Store（Redis）                                    │  │
│  │  - step_1: {status, input, output, error}              │  │
│  │  - step_2: {status, input, output, error}              │  │
│  │  ...                                                     │  │
│  └───────────────────────────────────────────────────────┘  │
│                            ↓                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Re-planner（当失败无法恢复时）                          │  │
│  │  - 跳过非关键步骤                                        │  │
│  │  - 替换失败工具                                          │  │
│  │  - 调整计划结构                                          │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

**具体容错策略**：

| 策略 | 说明 | 实现示例 |
|------|------|----------|
| **1. 指数退避重试** | 临时失败（网络超时、限流）自动重试 | `retry: {maxAttempts: 3, backoff: "exponential", initialDelay: 1s}` |
| **2. 降级工具** | 主工具失败，用备选工具替代 | `webSearch` 失败 → 降级为 `arxivSearch` |
| **3. 结果校验器** | 工具返回结果后，用 Schema / LLM 校验 | `if (!result.papers) throw new InvalidResultError()` |
| **4. 检查点回滚** | 每步成功后写检查点，失败回滚到上一检查点 | `rollbackToCheckpoint("step_2_complete")` |
| **5. 非关键步骤跳过** | 某些步骤失败不影响主流程，标记跳过继续 | `skipOnFailure: true`（如某篇论文解析失败，跳过继续其他两篇） |
| **6. 人工介入** | 多次失败仍无法恢复，暂停任务等待人工确认 | `suspendForHumanApproval()` |
| **7. 动态重规划** | 根据已完成的结果，重新生成后续计划 | `replanFromCurrentState()` |

---

**代码级设计（伪代码）**：

```java
// 步骤定义
class PlanStep {
    String stepId;
    ToolCall toolCall;
    RetryPolicy retryPolicy;
    FallbackStrategy fallback;
    boolean skipOnFailure;
    List<String> dependsOn;
}

// 执行引擎
class PlanExecutor {
    public PlanResult execute(Plan plan) {
        for (PlanStep step : plan.getSteps()) {
            // 1. 检查依赖
            if (!checkDependencies(step)) continue;

            // 2. 执行（带重试）
            StepResult result = executeWithRetry(step);

            if (result.isSuccess()) {
                // 3. 写检查点
                saveCheckpoint(step, result);
            } else {
                // 4. 尝试降级
                if (step.hasFallback()) {
                    result = executeFallback(step.getFallback());
                }

                // 5. 仍失败，判断是否跳过
                if (!result.isSuccess() && step.isSkipOnFailure()) {
                    markSkipped(step);
                    continue;
                }

                // 6. 无法跳过，尝试重规划
                if (!result.isSuccess()) {
                    Plan newPlan = replanner.replan(plan, currentState);
                    return execute(newPlan);
                }
            }
        }
        return assembleResult(plan);
    }
}
```

---

**针对“调研三篇论文”的具体策略配置**：

```json
{
  "steps": [
    {
      "stepId": "search_papers",
      "tool": "webSearch",
      "retry": {
        "maxAttempts": 3,
        "backoff": "exponential",
        "retryOn": ["TIMEOUT", "RATE_LIMIT"]
      },
      "fallback": {
        "tool": "arxivSearch",
        "input": {
          "query": "RAG reinforcement learning",
          "max": 10
        }
      },
      "skipOnFailure": false
    },
    {
      "stepId": "fetch_paper_1",
      "tool": "parseFile",
      "dependsOn": ["search_papers"],
      "retry": {
        "maxAttempts": 2
      },
      "skipOnFailure": true
    },
    {
      "stepId": "fetch_paper_2",
      "tool": "parseFile",
      "dependsOn": ["search_papers"],
      "retry": {
        "maxAttempts": 2
      },
      "skipOnFailure": true
    },
    {
      "stepId": "fetch_paper_3",
      "tool": "parseFile",
      "dependsOn": ["search_papers"],
      "retry": {
        "maxAttempts": 2
      },
      "skipOnFailure": true
    },
    {
      "stepId": "synthesize",
      "tool": "llm_summarize",
      "dependsOn": ["fetch_paper_1", "fetch_paper_2", "fetch_paper_3"],
      "skipOnFailure": false,
      "precondition": "atLeast(2, [fetch_paper_1, fetch_paper_2, fetch_paper_3])"
    }
  ]
}
```

---

### 四、Tree-of-Thoughts (ToT) / LATS 框架深度解析

#### 4. 详细解释 Tree-of-Thoughts (ToT) 或类似 LATS（使用 LLM 进行蒙特卡洛树搜索）的框架是如何工作的？它们与传统的线性规划相比，在探索最优解题路径上有何本质优势？

**参考答案**：

---

**Tree-of-Thoughts (ToT) 框架**：

```
问题：用 4 个数字通过四则运算得到 24

                    根节点：{1, 2, 3, 4}
                   /          |          \
                  /           |           \
           1+2=3           1×2=2          1+3=4
          {3, 3, 4}        {2, 3, 4}      {2, 4, 1}
         /    |    \       /    |    \
        /     |     \     /     |     \
      3+3=6  3×3=9  ... 2+3=5  2×3=6  ...
     {6, 4}  {9, 4}      {5, 4}  {6, 4}
       ↓        ↓           ↓        ↓
     6×4=24 ✓ ...         ...     6×4=24 ✓
```

**ToT 核心流程**：

```
1. 思考生成（Thought Generation）
   ├─ 对当前状态，用 LLM 生成 K 个候选下一步
   └─ 示例：K=3，生成三个可能的运算组合

2. 状态评估（State Evaluation）
   ├─ 对每个候选状态，评估其“价值”（是否接近答案）
   └─ 评分：0（不可能）~ 1（很可能）

3. 搜索算法（Search Algorithm）
   ├─ BFS：宽度优先，每层保留 top-K 状态
   ├─ DFS：深度优先，剪枝掉低分分支
   └─ Beam Search：束搜索，平衡探索与效率

4. 回溯选择（Backtracking）
   ├─ 当找到多个解时，选择最优的一个
   └─ 或当路径走死时，回到上一分支点
```

---

**LATS（LLM + MCTS）框架**：

```
蒙特卡洛树搜索（MCTS）四步循环：

┌─────────────────────────────────────────────────────────┐
│  1. 选择（Selection）                                    │
│     从根节点出发，按 UCT 公式选择子节点                  │
│     UCT = Q(s) + C * sqrt(log(N(s)) / n(s))            │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  2. 扩展（Expansion）                                    │
│     到达叶子节点后，用 LLM 生成 K 个新候选状态          │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  3. 模拟（Simulation）                                   │
│     用 LLM 快速“走棋”到结束，得到奖励值                  │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│  4. 回传（Backpropagation）                              │
│     将奖励值回传到路径上的所有节点，更新 Q(s)            │
└─────────────────────────────────────────────────────────┘

重复 N 轮后，选择访问次数最多的根节点子节点作为最终选择。
```

---

**ToT / LATS vs 线性规划（ReAct / Plan-Then-Act）**：

| 维度 | 线性规划 | ToT / LATS |
|------|----------|------------|
| **搜索空间** | 单路径，线性探索 | 树/图，多分支并行探索 |
| **回溯能力** | 无（错了就错了） | 有（可以回到任意父节点重新选择） |
| **最优性** | 找到一个可行解即可 | 探索多个解，选择最优解 |
| **剪枝** | 无剪枝 | 可以用评估函数剪枝掉低价值分支 |
| **计算成本** | 低（O(N) 步） | 高（O(K * N * D)，K=分支数，D=深度） |
| **适用场景** | 路径明确的工程任务 | 需要推理/探索的难题（数学、代码、逻辑） |

---

**本质优势**：

1. **全局视野**：线性规划“走一步看一步”，ToT/LATS“看到整片森林”
2. **纠错能力**：走错了可以退回来重走，而不是一条道走到黑
3. **最优性保证**：通过探索多个路径，可以比较选出最好的那个
4. **剪枝效率**：用评估函数提前剪掉不可能的分支，避免无效探索

---

**我的项目中的权衡**：

```
不使用 ToT/LATS 的原因：
1. 业务场景（政策咨询）不需要“最优解”，只需要“可行解”
2. Token 成本太高（分支探索会消耗几倍到几十倍 Token）
3. 延迟无法接受（用户等待 10s 以上会流失）

保留的思想：
1. 在生成计划时，可以让 LLM 想 2-3 个方案，选一个（轻量 ToT）
2. 工具失败时，可以“回退”到上一步重新规划（回溯思想）
```

---

### 五、推理断层与目标偏离的缓解方案

#### 5. 在 Agent 推理过程中，经常会出现“推理断层”或“结果与目标偏离”的问题。请结合具体技术或你的实践经验，说明如何通过提示工程、记忆机制或架构设计来缓解或解决这一问题。

**参考答案**：

---

**问题定义**：

| 现象 | 说明 |
|------|------|
| **推理断层** | 推理过程中突然“断片”，逻辑跳变，或直接忽略中间步骤 |
| **目标偏离** | 回答着回答着就跑题了，忘记了最初的用户意图 |

---

**解决方案全景**：

```
┌─────────────────────────────────────────────────────────────┐
│                      缓解方案全集                            │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │  提示工程    │  │  记忆机制    │  │  架构设计    │   │
│  │  • 系统词    │  │  • 目标记忆  │  │  • 意图分类  │   │
│  │  • 示例      │  │  • 进度跟踪  │  │  • 反思层    │   │
│  │  • 结构化    │  │  • 事实缓存  │  │  • 验证器    │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

**方案一：提示工程**

| 技术 | 具体做法 | 示例 |
|------|----------|------|
| **目标重述强制** | 要求模型每次推理前先重述用户目标 | `在开始推理前，请用一句话重述用户的核心需求：____` |
| **思维链强制显式化** | 要求分步输出，每步必须有明确的“因为/所以” | `步骤 1：...\n步骤 2：...\n...\n结论：...` |
| **自检提示** | 推理完成后，要求模型自我检查是否偏离目标 | `自检：上面的回答是否解决了用户的问题？[是/否]\n如果否，遗漏了什么？____` |
| **格式化约束** | 用 JSON Schema 强制输出结构，避免跑题 | `{"goal": "...", "reasoning": [...], "answer": "..."}` |
| **少样本示例（Few-Shot）** | 给 2-3 个正确的例子，展示应该怎么做 | （见下方示例） |

**Few-Shot 示例**：

```
示例 1：
用户：我在山东买了个 3500 的冰箱，补贴多少？
回答：
目标：计算山东省冰箱以旧换新补贴金额
推理：
1. 冰箱属于“家电”品类
2. 家电补贴比例是 10%，上限 500 元
3. 3500 × 10% = 350 元
4. 350 < 500，所以补贴 350 元
答案：350 元

示例 2：
用户：...
（正确示例）

现在回答用户的问题：
用户：[当前问题]
回答：
```

---

**方案二：记忆机制**

| 技术 | 具体做法 | 我的项目实现 |
|------|----------|--------------|
| **目标锚定记忆** | 对话开始时，将用户原始目标存入独立记忆，每轮都拼入提示词 | `sessionGoal: "计算山东省 3000 元家电补贴 + 查询附近门店"` |
| **任务进度追踪** | 用结构化状态机跟踪当前进度，显式记录“已完成”和“待完成” | `progress: {completed: ["step_1"], pending: ["step_2", "step_3"]}` |
| **会话事实缓存** | 将关键实体（价格、地区、型号）从对话历史中提取出来，结构化存储 | `facts: {productType: "家电", price: 3000, region: "山东省"}` |
| **重要性评分重排序** | 从历史记忆中检索时，按“与当前目标的相关性”重排序，而不是只按时间 | `retrieveTopK(memories, query=currentGoal, k=5)` |

**我的项目代码示意**：

```java
// SessionFactCacheService.java
public class SessionFactCacheService {

    // 每轮对话开始前，把目标和关键事实拼入提示词
    public String buildContextPrompt(String sessionId) {
        String goal = getSessionGoal(sessionId);  // 原始目标
        Map<String, Object> facts = getFacts(sessionId);  // 关键事实
        Progress progress = getProgress(sessionId);  // 进度

        return String.format("""
            【对话目标】
            %s

            【已确认的关键信息】
            %s

            【当前进度】
            已完成：%s
            待完成：%s

            请根据以上信息继续处理，不要偏离目标。
            """,
            goal,
            formatFacts(facts),
            progress.getCompleted(),
            progress.getPending()
        );
    }
}
```

---

**方案三：架构设计**

| 层 | 作用 | 实现方式 |
|----|------|----------|
| **意图分类层** | 在推理前先判断用户意图，锁定任务类型 | `IntentClassifier: {CHAT, CALCULATE, QUERY, ...}` |
| **反思层（Reflector）** | 推理完成后，让另一个 LLM 检查结果是否偏离目标 | `Reflector.grade(answer, goal) → {1..5}`，分数低则重写 |
| **验证器（Validator）** | 用规则或 LLM 验证输出格式和内容是否符合预期 | `if (!validator.validate(output)) throw new OffTargetError()` |
| **多智能体协作** | 拆分角色：Planner（只做规划）、Executor（只执行）、Critic（只批评） | Planner → Executor → Critic →（不满意则回退）→ Planner |

**反思层实现示意**：

```java
// Reflector.java
public class Reflector {

    public ReflectionResult reflect(String userGoal, String answer) {
        String prompt = String.format("""
            请判断以下回答是否偏离了用户的原始目标。

            用户目标：%s
            助手回答：%s

            请输出 JSON：
            {
                "onTarget": true/false,
                "score": 0-5,
                "issue": "如果偏离，说明问题在哪",
                "suggestion": "如何修正"
            }
            """, userGoal, answer);

        return llm.call(prompt, ReflectionResult.class);
    }
}

// 使用
ReflectionResult result = reflector.reflect(goal, answer);
if (!result.isOnTarget() || result.getScore() < 3) {
    // 重写回答
    answer = rewriteAnswer(goal, answer, result.getSuggestion());
}
```

---

**综合实践（我的项目）**：

```java
// ChatService.java 中的完整流水线
public String chat(String sessionId, String userMessage) {
    // 1. 意图分类 + 目标锚定
    Intent intent = intentClassifier.classify(userMessage);
    if (isNewSession(sessionId)) {
        setSessionGoal(sessionId, extractGoal(userMessage));
    }

    // 2. 构建上下文（目标 + 事实 + 进度）
    String context = factCache.buildContextPrompt(sessionId);

    // 3. 生成回答（带思维链 + Few-Shot）
    String answer = generateWithCoT(context, userMessage);

    // 4. 反思检查
    ReflectionResult reflection = reflector.reflect(
        getSessionGoal(sessionId),
        answer
    );

    // 5. 如果偏离，重写
    if (!reflection.isOnTarget()) {
        answer = rewriteAnswer(answer, reflection.getSuggestion());
    }

    // 6. 更新事实缓存和进度
    factCache.extractAndSaveFacts(sessionId, userMessage, answer);
    progressTracker.update(sessionId, answer);

    return answer;
}
```

---

### 六、长期记忆模块深度设计

#### 6. 请深入剖析大模型 Agent 的“长期记忆”模块。在设计一个能够持续运行、与用户长期交互的 Agent 时，你会如何设计记忆的存储结构（如向量数据库、图数据库）、更新策略（如记忆合并、遗忘机制）、检索机制（如重排序、混合检索）来确保记忆的高效和准确？

**参考答案**：

---

**长期记忆 vs 短期记忆**：

| 维度 | 短期记忆 | 长期记忆 |
|------|----------|----------|
| **时长** | 会话内（分钟级） | 跨会话（天/月/年级） |
| **存储介质** | Redis / 模型上下文 | 向量数据库 / 图数据库 |
| **容量** | 小（受限于上下文窗口） | 大（可扩展到百万级） |
| **粒度** | 完整对话历史 | 结构化记忆（实体/事件/偏好） |
| **检索方式** | 直接访问 / 滑动窗口 | 向量检索 / 图遍历 / 关键词 |

---

**整体架构设计**：

```
┌─────────────────────────────────────────────────────────────────┐
│                        长期记忆模块                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  写入路径（Memory Writer）                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │  记忆提取   │→ │  记忆合并   │→ │  记忆索引   │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  存储层                                                    │   │
│  │  ┌───────────────────────────────────────────────────┐   │   │
│  │  │ 向量数据库（PGVector / Milvus）                    │   │   │
│  │  │ - 语义记忆（text + embedding）                     │   │   │
│  │  └───────────────────────────────────────────────────┘   │   │
│  │  ┌───────────────────────────────────────────────────┐   │   │
│  │  │ 图数据库（Neo4j / 知识图谱）                        │   │   │
│  │  │ - 实体记忆（User, Product, Policy）                 │   │   │
│  │  │ - 关系记忆（BOUGHT, PREFER, LOCATED_IN）            │   │   │
│  │  └───────────────────────────────────────────────────┘   │   │
│  │  ┌───────────────────────────────────────────────────┐   │   │
│  │  │ SQL 数据库（PostgreSQL）                             │   │   │
│  │  │ - 记忆元数据（id, user_id, timestamp, access_count）│   │   │
│  │  └───────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  读取路径（Memory Retriever）                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │  混合检索   │→ │  重排序     │→ │  上下文组装  │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

**存储结构设计**：

| 存储类型 | 用途 | 数据结构示例 |
|----------|------|--------------|
| **向量数据库** | 存储非结构化语义记忆，用于相似度检索 | `{id: "mem_001", content: "用户买了一台 3000 元的冰箱", embedding: [...], user_id: "u_001", timestamp: "...", access_count: 0}` |
| **图数据库** | 存储实体和关系，支持推理和路径查询 | `(u:User {id: "u_001"})-[:BOUGHT]->(p:Product {type: "冰箱", price: 3000})`, `(u)-[:PREFER]->(c:Category {name: "家电"})` |
| **SQL 数据库** | 存储记忆元数据，支持按时间/访问量过滤 | `CREATE TABLE memories (id, user_id, type, vector_id, graph_id, created_at, accessed_at, access_count, importance_score)` |

---

**更新策略**：

| 策略 | 说明 | 实现方式 |
|------|------|----------|
| **记忆合并** | 相同/相似的记忆合并，避免冗余 | `如果新记忆与现有记忆相似度 > 0.9，则合并（取最新内容，更新时间）` |
| **重要性评分** | 给记忆打重要性分，优先保留高分记忆 | `评分维度：用户明确说“记住”(+3)、涉及金额/个人信息(+2)、被访问过(+1)` |
| **衰减机制** | 记忆重要性随时间衰减 | `current_score = initial_score * exp(-lambda * days_since_last_access)` |
| **定期遗忘** | 定期清理低分记忆 | `每月清理一次：删除 importance_score < 0.5 且 access_count < 2 的记忆` |
| **增量索引** | 不是每次都重写全量索引，只增量更新 | `新增记忆 → 异步任务 → 向量化 → 写入向量库` |

**遗忘策略伪代码**：

```java
// MemoryConsolidator.java
public class MemoryConsolidator {

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点执行
    public void consolidate() {
        // 1. 找出所有需要合并的相似记忆
        List<MemoryCluster> clusters = findSimilarMemories(threshold=0.9);
        for (MemoryCluster cluster : clusters) {
            mergeMemories(cluster);
        }

        // 2. 衰减分数
        for (Memory m : allMemories) {
            double ageDays = daysSince(m.getLastAccessedAt());
            double decayed = m.getImportanceScore() * Math.exp(-LAMBDA * ageDays);
            m.setImportanceScore(decayed);
        }

        // 3. 遗忘低分记忆
        int deleted = forgetLowScoreMemories(threshold=0.3);
        log.info("Forgot {} memories", deleted);
    }
}
```

---

**检索机制设计**：

| 机制 | 说明 | 实现 |
|------|------|------|
| **混合检索** | 向量检索 + 关键词检索（BM25）结合 | `向量检索 top 20 + BM25 top 20 → 合并去重 → rerank` |
| **重排序（Rerank）** | 用 Cross-Encoder 或 LLM 对初筛结果重新排序 | `reranker.score(query, memory) → 0-1 分数` |
| **时间过滤** | 只检索最近 N 天的记忆 | `WHERE created_at > NOW() - INTERVAL '30 days'` |
| **重要性过滤** | 优先检索重要记忆 | `ORDER BY importance_score DESC LIMIT 10` |
| **图遍历增强** | 从向量检索到的记忆出发，遍历图获取相关实体 | `(m:Memory)<-[:RELATED_TO]-(e:Entity)-[:RELATED_TO]->(other:Memory)` |
| **记忆压缩** | 检索到的记忆太多时，用 LLM 压缩成摘要 | `compress([m1, m2, m3]) → "摘要：..."` |

**检索流程代码**：

```java
// MemoryRetriever.java
public class MemoryRetriever {

    public List<Memory> retrieve(String userId, String query, int k) {
        // 1. 混合检索初筛
        List<Memory> vectorResults = vectorDb.search(query, topK=50);
        List<Memory> bm25Results = bm25Index.search(query, topK=50);
        Set<Memory> candidates = mergeAndDeduplicate(vectorResults, bm25Results);

        // 2. 图扩展（可选）
        Set<Memory> graphExtended = graphDb.traverseNeighbors(candidates, depth=1);
        candidates.addAll(graphExtended);

        // 3. 重排序
        List<ScoredMemory> scored = reranker.score(query, candidates);

        // 4. 按重要性 + 时间 + 重排序分数 综合排序
        List<Memory> finalResult = scored.stream()
            .sorted(comparing(m ->
                m.getRerankScore() * 0.5 +
                m.getImportanceScore() * 0.3 +
                recencyScore(m.getCreatedAt()) * 0.2
            ))
            .limit(k)
            .collect(toList());

        // 5. 更新访问时间
        updateAccessTime(finalResult);

        return finalResult;
    }
}
```

---

### 七、长历史对话的记忆优化策略

#### 7. 当历史对话记录非常长时（远超模型上下文窗口），你有哪些策略来优化记忆的查询效率并保证关键信息不丢失？请比较“滑动窗口”、“总结压缩”、“向量检索”等不同方案的优劣。

**参考答案**：

---

**问题背景**：

```
典型情况：
- 模型上下文窗口：32K / 128K tokens
- 历史对话：100+ 轮，可能达 500K+ tokens
- 直接全量塞入：超过窗口，报错 / 截断
- 目标：关键信息（用户说过的地址、价格、偏好）不丢，噪声（闲聊、废话）过滤
```

---

**策略全景对比**：

| 策略 | 原理 | 优点 | 缺点 | 关键信息丢失风险 | 实现难度 | 推荐场景 |
|------|------|------|------|------------------|----------|----------|
| **滑动窗口** | 只保留最后 N 轮对话 | 实现简单，保序，无失真 | 早期信息直接丢失，窗口外的关键信息会丢 | 高（如果关键信息在窗口外） | 极低 | 短对话、实时客服、健忘型场景 |
| **总结压缩** | 定期（如每 10 轮）把前面的对话压缩成摘要 | 可以保留早期的核心信息，节省 token | 压缩过程有信息损失，细粒度细节会丢，无法精确还原 | 中（核心信息保留，细节丢失） | 中 | 中等长度对话、一般闲聊 |
| **向量检索** | 将历史对话向量化，检索与当前 query 最相关的 K 条 | 可以从超长历史中精准召回相关内容，不局限于时间 | 可能丢失上下文连贯性，不相关但必要的信息可能被漏检，无法处理“指代消解” | 低（只要检索到位） | 高 | 长对话、知识库问答、需要历史回忆 |
| **混合方案（推荐）** | 滑动窗口（最近 5 轮）+ 总结（历史摘要）+ 向量检索（相关记忆） | 兼顾了“最近上下文”、“全局概要”、“精准回忆” | 实现复杂，需要维护多个组件 | 极低（多份保险） | 高 | 生产级 Agent、长会话助手 |

---

**各策略详细说明**：

#### 1. 滑动窗口（Sliding Window）

```
实现：
  窗口大小：最近 5-10 轮，或 3K-5K tokens
  新消息进来 → 如果超过窗口 → 丢弃最早的

对话历史示例：
  [丢弃] 轮 1：用户：你好
  [丢弃] 轮 2：助手：您好！
  ...
  [保留] 轮 96：用户：那我买 3000 元的
  [保留] 轮 97：助手：好的，补贴是...
  [保留] 轮 98：用户：对了，我是济南的
  [保留] 轮 99：助手：了解
  [保留] 轮 100：用户：附近有门店吗？← 当前问题

问题：用户在轮 50 说过“我住在青岛”，但被丢了，现在回答“济南门店”就错了。
```

**适用**：不需要长期记忆的场景（如实时客服，每次对话独立）

---

#### 2. 总结压缩（Summarization）

```
实现：
  每 10 轮对话，用 LLM 把这 10 轮压缩成 1-2 句摘要
  保留摘要，丢弃原对话

对话历史压缩示例：
  原始 1-10 轮：
    用户：我是山东的
    助手：您好
    用户：想买个冰箱
    ...
  压缩摘要：
    "用户来自山东省，意向购买冰箱，询问了家电补贴政策"

  原始 11-20 轮：
    用户：价格大概 3000
    助手：补贴 10%
    ...
  压缩摘要：
    "用户预算 3000 元左右，确认了补贴比例为 10%，上限 500 元"

  当前上下文中只放这些摘要，不放原始对话。
```

**问题**：用户在第 5 轮说“我对青岛不熟”，摘要里没提，后面推荐青岛门店就不合适了。

---

#### 3. 向量检索（Vector Retrieval）

```
实现：
  每轮对话（或每 N 句）向量化 → 存入向量库
  当前 query → 向量化 → 检索 top-K 最相似的历史轮次 → 拼入上下文

示例：
  当前 query："附近有门店吗？"
  向量检索召回：
    - 轮 50：用户："我住在青岛"
    - 轮 98：用户："对了，我是济南的"  （冲突，说明用户可能去了济南）
    - 轮 75：助手："青岛有 3 家门店：..."

  把这几条拼入提示词。
```

**问题**：
- 检索不到“看似不相关但必要”的信息
- 比如用户问“补贴多少”，但之前用户说过“我是残疾人”（有额外补贴），如果“补贴多少”和“残疾人”向量相似度不高，就可能漏检

---

#### 4. 混合方案（推荐生产用）

```
最终上下文 = [最近 3-5 轮] + [历史整体摘要] + [关键事实表] + [向量检索 top 5]

具体组装示例：
  【系统提示】
  你是政策咨询助手...

  【历史对话摘要】
  整个对话摘要：用户来自山东省，先后提到青岛和济南，意向购买 3000 元左右的冰箱，询问了补贴政策和门店信息。

  【关键事实表】
  - 省份：山东省
  - 城市：济南（最近提及）、青岛（ earlier）
  - 产品：冰箱
  - 预算：3000 元
  - 特殊身份：无

  【最近对话（滑动窗口）】
  轮 98：用户：对了，我是济南的
  轮 99：助手：了解
  轮 100：用户：附近有门店吗？

  【相关历史记忆（向量检索）】
  - 轮 75：助手："青岛有 3 家门店：...，济南有 2 家：..."

  现在回答用户。
```

---

**我的项目实现**：

```java
// ContextBuilder.java
public class ContextBuilder {

    public String buildContext(String sessionId, String currentQuery) {
        StringBuilder sb = new StringBuilder();

        // 1. 全局摘要
        String summary = summaryService.getOrUpdateSummary(sessionId);
        sb.append("【对话摘要】\n").append(summary).append("\n\n");

        // 2. 关键事实（结构化）
        Map<String, Object> facts = factCache.getFacts(sessionId);
        sb.append("【关键信息】\n").append(formatFacts(facts)).append("\n\n");

        // 3. 最近对话（滑动窗口，最近 5 轮）
        List<Message> recentMessages = messageService.getRecent(sessionId, 5);
        sb.append("【最近对话】\n").append(formatMessages(recentMessages)).append("\n\n");

        // 4. 相关历史记忆（向量检索 top 5）
        List<Memory> related = memoryRetriever.retrieve(sessionId, currentQuery, 5);
        sb.append("【相关历史】\n").append(formatMemories(related)).append("\n\n");

        return sb.toString();
    }
}
```

---

### 八、混合检索（Hybrid Search）深度解析

#### 8. 什么是“混合检索”（Hybrid Search）？请解释为什么在工业级 RAG 系统中，纯向量检索往往不够用，需要结合关键词检索（如 BM25）。请给出一个具体的业务场景，说明混合检索的必要性。

**参考答案**：

---

**纯向量检索的局限性**：

| 局限性 | 说明 | 例子 |
|--------|------|------|
| **对精确匹配弱** | 向量检索是语义相似，对“精确关键词”“专有名词”“编号”匹配不好 | 搜“鲁政发〔2024〕3 号”，向量可能找不到，但关键词一搜一个准 |
| **对低频实体差** | 训练数据中少见的实体，向量表达不准，搜不到 | 搜“小明家电维修部”（本地小商家），向量可能匹配成“家电维修”通用内容 |
| **可解释性差** | 不知道为什么搜到这条，debug 困难 | 用户：为什么给我推这个？ 你：因为向量距离近...  用户：？ |
| **索引更新延迟** | 向量化需要时间，实时新增内容无法立即检索到 | 刚上传的文档，要等向量化完才能搜到 |

---

**混合检索定义**：

```
混合检索 = 向量检索（语义相似） + 关键词检索（BM25 / TF-IDF / 倒排索引）

召回阶段：
  1. 向量检索：召回 top K1 个语义相关的结果
  2. 关键词检索：召回 top K2 个关键词匹配的结果
  3. 合并去重：得到一个候选池

重排序阶段：
  用 Cross-Encoder / LLM / 线性加权 对候选池重新排序，选出最终 top N

分数融合示例：
  final_score = 0.6 * vector_score + 0.4 * bm25_score
  （权重可以根据业务调）
```

---

**向量检索 vs 关键词检索对比**：

| 维度 | 向量检索（Dense Retrieval） | 关键词检索（Sparse Retrieval / BM25） |
|------|------------------------------|----------------------------------------|
| **核心能力** | 语义理解、同义词、同义句 | 精确匹配、专有名词、编号、缩略语 |
| **示例** | 搜“买东西有补贴吗” → 匹配“以旧换新政策” | 搜“鲁政发〔2024〕3 号” → 精确匹配该文件 |
| **对“未见过”的词** | 可以泛化（知道“电脑”≈“计算机”） | 不行（没见过就搜不到） |
| **对短 query** | 容易漂移（短 query 语义不明确） | 表现稳定（短词精确匹配） |
| **索引速度** | 慢（需要向量化） | 快（直接建倒排） |
| **存储成本** | 高（向量维度大，如 1536 维 float） | 低（只存词频） |

---

**具体业务场景说明混合检索的必要性**：

**场景：山东省以旧换新政策咨询 RAG**

```
知识库中的文档片段：
  doc1：
    标题：《山东省 2024 年以旧换新实施细则》
    内容：... 鲁政发〔2024〕3 号文件规定，家电以旧换新补贴比例为 10%，上限 500 元 ...

  doc2：
    标题：《关于开展家电下乡活动的通知》
    内容：... 购买计算机、冰箱、洗衣机等家电产品，可享受补贴 ...

  doc3：
    标题：《济南市门店列表》
    内容：... 小明家电维修部（地址：济南市历下区 ...） ...
```

---

**Case 1：用户搜“鲁政发〔2024〕3 号”**

```
纯向量检索：
  向量可能把“鲁政发〔2024〕3 号”映射成“政府文件”的通用语义
  结果：可能召回 doc1，也可能召回其他政府文件，排序可能靠后

纯关键词检索（BM25）：
  精确匹配到 doc1 中的“鲁政发〔2024〕3 号”
  结果：doc1 排第一 ✅

→ 这个 case 关键词检索更好
```

---

**Case 2：用户搜“买电脑有补贴吗”**

```
纯向量检索：
  知道“电脑” ≈ “计算机”，语义匹配到 doc2
  结果：doc2 排第一 ✅

纯关键词检索（BM25）：
  搜“电脑”，doc2 里只有“计算机”，没有“电脑”
  结果：搜不到 doc2 ❌

→ 这个 case 向量检索更好
```

---

**Case 3：用户搜“小明家电维修部”**

```
纯向量检索：
  “小明家电维修部”是低频实体，向量可能表达不准
  结果：可能召回“家电维修”相关的其他文档，把小明的排后面

纯关键词检索（BM25）：
  精确匹配 doc3 中的“小明家电维修部”
  结果：doc3 排第一 ✅

→ 这个 case 关键词检索更好
```

---

**Case 4：用户搜“我在山东买个冰箱，能补多少钱”**

```
纯向量检索：
  语义匹配 doc1，效果不错 ✅

纯关键词检索：
  可以匹配“山东”“冰箱”“补贴”，也还行

混合检索：
  向量召回 doc1（语义） + 关键词召回 doc1（“山东”“冰箱”“补贴”）
  双路加持，doc1 分数更高，更稳 ✅
```

---

**结论**：

| Query 类型 | 推荐方式 |
|------------|----------|
| 查编号、文件名、专有名词 | 关键词检索为主 |
| 自然语言问题、同义句 | 向量检索为主 |
| 通用业务 query | 混合检索（双路召回 + 重排序） |

---

**工业级 RAG 的混合检索实现架构**：

```
用户 Query
    ↓
┌─────────────────────────────────────────────┐
│          查询理解层                          │
│  - 提取关键词                               │
│  -  query 改写（同义替换）                  │
└─────────────────────────────────────────────┘
    ↓
    ├─────────────────┬─────────────────┐
    ↓                 ↓                 ↓
┌─────────┐    ┌─────────┐    ┌──────────────┐
│ 向量检索 │    │ BM25    │    │ 结构化查询    │
│ top 50  │    │ top 50  │    │ (SQL)         │
└─────────┘    └─────────┘    └──────────────┘
    ↓                 ↓                 ↓
    └─────────────────┴─────────────────┘
                      ↓
              ┌───────────────┐
              │  合并去重     │
              └───────────────┘
                      ↓
              ┌───────────────┐
              │  重排序       │
              │  (Cross-Encoder / LLM) │
              └───────────────┘
                      ↓
              ┌───────────────┐
              │  最终 top N   │
              └───────────────┘
```

---

**我的项目中的混合检索实现**：

```java
// HybridRetriever.java
public class HybridRetriever {

    public List<Document> retrieve(String query, int topN) {
        // 1. 并行执行两路检索
        CompletableFuture<List<ScoredDocument>> vectorFuture =
            CompletableFuture.supplyAsync(() -> vectorDb.search(query, 50));
        CompletableFuture<List<ScoredDocument>> bm25Future =
            CompletableFuture.supplyAsync(() -> bm25Index.search(query, 50));

        CompletableFuture.allOf(vectorFuture, bm25Future).join();

        // 2. 合并
        List<ScoredDocument> candidates = new ArrayList<>();
        candidates.addAll(vectorFuture.get());
        candidates.addAll(bm25Future.get());

        // 3. 去重（按 docId）
        Map<String, ScoredDocument> deduped = candidates.stream()
            .collect(toMap(ScoredDocument::getDocId, d -> d, (d1, d2) -> {
                // 去重时融合分数
                d1.setScore(0.6 * d1.getScore() + 0.4 * d2.getScore());
                return d1;
            }));

        // 4. 重排序（可选，用 Cross-Encoder）
        List<ScoredDocument> reranked = reranker.rerank(query, new ArrayList<>(deduped.values()));

        // 5. 返回 top N
        return reranked.stream().limit(topN).collect(toList());
    }
}
```

---

### 九、Function Calling Schema 优化与负向约束

#### 9. 在定义 Function Calling（或 Tool Calling）的工具 Schema 时，除了基本的参数名和类型，如何通过优化 description 字段来显著提高大模型调用工具的准确率？请结合一个具体例子，说明如何进行“负向约束”或提供调用策略。

**参考答案**：

---

**Bad vs Good Schema 对比**：

**反面教材（Bad）**：

```json
{
  "name": "calculateSubsidy",
  "description": "计算补贴",
  "parameters": {
    "type": "object",
    "properties": {
      "productType": {
        "type": "string",
        "description": "产品类型"
      },
      "price": {
        "type": "number",
        "description": "价格"
      }
    },
    "required": ["productType", "price"]
  }
}
```

问题：
- 描述太模糊，模型不知道什么时候该调用
- 参数没说明枚举值、范围、约束
- 模型可能传“手机”（实际只支持家电/汽车/数码）

---

**正面教材（Good）**：

```json
{
  "name": "calculateSubsidy",
  "description": "当用户询问以旧换新补贴金额、计算补贴、或想知道买某件商品能补多少钱时调用此工具。不要在用户只是随便聊聊政策时调用。",
  "parameters": {
    "type": "object",
    "properties": {
      "productType": {
        "type": "string",
        "enum": ["家电", "汽车", "数码"],
        "description": "产品类型，只能是这三个枚举值之一：家电（冰箱、电视、洗衣机等）、汽车（乘用车、新能源汽车）、数码（手机、电脑、平板）。如果用户说的是其他类型，不要强行归类，直接要求澄清。"
      },
      "price": {
        "type": "number",
        "description": "购买价格，单位是元，必须大于 0。如果用户没说价格，不要猜测，先询问用户。"
      },
      "region": {
        "type": "string",
        "description": "省份，可选，默认是山东省。如果用户提到了其他省，传对应的省名。"
      }
    },
    "required": ["productType", "price"]
  }
}
```

---

**优化技巧清单**：

| 技巧 | 说明 | 示例 |
|------|------|------|
| **调用时机说明** | 明确说“什么时候调用” | `当用户询问补贴金额、计算补贴时调用` |
| **负向约束（不要做什么）** | 明确说“不要什么时候调用” | `不要在用户只是随便聊聊政策时调用` |
| **枚举值 + 解释** | 列出可选值，并解释每个值的含义 | `enum: ["家电", "汽车", "数码"]，家电指冰箱、电视...` |
| **参数范围约束** | 说明数值的范围、格式 | `价格必须大于 0，单位是元` |
| **缺失处理策略** | 告诉模型参数缺失时该怎么办 | `如果用户没说价格，不要猜测，先询问用户` |
| **歧义处理策略** | 告诉模型有歧义时该怎么办 | `如果用户说的是其他类型，不要强行归类，直接要求澄清` |

---

**更复杂的例子：带负向约束的 webSearch**

```json
{
  "name": "webSearch",
  "description": "当用户询问实时信息（如今天的天气、新闻、最新价格）、或知识库中没有的信息时调用此工具。\n\n【什么时候不要调用】\n- 不要在用户只是闲聊时调用\n- 不要在问题可以从对话历史中找到答案时调用\n- 不要在问题属于政策常识且知识库中有时调用（如“补贴比例是多少”，知识库有，不要搜）\n- 不要搜索敏感内容、违法内容",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "搜索关键词，要简洁明了，不少于 2 个字，不超过 50 个字。\n\n【优化建议】\n- 如果用户问“今天济南天气怎么样”，query 就写“济南今天天气”\n- 不要包含多余的语气词，如“请问”“你好”\n- 如果用户的问题很长，提炼核心关键词"
      }
    },
    "required": ["query"]
  }
}
```

---

**我的项目中的真实 Schema（Java Spring AI）**：

```java
// PolicyTools.java
public class PolicyTools {

    @Tool("""
        当用户询问以旧换新补贴金额、计算补贴、或想知道买某件商品能补多少钱时调用此工具。
        不要在用户只是随便聊聊政策时调用，也不要在已经计算过且参数没变时重复调用。
        """)
    public SubsidyResult calculateSubsidy(
        @Property(name = "productType",
                  description = """
                      产品类型，只能是这三个枚举值之一：
                      - 家电（冰箱、电视、洗衣机、空调等）
                      - 汽车（乘用车、新能源汽车）
                      - 数码（手机、电脑、平板）
                      如果用户说的是其他类型，不要强行归类，直接拒绝调用并要求澄清。
                      """)
        String productType,

        @Property(name = "price",
                  description = """
                      购买价格，单位是元，必须大于 0。
                      如果用户没说价格，不要猜测，直接拒绝调用并先询问用户。
                      """)
        Double price,

        @Property(name = "region",
                  description = """
                      省份，可选，默认是山东省。
                      如果用户提到了其他省，传对应的省名。
                      """)
        String region
    ) {
        // 实现...
    }
}
```

---

**额外技巧：在 System Prompt 中补充工具使用策略**

除了 Schema 本身，还可以在 System Prompt 中统一约定工具使用规范：

```
【工具使用总原则】
1. Think before you act：先想清楚“是不是需要调用工具”“调用哪个工具”“参数全不全”，再决定。
2. No guess：不要猜测参数，参数缺失就问用户，不要编造。
3. No overuse：不要过度调用工具，能从历史或上下文中回答的，直接回答。
4. Fail fast：如果工具调用失败，尝试 1-2 次，不行就告诉用户“暂时无法查询”，不要死循环。
```

---

### 十、多工具场景下的高效工具选择策略

#### 10. 当 Agent 拥有众多工具时，如何设计一个高效且准确的工具选择策略？是每次将所有工具描述都推给模型（存在上下文窗口限制和干扰），还是采用分层过滤、基于检索的预选择机制？请详细说明你的设计方案。

**参考答案**：

---

**问题背景**：

```
场景：企业级 Agent，有 50+ 个工具
  - 通用工具：webSearch, calculate, fileParse, ...
  - 业务工具：calculateSubsidy, queryStore, verifyPolicy, ...
  - 第三方工具：amap, weather, stock, ...
  - MCP 工具：...

直接把 50+ 个工具的 Schema 全塞给模型的问题：
  1. 上下文窗口爆炸：一个工具 Schema 可能 500 tokens，50 个就是 25K tokens
  2. 干扰太多：模型看晕了，容易选错（“选择困难症”）
  3. 费用增加：多塞的 Token 都是钱
```

---

**设计方案：分层过滤 + 预选择架构**

```
┌─────────────────────────────────────────────────────────────┐
│                   用户 Query + 上下文                         │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第 0 层：快捷规则（Rule-based，0 成本）                     │
│  - 匹配“你好”“谢谢”→ 直接返回，不调工具                     │
│  - 匹配“补贴”→ 直接锁定 calculateSubsidy + 相关工具         │
│  - 匹配“天气”→ 直接锁定 weatherTool                          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第 1 层：工具意图分类（轻量 LLM / 小模型）                  │
│  输入：用户 Query                                             │
│  输出：工具类别（{GENERAL, POLICY, MAP, WEATHER, ...}）     │
│  模型：可以用小模型（如 qwen-turbo），成本低                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第 2 层：工具检索（Vector DB，预筛选 top K）                │
│  把所有工具的“功能描述”向量化，存向量库                       │
│  Query → 向量化 → 检索语义最相似的 top 5-10 个工具          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第 3 层：最终选择（主 LLM，精细选择 + 参数填充）            │
│  只把预筛选后的 5-10 个工具的 Schema 给主模型                │
│  主模型决定：用哪个工具 / 还是不用工具 / 还是问用户          │
└─────────────────────────────────────────────────────────────┘
                              ↓
                     工具调用 / 追问 / 直接回答
```

---

**详细设计**：

#### 第 0 层：快捷规则（Rule-based）

```java
// QuickRuleFilter.java
public class QuickRuleFilter {

    public QuickFilterResult filter(String query) {
        // 1. 纯闲聊，直接返回
        if (isPureChat(query)) {
            return QuickFilterResult.directAnswer("你好！有什么可以帮您？");
        }

        // 2. 关键词精确匹配，锁定工具
        if (query.contains("补贴") || query.contains("能补多少钱")) {
            return QuickFilterResult.lockTools(Arrays.asList("calculateSubsidy", "queryPolicy"));
        }

        if (query.contains("门店") || query.contains("地址")) {
            return QuickFilterResult.lockTools(Arrays.asList("amap-mcp", "queryStore"));
        }

        // 3. 继续下一层
        return QuickFilterResult.continueNextLayer();
    }
}
```

---

#### 第 1 层：工具意图分类（轻量 LLM）

```java
// ToolIntentClassifier.java
public class ToolIntentClassifier {

    public enum ToolCategory {
        GENERAL,   // 通用（webSearch, ...）
        POLICY,    // 政策相关
        MAP,       // 地图
        WEATHER,   // 天气
        NO_TOOL    // 不需要工具
    }

    public ToolCategory classify(String query) {
        String prompt = String.format("""
            请判断用户的问题属于哪个类别，只返回类别代号。

            可选类别：
            - GENERAL：需要搜索、计算等通用工具
            - POLICY：政策咨询、补贴计算
            - MAP：地图、位置、门店查询
            - WEATHER：天气查询
            - NO_TOOL：闲聊、不需要工具

            用户问题：%s
            """, query);

        // 用小模型，成本低，速度快
        String response = smallLlm.call(prompt);
        return ToolCategory.valueOf(response.trim());
    }
}
```

---

#### 第 2 层：工具检索（Vector DB）

```java
// ToolRetriever.java
public class ToolRetriever {

    // 工具描述库，提前向量化存好
    private VectorDb toolVectorDb;

    public List<ToolDefinition> retrieve(String query, ToolCategory category, int topK) {
        // 1. 先按类别过滤
        List<ToolDefinition> candidates = toolRegistry.getByCategory(category);

        // 2. 如果类别内工具少（<10），直接全返回
        if (candidates.size() <= 10) {
            return candidates;
        }

        // 3. 类别内工具多，用向量检索 top K
        return toolVectorDb.search(query, candidates, topK);
    }
}

// 初始化时，把每个工具的“功能描述”向量化
record ToolDef(
    String name,
    String description,  // e.g., "计算山东省以旧换新补贴金额，支持家电、汽车、数码"
    JsonSchema schema,
    ToolCategory category
) {}
```

---

#### 第 3 层：最终选择（主 LLM）

```java
// FinalToolSelector.java
public class FinalToolSelector {

    public ToolCallDecision select(String query,
                                    List<ToolDefinition> preselectedTools,
                                    String context) {
        // 只把预筛选后的 5-10 个工具给主模型
        String prompt = String.format("""
            你可以使用以下工具（如果需要的话）：
            %s

            用户问题：%s
            上下文：%s

            请决定：
            1. 调用工具（输出 {useTool: true, toolName: "...", arguments: {...}}）
            2. 不调用工具直接回答（输出 {useTool: false, answer: "..."}）
            3. 需要追问用户（输出 {useTool: false, askUser: "..."}）
            """,
            formatTools(preselectedTools),
            query,
            context
        );

        return mainLlm.call(prompt, ToolCallDecision.class);
    }
}
```

---

**各层成本与效果权衡**：

| 层 | 技术 | 成本 | 时间 | 准确率提升 | 作用 |
|----|------|------|------|------------|------|
| 第 0 层 | 规则 | 0 | 1ms | - | 快速拦截简单场景，省钱 |
| 第 1 层 | 小模型 | 低 | 50ms | ~20% | 粗分类，缩小范围 |
| 第 2 层 | 向量检索 | 低 | 20ms | ~30% | 语义预筛选 |
| 第 3 层 | 主模型 | 高 | 500ms+ | ~50% | 精细选择 + 参数填充 |

---

**我的项目中的实现（当前工具少，简化版）**：

```java
// 当前项目只有 4 个工具，所以只做了“第 0 层 + 第 3 层”

// ToolIntentClassifier.java
public class ToolIntentClassifier {

    public ClassificationResult classify(String query, AgentPlan plan) {
        // 规则：如果是问候，直接拦截
        if (isGreeting(query)) {
            return ClassificationResult.block("直接问候");
        }

        // 规则：如果计划里 needToolCall=false，拦截
        if (!plan.isNeedToolCall()) {
            return ClassificationResult.block("不需要工具");
        }

        // 参数校验：检查工具参数是否齐全
        if (!hasAllRequiredParams(plan)) {
            return ClassificationResult.needClarification("缺少参数：...");
        }

        return ClassificationResult.allow();
    }
}
```

---

**总结：工具选择策略演进路径**

```
工具数量 < 10：
  → 直接全量给模型，简单粗暴，不用折腾

工具数量 10-30：
  → 加“工具类别标签”，按类别先过滤一下

工具数量 30-100：
  → 上“分层过滤 + 向量检索预选择”架构

工具数量 > 100：
  → 考虑“工具市场”“工具发现”“动态加载”
```

---

## 面试要点总结

| 模块 | 核心考点 |
|------|----------|
| **规划范式** | ReAct 局限、Plan-Then-Act vs ReAct+轻规划 vs ToT/LATS |
| **CoT vs Planning** | 推理过程 vs 可执行任务表，有无反馈、变量引用、状态机 |
| **容错机制** | 重试、降级、回滚、检查点、重规划、非关键步骤跳过 |
| **ToT/LATS** | 树搜索、MCTS（选择/扩展/模拟/回传）、全局视野与回溯 |
| **推理断层** | 提示工程（自检/Few-Shot）、记忆机制（目标锚定/事实缓存）、架构设计（反思层/验证器） |
| **长期记忆** | 向量库 + 图数据库 + SQL、记忆合并/遗忘/衰减、混合检索 + 重排序 |
| **长对话优化** | 滑动窗口 vs 总结压缩 vs 向量检索 vs 混合方案 |
| **混合检索** | 语义（向量）+ 关键词（BM25）、召回 + 重排序 |
| **Tool Schema** | 调用时机说明、负向约束、枚举解释、缺失/歧义处理策略 |
| **多工具选择** | 分层过滤（规则 → 小模型分类 → 向量检索 → 主模型选择） |
