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
     * <p>
     * A11 优化：已移除"当前"——政策咨询类问题（如"当前政策"）不应误触发强制联网搜索；
     * 价格类实时诉求仍由"价格/报价/市场价"等词覆盖。
     * </p>
     */
    public static final Set<String> REALTIME_SEARCH_KEYWORDS = Set.of(
            "最新", "实时", "今日", "新闻", "动态",
            "价格", "市场价", "报价", "电商", "官网",
            "搜索", "search", "websearch", "联网", "今天", "股价", "指数"
    );

    /**
     * 显式联网请求关键词。
     *
     * <p>用户明确说"联网搜索/上网查/搜一下"时，这是<b>最高优先级意图</b>，
     * 应当短路"政策+价格混合问法"的保守策略（先给政策结论、暂不联网）。</p>
     *
     * <p>背景：手机/电脑等商品的"标准版/Pro/Max"等型号后缀会命中政策关键词"标准"，
     * 导致"iPhone17标准版256GB 价格"被误判为混合问法，联网搜索被静默跳过。</p>
     */
    public static final Set<String> EXPLICIT_SEARCH_KEYWORDS = Set.of(
            "联网搜索", "联网查", "上网查", "上网搜", "网上搜", "网上查",
            "搜一下", "搜索一下", "查一下最新", "帮我搜", "帮我查"
    );

    /**
     * 政策语境关键词（强信号）。
     *
     * <p>与 POLICY_CONSULTING_KEYWORDS 的区别：这里只保留"几乎不可能出现在商品名里"的词。
     * "标准/条件/流程/资格/规则"等词在商品型号语境中极常见（如"iPhone17标准版"），
     * 单独用它们判定政策意图会产生大量误判，因此只在混合问法判定中降级处理。</p>
     */
    public static final Set<String> STRONG_POLICY_KEYWORDS = Set.of(
            "政策", "补贴", "国补", "以旧换新", "申领"
    );

    /**
     * 政策语境关键词（弱信号）。
     *
     * <p>必须与 STRONG_POLICY_KEYWORDS 同时出现才算政策意图，
     * 避免"标准版/配置标准"这类商品描述被当成政策问题。</p>
     */
    public static final Set<String> WEAK_POLICY_KEYWORDS = Set.of(
            "流程", "条件", "资格", "标准", "规则"
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
     *
     * <p><b>误判修复</b>：原实现把"标准/条件/流程/资格/规则"直接当政策关键词，
     * 而这些词在商品型号语境中极常见——"iPhone17标准版256GB 价格"里的"标准"是
     * 型号后缀而非政策口径，导致该问法被判成混合问法，联网搜索被静默跳过。
     * 现改为：弱政策词必须与强政策词（政策/补贴/国补/以旧换新/申领）共现才算政策意图。</p>
     */
    public static boolean isPolicyPriceMixed(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        boolean hasPolicy = containsAny(normalized, STRONG_POLICY_KEYWORDS)
                || (containsAny(normalized, WEAK_POLICY_KEYWORDS)
                    && containsAny(normalized, STRONG_POLICY_KEYWORDS));
        boolean hasPrice = containsAny(normalized,
                "价格", "报价", "市场价", "多少钱", "优惠", "到手价", "售价", "价位", "价");
        return hasPolicy && hasPrice;
    }

    /**
     * 是否为用户显式要求的联网检索。
     *
     * <p>用户明确说"联网搜索/上网查/搜一下"时，联网检索是用户的显式指令，
     * 优先级高于"政策+价格混合问法"的保守策略（先给政策结论、按需再联网）。</p>
     */
    public static boolean isExplicitSearchRequest(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized, EXPLICIT_SEARCH_KEYWORDS);
    }
}
