import React, { useState, useEffect } from 'react';
import { adminApi } from '../services/adminApi';
import AdminNavbar from '../components/admin/AdminNavbar';
import ConfigPanel from '../components/admin/ConfigPanel';
import PreviewPanel from '../components/admin/PreviewPanel';
import KnowledgeBaseTab from '../components/admin/KnowledgeBaseTab';
import ToolsTab from '../components/admin/ToolsTab';
import ModelsTab from '../components/admin/ModelsTab';
import { AdminConsoleProvider } from '../components/admin/AdminConsoleFeedback';
import './AdminConsolePage.css';

const formatDateTime = (value) => {
    if (!value) {
        return '尚未保存';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '时间未知';
    }

    return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
};

const getEnabledSkillCount = (skills) => Object.values(skills || {}).filter((skill) => skill?.enabled).length;

const getBoundModelCount = (config) => [
    config?.llmModelId,
    config?.visionModelId,
    config?.audioModelId,
    config?.embeddingModelId
].filter((value) => value != null).length;

const AdminConsolePage = () => {
    const [activeTab, setActiveTab] = useState('agent');
    const [config, setConfig] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        if (activeTab === 'agent') {
            loadConfig();
        }
    }, [activeTab]);

    const loadConfig = async () => {
        setLoading(true);
        try {
            const data = await adminApi.getAgentConfig();
            setConfig(data);
            setError('');
        } catch (err) {
            setError(err.message || '获取配置失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (newConfig) => {
        try {
            await adminApi.updateAgentConfig(newConfig);
            const latestConfig = await adminApi.getAgentConfig();
            setConfig(latestConfig);
            return { success: true };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    const handleReset = async () => {
        try {
            await adminApi.resetAgentConfig();
            const latestConfig = await adminApi.getAgentConfig();
            setConfig(latestConfig);
            return { success: true };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    const renderContent = () => {
        switch (activeTab) {
            case 'agent':
                if (loading) {
                    return (
                        <div className="admin-tab-loading">
                            <div className="loading-spinner">加载中...</div>
                        </div>
                    );
                }
                if (error && !config) {
                    return (
                        <div className="admin-tab-error">
                            <div className="error-message">
                                <h3>出错了</h3>
                                <p>{error}</p>
                                <button onClick={loadConfig}>重试</button>
                            </div>
                        </div>
                    );
                }
                {
                    const effectiveModelName = config?.effectiveModelName || config?.modelName || '未配置';
                    const enabledSkillCount = getEnabledSkillCount(config?.skills);
                    const boundModelCount = getBoundModelCount(config);
                    const overviewCards = [
                        {
                            label: '当前生效模型',
                            value: effectiveModelName,
                            note: config?.effectiveModelProvider || config?.modelProvider || '待绑定提供商'
                        },
                        {
                            label: '已绑定模型',
                            value: `${boundModelCount}/4`,
                            note: '覆盖 LLM、视觉、语音、嵌入'
                        },
                        {
                            label: '启用技能',
                            value: `${enabledSkillCount} 项`,
                            note: enabledSkillCount > 0 ? '会影响工具调用能力' : '当前未启用扩展技能'
                        },
                        {
                            label: '欢迎语状态',
                            value: config?.greetingMessage?.trim() ? '已配置' : '待完善',
                            note: `最近更新 ${formatDateTime(config?.updatedAt)}`
                        }
                    ];

                    return (
                        <div className="admin-workspace">
                            <section className="admin-overview compact">
                                <div className="admin-overview-copy">
                                    <span className="admin-overview-kicker">智能体配置工作台</span>
                                    <span className="admin-overview-updated">最近更新 {formatDateTime(config?.updatedAt)}</span>
                                </div>
                                <div className="admin-overview-stats">
                                    {overviewCards.map((card) => (
                                        <article key={card.label} className="admin-overview-card">
                                            <span className="admin-overview-card-label">{card.label}</span>
                                            <strong>{card.value}</strong>
                                            <p>{card.note}</p>
                                        </article>
                                    ))}
                                </div>
                            </section>

                            <div className="admin-layout admin-layout-agent">
                                <div className="admin-left-panel">
                                    <ConfigPanel
                                        key={config?.updatedAt || 'agent-config'}
                                        config={config}
                                        onSave={handleSave}
                                        onReset={handleReset}
                                    />
                                </div>
                                <div className="admin-right-panel">
                                    <PreviewPanel config={config} />
                                </div>
                            </div>
                        </div>
                    );
                }
            case 'knowledge':
                return (
                    <div className="admin-layout single-panel">
                        <div className="admin-full-panel">
                            <KnowledgeBaseTab />
                        </div>
                    </div>
                );
            case 'tools':
                return (
                    <div className="admin-layout single-panel">
                        <div className="admin-full-panel">
                            <ToolsTab />
                        </div>
                    </div>
                );
            case 'models':
                return (
                    <div className="admin-layout single-panel">
                        <div className="admin-full-panel">
                            <ModelsTab />
                        </div>
                    </div>
                );
            default:
                return null;
        }
    };

    return (
        <AdminConsoleProvider>
            <div className="admin-console-page">
                <AdminNavbar activeTab={activeTab} onTabChange={setActiveTab} />
                <main className="admin-main-content">
                    {renderContent()}
                </main>
            </div>
        </AdminConsoleProvider>
    );
};

export default AdminConsolePage;
