package com.shandong.policyagent.tool;

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
    @Description("计算山东省以旧换新补贴金额。输入商品类型(type)和购买价格(price)，返回可获得的补贴金额。支持的商品类型包括：空调、冰箱、洗衣机、电视、热水器、微波炉、油烟机、洗碗机、燃气灶、净水器、手机、平板、智能手表、手环。补贴比例为15%，不同品类有不同的补贴上限。")
    public Function<SubsidyRequest, SubsidyResponse> calculateSubsidy() {
        return request -> {
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
