import React, { useEffect, useMemo, useState } from 'react';
import {
    BookOpen,
    FileText,
    MessageCircle,
    Map,
    Archive,
    Plus,
    Search
} from 'lucide-react';
import adminKnowledgeApi from '../../services/adminKnowledgeApi';
import './admin-card.css';

const SECTION_TABS = [
    { id: 'all', label: '全部' },
    { id: 'mine', label: '我的' },
    { id: 'team', label: '团队共享' }
];

const ICON_MAP = {
    BookOpen,
    FileText,
    MessageCircle,
    Map,
    Archive
};

const ICON_TONE_MAP = {
    BookOpen: '',
    FileText: 'tone-secondary',
    MessageCircle: 'tone-success',
    Map: 'tone-accent',
    Archive: 'tone-warning'
};

const STATUS_TONE = {
    synced: { dot: 'live', label: '已同步' },
    syncing: { dot: 'off', label: '同步中' },
    archived: { dot: 'off', label: '已归档' },
    error: { dot: 'draft', label: '异常' }
};

const flattenFolders = (tree) => {
    const out = [];
    const walk = (nodes) => {
        for (const n of nodes || []) {
            out.push(n);
            if (n.children?.length) walk(n.children);
        }
    };
    walk(tree);
    return out;
};

const KnowledgeTab = () => {
    const [folders, setFolders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [search, setSearch] = useState('');

    useEffect(() => {
        loadFolders();
    }, []);

    const loadFolders = async () => {
        setLoading(true);
        try {
            const res = await adminKnowledgeApi.getFolderTree();
            const tree = res?.folders || res || [];
            setFolders(flattenFolders(tree));
            setError('');
        } catch (err) {
            setError(err.message || '加载知识库失败');
            setFolders([]);
        } finally {
            setLoading(false);
        }
    };

    const enriched = useMemo(() => {
        return folders.map((f) => ({
            ...f,
            icon: f.icon || (f.kind === 'faq' ? 'MessageCircle' : f.kind === 'region' ? 'Map' : f.kind === 'archive' ? 'Archive' : 'BookOpen'),
            kindLabel: f.kindLabel || f.region || (f.kind === 'faq' ? 'FAQ' : f.kind === 'region' ? '地市' : f.kind === 'archive' ? '归档' : '山东省'),
            status: f.status || 'synced',
            documentCount: f.documentCount ?? 0,
            sizeBytes: f.sizeBytes ?? 0
        }));
    }, [folders]);

    const filtered = useMemo(() => {
        return enriched.filter((f) => {
            if (sectionTab === 'mine' && !f.isMine) return false;
            if (sectionTab === 'team' && !f.isTeam) return false;
            if (search) {
                const kw = search.toLowerCase();
                return (
                    (f.name || '').toLowerCase().includes(kw) ||
                    (f.description || '').toLowerCase().includes(kw)
                );
            }
            return true;
        });
    }, [enriched, sectionTab, search]);

    const formatSize = (bytes) => {
        if (!bytes) return '0 B';
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    };

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">知识库</h2>
                <span className="admin-card-section-sub">Knowledge · 文档集合与目录</span>
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
                        placeholder="搜索知识库..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </div>
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((folder) => {
                    const Icon = ICON_MAP[folder.icon] || BookOpen;
                    const tone = ICON_TONE_MAP[folder.icon] || '';
                    const status = STATUS_TONE[folder.status] || STATUS_TONE.synced;
                    const kindTone = folder.kind === 'archive'
                        ? 'warning'
                        : folder.kind === 'faq'
                            ? 'success'
                            : folder.kind === 'region'
                                ? 'accent'
                                : 'primary';
                    return (
                        <div className="admin-card" key={folder.id} onClick={() => alert(`进入「${folder.name}」`)}>
                            <div className="admin-card-head-row">
                                <div className="admin-card-icon-row">
                                    <div className={`admin-card-icon ${tone}`}>
                                        <Icon size={16} strokeWidth={2.2} />
                                    </div>
                                    <div className="admin-card-title-wrap">
                                        <h3 className="admin-card-title">{folder.name}</h3>
                                        {folder.code && <span className="admin-card-subtitle">{folder.code}</span>}
                                    </div>
                                </div>
                                <span className={`admin-card-badge ${kindTone}`}>{folder.kindLabel}</span>
                            </div>
                            <p className="admin-card-desc">{folder.description || '点击进入查看文档与检索测试'}</p>
                            <div className="admin-card-meta">
                                <span><strong>{folder.documentCount.toLocaleString('zh-CN')}</strong> 份文档</span>
                                <span className="dot" />
                                <span><strong>{formatSize(folder.sizeBytes)}</strong></span>
                                <span className="dot" />
                                <span className={status.dot}>{status.label}</span>
                            </div>
                            <div className="admin-card-actions">
                                <button
                                    className="admin-card-action primary"
                                    onClick={(e) => { e.stopPropagation(); alert(`进入「${folder.name}」`); }}
                                >
                                    进入
                                </button>
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => { e.stopPropagation(); alert(`检索测试「${folder.name}」`); }}
                                >
                                    检索测试
                                </button>
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={() => alert('新建知识库（待实现）')}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>新建知识库</div>
                </div>
            </div>
        </section>
    );
};

export default KnowledgeTab;
