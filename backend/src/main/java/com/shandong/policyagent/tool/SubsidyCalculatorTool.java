package com.shandong.policyagent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SubsidyCalculatorTool {

    private static final BigDecimal HOME_APPLIANCE_SUBSIDY_RATE = new BigDecimal("0.15");
    private static final BigDecimal HOME_APPLIANCE_MAX_SUBSIDY = new BigDecimal("2000");
    
    private static final BigDecimal PHONE_MAX_SUBSIDY = new BigDecimal("500");
    private static final BigDecimal TABLET_MAX_SUBSIDY = new BigDecimal("500");
    private static final BigDecimal SMARTWATCH_MAX_SUBSIDY = new BigDecimal("200");

    private static final Map<String, BigDecimal> APPLIANCE_MAX_SUBSIDY = Map.of(
            "空调", new BigDecimal("2000"),
            "冰箱", new BigDecimal("2000"),
            "洗衣机", new BigDecimal("2000"),
            "电视", new BigDecimal("2000"),
            "热水器", new BigDecimal("1000"),
            "微波炉", new BigDecimal("500"),
            "油烟机", new BigDecimal("1000"),
            "洗碗机", new BigDecimal("1000"),
            "燃气灶", new BigDecimal("500"),
            "净水器", new BigDecimal("500")
    );

    private final ToolStateManager toolStateManager;

    public record SubsidyRequest(String type, double price) {}
    
    public record SubsidyResponse(
            String type,
            double purchasePrice,
            double subsidyRate,
            double calculatedSubsidy,
            double maxSubsidy,
            double actualSubsidy,
            String summary
    ) {}

    @Bean
    @Description("""
            工具用途：计算山东省以旧换新补贴金额。
            必填参数：
            - type: 明确商品类型（如 手机/平板/空调/冰箱/洗衣机/电视/热水器/微波炉/油烟机/洗碗机/燃气灶/净水器/智能手表/手环）
            - price: 明确成交价（数字，单位元）
            补贴规则：默认按 15% 计算，不同品类有封顶上限。
            调用时机：仅当“商品类型 + 数字价格”都明确时调用。
            使用约束：
            - 参数缺失必须先追问，不得猜测补全。
            - 价格模糊（如“几千左右”）或类型模糊（如“某款家电”）时禁止调用。
            """)
    public Function<SubsidyRequest, SubsidyResponse> calculateSubsidy() {
        return request -> {
            // 检查技能是否启用
            if (!toolStateManager.isSubsidyCalculatorEnabled()) {
                log.warn("补贴计算工具已被管理员禁用");
                return new SubsidyResponse(
                        request != null ? request.type() : "",
                        request != null ? request.price() : 0,
                        0, 0, 0, 0,
                        "补贴计算功能当前已禁用，请联系管理员启用"
                );
            }

            log.info("计算补贴 | 类型={} | 价格={}", request.type(), request.price());
            
            if (request.price() <= 0) {
                return new SubsidyResponse(
                        request.type(),
                        request.price(),
                        0.15,
                        0,
                        0,
                        0,
                        "购买价格必须大于0元"
                );
            }

            BigDecimal price = BigDecimal.valueOf(request.price());
            BigDecimal rate = HOME_APPLIANCE_SUBSIDY_RATE;
            BigDecimal maxSubsidy = getMaxSubsidy(request.type());
            
            BigDecimal calculatedSubsidy = price.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualSubsidy = calculatedSubsidy.min(maxSubsidy);
            
            String summary = String.format(
                    "【%s以旧换新补贴】购买价格%.2f元，补贴比例15%%，计算补贴%.2f元，补贴上限%.2f元，实际可获补贴%.2f元",
                    request.type(),
                    request.price(),
                    calculatedSubsidy.doubleValue(),
                    maxSubsidy.doubleValue(),
                    actualSubsidy.doubleValue()
            );
            
            log.info("补贴计算完成 | 实际补贴={}", actualSubsidy);
            
            return new SubsidyResponse(
                    request.type(),
                    request.price(),
                    0.15,
                    calculatedSubsidy.doubleValue(),
                    maxSubsidy.doubleValue(),
                    actualSubsidy.doubleValue(),
                    summary
            );
        };
    }

    private BigDecimal getMaxSubsidy(String type) {
        if (type == null) {
            return HOME_APPLIANCE_MAX_SUBSIDY;
        }
        
        return switch (type) {
            case "手机" -> PHONE_MAX_SUBSIDY;
            case "平板", "平板电脑" -> TABLET_MAX_SUBSIDY;
            case "智能手表", "手环", "智能手环" -> SMARTWATCH_MAX_SUBSIDY;
            default -> APPLIANCE_MAX_SUBSIDY.getOrDefault(type, HOME_APPLIANCE_MAX_SUBSIDY);
        };
    }
}
