import React, { useEffect, useMemo, useState } from 'react';
import {
    MessageSquare,
    Calculator,
    FileSearch,
    BarChart3,
    Mic,
    Plus,
    Search
} from 'lucide-react';
import { adminAgentApi } from '../../services/adminAgentApi';
import './admin-card.css';

const SECTION_TABS = [
    { id: 'all', label: '全部' },
    { id: 'chat', label: '对话型' },
    { id: 'workflow', label: '工作流' },
    { id: 'support', label: '客服型' }
];

const STATUS_FILTERS = [
    { id: 'all', label: '全部状态' },
    { id: 'published', label: '● 已发布' },
    { id: 'draft', label: '● 草稿' }
];

const ICON_MAP = {
    MessageSquare,
    Calculator,
    FileSearch,
    BarChart3,
    Mic
};

const ICON_TONE_MAP = {
    MessageSquare: '',
    Calculator: 'tone-success',
    FileSearch: 'tone-warning',
    BarChart3: 'tone-secondary',
    Mic: 'tone-accent'
};

const AgentsTab = () => {
    const [agents, setAgents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [statusFilter, setStatusFilter] = useState('all');
    const [search, setSearch] = useState('');

    useEffect(() => {
        loadAgents();
    }, []);

    const loadAgents = async () => {
        setLoading(true);
        try {
            const data = await adminAgentApi.listAgents();
            setAgents(Array.isArray(data) ? data : []);
            setError('');
        } catch (err) {
            setError(err.message || '加载智能体失败');
            setAgents([]);
        } finally {
            setLoading(false);
        }
    };

    const filtered = useMemo(() => {
        return agents.filter((agent) => {
            if (sectionTab !== 'all' && agent.category !== sectionTab) {
                return false;
            }
            if (statusFilter !== 'all' && agent.status !== statusFilter) {
                return false;
            }
            if (search) {
                const kw = search.toLowerCase();
                return (
                    (agent.name || '').toLowerCase().includes(kw) ||
                    (agent.code || '').toLowerCase().includes(kw) ||
                    (agent.description || '').toLowerCase().includes(kw)
                );
            }
            return true;
        });
    }, [agents, sectionTab, statusFilter, search]);

    const counts = useMemo(() => {
        const published = agents.filter((a) => a.status === 'published').length;
        const draft = agents.filter((a) => a.status === 'draft').length;
        return { published, draft };
    }, [agents]);

    const handleToggleDefault = async (agent, e) => {
        e.stopPropagation();
        try {
            await adminAgentApi.setDefault(agent.id);
            await loadAgents();
        } catch (err) {
            setError(err.message || '设置默认失败');
        }
    };

    const handleDelete = async (agent, e) => {
        e.stopPropagation();
        if (!window.confirm(`确认删除「${agent.name}」？`)) {
            return;
        }
        try {
            await adminAgentApi.deleteAgent(agent.id);
            await loadAgents();
        } catch (err) {
            setError(err.message || '删除失败');
        }
    };

    const handleDuplicate = async (agent, e) => {
        e.stopPropagation();
        try {
            await adminAgentApi.duplicateAgent(agent.id);
            await loadAgents();
        } catch (err) {
            setError(err.message || '复制失败');
        }
    };

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">智能体</h2>
                <span className="admin-card-section-sub">Agents · 多个独立智能体，每个绑定不同模型</span>
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
                        placeholder="搜索智能体..."
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
                        {f.id === 'published' && counts.published > 0 && <span> {counts.published}</span>}
                        {f.id === 'draft' && counts.draft > 0 && <span> {counts.draft}</span>}
                    </button>
                ))}
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((agent) => {
                    const Icon = ICON_MAP[agent.icon] || MessageSquare;
                    const tone = ICON_TONE_MAP[agent.icon] || '';
                    return (
                        <div className="admin-card" key={agent.id} onClick={() => alert(`跳转到「${agent.name}」编辑页（待实现）`)}>
                            <div className="admin-card-head-row">
                                <div className="admin-card-icon-row">
                                    <div className={`admin-card-icon ${tone}`}>
                                        <Icon size={16} strokeWidth={2.2} />
                                    </div>
                                    <div className="admin-card-title-wrap">
                                        <h3 className="admin-card-title">{agent.name}</h3>
                                        <span className="admin-card-subtitle">{agent.code}</span>
                                    </div>
                                </div>
                                {agent.isDefault && <span className="admin-card-badge primary">默认</span>}
                                {!agent.isDefault && agent.categoryLabel && (
                                    <span className={`admin-card-badge ${agent.categoryTone || 'muted'}`}>
                                        {agent.categoryLabel}
                                    </span>
                                )}
                            </div>
                            <p className="admin-card-desc">{agent.description}</p>
                            {agent.bindings && agent.bindings.length > 0 && (
                                <div className="admin-card-models">
                                    {agent.bindings.map((b) => (
                                        <React.Fragment key={b.role}>
                                            <span className="admin-card-models-label">{b.role}</span>
                                            <span className="admin-card-model-chip">{b.modelName}</span>
                                        </React.Fragment>
                                    ))}
                                </div>
                            )}
                            <div className="admin-card-meta">
                                {agent.status === 'published' ? (
                                    <span className="live">运行中</span>
                                ) : (
                                    <span className="draft">草稿</span>
                                )}
                                {agent.dailyCalls > 0 && (
                                    <>
                                        <span className="dot" />
                                        <span><strong>{agent.dailyCalls.toLocaleString('zh-CN')}</strong> 次/日</span>
                                    </>
                                )}
                                {agent.version && (
                                    <>
                                        <span className="dot" />
                                        <span>v{agent.version} · {agent.updatedAtLabel}</span>
                                    </>
                                )}
                            </div>
                            <div className="admin-card-actions">
                                <button
                                    className="admin-card-action primary"
                                    onClick={(e) => { e.stopPropagation(); alert(`编辑「${agent.name}」`); }}
                                >
                                    编辑
                                </button>
                                {!agent.isDefault && (
                                    <button
                                        className="admin-card-action"
                                        onClick={(e) => handleToggleDefault(agent, e)}
                                    >
                                        设为默认
                                    </button>
                                )}
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => handleDuplicate(agent, e)}
                                >
                                    复制
                                </button>
                                {!agent.isDefault && (
                                    <button
                                        className="admin-card-action danger"
                                        onClick={(e) => handleDelete(agent, e)}
                                    >
                                        删除
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={() => alert('新建智能体（待实现）')}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>新建智能体</div>
                </div>
            </div>
        </section>
    );
};

export default AgentsTab;
