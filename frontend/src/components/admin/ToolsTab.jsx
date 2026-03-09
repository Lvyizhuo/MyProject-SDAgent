import React, { useMemo, useState } from 'react';
import { Bot, Link2, ShieldCheck, Wrench, ChevronRight, Sparkles, Workflow, PlugZap } from 'lucide-react';
import './ToolsTab.css';

const TOOL_SECTIONS = [
    {
        id: 'orchestration',
        label: '统一编排',
        eyebrow: 'Phase 01',
        icon: Workflow,
        title: '统一工具编排',
        description: '按场景集中管理补贴计算、文件解析、联网搜索等工具，统一启停、路由和失败回退策略。',
        highlights: ['按业务场景启用工具组合', '为不同工具设定调用优先级', '让工具失败提示和降级路径保持一致']
    },
    {
        id: 'integration',
        label: '服务接入',
        eyebrow: 'Phase 02',
        icon: PlugZap,
        title: '外部服务接入',
        description: '集中维护第三方接口地址、鉴权方式与连通性检测，减少配置分散导致的排障成本。',
        highlights: ['统一保存 API 地址和密钥', '提供服务可用性测试入口', '把不同来源的接入方式汇总到同一处']
    },
    {
        id: 'governance',
        label: '反馈治理',
        eyebrow: 'Phase 03',
        icon: ShieldCheck,
        title: '运行反馈闭环',
        description: '把调用日志、失败策略、权限控制和风险提示沉淀到同一套治理面板，保证管理员的操作结果更可追踪。',
        highlights: ['沉淀工具调用日志和错误上下文', '为高风险工具增加权限控制', '统一展示执行结果与异常提醒']
    }
];

const ToolsTab = () => {
    const [activeSectionId, setActiveSectionId] = useState(TOOL_SECTIONS[0].id);

    const activeSection = useMemo(
        () => TOOL_SECTIONS.find((section) => section.id === activeSectionId) || TOOL_SECTIONS[0],
        [activeSectionId]
    );

    const ActiveIcon = activeSection.icon;

    return (
        <div className="tools-tab">
            <div className="tools-header">
                <div className="tools-header-main">
                    <span className="tools-eyebrow">工具中心</span>
                    <h2>工具管理</h2>
                    <p>当前先统一管理信息架构和规划态展示，后续工具接入会延续知识库与模型页相同的浏览方式、提示反馈和操作节奏。</p>
                </div>
                <div className="tools-header-badge">
                    <Sparkles size={16} />
                    <span>规划中</span>
                </div>
            </div>

            <div className="tools-content">
                <aside className="tools-sidebar">
                    <div className="tools-sidebar-header">
                        <h3>规划模块</h3>
                    </div>
                    <div className="tools-nav-list">
                        {TOOL_SECTIONS.map((section) => {
                            const SectionIcon = section.icon;
                            const isActive = section.id === activeSectionId;
                            return (
                                <button
                                    key={section.id}
                                    type="button"
                                    className={`tools-nav-item ${isActive ? 'active' : ''}`}
                                    onClick={() => setActiveSectionId(section.id)}
                                >
                                    <div className="tools-nav-copy">
                                        <span>{section.label}</span>
                                        <small>{section.eyebrow}</small>
                                    </div>
                                    <SectionIcon size={16} />
                                </button>
                            );
                        })}
                    </div>
                </aside>

                <section className="tools-main">
                    <div className="tools-main-header">
                        <div>
                            <div className="tools-main-title-row">
                                <ActiveIcon size={18} />
                                <h3>{activeSection.title}</h3>
                            </div>
                            <p>{activeSection.description}</p>
                        </div>
                        <div className="tools-main-meta">
                            <span><Wrench size={14} /> 待接入配置面板</span>
                            <span><ChevronRight size={14} /> 统一右下提示已可复用</span>
                        </div>
                    </div>

                    <div className="tools-panels">
                        <article className="tools-focus-card hero">
                            <div className="tools-focus-icon">
                                <ActiveIcon size={24} />
                            </div>
                            <div>
                                <span className="tools-card-eyebrow">{activeSection.eyebrow}</span>
                                <h4>{activeSection.title}</h4>
                                <p>{activeSection.description}</p>
                            </div>
                        </article>

                        <div className="tools-grid">
                            {activeSection.highlights.map((item) => (
                                <article key={item} className="tools-focus-card">
                                    <Bot size={18} />
                                    <h4>{item}</h4>
                                    <p>该能力会沿用管理员控制台统一的弹层、通知和信息密度，避免每个模块各自形成一套交互习惯。</p>
                                </article>
                            ))}
                            <article className="tools-focus-card muted">
                                <Link2 size={18} />
                                <h4>下一步接入真实配置</h4>
                                <p>后续这里会补充工具开关、参数表单、连接测试和执行记录，不再新增另一种页面骨架。</p>
                            </article>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default ToolsTab;
