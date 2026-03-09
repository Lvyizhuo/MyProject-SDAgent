import React from 'react';
import { Edit, Trash2, Star, Zap } from 'lucide-react';
import './ModelListPanel.css';

const PROVIDER_LABELS = {
    deepseek: 'DeepSeek',
    siliconflow: '硅基流动',
    dashscope: '阿里云百炼',
    zhipuai: '智谱AI',
    moonshot: 'Kimi',
    modelscope: '魔搭社区',
    volcano: '火山引擎',
    custom: '自定义'
};

const ModelListPanel = ({
    typeLabel,
    models,
    loading,
    refreshing,
    error,
    onEdit,
    onDelete,
    onSetDefault,
    onTest,
    testingId
}) => {
    const enabledCount = models.filter((model) => model.isEnabled).length;

    if (loading && models.length === 0) {
        return (
            <div className="model-list-loading">
                <div className="loading-spinner">加载中...</div>
            </div>
        );
    }

    if (error && models.length === 0) {
        return (
            <div className="model-list-error">
                <p>{error}</p>
                <p className="empty-hint">可使用页面顶部刷新按钮重新加载。</p>
            </div>
        );
    }

    return (
        <div className="model-list-panel">
            <div className="model-list-header">
                <div className="model-list-header-copy">
                    <span className="model-list-type-chip">{typeLabel || '模型'}</span>
                    <h3>模型列表</h3>
                    <p>以卡片方式查看当前分类模型，常用操作直接在卡片内完成。</p>
                </div>
                <div className="model-list-header-meta" aria-label="模型统计信息">
                    <span>共 {models.length} 个模型</span>
                    <span>已启用 {enabledCount} 个</span>
                </div>
            </div>

            <div className="model-list-content">
                {models.length === 0 ? (
                    <div className="model-list-empty">
                        <p>当前类型还没有配置模型</p>
                        <p className="empty-hint">先新增一个模型，再测试连通性并设置默认值。</p>
                    </div>
                ) : (
                    <div className="model-card-grid">
                        {models.map((model) => (
                            <article
                                key={model.id}
                                className={`model-card ${model.isDefault ? 'default' : ''}`}
                            >
                                <div className="model-card-top">
                                    <div className="model-name-cell">
                                        <span className="model-name" title={model.name}>{model.name}</span>
                                        {model.isDefault && (
                                            <span className="default-badge">默认模型</span>
                                        )}
                                    </div>
                                </div>

                                <div className="model-card-body">
                                    <div className="model-card-field">
                                        <span className="model-card-label">供应商</span>
                                        <div className="model-card-provider">
                                            <span className="provider-badge">{model.provider}</span>
                                            <span className="provider-label" title={PROVIDER_LABELS[model.provider] || model.provider}>
                                                {PROVIDER_LABELS[model.provider] || model.provider}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="model-card-field">
                                        <span className="model-card-label">启用状态</span>
                                        <span className={`status-badge ${model.isEnabled ? 'enabled' : 'disabled'}`}>
                                            {model.isEnabled ? '已启用' : '未启用'}
                                        </span>
                                    </div>
                                </div>

                                <div className="action-buttons">
                                    <button
                                        className="action-btn"
                                        title="测试连接"
                                        onClick={() => onTest(model.id)}
                                        disabled={testingId === model.id}
                                    >
                                        <Zap size={14} />
                                        {testingId === model.id ? '测试中' : '测试连接'}
                                    </button>
                                    <button
                                        className="action-btn"
                                        title={model.isDefault ? '已是默认' : '设为默认'}
                                        onClick={() => onSetDefault(model.id)}
                                        disabled={model.isDefault}
                                    >
                                        <Star size={14} />
                                        {model.isDefault ? '默认模型' : '设为默认'}
                                    </button>
                                    <button
                                        className="action-btn"
                                        title="编辑"
                                        onClick={() => onEdit(model)}
                                    >
                                        <Edit size={14} />
                                        编辑
                                    </button>
                                    <button
                                        className="action-btn delete"
                                        title="删除"
                                        onClick={() => onDelete(model.id)}
                                    >
                                        <Trash2 size={14} />
                                        删除
                                    </button>
                                </div>
                            </article>
                        ))}
                    </div>
                )}

                {refreshing && (
                    <div className="model-list-refreshing" aria-live="polite">
                        <div className="loading-spinner">更新中...</div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ModelListPanel;
