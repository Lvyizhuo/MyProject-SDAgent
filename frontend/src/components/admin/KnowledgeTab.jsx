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
    const [selectedId, setSelectedId] = useState(null);

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

    const handleCreate = async () => {
        const name = window.prompt('新建知识库名称（如：地市政策-青岛）');
        if (!name) return;
        try {
            const created = await adminKnowledgeApi.createFolder({
                name: name.trim(),
                description: '',
                embeddingModel: 'ollama:bge-m3',
                rerankModelId: null
            });
            await loadFolders();
            if (created?.id) setSelectedId(created.id);
        } catch (err) {
            setError(err.message || '创建失败');
        }
    };

    const handleDelete = async (folder) => {
        if (!window.confirm(`确认删除「${folder.name}」？该操作不可恢复。`)) {
            return;
        }
        try {
            await adminKnowledgeApi.deleteFolder(folder.id);
            await loadFolders();
        } catch (err) {
            setError(err.message || '删除失败');
        }
    };

    const selectedFolder = useMemo(
        () => enriched.find((f) => f.id === selectedId) || null,
        [enriched, selectedId]
    );

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
                        <div className="admin-card" key={folder.id} onClick={() => setSelectedId(folder.id)}>
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
                            <p className="admin-card-desc">{folder.description || '点击查看文档与检索测试'}</p>
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
                                    onClick={(e) => { e.stopPropagation(); setSelectedId(folder.id); }}
                                >
                                    查看
                                </button>
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => { e.stopPropagation(); alert(`检索测试「${folder.name}」请前往原知识库管理页（顶部 tab 切换）`); }}
                                >
                                    检索测试
                                </button>
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={handleCreate}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>新建知识库</div>
                </div>
            </div>

            {selectedFolder && (
                <KnowledgeDetailPanel
                    folder={selectedFolder}
                    onClose={() => setSelectedId(null)}
                    onDelete={handleDelete}
                />
            )}
        </section>
    );
};

const KnowledgeDetailPanel = ({ folder, onClose, onDelete }) => {
    const Icon = ICON_MAP[folder.icon] || BookOpen;
    const tone = ICON_TONE_MAP[folder.icon] || '';
    const status = STATUS_TONE[folder.status] || STATUS_TONE.synced;
    const formatSize = (bytes) => {
        if (!bytes) return '0 B';
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    };
    return (
        <>
            <div className="admin-drawer-mask" onClick={onClose} />
            <aside className="admin-drawer">
                <header className="admin-drawer-head">
                    <div className="admin-drawer-title-row">
                        <div className={`admin-card-icon ${tone}`} style={{ width: 36, height: 36 }}>
                            <Icon size={18} strokeWidth={2.2} />
                        </div>
                        <div>
                            <h2 className="admin-drawer-title">{folder.name}</h2>
                            <span className="admin-drawer-subtitle">{folder.kindLabel}</span>
                        </div>
                    </div>
                    <button className="admin-drawer-close" onClick={onClose} aria-label="关闭">×</button>
                </header>
                <div className="admin-drawer-body">
                    <section className="admin-drawer-section">
                        <h3>基础信息</h3>
                        <dl className="admin-drawer-dl">
                            <dt>分类</dt><dd>{folder.kindLabel || '山东省'}</dd>
                            <dt>文档数</dt><dd>{(folder.documentCount || 0).toLocaleString('zh-CN')} 份</dd>
                            <dt>大小</dt><dd>{formatSize(folder.sizeBytes)}</dd>
                            <dt>状态</dt><dd><span className={status.dot}>{status.label}</span></dd>
                        </dl>
                    </section>
                    <section className="admin-drawer-section">
                        <h3>描述</h3>
                        <p>{folder.description || '尚未填写'}</p>
                    </section>
                </div>
                <footer className="admin-drawer-foot">
                    <button
                        className="admin-drawer-btn danger"
                        onClick={() => {
                            onDelete(folder);
                            onClose();
                        }}
                    >
                        删除
                    </button>
                </footer>
            </aside>
        </>
    );
};

export default KnowledgeTab;
