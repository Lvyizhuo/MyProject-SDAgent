package com.shandong.policyagent.agent;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * 查询意图共享规则引擎。
 * <p>
 * 集中定义所有关键词集合与公共判定方法，避免"同一套规则在 5 处重复实现"导致的规则漂移。
 * 关键词取自 ChatService、ReActPlanningService、ToolIntentClassifier、AgentPlanParser、
 * FastPathService 五处的并集，遵循"宁可多判不可漏判"原则。
 * </p>
 */
public final class QueryIntentRules {

    private QueryIntentRules() {
    }

    // ==================== 关键词集合 ====================

    /**
     * 政策咨询关键词（取 5 处并集）。
     * 用于判断用户问题是否涉及政策话题（含补贴主题识别）。
     */
    public static final Set<String> POLICY_CONSULTING_KEYWORDS = Set.of(
            "政策", "国补", "以旧换新", "流程", "条件", "资格", "标准",
            "申领", "规则", "材料", "怎么办", "申请", "补贴", "依据", "文档", "检索"
    );

    /**
     * 补贴计算关键词（取 5 处并集）。
     * 用于判断用户是否有补贴金额测算意图。
     * 注意："国补能有多少"仅存在于 ReActPlanningService 原实现，已并入公共集。
     */
    public static final Set<String> SUBSIDY_CALCULATION_KEYWORDS = Set.of(
            "计算", "帮我算", "算一下", "补贴后", "到手价", "补贴金额", "补多少",
            "算补贴", "能补多少", "补贴额度", "核算补贴", "国补能有多少",
            "补贴后的价格", "计算补贴", "核算", "国补后",
            "优惠多少", "能优惠多少", "享受多少优惠", "能享受多少优惠",
            "补贴多少", "有多少优惠", "补多少钱"
    );

    /**
     * 价格查询关键词（取 5 处并集）。
     */
    public static final Set<String> PRICE_LOOKUP_KEYWORDS = Set.of(
            "价格", "报价", "多少钱", "市场价", "售价", "价位", "优惠",
            "官网", "电商", "查", "搜索", "参数", "配置", "型号", "款", "对比"
    );

    /**
     * 实时搜索关键词（取 5 处并集）。
     * 触发联网检索的关键词，含时效性词和价格/商品信息词。
     */
    public static final Set<String> REALTIME_SEARCH_KEYWORDS = Set.of(
            "最新", "实时", "今日", "当前", "新闻", "动态",
            "价格", "市场价", "报价", "电商", "官网",
            "搜索", "search", "websearch", "联网", "今天", "股价", "指数"
    );

    /**
     * 商品类别关键词（取 5 处并集）。
     * 用于识别用户提及的具体商品品类。
     */
    public static final Set<String> DEVICE_CATEGORIES = Set.of(
            "手机", "平板", "手表", "手环", "空调", "冰箱", "洗衣机", "电视", "热水器",
            "微波炉", "油烟机", "洗碗机", "燃气灶", "净水器",
            "笔记本", "电脑", "汽车", "新能源车"
    );

    /**
     * 仅含大类（非具体品类）的关键词，用于判断用户是否只给了模糊分类。
     */
    public static final Set<String> BROAD_CATEGORIES = Set.of("家电", "数码");

    /**
     * 品牌/型号关键词（取 5 处并集）。
     */
    public static final Set<String> BRAND_MODEL_KEYWORDS = Set.of(
            "macbook", "thinkpad", "surface", "matebook",
            "iphone", "ipad", "华为", "小米", "荣耀", "oppo", "vivo",
            "三星", "联想", "戴尔", "惠普", "xiaomi", "redmi"
    );

    /**
     * 地图/导航关键词（取 5 处并集）。
     */
    public static final Set<String> MAP_KEYWORDS = Set.of(
            "地图", "导航", "路线", "门店", "附近", "回收点", "高德", "amap"
    );

    /**
     * 文件解析关键词（取 5 处并集）。
     */
    public static final Set<String> FILE_PARSE_KEYWORDS = Set.of(
            "发票", "附件", "上传", "图片", "文件", "pdf", "jpg", "png"
    );

    // ==================== 公共工具方法 ====================

    /**
     * 归一化文本：trim + 小写。
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断文本是否包含任意一个关键词（varargs 版本）。
     */
    public static boolean containsAny(String text, String... keywords) {
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

    /**
     * 判断文本是否包含集合中的任意一个关键词。
     */
    public static boolean containsAny(String text, Collection<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ==================== 公共场景判定方法 ====================

    /**
     * 政策+价格混合问法判定（ChatService 和 ReActPlanningService 共用，逻辑完全一致）。
     */
    public static boolean isPolicyPriceMixed(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        boolean hasPolicy = containsAny(normalized,
                "政策", "补贴", "国补", "以旧换新", "申领", "流程", "条件", "资格", "标准", "规则");
        boolean hasPrice = containsAny(normalized,
                "价格", "报价", "市场价", "多少钱", "优惠", "到手价", "售价", "价位", "价");
        return hasPolicy && hasPrice;
    }
}
