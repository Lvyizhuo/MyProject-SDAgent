package com.shandong.policyagent.support.logging;

import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;

import java.io.File;

/**
 * 纯按容量触发的滚动策略（不含"按天"维度）。
 *
 * <p>为什么需要它：Spring Boot 内置的 {@code SizeAndTimeBasedRollingPolicy} 强制要求
 * {@code fileNamePattern} 里带 {@code %d} 日期占位符，而 {@code %d} 取的是**滚动触发那一刻**
 * 的日期 —— 也就是 11-11 00:00 滚动时，归档文件名会是 {@code 2026-11-11}，
 * 但装进去的是 11-10 的内容，文件名与内容错位一天。
 *
 * <p>本项目的日志文件名带日期（{@code backend-<日期>.log}），日期由启动时注入的
 * {@code LOG_DATE} 决定（见 {@code logback-spring.xml}）。若再叠加 logback 的按天滚动，
 * 就会出现"文件名日期 ≠ 内容日期"的歧义。
 *
 * <p>因此这里改用：
 * <ul>
 *   <li>{@code FixedWindowRollingPolicy} —— 不要求日期占位符，只按序号切分；</li>
 *   <li>本类作为触发策略 —— 只看文件大小，不做按天判定。</li>
 * </ul>
 * 于是行为变得完全可预期：
 * <ul>
 *   <li>活动文件 {@code backend-<启动日>.log}，跨天不会改名，内容不会错位；</li>
 *   <li>超过 max-file-size（默认 100MB）才切分为 {@code .1.log / .2.log ...}；</li>
 *   <li>按天切换靠"进程重启"实现（重启即用新日期命名），开发环境与每日发布的容器都适用。</li>
 * </ul>
 *
 * @param <E> 日志事件类型
 */
public class SizeOnlyTriggeringPolicy<E> extends SizeBasedTriggeringPolicy<E> {

    @Override
    public boolean isTriggeringEvent(final File activeFile, final E event) {
        return activeFile != null && activeFile.length() >= getMaxFileSize().getSize();
    }
}
