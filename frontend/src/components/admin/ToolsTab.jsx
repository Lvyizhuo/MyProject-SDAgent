import React, { useEffect, useMemo, useState } from 'react';
import {
    Globe,
    Calculator,
    FileText,
    Code2,
    Plus,
    Search
} from 'lucide-react';
import { adminToolApi } from '../../services/adminToolApi';
import './admin-card.css';

const SECTION_TABS = [
    { id: 'all', label: '全部' },
    { id: 'web', label: '联网' },
    { id: 'calc', label: '计算' },
    { id: 'parse', label: '解析' },
    { id: 'external', label: '外部 API' }
];

const STATUS_FILTERS = [
    { id: 'all', label: '全部状态' },
    { id: 'enabled', label: '● 已启用' },
    { id: 'disabled', label: '● 已停用' }
];

const ICON_MAP = {
    Globe,
    Calculator,
    FileText,
    Code2
};

const ICON_TONE_MAP = {
    Globe: 'tone-secondary',
    Calculator: 'tone-success',
    FileText: 'tone-accent',
    Code2: 'tone-warning'
};

const CATEGORY_MAP = {
    web: 'web',
    calc: 'calc',
    parse: 'parse',
    external: 'external'
};

const ToolsTab = () => {
    const [tools, setTools] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [statusFilter, setStatusFilter] = useState('all');
    const [search, setSearch] = useState('');
    const [pending, setPending] = useState(null);

    useEffect(() => {
        loadTools();
    }, []);

    const loadTools = async () => {
        setLoading(true);
        try {
            const data = await adminToolApi.listTools();
            setTools(Array.isArray(data) ? data : []);
            setError('');
        } catch (err) {
            setError(err.message || '加载工具失败');
            setTools([]);
        } finally {
            setLoading(false);
        }
    };

    const handleToggle = async (tool, e) => {
        e.stopPropagation();
        const next = !tool.enabled;
        setPending(tool.id);
        try {
            await adminToolApi.setEnabled(tool.id, next);
            setTools((prev) => prev.map((t) => (t.id === tool.id ? { ...t, enabled: next } : t)));
        } catch (err) {
            setError(err.message || '更新失败');
        } finally {
            setPending(null);
        }
    };

    const handleTest = async (tool, e) => {
        e.stopPropagation();
        try {
            const result = await adminToolApi.testTool(tool.id);
            alert(result.message || `测试完成：${result.ok ? '✓ 成功' : '✗ 失败'}`);
        } catch (err) {
            alert(`测试失败：${err.message}`);
        }
    };

    const counts = useMemo(() => {
        const enabled = tools.filter((t) => t.enabled).length;
        const disabled = tools.length - enabled;
        return { enabled, disabled };
    }, [tools]);

    const filtered = useMemo(() => {
        return tools.filter((t) => {
            if (sectionTab !== 'all' && t.category !== CATEGORY_MAP[sectionTab]) return false;
            if (statusFilter === 'enabled' && !t.enabled) return false;
            if (statusFilter === 'disabled' && t.enabled) return false;
            if (search) {
                const kw = search.toLowerCase();
                return (
                    (t.name || '').toLowerCase().includes(kw) ||
                    (t.code || '').toLowerCase().includes(kw) ||
                    (t.description || '').toLowerCase().includes(kw)
                );
            }
            return true;
        });
    }, [tools, sectionTab, statusFilter, search]);

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">工具</h2>
                <span className="admin-card-section-sub">Tools · 智能体可调用的能力</span>
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
                        placeholder="搜索工具..."
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
                        {f.id === 'enabled' && counts.enabled > 0 && <span> {counts.enabled}</span>}
                        {f.id === 'disabled' && counts.disabled > 0 && <span> {counts.disabled}</span>}
                    </button>
                ))}
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((tool) => {
                    const Icon = ICON_MAP[tool.icon] || FileText;
                    const tone = ICON_TONE_MAP[tool.icon] || '';
                    const isPending = pending === tool.id;
                    return (
                        <div className="admin-card" key={tool.id} onClick={() => alert(`配置「${tool.name}」`)}>
                            <div className="admin-card-head-row">
                                <div className="admin-card-icon-row">
                                    <div className={`admin-card-icon ${tone}`}>
                                        <Icon size={16} strokeWidth={2.2} />
                                    </div>
                                    <div className="admin-card-title-wrap">
                                        <h3 className="admin-card-title">{tool.name}</h3>
                                        <span className="admin-card-subtitle">{tool.code}</span>
                                    </div>
                                </div>
                                <div
                                    className={`admin-card-tool-toggle ${tool.enabled ? 'on' : ''}`}
                                    onClick={(e) => handleToggle(tool, e)}
                                    role="switch"
                                    aria-checked={tool.enabled}
                                    title={tool.enabled ? '点击停用' : '点击启用'}
                                >
                                    <div className="track" />
                                    <div className="knob" />
                                    {isPending && <span style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontSize: 9, color: 'white' }}>…</span>}
                                </div>
                            </div>
                            <p className="admin-card-desc">{tool.description}</p>
                            <div className="admin-card-meta">
                                {tool.enabled ? (
                                    <span className="live">已启用</span>
                                ) : (
                                    <span className="off">已停用</span>
                                )}
                                {tool.dailyCalls > 0 && (
                                    <>
                                        <span className="dot" />
                                        <span><strong>{(tool.dailyCalls / 1000).toFixed(1)}k</strong> 调用/日</span>
                                    </>
                                )}
                                {tool.usedByAgentCount > 0 && (
                                    <>
                                        <span className="dot" />
                                        <span><strong>{tool.usedByAgentCount}</strong> 个 agent 使用</span>
                                    </>
                                )}
                            </div>
                            <div className="admin-card-actions">
                                <button
                                    className="admin-card-action primary"
                                    onClick={(e) => { e.stopPropagation(); alert(`配置「${tool.name}」`); }}
                                >
                                    配置
                                </button>
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => handleTest(tool, e)}
                                >
                                    测试
                                </button>
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={() => alert('新建工具（待实现）')}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>新建工具</div>
                </div>
            </div>
        </section>
    );
};

export default ToolsTab;
