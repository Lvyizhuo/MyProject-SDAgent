package com.shandong.policyagent.tool;

import com.shandong.policyagent.tool.SubsidyCalculatorTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把服务端内置工具注册为模型可见的 {@link ToolCallback}。
 *
 * <h3>为什么需要它</h3>
 * <p>项目里 {@link WebSearchTool#webSearch()} 与 {@code SubsidyCalculatorTool#calculateSubsidy()}
 * 都是 {@code @Bean @Description Function<...>} 的形式，供服务端预执行器
 * （{@code PlannedToolExecutor}）在模型调用前主动执行并把结果注入提示词。</p>
 *
 * <p>但 Spring AI 只会把 {@link ToolCallbackProvider} 装配进 {@code ChatClient} 的工具列表——
 * {@code Function} 类型的 Bean 不会被自动转成工具。于是：</p>
 * <ul>
 *   <li>模型的 tools schema 里<b>看不到</b> webSearch / calculateSubsidy</li>
 *   <li>模型即便在推理中想调用某个工具，也只能报 {@code No ToolCallback found}</li>
 *   <li>服务端预执行一旦被意图路由跳过（如"政策+价格混合问法"策略），模型就<b>完全失去</b>联网能力，
 *       只能基于 RAG 政策文档回答，于是出现"未检索到 iPhone17 实时价格"的错误回复</li>
 * </ul>
 *
 * <p>本类把这两个 Function 显式包装为 {@link ToolCallback} 并以 {@code ToolCallbackProvider}
 * 形式暴露，使它们既能被服务端预执行（原有路径），也能被模型直接调用（兜底路径）。</p>
 */
@Slf4j
@Component
public class BuiltinToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallback[] toolCallbacks;

    public BuiltinToolCallbackProvider(WebSearchTool webSearchTool,
                                       SubsidyCalculatorTool subsidyCalculatorTool) {
        this.toolCallbacks = new ToolCallback[]{
                buildWebSearchCallback(webSearchTool),
                buildSubsidyCalculatorCallback(subsidyCalculatorTool)
        };
        log.info("内置工具已注册为模型可见 ToolCallback | tools={}", toolNames());
    }

    /**
     * 包装 webSearch：模型调用时以 "webSearch" 为工具名，
     * 参数为 {@code {"query": "...", "maxResults": 5}}。
     */
    private ToolCallback buildWebSearchCallback(WebSearchTool webSearchTool) {
        return FunctionToolCallback
                .builder("webSearch", webSearchTool.webSearch())
                .description("联网搜索工具，用于搜索最新的产品价格、政策信息、新闻动态等实时网络内容。"
                        + "当用户询问具体产品的市场价格（如iPhone、华为手机的售价）、最新政策变化、新闻事件等需要实时信息时，必须调用此工具。"
                        + "输入搜索关键词(query)和最大结果数(maxResults，默认5条)，返回搜索结果摘要和详细列表。")
                .inputType(WebSearchTool.SearchRequest.class)
                .build();
    }

    /**
     * 包装 calculateSubsidy：模型调用时以 "calculateSubsidy" 为工具名。
     */
    private ToolCallback buildSubsidyCalculatorCallback(SubsidyCalculatorTool subsidyCalculatorTool) {
        return FunctionToolCallback
                .builder("calculateSubsidy", subsidyCalculatorTool.calculateSubsidy())
                .description("计算山东省以旧换新补贴金额工具。必填参数：type（明确商品类型，如 手机/平板/空调/冰箱/洗衣机/电视/热水器/微波炉/油烟机/洗碗机/燃气灶/净水器/智能手表/手环）、"
                        + "price（明确成交价，数字，单位元）。补贴规则：默认按 15% 计算，不同品类有封顶上限。"
                        + "调用时机：仅当商品类型与数字价格都明确时调用。")
                .inputType(SubsidyCalculatorTool.SubsidyRequest.class)
                .build();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolCallbacks.clone();
    }

    /**
     * 工具名清单，供日志与测试断言使用。
     */
    public List<String> toolNames() {
        return java.util.Arrays.stream(toolCallbacks)
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }
}
