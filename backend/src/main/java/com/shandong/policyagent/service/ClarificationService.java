package com.shandong.policyagent.service;

import com.shandong.policyagent.agent.QueryIntentRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 澄清提问服务。
 * <p>
 * 负责检查会话事实中缺失的槽位（品类/价格/年份），并根据意图关键词生成引导用户补充信息的问题。
 * 从 ChatService 拆分而来，保持原逻辑行为完全不变。
 * </p>
 */
@Slf4j
@Service
public class ClarificationService {

    /**
     * 澄清步骤记录：槽位名 + 向用户提出的澄清问题。
     */
    public record ClarificationStep(String slotName, String question) {
    }

    /**
     * 判断当前用户输入是否需要澄清，生成对应的追问文案。
     *
     * @param rawUserMessage 用户原始输入
     * @param facts          会话结构化事实（含 pendingSlot、品类、价格等）
     * @return 若需澄清则返回 ClarificationStep，否则返回 null
     */
    public ClarificationStep resolveClarificationStep(String rawUserMessage,
                                                       SessionFactCacheService.SessionFacts facts) {
        String normalized = rawUserMessage == null ? "" : rawUserMessage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        String pendingSlot = facts == null ? null : facts.getPendingSlot();
        if ("商品类别".equals(pendingSlot) && !hasUsableCategory(facts, normalized)) {
            return new ClarificationStep("商品类别", "请问您要查询哪类产品？例如手机、平板、家电、汽车或笔记本电脑。");
        }
        if ("购买价格".equals(pendingSlot) && !hasUsablePrice(facts, normalized)) {
            if (shouldAutoLookupPrice(facts, normalized)) {
                return null;
            }
            return new ClarificationStep("购买价格", "请提供商品成交价（单位：元），我再为您计算补贴金额。比如：17999元。");
        }
        if ("政策年份".equals(pendingSlot) && !hasPolicyYear(facts, normalized)) {
            return new ClarificationStep("政策年份", "请问您要查询哪一年的政策？例如 2025 年或 2026 年。 ");
        }

        boolean subsidyIntent = isSubsidyCalculationIntent(normalized, facts);
        boolean policyIntent = isPolicyConsultingIntent(normalized, facts);

        if (subsidyIntent) {
            if (!hasUsableCategory(facts, normalized)) {
                return new ClarificationStep("商品类别", "为了准确计算补贴，请先告诉我商品类别（如手机、平板、电视、空调、笔记本等）。");
            }
            if (hasBroadCategoryOnly(facts, normalized)) {
                return new ClarificationStep("商品类别", "您给的是大类信息，请再补充具体品类（如电视/空调/冰箱/手机/平板/笔记本）。");
            }
            if (!hasUsablePrice(facts, normalized)) {
                if (shouldAutoLookupPrice(facts, normalized)) {
                    return null;
                }
                return new ClarificationStep("购买价格", "还差购买价格。请告诉我成交价（单位：元），我即可继续计算。 ");
            }
        }

        if (policyIntent) {
            if (!hasUsableCategory(facts, normalized)) {
                return new ClarificationStep("商品类别", "请问您重点关注哪类产品政策？例如家电、数码、汽车或笔记本电脑。 ");
            }
        }

        return null;
    }

    public boolean isPolicyConsultingIntent(String normalized,
                                             SessionFactCacheService.SessionFacts facts) {
        if (QueryIntentRules.containsAny(normalized, QueryIntentRules.POLICY_CONSULTING_KEYWORDS)) {
            return true;
        }
        return facts != null && facts.getIntentHints().contains("政策咨询");
    }

    public boolean isSubsidyCalculationIntent(String normalized,
                                               SessionFactCacheService.SessionFacts facts) {
        if (QueryIntentRules.containsAny(normalized, QueryIntentRules.SUBSIDY_CALCULATION_KEYWORDS)) {
            return true;
        }
        return facts != null && facts.getIntentHints().contains("补贴测算");
    }

    public boolean hasUsableCategory(SessionFactCacheService.SessionFacts facts, String normalized) {
        if (facts != null && facts.getCategories() != null && !facts.getCategories().isEmpty()) {
            return true;
        }
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.DEVICE_CATEGORIES)
                || QueryIntentRules.containsAny(normalized, QueryIntentRules.BROAD_CATEGORIES);
    }

    public boolean hasUsablePrice(SessionFactCacheService.SessionFacts facts, String normalized) {
        if (facts != null && facts.getLatestPrice() != null && facts.getLatestPrice() > 0) {
            return true;
        }
        return normalized.matches(".*(?:\\d{1,3}(?:[,，]\\d{3})+|\\d{3,6})(?:\\.\\d{1,2})?\\s*(元|rmb|人民币|¥|￥)?.*");
    }

    public boolean hasPolicyYear(SessionFactCacheService.SessionFacts facts, String normalized) {
        if (facts != null && facts.getLatestPolicyYear() != null) {
            return true;
        }
        return normalized.matches(".*20\\d{2}年?.*");
    }

    public boolean hasBroadCategoryOnly(SessionFactCacheService.SessionFacts facts, String normalized) {
        if (facts != null && facts.getCategories() != null && !facts.getCategories().isEmpty()) {
            return facts.getCategories().stream().allMatch(category -> "家电".equals(category) || "数码".equals(category));
        }
        return QueryIntentRules.containsAny(normalized, QueryIntentRules.BROAD_CATEGORIES)
                && !QueryIntentRules.containsAny(normalized, QueryIntentRules.DEVICE_CATEGORIES);
    }

    public boolean shouldAutoLookupPrice(SessionFactCacheService.SessionFacts facts, String normalized) {
        boolean hasDeviceHints = (facts != null
                && facts.getDeviceModels() != null
                && !facts.getDeviceModels().isEmpty())
                || QueryIntentRules.containsAny(normalized, QueryIntentRules.BRAND_MODEL_KEYWORDS)
                || QueryIntentRules.containsAny(normalized,
                "笔记本", "电脑", "手机", "平板", "电视", "空调", "冰箱", "洗衣机");
        boolean hasComputationOrPriceIntent = QueryIntentRules.containsAny(normalized,
                "帮我算", "算一下", "计算", "优惠", "补多少", "多少钱", "价格", "报价", "查", "搜索");
        return hasDeviceHints && hasComputationOrPriceIntent;
    }
}
