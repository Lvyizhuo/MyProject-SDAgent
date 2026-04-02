package com.shandong.policyagent.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FastPathService {

    private static final Pattern CITY_PATTERN = Pattern.compile("([\\p{IsHan}]{2,8}市)");

    public String tryDirectAnswer(String rawQuestion, SessionFactCacheService.SessionFacts facts) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return null;
        }

        String normalized = normalize(rawQuestion);
        if (isGreeting(normalized)) {
            return "您好，我是山东省以旧换新政策咨询助手。您可以直接告诉我商品类型、购买价格和所在地市，我可以帮您估算补贴并说明申领流程。";
        }

        if (isCapabilityQuestion(normalized)) {
            return "我可以帮您做三类事情：\n"
                    + "1. 解释山东省及各地市以旧换新补贴政策\n"
                    + "2. 根据商品类型和价格估算补贴金额\n"
                    + "3. 提供申请条件、所需材料和办理步骤\n\n"
                    + "如果您愿意，现在就可以发我：商品类型 + 购买价格 + 所在地市。";
        }

        if (isSubsidyRateQuestion(normalized)) {
            return "当前山东家电与数码以旧换新常见补贴比例一般按成交价 15% 估算，不同品类会有补贴上限。"
                    + "\n您可以告诉我具体商品类型和价格，我马上按规则帮您测算可享补贴。";
        }

        if (isApplicationMaterialsQuestion(normalized)) {
            return "常见申领材料包括：身份证明、购买发票/订单、支付凭证、旧机回收或交售凭证（按活动要求）、收款账户信息。"
                    + "\n不同地市活动会有细节差异，我可以按您所在城市给出更精确的材料清单。";
        }

        if (isApplicationProcessQuestion(normalized)) {
            return "一般流程是：确认资格和品类 -> 在活动渠道下单并完成支付 -> 提交发票与回收等材料 -> 审核通过后发放补贴。"
                    + "\n把您的城市和商品类型告诉我，我可以给您对应渠道和操作要点。";
        }

        if (isArrivalTimeQuestion(normalized)) {
            return "补贴到账时间通常取决于活动平台与审核进度，常见为审核通过后几个工作日到数周内。"
                    + "\n如果您告诉我申领渠道（平台/门店）和城市，我可以给您更具体的时效参考与催办建议。";
        }

        if (isLocalSubsidyQuestion(normalized)) {
            String city = resolveCityHint(facts, rawQuestion);
            if (city == null || city.isBlank()) {
                return null;
            }
            return city + "通常会结合省级政策开展本地补贴活动，但具体品类、名额和时间以当地最新公告为准。"
                    + "\n您可以继续告诉我商品类型和预算，我会按该城市口径帮您判断是否适用。";
        }

        return null;
    }

    private boolean isGreeting(String normalized) {
        return normalized.matches("^(你好|您好|嗨|hello|hi|在吗|有人吗)[!！。,.?？ ]*$");
    }

    private boolean isCapabilityQuestion(String normalized) {
        return normalized.contains("你能做什么")
                || normalized.contains("你会什么")
                || normalized.contains("怎么用")
                || normalized.contains("可以帮我")
                || normalized.contains("功能");
    }

    private boolean isSubsidyRateQuestion(String normalized) {
        return containsAny(normalized, "补贴比例", "补贴几个点", "补贴多少比例", "按多少补贴", "国补比例");
    }

    private boolean isApplicationMaterialsQuestion(String normalized) {
        return containsAny(normalized, "申请材料", "需要什么材料", "要准备什么", "提交什么资料", "要哪些资料");
    }

    private boolean isApplicationProcessQuestion(String normalized) {
        return containsAny(normalized, "申请流程", "怎么办理", "怎么申请", "怎么操作", "申领流程", "领取流程");
    }

    private boolean isArrivalTimeQuestion(String normalized) {
        return containsAny(normalized, "多久到账", "多久到", "多久能到账", "什么时候到账", "发放时间", "补贴到账");
    }

    private boolean isLocalSubsidyQuestion(String normalized) {
        return containsAny(normalized,
                "本地有补贴吗", "当地有补贴吗", "我们这有补贴吗", "这个城市有补贴吗", "有地方补贴吗", "市补贴");
    }

    private String resolveCityHint(SessionFactCacheService.SessionFacts facts, String rawQuestion) {
        if (facts != null && facts.getRegions() != null && !facts.getRegions().isEmpty()) {
            String region = facts.getRegions().iterator().next();
            if (region != null && region.endsWith("市")) {
                return region;
            }
        }

        if (rawQuestion != null && !rawQuestion.isBlank()) {
            Matcher matcher = CITY_PATTERN.matcher(rawQuestion);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}