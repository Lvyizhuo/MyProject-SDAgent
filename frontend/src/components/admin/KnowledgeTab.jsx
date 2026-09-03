import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    BookOpen,
    FileText,
    MessageCircle,
    Map,
    Archive,
    Plus,
    Search,
    FolderInput,
    Link2,
    Clock,
    Download,
    Upload,
    X,
    Edit2,
    Trash2,
    CheckCircle2,
    AlertCircle,
    Loader2,
    Eye,
    RefreshCw,
    ExternalLink,
    Square,
    CheckSquare,
    Settings,
    FolderPlus,
    Sparkles
} from 'lucide-react';
import adminKnowledgeApi from '../../services/adminKnowledgeApi';
import { adminApi } from '../../services/adminApi';
import { useAdminConsole } from './useAdminConsole';
import './admin-card.css';
import './admin-modal.css';

const SECTION_TABS = [
    { id: 'all', label: '全部' },
    { id: 'mine', label: '我的' },
    { id: 'team', label: '团队共享' }
];

const ICON_MAP = { BookOpen, FileText, MessageCircle, Map, Archive };
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

const formatSize = (bytes) => {
    if (!bytes) return '0 B';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const DocumentStatusIcon = ({ status }) => {
    switch (status) {
        case 'COMPLETED':
            return <CheckCircle2 size={16} style={{ color: 'hsl(var(--color-success))' }} />;
        case 'PROCESSING':
            return <Loader2 size={16} className="animate-spin" style={{ color: 'hsl(var(--color-primary))' }} />;
        case 'FAILED':
            return <AlertCircle size={16} style={{ color: 'hsl(0 72% 50%)' }} />;
        default:
            return <Clock size={16} style={{ color: 'hsl(var(--color-text-muted))' }} />;
    }
};

const DocumentStatusText = (status) => {
    switch (status) {
        case 'COMPLETED':
            return '已完成';
        case 'PROCESSING':
            return '处理中';
        case 'FAILED':
            return '失败';
        case 'CANCELED':
            return '已取消';
        default:
            return '等待中';
    }
};

/* ============================================================
   KnowledgeTab — 主组件
   ============================================================ */
const KnowledgeTab = () => {
    const { notify, confirm, prompt } = useAdminConsole();
    const [folders, setFolders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [search, setSearch] = useState('');
    const [selectedId, setSelectedId] = useState(null);
    // dialogs
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [showWebImportDialog, setShowWebImportDialog] = useState(false);
    const [showTaskListDialog, setShowTaskListDialog] = useState(false);
    const [showConfigDialog, setShowConfigDialog] = useState(false);
    // detail drawer states
    const [embeddingModels, setEmbeddingModels] = useState([]);
    const [rerankModels, setRerankModels] = useState([]);
    const [config, setConfig] = useState(null);
    const [urlImportJobs, setUrlImportJobs] = useState([]);
    const [urlImportCandidates, setUrlImportCandidates] = useState([]);
    const archiveInputRef = useRef(null);

    useEffect(() => {
        loadFolders();
        loadEmbeddingModels();
        loadRerankModels();
        loadConfig();
    }, []);

    // 每 15s 轮询 URL 导入任务
    useEffect(() => {
        if (!showTaskListDialog) return;
        const timer = setInterval(loadUrlImports, 15000);
        return () => clearInterval(timer);
    }, [showTaskListDialog]);

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

    const loadEmbeddingModels = async () => {
        try {
            const data = await adminKnowledgeApi.getEmbeddingModels();
            setEmbeddingModels(data.models || []);
        } catch (e) {
            console.error('加载嵌入模型失败', e);
        }
    };

    const loadRerankModels = async () => {
        try {
            const data = await adminApi.getModels('RERANK');
            setRerankModels(Array.isArray(data) ? data.filter(m => m && m.id != null && m.isEnabled !== false) : []);
        } catch (e) {
            console.error('加载重排序模型失败', e);
        }
    };

    const loadConfig = async () => {
        try {
            const data = await adminKnowledgeApi.getConfig();
            setConfig(data);
        } catch (e) {
            console.error('加载配置失败', e);
        }
    };

    const loadUrlImports = async () => {
        try {
            const data = await adminKnowledgeApi.listUrlImports();
            setUrlImportJobs(data.jobs || []);
            setUrlImportCandidates(data.candidates || []);
        } catch (e) {
            console.error('加载导入任务失败', e);
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

    const handleCreateFolder = async () => {
        await Promise.all([loadEmbeddingModels(), loadRerankModels()]);
        setShowCreateDialog(true);
    };

    const handleDeleteFolder = async (folder, e) => {
        e.stopPropagation();
        const ok = await confirm({
            title: '删除知识库',
            message: `确认删除「${folder.name}」？该操作不可恢复。`,
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!ok) return;
        try {
            await adminKnowledgeApi.deleteFolder(folder.id);
            notify({ text: '知识库已删除', type: 'success', source: '知识库' });
            if (selectedId === folder.id) setSelectedId(null);
            await loadFolders();
        } catch (err) {
            notify({ text: '删除失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleExportArchive = async () => {
        try {
            const blob = await adminKnowledgeApi.exportArchive();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `knowledge-archive-${new Date().toISOString().slice(0, 10)}.zip`;
            a.click();
            window.URL.revokeObjectURL(url);
            notify({ text: '知识库导出成功', type: 'success', source: '知识库' });
        } catch (err) {
            notify({ text: '导出失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleImportArchive = async (file) => {
        if (!file) return;
        const ok = await confirm({
            title: '导入知识库归档',
            message: `确认导入 ${file.name} 吗？`,
            confirmText: '开始导入'
        });
        if (!ok) return;
        try {
            const result = await adminKnowledgeApi.importArchive(file);
            const summary = `导入完成：新增 ${result.importedCount || 0}，跳过 ${result.skippedCount || 0}，失败 ${result.failedCount || 0}`;
            notify({ text: summary, type: result.failedCount > 0 ? 'warning' : 'success', source: '知识库' });
            await Promise.all([loadFolders()]);
        } catch (err) {
            notify({ text: '导入失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleEditFolder = async (folder, e) => {
        e.stopPropagation();
        const newName = await prompt({
            title: '编辑知识库名称',
            message: '输入新的名称',
            label: '名称',
            initialValue: folder.name,
            confirmText: '保存'
        });
        if (newName == null || !newName.trim()) return;
        try {
            await adminKnowledgeApi.updateFolder(folder.id, { name: newName.trim() });
            notify({ text: '知识库已更新', type: 'success', source: '知识库' });
            await loadFolders();
        } catch (err) {
            notify({ text: '更新失败: ' + err.message, type: 'error', source: '知识库' });
        }
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

            <div className="admin-card-filter-bar">
                <button className="admin-card-action primary" onClick={handleCreateFolder}>
                    <FolderPlus size={14} /> 新建知识库
                </button>
                <button className="admin-card-action" onClick={() => setShowWebImportDialog(true)}>
                    <Link2 size={14} /> 网站导入
                </button>
                <button className="admin-card-action" onClick={() => { loadUrlImports(); setShowTaskListDialog(true); }}>
                    <Clock size={14} /> 任务列表
                </button>
                <button className="admin-card-action" onClick={handleExportArchive}>
                    <Download size={14} /> 导出
                </button>
                <button className="admin-card-action" onClick={() => archiveInputRef.current?.click()}>
                    <Upload size={14} /> 导入
                </button>
                <button className="admin-card-action" onClick={() => setShowConfigDialog(true)}>
                    <Settings size={14} /> 配置
                </button>
                <input
                    ref={archiveInputRef}
                    type="file"
                    accept=".zip,application/zip"
                    style={{ display: 'none' }}
                    onChange={(e) => { handleImportArchive(e.target.files?.[0]); e.target.value = ''; }}
                />
                <div className="admin-card-filter-spacer" />
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((folder) => {
                    const Icon = ICON_MAP[folder.icon] || BookOpen;
                    const tone = ICON_TONE_MAP[folder.icon] || '';
                    const status = STATUS_TONE[folder.status] || STATUS_TONE.synced;
                    const kindTone = folder.kind === 'archive' ? 'warning' : folder.kind === 'faq' ? 'success' : folder.kind === 'region' ? 'accent' : 'primary';
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
                                {folder.embeddingModel && (
                                    <>
                                        <span className="dot" />
                                        <span>{folder.embeddingModel}</span>
                                    </>
                                )}
                            </div>
                            <div className="admin-card-actions">
                                <button className="admin-card-action primary" onClick={(e) => { e.stopPropagation(); setSelectedId(folder.id); }}>
                                    查看
                                </button>
                                <button className="admin-card-action" onClick={(e) => handleEditFolder(folder, e)}>
                                    编辑 <Edit2 size={12} />
                                </button>
                                <button className="admin-card-action danger" onClick={(e) => handleDeleteFolder(folder, e)}>
                                    删除 <Trash2 size={12} />
                                </button>
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={handleCreateFolder}>
                    <div className="plus"><Plus size={20} /></div>
                    <div>新建知识库</div>
                </div>
            </div>

            {/* Detail Drawer */}
            {selectedId && (
                <KnowledgeDetailDrawer
                    folderId={selectedId}
                    folders={enriched}
                    onClose={() => setSelectedId(null)}
                    onRefresh={loadFolders}
                    onNotify={notify}
                />
            )}

            {/* Create Dialog */}
            {showCreateDialog && (
                <CreateKnowledgeBaseModal
                    embeddingModels={embeddingModels}
                    rerankModels={rerankModels}
                    onClose={() => setShowCreateDialog(false)}
                    onCreated={() => { setShowCreateDialog(false); loadFolders(); }}
                    onNotify={notify}
                />
            )}

            {/* Web Import Dialog */}
            {showWebImportDialog && (
                <WebImportModal
                    folders={folders}
                    onClose={() => setShowWebImportDialog(false)}
                    onNotify={notify}
                />
            )}

            {/* Task List Dialog */}
            {showTaskListDialog && (
                <TaskListModal
                    onClose={() => setShowTaskListDialog(false)}
                    onNotify={notify}
                    jobs={urlImportJobs}
                    candidates={urlImportCandidates}
                    onRefresh={loadUrlImports}
                />
            )}

            {/* Config Dialog */}
            {showConfigDialog && (
                <ConfigModal
                    config={config}
                    onClose={() => setShowConfigDialog(false)}
                    onSave={async (newConfig) => {
                        try {
                            await adminKnowledgeApi.updateConfig(newConfig);
                            notify({ text: '配置保存成功', type: 'success', source: '知识库' });
                            setShowConfigDialog(false);
                            await loadConfig();
                        } catch (err) {
                            notify({ text: '保存失败: ' + err.message, type: 'error', source: '知识库' });
                        }
                    }}
                    onNotify={notify}
                />
            )}
        </section>
    );
};

/* ============================================================
   KnowledgeDetailDrawer — 知识库详情抽屉
   ============================================================ */
const KnowledgeDetailDrawer = ({ folderId, folders, onClose, onRefresh, onNotify }) => {
    const { confirm } = useAdminConsole();
    const folder = folders.find((f) => f.id === folderId);
    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pagination, setPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState(null);
    const [selectedDocIds, setSelectedDocIds] = useState([]);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [showUploadDialog, setShowUploadDialog] = useState(false);
    const fileInputRef = useRef(null);

    useEffect(() => {
        loadDocuments(0);
    }, [folderId, filterStatus]);

    const loadDocuments = async (page) => {
        setLoading(true);
        try {
            const data = await adminKnowledgeApi.listDocuments({
                folderId,
                page,
                size: 20,
                status: filterStatus,
                q: searchQuery?.trim() || null
            });
            setDocuments(data.content || []);
            setPagination({ page: data.page || 0, size: data.size || 20, totalElements: data.totalElements || 0, totalPages: data.totalPages || 0 });
        } catch (err) {
            onNotify({ text: '加载文档失败: ' + err.message, type: 'error', source: '知识库' });
            setDocuments([]);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = () => {
        loadDocuments(0);
    };

    useEffect(() => {
        const timer = setTimeout(handleSearch, 300);
        return () => clearTimeout(timer);
    }, [searchQuery]);

    const handleDeleteDoc = async (doc) => {
        const ok = await confirm({ title: '删除文档', message: `确认删除「${doc.title}」？`, confirmText: '确认删除', tone: 'danger' });
        if (!ok) return;
        try {
            await adminKnowledgeApi.deleteDocument(doc.id);
            onNotify({ text: '文档已删除', type: 'success', source: '知识库' });
            loadDocuments(pagination.page);
        } catch (err) {
            onNotify({ text: '删除失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleBatchDelete = async () => {
        if (!selectedDocIds.length) {
            onNotify({ text: '请先选择文档', type: 'warning', source: '知识库' });
            return;
        }
        const ok = await confirm({ title: '批量删除', message: `确认删除 ${selectedDocIds.length} 份文档？`, confirmText: '确认删除', tone: 'danger' });
        if (!ok) return;
        try {
            await adminKnowledgeApi.batchDeleteDocuments(selectedDocIds);
            onNotify({ text: '批量删除成功', type: 'success', source: '知识库' });
            setSelectedDocIds([]);
            loadDocuments(0);
        } catch (err) {
            onNotify({ text: '批量删除失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleFileUpload = async (files) => {
        if (!files?.length) return;
        setShowUploadDialog(true);
        setUploadProgress(0);

        let success = 0;
        let failed = 0;
        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            const formData = new FormData();
            formData.append('file', file);
            formData.append('folderId', folderId);
            try {
                await adminKnowledgeApi.uploadDocument(formData, (pct) => {
                    setUploadProgress(Math.round(((i + pct / 100) / files.length) * 100));
                });
                success++;
            } catch (err) {
                failed++;
                console.error('上传失败:', file.name, err.message);
            }
        }

        setShowUploadDialog(false);
        setUploadProgress(0);
        if (success > 0) {
            onNotify({ text: `上传完成：${success} 成功${failed ? `，${failed} 失败` : ''}`, type: failed > 0 ? 'warning' : 'success', source: '知识库' });
            loadDocuments(0);
        }
    };

    const handleReingest = async (doc) => {
        try {
            await adminKnowledgeApi.reingestDocument(doc.id);
            onNotify({ text: '已加入重新处理队列', type: 'info', source: '知识库' });
            loadDocuments(pagination.page);
        } catch (err) {
            onNotify({ text: '重新处理失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const handleDownloadDoc = async (doc) => {
        try {
            if (doc.externalSourceUrl) {
                window.open(doc.externalSourceUrl, '_blank');
                return;
            }
            const blob = await adminKnowledgeApi.downloadDocument(doc.id);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = doc.fileName;
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            onNotify({ text: '下载失败: ' + err.message, type: 'error', source: '知识库' });
        }
    };

    const Icon = folder ? (ICON_MAP[folder.icon] || BookOpen) : BookOpen;
    const tone = folder ? (ICON_TONE_MAP[folder.icon] || '') : '';

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
                            <h2 className="admin-drawer-title">{folder?.name || '知识库'}</h2>
                            <span className="admin-drawer-subtitle">
                                {folder?.kindLabel || '知识库'} · {folder?.description || ''}
                            </span>
                        </div>
                    </div>
                    <button className="admin-drawer-close" onClick={onClose}>×</button>
                </header>

                <div className="admin-drawer-body" style={{ padding: '16px 22px' }}>
                    {/* Toolbar */}
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}>
                        <div className="admin-card-section-search" style={{ position: 'relative', flex: 1, minWidth: 160 }}>
                            <Search size={14} />
                            <input
                                placeholder="搜索文档..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                style={{ width: '100%', padding: '7px 12px 7px 32px', border: '1px solid hsl(var(--color-border))', borderRadius: 'var(--radius-pill)', fontSize: 13, background: 'hsl(var(--color-bg-elevated))', outline: 'none', fontFamily: 'inherit', boxSizing: 'border-box' }}
                            />
                        </div>
                        <select
                            value={filterStatus || ''}
                            onChange={(e) => setFilterStatus(e.target.value || null)}
                            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid hsl(var(--color-border))', fontSize: 13, fontFamily: 'inherit', background: 'hsl(var(--color-bg-elevated))' }}
                        >
                            <option value="">全部状态</option>
                            <option value="PENDING">等待中</option>
                            <option value="PROCESSING">处理中</option>
                            <option value="COMPLETED">已完成</option>
                            <option value="FAILED">失败</option>
                        </select>
                        <button className="admin-card-action" onClick={() => { fileInputRef.current?.click(); }}>
                            <Upload size={14} /> 上传
                        </button>
                        <input
                            ref={fileInputRef}
                            type="file"
                            multiple
                            accept=".pdf,.docx,.doc,.txt,.md,.csv"
                            style={{ display: 'none' }}
                            onChange={(e) => { handleFileUpload(e.target.files); e.target.value = ''; }}
                        />
                        {selectedDocIds.length > 0 && (
                            <button className="admin-card-action danger" onClick={handleBatchDelete}>
                                <Trash2 size={14} /> 删除{selectedDocIds.length > 1 ? ` (${selectedDocIds.length})` : ''}
                            </button>
                        )}
                    </div>

                    {/* Upload Progress */}
                    {showUploadDialog && (
                        <div style={{ padding: '8px 12px', background: 'hsl(var(--color-primary-light) / 0.3)', borderRadius: 6, marginBottom: 12, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
                            <Loader2 size={16} className="animate-spin" />
                            上传中 {uploadProgress}%
                            <div style={{ flex: 1, height: 6, borderRadius: 3, background: 'hsl(var(--color-border))' }}>
                                <div style={{ width: `${uploadProgress}%`, height: '100%', borderRadius: 3, background: 'hsl(var(--color-primary))', transition: 'width 0.3s' }} />
                            </div>
                        </div>
                    )}

                    {/* Document List */}
                    {loading ? (
                        <div className="admin-card-loading">加载中…</div>
                    ) : documents.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: 40, color: 'hsl(var(--color-text-muted))' }}>
                            <FileText size={40} style={{ margin: '0 auto 12px', display: 'block', opacity: 0.3 }} />
                            <p>暂无文档，点击「上传」添加</p>
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                            {documents.map((doc) => (
                                <div key={doc.id} style={{
                                    display: 'flex', alignItems: 'center', gap: 10,
                                    padding: '10px 12px', borderRadius: 8,
                                    border: '1px solid hsl(var(--color-border))',
                                    background: 'hsl(var(--color-bg-elevated))'
                                }}>
                                    <button
                                        className="admin-card-action"
                                        style={{ padding: 2, minWidth: 20 }}
                                        onClick={() => setSelectedDocIds(prev => prev.includes(doc.id) ? prev.filter(id => id !== doc.id) : [...prev, doc.id])}
                                    >
                                        {selectedDocIds.includes(doc.id) ? <CheckSquare size={14} /> : <Square size={14} />}
                                    </button>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ fontSize: 13, fontWeight: 600, color: 'hsl(var(--color-title))', marginBottom: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                            {doc.title}
                                        </div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11.5, color: 'hsl(var(--color-text-muted))' }}>
                                            <span><DocumentStatusIcon status={doc.status} /> {DocumentStatusText(doc.status)}</span>
                                            {doc.fileSize != null && (<><span className="dot" />{formatSize(doc.fileSize)}</>)}
                                            {doc.chunkCount > 0 && (<><span className="dot" />{doc.chunkCount} 切片</>)}
                                            {doc.errorMessage && <span style={{ color: 'hsl(0 72% 50%)' }}>{doc.errorMessage}</span>}
                                        </div>
                                    </div>
                                    <div className="admin-card-actions" style={{ opacity: 1, marginTop: 0, flexShrink: 0 }}>
                                        {doc.status === 'COMPLETED' && (
                                            <button className="admin-card-action" title="预览" onClick={() => handleDownloadDoc(doc)}>
                                                <Eye size={12} />
                                            </button>
                                        )}
                                        {doc.status === 'FAILED' && (
                                            <button className="admin-card-action" title="重新处理" onClick={() => handleReingest(doc)}>
                                                <RefreshCw size={12} />
                                            </button>
                                        )}
                                        <button className="admin-card-action" title="下载" onClick={() => handleDownloadDoc(doc)}>
                                            <Download size={12} />
                                        </button>
                                        <button className="admin-card-action danger" title="删除" onClick={() => handleDeleteDoc(doc)}>
                                            <Trash2 size={12} />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Pagination */}
                    {pagination.totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 12, marginTop: 16, fontSize: 13 }}>
                            <button className="admin-card-action" disabled={pagination.page === 0} onClick={() => loadDocuments(pagination.page - 1)}>上一页</button>
                            <span style={{ color: 'hsl(var(--color-text-muted))' }}>{pagination.page + 1} / {pagination.totalPages} · 共 {pagination.totalElements} 条</span>
                            <button className="admin-card-action" disabled={pagination.page >= pagination.totalPages - 1} onClick={() => loadDocuments(pagination.page + 1)}>下一页</button>
                        </div>
                    )}
                </div>

                <footer className="admin-drawer-foot">
                    <button className="admin-drawer-btn" onClick={onClose}>关闭</button>
                </footer>
            </aside>
        </>
    );
};

/* ============================================================
   CreateKnowledgeBaseModal — 新建知识库
   ============================================================ */
const CreateKnowledgeBaseModal = ({ embeddingModels, rerankModels, onClose, onCreated, onNotify }) => {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [embeddingModel, setEmbeddingModel] = useState(
        embeddingModels.find(m => m.isDefault)?.id || embeddingModels[0]?.id || ''
    );
    const [rerankModelId, setRerankModelId] = useState(rerankModels[0]?.id || '');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!embeddingModel && embeddingModels.length > 0) {
            setEmbeddingModel(embeddingModels.find(m => m.isDefault)?.id || embeddingModels[0].id);
        }
    }, [embeddingModels]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!name.trim() || !embeddingModel) return;
        setSaving(true);
        try {
            await adminKnowledgeApi.createFolder({
                name: name.trim(),
                description: description.trim(),
                embeddingModel,
                rerankModelId: rerankModelId ? Number(rerankModelId) : null
            });
            onNotify({ text: '知识库创建成功', type: 'success', source: '知识库' });
            onCreated();
        } catch (err) {
            onNotify({ text: '创建失败: ' + err.message, type: 'error', source: '知识库' });
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="admin-modal-mask" onClick={onClose}>
            <div className="admin-modal" onClick={(e) => e.stopPropagation()}>
                <header className="admin-modal-head">
                    <h2>新建知识库</h2>
                    <button className="admin-modal-close" onClick={onClose}>×</button>
                </header>
                <form onSubmit={handleSubmit}>
                    <div className="admin-modal-body">
                        <div className="admin-modal-grid">
                            <div className="admin-form-group full">
                                <label>知识库名称 <span className="required">*</span></label>
                                <input value={name} onChange={(e) => setName(e.target.value)} placeholder="如：2026 山东家电补贴" required />
                            </div>
                            <div className="admin-form-group full">
                                <label>描述</label>
                                <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} placeholder="简要描述政策范围与用途" />
                            </div>
                            <div className="admin-form-group">
                                <label>嵌入模型 <span className="required">*</span></label>
                                <select value={embeddingModel} onChange={(e) => setEmbeddingModel(e.target.value)} required>
                                    <option value="" disabled>请选择</option>
                                    {embeddingModels.map(m => (
                                        <option key={m.id} value={m.id}>{m.name}{m.dimensions ? ` (${m.dimensions}维)` : ''}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="admin-form-group">
                                <label>重排序模型（可选）</label>
                                <select value={rerankModelId} onChange={(e) => setRerankModelId(e.target.value)}>
                                    <option value="">不配置</option>
                                    {rerankModels.map(m => (
                                        <option key={m.id} value={m.id}>{m.name || m.modelName} ({m.provider})</option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>
                    <footer className="admin-modal-foot">
                        <button type="button" className="admin-drawer-btn" onClick={onClose}>取消</button>
                        <button type="submit" className="admin-drawer-btn primary" disabled={saving || !name.trim() || !embeddingModel}>
                            {saving ? '创建中...' : '创建'}
                        </button>
                    </footer>
                </form>
            </div>
        </div>
    );
};

/* ============================================================
   WebImportModal — 网站导入
   ============================================================ */
const WebImportModal = ({ folders, onClose, onNotify }) => {
    const [url, setUrl] = useState('');
    const [folderId, setFolderId] = useState(folders[0]?.id || '');
    const [titleOverride, setTitleOverride] = useState('');
    const [remark, setRemark] = useState('');
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!url.trim()) return;
        setSaving(true);
        try {
            await adminKnowledgeApi.createUrlImport({
                url: url.trim(),
                folderId: folderId || null,
                titleOverride: titleOverride.trim() || undefined,
                remark: remark.trim() || undefined
            });
            onNotify({ text: '网站导入任务已创建', type: 'success', source: '知识库' });
            onClose();
        } catch (err) {
            onNotify({ text: '创建失败: ' + err.message, type: 'error', source: '知识库' });
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="admin-modal-mask" onClick={onClose}>
            <div className="admin-modal narrow" onClick={(e) => e.stopPropagation()}>
                <header className="admin-modal-head">
                    <h2>网站导入</h2>
                    <button className="admin-modal-close" onClick={onClose}>×</button>
                </header>
                <form onSubmit={handleSubmit}>
                    <div className="admin-modal-body">
                        <div className="admin-modal-grid">
                            <div className="admin-form-group full">
                                <label>网站 URL <span className="required">*</span></label>
                                <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/policy" required />
                            </div>
                            <div className="admin-form-group">
                                <label>目标知识库</label>
                                <select value={folderId} onChange={(e) => setFolderId(e.target.value)}>
                                    <option value="">选择知识库</option>
                                    {folders.map(f => (
                                        <option key={f.id} value={f.id}>{f.name}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="admin-form-group">
                                <label>任务标题（可选）</label>
                                <input value={titleOverride} onChange={(e) => setTitleOverride(e.target.value)} placeholder="如：青岛市以旧换新政策" />
                            </div>
                            <div className="admin-form-group full">
                                <label>备注</label>
                                <textarea value={remark} onChange={(e) => setRemark(e.target.value)} rows={2} placeholder="可选备注信息" />
                            </div>
                        </div>
                    </div>
                    <footer className="admin-modal-foot">
                        <button type="button" className="admin-drawer-btn" onClick={onClose}>取消</button>
                        <button type="submit" className="admin-drawer-btn primary" disabled={saving || !url.trim()}>
                            {saving ? '创建中...' : '创建导入任务'}
                        </button>
                    </footer>
                </form>
            </div>
        </div>
    );
};

/* ============================================================
   TaskListModal — 任务列表
   ============================================================ */
const TaskListModal = ({ onClose, onNotify, jobs, candidates, onRefresh }) => {
    const [selectedTaskId, setSelectedTaskId] = useState(jobs[0]?.id || null);
    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (selectedTaskId) loadTaskDocs(selectedTaskId);
    }, [selectedTaskId]);

    const loadTaskDocs = async (taskId) => {
        setLoading(true);
        try {
            const data = await adminKnowledgeApi.listDocuments({ importJobId: taskId, page: 0, size: 50 });
            setDocuments(data.content || []);
        } catch (err) {
            setDocuments([]);
        } finally {
            setLoading(false);
        }
    };

    const getImportStatusText = (status) => {
        switch (status) {
            case 'CRAWLING': return '抓取中';
            case 'PROCESSING': return '解析中';
            case 'WAITING_CONFIRM': return '待入库';
            case 'PARTIALLY_IMPORTED': return '部分已入库';
            case 'COMPLETED': return '已完成';
            case 'FAILED': return '失败';
            default: return '等待中';
        }
    };

    const selectedTask = jobs.find(j => j.id === selectedTaskId);

    return (
        <div className="admin-modal-mask" onClick={onClose}>
            <div className="admin-modal wide" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 800 }}>
                <header className="admin-modal-head">
                    <h2>网站导入任务</h2>
                    <div style={{ display: 'flex', gap: 8 }}>
                        <button className="admin-card-action" onClick={onRefresh} title="刷新"><RefreshCw size={14} /> 刷新</button>
                        <button className="admin-modal-close" onClick={onClose}>×</button>
                    </div>
                </header>
                <div className="admin-modal-body" style={{ display: 'flex', gap: 16, minHeight: 300 }}>
                    {/* Sidebar */}
                    <div style={{ width: 220, flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 6, borderRight: '1px solid hsl(var(--color-border))', paddingRight: 12 }}>
                        {jobs.length === 0 ? (
                            <div style={{ textAlign: 'center', padding: 24, color: 'hsl(var(--color-text-muted))', fontSize: 13 }}>暂无导入任务</div>
                        ) : jobs.map(job => (
                            <div
                                key={job.id}
                                onClick={() => setSelectedTaskId(job.id)}
                                style={{
                                    padding: '10px 12px', borderRadius: 8, cursor: 'pointer', fontSize: 13,
                                    background: selectedTaskId === job.id ? 'hsl(var(--color-primary-light))' : 'transparent',
                                    border: selectedTaskId === job.id ? '1px solid hsl(var(--color-primary) / 0.3)' : '1px solid transparent'
                                }}
                            >
                                <div style={{ fontWeight: 600, marginBottom: 4 }}>{job.title || `任务 #${job.id}`}</div>
                                <div style={{ color: 'hsl(var(--color-text-muted))', fontSize: 11.5, marginBottom: 4 }}>{job.sourceUrl}</div>
                                <span style={{
                                    display: 'inline-block', padding: '2px 8px', borderRadius: 12, fontSize: 11,
                                    background: job.status === 'COMPLETED' ? 'hsl(142 71% 95%)' : job.status === 'FAILED' ? 'hsl(0 72% 95%)' : 'hsl(var(--color-primary-light))',
                                    color: job.status === 'COMPLETED' ? 'hsl(var(--color-success))' : job.status === 'FAILED' ? 'hsl(0 72% 50%)' : 'hsl(var(--color-primary))'
                                }}>
                                    {getImportStatusText(job.status)}
                                </span>
                            </div>
                        ))}
                    </div>
                    {/* Detail */}
                    <div style={{ flex: 1, minWidth: 0 }}>
                        {selectedTask ? (
                            <>
                                <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
                                    <div style={{ padding: '8px 12px', background: 'hsl(var(--color-bg-soft))', borderRadius: 8, flex: 1, textAlign: 'center' }}>
                                        <div style={{ fontSize: 11, color: 'hsl(var(--color-text-muted))', marginBottom: 2 }}>抓取</div>
                                        <strong>{selectedTask.discoveredCount || 0}</strong>
                                    </div>
                                    <div style={{ padding: '8px 12px', background: 'hsl(var(--color-bg-soft))', borderRadius: 8, flex: 1, textAlign: 'center' }}>
                                        <div style={{ fontSize: 11, color: 'hsl(var(--color-text-muted))', marginBottom: 2 }}>待审批</div>
                                        <strong>{selectedTask.candidateCount || 0}</strong>
                                    </div>
                                    <div style={{ padding: '8px 12px', background: 'hsl(var(--color-bg-soft))', borderRadius: 8, flex: 1, textAlign: 'center' }}>
                                        <div style={{ fontSize: 11, color: 'hsl(var(--color-text-muted))', marginBottom: 2 }}>已入库</div>
                                        <strong>{selectedTask.importedCount || 0}</strong>
                                    </div>
                                    <div style={{ padding: '8px 12px', background: 'hsl(var(--color-bg-soft))', borderRadius: 8, flex: 1, textAlign: 'center' }}>
                                        <div style={{ fontSize: 11, color: 'hsl(var(--color-text-muted))', marginBottom: 2 }}>已驳回</div>
                                        <strong>{selectedTask.rejectedCount || 0}</strong>
                                    </div>
                                </div>
                                {/* Candidates from this task */}
                                {(() => {
                                    const taskCandidates = candidates.filter(c => c.jobId === selectedTask.id);
                                    return taskCandidates.length > 0 && (
                                        <div>
                                            <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>待入库候选内容</h4>
                                            {taskCandidates.map(item => (
                                                <div key={item.id} style={{ padding: '8px 10px', border: '1px solid hsl(var(--color-border))', borderRadius: 6, marginBottom: 6, fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
                                                    <ExternalLink size={14} style={{ flexShrink: 0 }} />
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <div style={{ fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.title}</div>
                                                        <div style={{ fontSize: 11, color: 'hsl(var(--color-text-muted))', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.sourceUrl}</div>
                                                    </div>
                                                    <span style={{ fontSize: 11, padding: '2px 6px', borderRadius: 4, background: 'hsl(42 100% 90%)', color: 'hsl(31 91% 36%)' }}>
                                                        {item.qualityScore != null ? `评分 ${item.qualityScore}` : '待审'}
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    );
                                })()}
                                {/* Documents from task */}
                                {loading ? (
                                    <div className="admin-card-loading">加载中...</div>
                                ) : documents.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                        <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>已导入文档</h4>
                                        {documents.slice(0, 10).map(doc => (
                                            <div key={doc.id} style={{ padding: '8px 10px', fontSize: 13, display: 'flex', gap: 8, alignItems: 'center' }}>
                                                <DocumentStatusIcon status={doc.status} />
                                                <span>{doc.title}</span>
                                                <span style={{ color: 'hsl(var(--color-text-muted))', fontSize: 11 }}>{DocumentStatusText(doc.status)}</span>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </>
                        ) : (
                            <div style={{ textAlign: 'center', padding: 40, color: 'hsl(var(--color-text-muted))' }}>选择一个任务查看详情</div>
                        )}
                    </div>
                </div>
                <footer className="admin-modal-foot">
                    <button className="admin-drawer-btn" onClick={onClose}>关闭</button>
                </footer>
            </div>
        </div>
    );
};

/* ============================================================
   ConfigModal — 知识库配置
   ============================================================ */
const ConfigModal = ({ config, onClose, onSave }) => {
    const [form, setForm] = useState(config ? { ...config } : {});
    const [saving, setSaving] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        try {
            await onSave(form);
        } finally {
            setSaving(false);
        }
    };

    if (!config) {
        return (
            <div className="admin-modal-mask" onClick={onClose}>
                <div className="admin-modal narrow" onClick={(e) => e.stopPropagation()}>
                    <header className="admin-modal-head"><h2>知识库配置</h2><button className="admin-modal-close" onClick={onClose}>×</button></header>
                    <div className="admin-modal-body"><p style={{ color: 'hsl(var(--color-text-muted))' }}>配置加载中...</p></div>
                    <footer className="admin-modal-foot"><button className="admin-drawer-btn" onClick={onClose}>关闭</button></footer>
                </div>
            </div>
        );
    }

    return (
        <div className="admin-modal-mask" onClick={onClose}>
            <div className="admin-modal narrow" onClick={(e) => e.stopPropagation()}>
                <header className="admin-modal-head">
                    <h2>知识库配置</h2>
                    <button className="admin-modal-close" onClick={onClose}>×</button>
                </header>
                <div className="admin-modal-body">
                    <div className="admin-modal-grid">
                        <div className="admin-form-group">
                            <label>默认切片大小</label>
                            <input type="number" value={form.defaultChunkSize ?? ''} onChange={(e) => setForm(s => ({ ...s, defaultChunkSize: Number(e.target.value) }))} />
                        </div>
                        <div className="admin-form-group">
                            <label>切片重叠</label>
                            <input type="number" value={form.chunkOverlap ?? ''} onChange={(e) => setForm(s => ({ ...s, chunkOverlap: Number(e.target.value) }))} />
                        </div>
                        <div className="admin-form-group full">
                            <label>最大 Token 数</label>
                            <input type="number" value={form.defaultMaxTokens ?? ''} onChange={(e) => setForm(s => ({ ...s, defaultMaxTokens: Number(e.target.value) }))} />
                        </div>
                    </div>
                </div>
                <footer className="admin-modal-foot">
                    <button className="admin-drawer-btn" onClick={onClose}>取消</button>
                    <button className="admin-drawer-btn primary" onClick={handleSave} disabled={saving}>
                        {saving ? '保存中...' : '保存'}
                    </button>
                </footer>
            </div>
        </div>
    );
};

export default KnowledgeTab;
