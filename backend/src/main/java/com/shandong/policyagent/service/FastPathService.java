package com.shandong.policyagent.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class FastPathService {

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

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}