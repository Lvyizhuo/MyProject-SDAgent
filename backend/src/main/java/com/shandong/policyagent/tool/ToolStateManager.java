package com.shandong.policyagent.tool;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolStateManager {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;

    /**
     * 判断指定技能 key 是否启用。
     * 若配置不存在或 enabled 字段缺失，默认返回 true（不阻断现有行为）。
     *
     * @param skillKey skills 配置中的 key，例如 "webSearch"、"subsidyCalculator"、"fileParser"
     * @return true 表示允许执行，false 表示已禁用
     */
    public boolean isEnabled(String skillKey) {
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
