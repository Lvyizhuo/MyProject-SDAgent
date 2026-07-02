import React, { useEffect, useMemo, useState } from 'react';
import {
    Cpu,
    Eye,
    Mic,
    BarChart3,
    Server,
    Plus,
    Search
} from 'lucide-react';
import { adminModelApi } from '../../services/adminModelApi';
import './admin-card.css';

const SECTION_TABS = [
    { id: 'all', label: '全部' },
    { id: 'LLM', label: 'LLM' },
    { id: 'VISION', label: '视觉' },
    { id: 'AUDIO', label: '语音' },
    { id: 'RERANK', label: '重排序' },
    { id: 'EMBEDDING', label: '嵌入' }
];

const STATUS_FILTERS = [
    { id: 'all', label: '全部' },
    { id: 'default', label: '● 默认' },
    { id: 'enabled', label: '● 已启用' },
    { id: 'disabled', label: '● 已停用' }
];

const ICON_MAP = {
    Cpu,
    Eye,
    Mic,
    BarChart3,
    Server
};

const ICON_TONE_MAP = {
    Cpu: '',
    Eye: 'tone-accent',
    Mic: 'tone-warning',
    BarChart3: 'tone-success',
    Server: 'tone-secondary'
};

const STATUS_TONE = {
    ok: { tone: 'ok', label: '连通正常' },
    warn: { tone: 'warn', label: '响应较慢' },
    err: { tone: 'err', label: '本地离线' }
};

const ModelsTab = () => {
    const [models, setModels] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [statusFilter, setStatusFilter] = useState('all');
    const [search, setSearch] = useState('');

    useEffect(() => {
        loadModels();
    }, []);

    const loadModels = async () => {
        setLoading(true);
        try {
            const data = await adminModelApi.listModels();
            setModels(Array.isArray(data) ? data : []);
            setError('');
        } catch (err) {
            setError(err.message || '加载模型失败');
            setModels([]);
        } finally {
            setLoading(false);
        }
    };

    const handleTest = async (model, e) => {
        e.stopPropagation();
        try {
            const result = await adminModelApi.testConnection(model.id);
            setModels((prev) =>
                prev.map((m) => (m.id === model.id ? { ...m, status: result.status, latencyMs: result.latencyMs } : m))
            );
            alert(result.message || '测试完成');
        } catch (err) {
            alert(`测试失败：${err.message}`);
        }
    };

    const handleSetDefault = async (model, e) => {
        e.stopPropagation();
        try {
            await adminModelApi.setDefault(model.id);
            await loadModels();
        } catch (err) {
            setError(err.message || '设置默认失败');
        }
    };

    const counts = useMemo(() => {
        const isDefault = models.filter((m) => m.isDefault).length;
        const enabled = models.filter((m) => m.enabled).length;
        const disabled = models.filter((m) => !m.enabled).length;
        return { isDefault, enabled, disabled };
    }, [models]);

    const filtered = useMemo(() => {
        return models.filter((m) => {
            if (sectionTab !== 'all' && m.modelType !== sectionTab) return false;
            if (statusFilter === 'default' && !m.isDefault) return false;
            if (statusFilter === 'enabled' && !m.enabled) return false;
            if (statusFilter === 'disabled' && m.enabled) return false;
            if (search) {
                const kw = search.toLowerCase();
                return (
                    (m.name || '').toLowerCase().includes(kw) ||
                    (m.modelKey || '').toLowerCase().includes(kw) ||
                    (m.provider || '').toLowerCase().includes(kw)
                );
            }
            return true;
        });
    }, [models, sectionTab, statusFilter, search]);

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">模型</h2>
                <span className="admin-card-section-sub">Models · LLM / 视觉 / 语音 / 重排序 / 嵌入</span>
                <nav className="admin-card-section-tabs">
                    {SECTION_TABS.map((tab) => (
                        <button
                            key={tab.id}
                            className={`admin-card-section-tab ${sectionTab === tab.id ? 'active' : ''}`}
                            onClick={() => setSectionTab(tab.id)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </nav>
                <div className="admin-card-section-search">
                    <Search size={14} />
                    <input
                        placeholder="搜索模型..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
            </div>

            <div className="admin-card-filter-bar">
                {STATUS_FILTERS.map((f) => (
                    <button
                        key={f.id}
                        className={`admin-card-filter-pill ${statusFilter === f.id ? 'active' : ''}`}
                        onClick={() => setStatusFilter(f.id)}
                    >
                        {f.label}
                        {f.id === 'default' && counts.isDefault > 0 && <span> {counts.isDefault}</span>}
                        {f.id === 'enabled' && counts.enabled > 0 && <span> {counts.enabled}</span>}
                        {f.id === 'disabled' && counts.disabled > 0 && <span> {counts.disabled}</span>}
                    </button>
                ))}
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((model) => {
                    const Icon = ICON_MAP[model.icon] || Cpu;
                    const tone = ICON_TONE_MAP[model.icon] || '';
                    const status = STATUS_TONE[model.status] || STATUS_TONE.ok;
                    const typeBadge = {
                        LLM: 'primary',
                        VISION: 'accent',
                        AUDIO: 'warning',
                        RERANK: 'secondary',
                        EMBEDDING: 'success'
                    }[model.modelType] || 'muted';
                    return (
                        <div className="admin-card" key={model.id} onClick={() => alert(`编辑模型「${model.name}」`)}>
                            <div className="admin-card-head-row">
                                <div className="admin-card-icon-row">
                                    <div className={`admin-card-icon ${tone}`}>
                                        <Icon size={16} strokeWidth={2.2} />
                                    </div>
                                    <div className="admin-card-title-wrap">
                                        <h3 className="admin-card-title">{model.name}</h3>
                                        <span className="admin-card-subtitle">
                                            {model.provider} · {model.modelKey}
                                        </span>
                                    </div>
                                </div>
                                {model.isDefault ? (
                                    <span className="admin-card-badge primary">默认</span>
                                ) : (
                                    <span className={`admin-card-badge ${typeBadge}`}>{model.modelTypeLabel || model.modelType}</span>
                                )}
                            </div>
                            <p className="admin-card-desc">{model.description || '点击编辑模型参数与端点配置'}</p>
                            <div className="admin-card-meta">
                                <span className={`admin-card-test-dot ${status.tone}`}>{status.label}</span>
                                {model.latencyMs != null && (
                                    <>
                                        <span className="dot" />
                                        <span>avg <strong>{model.latencyMs}ms</strong></span>
                                    </>
                                )}
                                {model.usedByAgentCount > 0 && (
                                    <>
                                        <span className="dot" />
                                        <span><strong>{model.usedByAgentCount}</strong> 个 agent</span>
                                    </>
                                )}
                            </div>
                            <div className="admin-card-actions">
                                <button
                                    className="admin-card-action primary"
                                    onClick={(e) => { e.stopPropagation(); alert(`编辑「${model.name}」`); }}
                                >
                                    编辑
                                </button>
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => handleTest(model, e)}
                                >
                                    测试
                                </button>
                                {!model.isDefault && model.enabled && (
                                    <button
                                        className="admin-card-action"
                                        onClick={(e) => handleSetDefault(model, e)}
                                    >
                                        设为默认
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={() => alert('接入新模型（待实现）')}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>接入新模型</div>
                </div>
            </div>
        </section>
    );
};

export default ModelsTab;
