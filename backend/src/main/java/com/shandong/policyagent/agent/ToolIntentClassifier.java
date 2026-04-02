package com.shandong.policyagent.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 工具调用前意图分类器，用于降低无效工具调用。
 */
@Component
public class ToolIntentClassifier {

    private static final Set<String> EXTERNAL_TOOLS = Set.of(
            "webSearch", "amap-mcp", "calculateSubsidy", "parseFile"
    );

    public IntentDecision classify(String userMessage, AgentExecutionPlan plan) {
        if (plan == null || !plan.needToolCall()) {
            return IntentDecision.allow("none", "当前计划无需外部工具");
        }

        String normalized = normalize(userMessage);
        String targetTool = inferPrimaryTool(plan);

        return switch (targetTool) {
            case "webSearch" -> classifyWebSearch(normalized);
            case "amap-mcp" -> classifyMapSearch(normalized);
            case "calculateSubsidy" -> classifySubsidy(normalized);
            case "parseFile" -> classifyFileParse(normalized);
            default -> IntentDecision.allow("none", "工具类型未知，保持原计划");
        };
    }

    public AgentExecutionPlan applyDecision(AgentExecutionPlan plan, IntentDecision decision) {
        if (decision == null || decision.allowToolCall()) {
            return plan;
        }

        List<AgentExecutionPlan.AgentStep> steps = new ArrayList<>();
        steps.add(new AgentExecutionPlan.AgentStep(
                1,
                "先向用户补充必要参数：" + decision.clarificationQuestion(),
                "none"
        ));
        steps.add(new AgentExecutionPlan.AgentStep(
                2,
                "在参数明确前，仅给出简短的补充说明，不调用外部工具",
                "none"
        ));

        return new AgentExecutionPlan(
                "工具调用前校验发现参数不足，先澄清后执行",
                false,
                steps
        );
    }

    private IntentDecision classifyWebSearch(String normalized) {
        boolean productOrPriceIntent = containsAny(normalized,
                "价格", "报价", "多少钱", "优惠", "参数", "配置", "型号", "款", "对比", "电商", "官网");
        if (!productOrPriceIntent && containsAny(normalized, "最新", "实时", "新闻", "政策", "动态")) {
            return IntentDecision.allow("webSearch", "非商品价格类实时查询，允许联网检索");
        }

        if (hasExplicitModelInfo(normalized)) {
            return IntentDecision.allow("webSearch", "已识别明确品牌/型号/规格，允许联网查询");
        }

        return IntentDecision.block(
                "webSearch",
                "为避免查错，请补充“品牌 + 型号”（可带规格），例如：华为 Mate 70 Pro 512GB。",
                "webSearch 缺少明确型号信息"
        );
    }

    private IntentDecision classifyMapSearch(String normalized) {
        boolean hasCoordinate = normalized.contains("lat=") && normalized.contains("lng=");
        boolean hasExplicitLocation = containsAny(normalized, "省", "市", "区", "县", "路", "街", "地铁", "学校");
        if (hasCoordinate || hasExplicitLocation) {
            return IntentDecision.allow("amap-mcp", "存在定位或地点上下文");
        }
        return IntentDecision.block(
                "amap-mcp",
                "请提供当前所在城市和区县，或一个明确地标（例如：济南市历下区泉城广场）。",
                "地图检索缺少位置参数"
        );
    }

    private IntentDecision classifySubsidy(String normalized) {
        boolean hasPrice = normalized.matches(".*(?:\\d{1,3}(?:[,，]\\d{3})+|\\d{3,6})(?:\\.\\d{1,2})?\\s*(元|rmb|人民币|¥|￥)?.*");
        boolean hasCategory = containsAny(normalized,
                "手机", "平板", "手表", "手环", "空调", "冰箱", "洗衣机", "电视", "热水器",
            "笔记本", "电脑", "macbook", "thinkpad", "surface") || hasExplicitModelInfo(normalized);
        if (hasPrice && hasCategory) {
            return IntentDecision.allow("calculateSubsidy", "补贴计算参数基本充分");
        }
        return IntentDecision.block(
                "calculateSubsidy",
            "请先补充“明确商品类型 + 数字价格”（例如：手机，5999元），我再计算补贴。",
            "补贴计算缺少明确类型或价格"
        );
    }

    private IntentDecision classifyFileParse(String normalized) {
        if (containsAny(normalized, "发票", "附件", "上传", "图片", "文件", "pdf", "jpg", "png")) {
            return IntentDecision.allow("parseFile", "文件解析请求明确");
        }
        return IntentDecision.block(
                "parseFile",
                "请先上传待解析文件，或说明需要解析的文件类型。",
                "文件解析缺少输入文件"
        );
    }

    private String inferPrimaryTool(AgentExecutionPlan plan) {
        if (plan.steps() == null) {
            return "none";
        }
        for (AgentExecutionPlan.AgentStep step : plan.steps()) {
            if (EXTERNAL_TOOLS.contains(step.toolHint())) {
                return step.toolHint();
            }
        }
        return "none";
    }

    private boolean hasExplicitModelInfo(String normalized) {
        return containsAny(normalized,
                "iphone", "ipad", "macbook", "thinkpad", "surface", "matebook", "xiaomi", "redmi",
                "华为", "小米", "荣耀", "oppo", "vivo", "mate", "pro", "max", "air", "mini", "ultra",
                "16e", "16", "15", "14")
                || normalized.matches(".*[a-z]{2,}(?:\\s+[a-z0-9+-]{1,12}){0,4}\\s+\\d{1,3}.*")
                || normalized.matches(".*\\d{1,3}(?:gb|tb|英寸|寸).*\\d{1,3}(?:gb|tb|英寸|寸).*")
                || normalized.matches(".*\\d{4}款.*");
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public record IntentDecision(
            boolean allowToolCall,
            String targetTool,
            String clarificationQuestion,
            String reason
    ) {
        static IntentDecision allow(String targetTool, String reason) {
            return new IntentDecision(true, targetTool, "", reason);
        }

        static IntentDecision block(String targetTool, String clarificationQuestion, String reason) {
            return new IntentDecision(false, targetTool, clarificationQuestion, reason);
        }
    }
}
