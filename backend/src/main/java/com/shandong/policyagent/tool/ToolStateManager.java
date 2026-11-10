package com.shandong.policyagent.tool;

import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具状态管理器
 *
 * 读取运行时 AgentConfig 中的 skills 配置，判断各工具是否允许执行。
 * 各工具在执行业务逻辑前调用 isEnabled() 进行检查；若禁用则直接返回友好提示。
 *
 * skills 配置结构（JSON）：
 * {
 *   "webSearch":          { "enabled": true },
 *   "subsidyCalculator":  { "enabled": true },
 *   "fileParser":         { "enabled": false }
 * }
 *
 * 工具名到 skills key 的映射：
 * - webSearch         → "webSearch"
 * - calculateSubsidy  → "subsidyCalculator"
 * - parseFile         → "fileParser"
 *
 * <h3>两套开关的关系</h3>
 * <p>管理台有两个地方可以开关工具，此前它们互不相通：</p>
 * <ul>
 *   <li><b>智能体页（AgentsTab）</b>：写 {@code agent_config.skills.<skillKey>.enabled} —— 智能体级</li>
 *   <li><b>工具管理页（ToolsTab）</b>：写 {@code tool_definition.is_enabled}（code = web_search 等）—— 全局级</li>
 * </ul>
 * <p>此前只有 skills 被读取，导致「工具管理」页的开关完全是哑弹——用户在那里打开/关闭工具，
 * 运行时毫无反应。现改为两套开关同时生效（AND 关系）：任一关闭即禁用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolStateManager {

    /**
     * skills key → tool_definition.code 的映射。
     * 用于把智能体级开关与全局工具开关对应起来。
     */
    private static final Map<String, String> SKILL_KEY_TO_TOOL_CODE = Map.of(
            "webSearch", "web_search",
            "subsidyCalculator", "subsidy_calculator",
            "fileParser", "file_parser"
    );

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final ToolDefinitionRepository toolDefinitionRepository;

    /**
     * 判断指定技能 key 是否启用。
     * 若配置不存在或 enabled 字段缺失，默认返回 true（不阻断现有行为）。
     *
     * @param skillKey skills 配置中的 key，例如 "webSearch"、"subsidyCalculator"、"fileParser"
     * @return true 表示允许执行，false 表示已禁用
     */
    public boolean isEnabled(String skillKey) {
        // 全局工具开关（管理台「工具管理」页）：关闭即禁用
        if (!isGloballyEnabled(skillKey)) {
            return false;
        }
        try {
            Map<String, Object> skills = dynamicAgentConfigHolder.getSkills();
            if (skills == null || skills.isEmpty()) {
                return true; // 无配置时放行
            }

            Object skillConfig = skills.get(skillKey);
            if (skillConfig == null) {
                return true; // 该 key 不存在时放行
            }

            if (skillConfig instanceof Map<?, ?> skillMap) {
                Object enabled = skillMap.get("enabled");
                if (enabled instanceof Boolean b) {
                    return b;
                }
                // 字符串形式 "true"/"false"
                if (enabled instanceof String s) {
                    return Boolean.parseBoolean(s);
                }
            }

            return true; // 无法解析时放行
        } catch (Exception e) {
            log.warn("检查工具状态时发生异常，默认放行 | skillKey={} | error={}", skillKey, e.getMessage());
            return true;
        }
    }

    /**
     * 查询管理台「工具管理」页的全局开关（tool_definition.is_enabled）。
     *
     * <p>记录不存在时放行（工具定义表可能尚未初始化），查询异常同样放行，
     * 避免数据库抖动导致工具整体不可用。</p>
     */
    private boolean isGloballyEnabled(String skillKey) {
        String toolCode = SKILL_KEY_TO_TOOL_CODE.get(skillKey);
        if (toolCode == null || toolDefinitionRepository == null) {
            return true;
        }
        try {
            return toolDefinitionRepository.findByCode(toolCode)
                    .map(tool -> !Boolean.FALSE.equals(tool.getIsEnabled()))
                    .orElse(true);
        } catch (Exception e) {
            log.warn("查询全局工具开关失败，默认放行 | skillKey={} | toolCode={} | error={}",
                    skillKey, toolCode, e.getMessage());
            return true;
        }
    }

    /**
     * 检查 webSearch 工具是否启用
     */
    public boolean isWebSearchEnabled() {
        return isEnabled("webSearch");
    }

    /**
     * 检查 calculateSubsidy 工具是否启用
     */
    public boolean isSubsidyCalculatorEnabled() {
        return isEnabled("subsidyCalculator");
    }

    /**
     * 检查 parseFile 工具是否启用
     */
    public boolean isFileParserEnabled() {
        return isEnabled("fileParser");
    }
}
