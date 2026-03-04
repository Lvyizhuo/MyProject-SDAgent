import React, { useState, useEffect, useCallback } from 'react';
import {
    Folder,
    FolderPlus,
    FileText,
    Upload,
    Settings,
    Trash2,
    RefreshCw,
    Search,
    ChevronRight,
    ChevronDown,
    Plus,
    Download,
    Eye,
    MoreVertical,
    X,
    CheckCircle2,
    AlertCircle,
    Loader2,
    Clock,
    Sparkles
} from 'lucide-react';
import './KnowledgeBaseTab.css';
import adminKnowledgeApi from '../../services/adminKnowledgeApi';

const KnowledgeBaseTab = () => {
    const [loading, setLoading] = useState(true);
    const [folders, setFolders] = useState([]);
    const [documents, setDocuments] = useState([]);
    const [selectedFolderId, setSelectedFolderId] = useState(null);
    const [expandedFolders, setExpandedFolders] = useState(new Set());
    const [showUploadDialog, setShowUploadDialog] = useState(false);
    const [showConfigPanel, setShowConfigPanel] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [embeddingModels, setEmbeddingModels] = useState([]);
    const [config, setConfig] = useState(null);
    const [showChunkDialog, setShowChunkDialog] = useState(false);
    const [chunkLoading, setChunkLoading] = useState(false);
    const [chunkPageData, setChunkPageData] = useState(null);
    const [chunkDocumentInfo, setChunkDocumentInfo] = useState(null);
    const [pendingDocuments, setPendingDocuments] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [message, setMessage] = useState({ text: '', type: '' });
    const [toast, setToast] = useState({ text: '', type: '', visible: false });
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState(null);

    const showToast = useCallback((text, type = 'success', duration = 3000) => {
        setToast({ text, type, visible: true });
        setTimeout(() => {
            setToast(prev => ({ ...prev, visible: false }));
        }, duration);
    }, []);

    const loadFolderTree = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getFolderTree();
            setFolders(data.folders || []);
        } catch (error) {
            console.error('Failed to load folder tree:', error);
        }
    }, []);

    const loadDocuments = useCallback(async (folderId = null, page = 0) => {
        try {
            const params = {
                folderId,
                page,
                size: pagination.size,
                status: filterStatus
            };
            const data = await adminKnowledgeApi.listDocuments(params);
            setDocuments(data.content || []);
            setPagination({
                page: data.page,
                size: data.size,
                totalElements: data.totalElements,
                totalPages: data.totalPages
            });
        } catch (error) {
            console.error('Failed to load documents:', error);
        }
    }, [pagination.size, filterStatus]);

    const loadEmbeddingModels = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getEmbeddingModels();
            setEmbeddingModels(data.models || []);
        } catch (error) {
            console.error('Failed to load embedding models:', error);
        }
    }, []);

    const loadConfig = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getConfig();
            setConfig(data);
        } catch (error) {
            console.error('Failed to load config:', error);
        }
    }, []);

    useEffect(() => {
        const initialize = async () => {
            setLoading(true);
            await Promise.all([
                loadFolderTree(),
                loadEmbeddingModels(),
                loadConfig()
            ]);
            await loadDocuments();
            setLoading(false);
        };
        initialize();
    }, [loadFolderTree, loadDocuments, loadEmbeddingModels, loadConfig]);

    useEffect(() => {
        loadDocuments(selectedFolderId, 0);
    }, [selectedFolderId, filterStatus]);

    const toggleFolder = (folderId) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            if (next.has(folderId)) {
                next.delete(folderId);
            } else {
                next.add(folderId);
            }
            return next;
        });
    };

    const handleCreateFolder = async () => {
        const name = prompt('请输入文件夹名称:');
        if (!name?.trim()) return;

        try {
            await adminKnowledgeApi.createFolder({
                parentId: selectedFolderId,
                name: name.trim(),
                description: ''
            });
            setMessage({ text: '文件夹创建成功', type: 'success' });
            setTimeout(() => setMessage({ text: '', type: '' }), 3000);
            await loadFolderTree();
        } catch (error) {
            setMessage({ text: '文件夹创建失败: ' + error.message, type: 'error' });
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        }
    };

    const handleDeleteFolder = async (folderId, event) => {
        event.stopPropagation();
        if (!window.confirm('确定要删除此文件夹及其所有内容吗？')) return;

        try {
            await adminKnowledgeApi.deleteFolder(folderId);
            setMessage({ text: '文件夹删除成功', type: 'success' });
            setTimeout(() => setMessage({ text: '', type: '' }), 3000);
            if (selectedFolderId === folderId) {
                setSelectedFolderId(null);
            }
            await loadFolderTree();
        } catch (error) {
            setMessage({ text: '文件夹删除失败: ' + error.message, type: 'error' });
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        }
    };

    const handleDeleteDocument = async (docId) => {
        if (!window.confirm('确定要删除此文档吗？')) return;

        try {
            await adminKnowledgeApi.deleteDocument(docId);
            showToast('文档删除成功', 'success');
            await loadDocuments(selectedFolderId, pagination.page);
        } catch (error) {
            setMessage({ text: '文档删除失败: ' + error.message, type: 'error' });
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        }
    };

    const handleReingestDocument = async (docId) => {
        try {
            await adminKnowledgeApi.reingestDocument(docId);
            setMessage({ text: '文档重新处理中...', type: 'success' });
            setTimeout(() => setMessage({ text: '', type: '' }), 3000);
            await loadDocuments(selectedFolderId, pagination.page);
        } catch (error) {
            setMessage({ text: '重新处理失败: ' + error.message, type: 'error' });
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        }
    };

    const handleDownloadDocument = async (doc) => {
        try {
            const blob = await adminKnowledgeApi.downloadDocument(doc.id);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = doc.fileName;
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (error) {
            setMessage({ text: '下载失败: ' + error.message, type: 'error' });
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        }
    };

    const handleViewChunks = async (doc) => {
        setChunkDocumentInfo(doc);
        setChunkLoading(true);
        setShowChunkDialog(true);
        setChunkPageData(null);

        try {
            const pageSize = 500;
            const firstPage = await adminKnowledgeApi.getDocumentChunks(doc.id, { page: 0, size: pageSize });
            const allChunks = [...(firstPage.content || [])];
            const totalPages = firstPage.totalPages || 1;

            for (let page = 1; page < totalPages; page += 1) {
                const nextPage = await adminKnowledgeApi.getDocumentChunks(doc.id, { page, size: pageSize });
                allChunks.push(...(nextPage.content || []));
            }

            setChunkPageData({
                ...firstPage,
                page: 0,
                size: allChunks.length,
                totalPages: 1,
                totalElements: firstPage.totalElements || allChunks.length,
                content: allChunks
            });
        } catch (error) {
            setMessage({ text: '获取分段失败: ' + error.message, type: 'error' });
            setShowChunkDialog(false);
            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
        } finally {
            setChunkLoading(false);
        }
    };

    const renderFolderTree = (folderList, depth = 0) => {
        return folderList.map(folder => (
            <div key={folder.id} className="folder-item" style={{ paddingLeft: `${depth * 16}px` }}>
                <div
                    className={`folder-node ${selectedFolderId === folder.id ? 'selected' : ''}`}
                    onClick={() => setSelectedFolderId(folder.id)}
                >
                    <button
                        className="folder-toggle"
                        onClick={(e) => {
                            e.stopPropagation();
                            toggleFolder(folder.id);
                        }}
                    >
                        {folder.children?.length > 0 ? (
                            expandedFolders.has(folder.id) ? <ChevronDown size={16} /> : <ChevronRight size={16} />
                        ) : (
                            <span style={{ width: '16px' }} />
                        )}
                    </button>
                    <Folder size={16} className="folder-icon" />
                    <span className="folder-name">{folder.name}</span>
                    <button
                        className="folder-delete"
                        onClick={(e) => handleDeleteFolder(folder.id, e)}
                        title="删除文件夹"
                    >
                        <Trash2 size={14} />
                    </button>
                </div>
                {folder.children?.length > 0 && expandedFolders.has(folder.id) && (
                    <div className="folder-children">
                        {renderFolderTree(folder.children, depth + 1)}
                    </div>
                )}
            </div>
        ));
    };

    const getStatusIcon = (status) => {
        switch (status) {
            case 'COMPLETED':
                return <CheckCircle2 size={16} className="status-completed" />;
            case 'PROCESSING':
                return <Loader2 size={16} className="status-processing animate-spin" />;
            case 'FAILED':
                return <AlertCircle size={16} className="status-failed" />;
            default:
                return <Clock size={16} className="status-pending" />;
        }
    };

    const getStatusText = (status) => {
        switch (status) {
            case 'COMPLETED':
                return '已完成';
            case 'PROCESSING':
                return '处理中';
            case 'FAILED':
                return '失败';
            default:
                return '等待中';
        }
    };

    const getStatusBarClass = (status) => {
        if (status === 'COMPLETED') return 'completed';
        if (status === 'FAILED') return 'failed';
        return 'processing';
    };

    const mergedDocuments = [...pendingDocuments, ...documents];

    if (loading) {
        return (
            <div className="knowledge-base-tab loading">
                <Loader2 size={32} className="animate-spin" />
                <p>加载中...</p>
            </div>
        );
    }

    return (
        <div className="knowledge-base-tab">
            <div className="kb-header">
                <div className="kb-title">
                    <h2>知识库管理</h2>
                    <p>管理政策文档、文件夹和向量索引配置</p>
                </div>
                <div className="kb-actions">
                    <button className="btn-secondary" onClick={() => setShowConfigPanel(true)}>
                        <Settings size={16} />
                        配置
                    </button>
                    <button className="btn-secondary" onClick={handleCreateFolder}>
                        <FolderPlus size={16} />
                        新建文件夹
                    </button>
                    <button className="btn-primary" onClick={() => setShowUploadDialog(true)}>
                        <Upload size={16} />
                        上传文档
                    </button>
                </div>
            </div>

            {message.text && (
                <div className={`kb-message ${message.type}`}>
                    {message.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
                    {message.text}
                </div>
            )}

            <div className="kb-content">
                <div className="kb-sidebar">
                    <div className="sidebar-header">
                        <h3>文件夹</h3>
                    </div>
                    <div className="folder-tree">
                        <div
                            className={`folder-node ${selectedFolderId === null ? 'selected' : ''}`}
                            onClick={() => setSelectedFolderId(null)}
                        >
                            <span style={{ width: '16px' }} />
                            <Folder size={16} className="folder-icon" />
                            <span className="folder-name">全部文档</span>
                        </div>
                        {renderFolderTree(folders)}
                    </div>
                </div>

                <div className="kb-main">
                    <div className="kb-toolbar">
                        <div className="toolbar-search">
                            <Search size={16} />
                            <input
                                type="text"
                                placeholder="搜索文档..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                            />
                        </div>
                        <div className="toolbar-filters">
                            <select
                                value={filterStatus || ''}
                                onChange={(e) => setFilterStatus(e.target.value || null)}
                            >
                                <option value="">全部状态</option>
                                <option value="PENDING">等待中</option>
                                <option value="PROCESSING">处理中</option>
                                <option value="COMPLETED">已完成</option>
                                <option value="FAILED">失败</option>
                            </select>
                        </div>
                        <button
                            className="btn-icon"
                            onClick={() => loadDocuments(selectedFolderId, pagination.page)}
                            title="刷新"
                        >
                            <RefreshCw size={16} />
                        </button>
                    </div>

                    <div className="document-list">
                        {mergedDocuments.length === 0 ? (
                            <div className="empty-state">
                                <FileText size={48} />
                                <p>暂无文档</p>
                            </div>
                        ) : (
                            mergedDocuments.map(doc => (
                                <div key={doc.id} className="document-item">
                                    <div className="doc-icon">
                                        <FileText size={24} />
                                    </div>
                                    <div className="doc-info">
                                        <div className="doc-title">{doc.title}</div>
                                        <div className="doc-meta">
                                            <span className="doc-folder">{doc.folderPath || '/'}</span>
                                            <span className="doc-size">
                                                {typeof doc.fileSize === 'number' ? `${(doc.fileSize / 1024).toFixed(1)} KB` : '-'}
                                            </span>
                                            <span className="doc-status">
                                                {getStatusIcon(doc.status)}
                                                {doc.status === 'PROCESSING' || doc.status === 'PENDING'
                                                    ? '正在解析'
                                                    : getStatusText(doc.status)}
                                            </span>
                                            {doc.chunkCount > 0 && (
                                                <span className="doc-chunks">{doc.chunkCount} 个切片</span>
                                            )}
                                        </div>
                                        <div className={`doc-status-bar ${getStatusBarClass(doc.status)}`}>
                                            <div className="doc-status-bar-fill" />
                                            <span className="doc-status-bar-text">
                                                {doc.status === 'PROCESSING' || doc.status === 'PENDING'
                                                    ? '正在解析'
                                                    : getStatusText(doc.status)}
                                            </span>
                                        </div>
                                        {doc.errorMessage && (
                                            <div className="doc-error">{doc.errorMessage}</div>
                                        )}
                                    </div>
                                    <div className="doc-actions">
                                        {!doc.isTransient && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleDownloadDocument(doc)}
                                                title="下载"
                                            >
                                                <Download size={16} />
                                            </button>
                                        )}
                                        {!doc.isTransient && doc.status === 'COMPLETED' && doc.chunkCount > 0 && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleViewChunks(doc)}
                                                title="查看分段"
                                            >
                                                <Eye size={16} />
                                            </button>
                                        )}
                                        {doc.status === 'FAILED' && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleReingestDocument(doc.id)}
                                                title="重新处理"
                                            >
                                                <RefreshCw size={16} />
                                            </button>
                                        )}
                                        {!doc.isTransient && (
                                            <button
                                                className="btn-icon danger"
                                                onClick={() => handleDeleteDocument(doc.id)}
                                                title="删除"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))
                        )}
                    </div>

                    {pagination.totalPages > 1 && (
                        <div className="kb-pagination">
                            <button
                                disabled={pagination.page === 0}
                                onClick={() => loadDocuments(selectedFolderId, pagination.page - 1)}
                            >
                                上一页
                            </button>
                            <span>
                                第 {pagination.page + 1} / {pagination.totalPages} 页，共 {pagination.totalElements} 条
                            </span>
                            <button
                                disabled={pagination.page >= pagination.totalPages - 1}
                                onClick={() => loadDocuments(selectedFolderId, pagination.page + 1)}
                            >
                                下一页
                            </button>
                        </div>
                    )}
                </div>
            </div>

            {showUploadDialog && (
                <UploadDialog
                    embeddingModels={embeddingModels}
                    folders={folders}
                    defaultFolderId={selectedFolderId}
                    onClose={() => {
                        setShowUploadDialog(false);
                        setUploadProgress(0);
                    }}
                    onUpload={async ({ formData, fileName, title, fileSize }) => {
                        const tempId = `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
                        setPendingDocuments(prev => [
                            {
                                id: tempId,
                                title: title || fileName,
                                fileName,
                                fileSize,
                                folderPath: selectedFolderId ? folders.find(f => f.id === selectedFolderId)?.path || '/' : '/',
                                status: 'PROCESSING',
                                chunkCount: 0,
                                isTransient: true
                            },
                            ...prev
                        ]);
                        setShowUploadDialog(false);
                        setUploadProgress(0);
                        try {
                            await adminKnowledgeApi.uploadDocument(formData, (progress) => {
                                setUploadProgress(progress);
                            });
                            setPendingDocuments(prev => prev.filter(doc => doc.id !== tempId));
                            showToast('上传成功，文档已进入解析队列', 'success');
                            setUploadProgress(0);
                            await loadDocuments(selectedFolderId, 0);
                        } catch (error) {
                            setPendingDocuments(prev => prev.filter(doc => doc.id !== tempId));
                            showToast('上传失败: ' + error.message, 'error', 5000);
                            setUploadProgress(0);
                        }
                    }}
                    uploadProgress={uploadProgress}
                />
            )}

            {showChunkDialog && (
                <ChunkPreviewDialog
                    documentInfo={chunkDocumentInfo}
                    chunkPageData={chunkPageData}
                    loading={chunkLoading}
                    onClose={() => {
                        setShowChunkDialog(false);
                        setChunkPageData(null);
                        setChunkDocumentInfo(null);
                    }}
                />
            )}

            {showConfigPanel && (
                <ConfigPanel
                    config={config}
                    embeddingModels={embeddingModels}
                    onClose={() => setShowConfigPanel(false)}
                    onSave={async (newConfig) => {
                        try {
                            await adminKnowledgeApi.updateConfig(newConfig);
                            setMessage({ text: '配置保存成功', type: 'success' });
                            setTimeout(() => setMessage({ text: '', type: '' }), 3000);
                            setShowConfigPanel(false);
                            await loadConfig();
                        } catch (error) {
                            setMessage({ text: '保存失败: ' + error.message, type: 'error' });
                            setTimeout(() => setMessage({ text: '', type: '' }), 5000);
                        }
                    }}
                />
            )}

            {toast.visible && (
                <div className={`kb-toast ${toast.type}`}>
                    {toast.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
                    <span>{toast.text}</span>
                </div>
            )}
        </div>
    );
};

const ChunkPreviewDialog = ({ documentInfo, chunkPageData, loading, onClose }) => {
    const chunks = chunkPageData?.content || [];

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog chunks-dialog" onClick={e => e.stopPropagation()}>
                <div className="dialog-header">
                    <h3>分段结果预览</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                <div className="dialog-body chunks-dialog-body">
                    <div className="chunks-summary">
                        <div className="chunks-title">{documentInfo?.title || '-'}</div>
                        <div className="chunks-meta">
                            <span>总切片：{chunkPageData?.totalElements ?? documentInfo?.chunkCount ?? 0}</span>
                            <span>模型：{documentInfo?.embeddingModel || '-'}</span>
                        </div>
                    </div>

                    {loading ? (
                        <div className="chunks-loading">
                            <Loader2 size={20} className="animate-spin" />
                            <span>正在加载分段结果...</span>
                        </div>
                    ) : chunks.length === 0 ? (
                        <div className="chunks-empty">暂无分段内容</div>
                    ) : (
                        <div className="chunks-list">
                            {chunks.map((chunk, index) => (
                                <div key={chunk.chunkId || index} className="chunk-item">
                                    <div className="chunk-head">
                                        <span>切片 #{chunk.chunkIndex ?? index + 1}</span>
                                        <span>{chunk.chunkChars ?? (chunk.content?.length || 0)} 字</span>
                                        <span>{chunk.splitStrategy || 'UNKNOWN'}</span>
                                    </div>
                                    <pre className="chunk-content">{chunk.content}</pre>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                <div className="dialog-footer">
                    <button type="button" className="btn-secondary" onClick={onClose}>
                        关闭
                    </button>
                </div>
            </div>
        </div>
    );
};

const FIELD_CACHE_KEY = 'kb-upload-field-cache-v1';

const UploadDialog = ({ embeddingModels, folders, defaultFolderId, onClose, onUpload, uploadProgress }) => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isExtracting, setIsExtracting] = useState(false);
    const [extractError, setExtractError] = useState('');
    const [activeFieldMenu, setActiveFieldMenu] = useState('');
    const [cache, setCache] = useState(() => {
        try {
            const parsed = JSON.parse(localStorage.getItem(FIELD_CACHE_KEY) || '{}');
            return {
                categories: Array.isArray(parsed.categories) ? parsed.categories : [],
                tags: Array.isArray(parsed.tags) ? parsed.tags : [],
                sources: Array.isArray(parsed.sources) ? parsed.sources : []
            };
        } catch {
            return { categories: [], tags: [], sources: [] };
        }
    });
    const [formData, setFormData] = useState({
        folderId: defaultFolderId,
        title: '',
        embeddingModel: embeddingModels.find(m => m.isDefault)?.id || '',
        category: '',
        tags: '',
        publishDate: '',
        source: '',
        validFrom: '',
        validTo: '',
        summary: ''
    });

    const uniquePush = (arr, value, max = 20) => {
        const normalized = (value || '').trim();
        if (!normalized) {
            return arr;
        }
        const filtered = [normalized, ...arr.filter(item => item !== normalized)];
        return filtered.slice(0, max);
    };

    const mergeCsvTags = (current, nextTags) => {
        const currentList = current
            .split(',')
            .map(tag => tag.trim())
            .filter(Boolean);
        const all = new Set([...currentList, ...nextTags.map(tag => tag.trim()).filter(Boolean)]);
        return Array.from(all).join(', ');
    };

    const saveCache = useCallback((currentFormData) => {
        const nextCache = {
            categories: uniquePush(cache.categories, currentFormData.category),
            tags: currentFormData.tags
                .split(',')
                .map(tag => tag.trim())
                .filter(Boolean)
                .reduce((acc, tag) => uniquePush(acc, tag, 40), cache.tags),
            sources: uniquePush(cache.sources, currentFormData.source)
        };
        setCache(nextCache);
        localStorage.setItem(FIELD_CACHE_KEY, JSON.stringify(nextCache));
    }, [cache]);

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            setSelectedFile(file);
            setExtractError('');
            setFormData(prev => ({ ...prev, title: file.name.replace(/\.[^/.]+$/, '') }));
        }
    };

    const handleExtract = async () => {
        if (!selectedFile) {
            setExtractError('请先选择文件，再进行智能提取');
            return;
        }
        setExtractError('');
        setIsExtracting(true);
        try {
            const metadata = await adminKnowledgeApi.extractDocumentMetadata(selectedFile);
            setFormData(prev => ({
                ...prev,
                title: metadata.title || prev.title,
                category: metadata.category || prev.category,
                tags: mergeCsvTags(prev.tags, metadata.tags || []),
                source: metadata.source || prev.source,
                summary: metadata.summary || prev.summary
            }));
        } catch (error) {
            setExtractError(error.message || '智能提取失败');
        } finally {
            setIsExtracting(false);
        }
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!selectedFile) return;

        const submitFormData = new FormData();
        submitFormData.append('file', selectedFile);
        if (formData.folderId) submitFormData.append('folderId', formData.folderId);
        if (formData.title) submitFormData.append('title', formData.title);
        if (formData.embeddingModel) submitFormData.append('embeddingModel', formData.embeddingModel);
        if (formData.category) submitFormData.append('category', formData.category);
        if (formData.tags) {
            formData.tags.split(',').forEach(tag => {
                if (tag.trim()) submitFormData.append('tags', tag.trim());
            });
        }
        if (formData.publishDate) submitFormData.append('publishDate', formData.publishDate);
        if (formData.source) submitFormData.append('source', formData.source);
        if (formData.validFrom) submitFormData.append('validFrom', formData.validFrom);
        if (formData.validTo) submitFormData.append('validTo', formData.validTo);
        if (formData.summary) submitFormData.append('summary', formData.summary);

        saveCache(formData);
        onUpload({
            formData: submitFormData,
            fileName: selectedFile.name,
            title: formData.title,
            fileSize: selectedFile.size
        });
    };

    const flattenFolders = (folderList, depth = 0) => {
        let result = [];
        for (const folder of folderList) {
            result.push({ ...folder, depth });
            if (folder.children) {
                result = result.concat(flattenFolders(folder.children, depth + 1));
            }
        }
        return result;
    };

    const formatFolderOptionLabel = (folder) => {
        if (!folder.depth) {
            return folder.name;
        }
        return `${'— '.repeat(folder.depth)}${folder.name}`;
    };

    const renderInputWithDropdown = (field, options, placeholder) => {
        const value = formData[field] || '';
        const hasOptions = Array.isArray(options) && options.length > 0;
        const isOpen = activeFieldMenu === field;

        return (
            <div className="input-with-menu">
                <input
                    type="text"
                    value={value}
                    onChange={e => setFormData(prev => ({ ...prev, [field]: e.target.value }))}
                    placeholder={placeholder}
                />
                <button
                    type="button"
                    className="input-menu-toggle"
                    title="选择历史值"
                    disabled={!hasOptions}
                    onClick={() => setActiveFieldMenu(prev => (prev === field ? '' : field))}
                >
                    <ChevronDown size={14} />
                </button>
                {isOpen && hasOptions && (
                    <div className="input-menu-list">
                        {options.map(option => (
                            <button
                                key={option}
                                type="button"
                                className="input-menu-item"
                                onClick={() => {
                                    if (field === 'tags') {
                                        setFormData(prev => ({ ...prev, tags: mergeCsvTags(prev.tags, [option]) }));
                                    } else {
                                        setFormData(prev => ({ ...prev, [field]: option }));
                                    }
                                    setActiveFieldMenu('');
                                }}
                            >
                                {option}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        );
    };

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog upload-dialog" onClick={e => e.stopPropagation()}>
                <div className="dialog-header">
                    <h3>上传文档</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>
                <form className="dialog-body" onSubmit={handleSubmit}>
                    <div className="upload-dropzone">
                        {selectedFile ? (
                            <div className="selected-file">
                                <FileText size={32} />
                                <div className="file-info">
                                    <div className="file-name">{selectedFile.name}</div>
                                    <div className="file-size">{(selectedFile.size / 1024).toFixed(1)} KB</div>
                                </div>
                                <button type="button" className="btn-remove" onClick={() => setSelectedFile(null)}>
                                    <X size={16} />
                                </button>
                            </div>
                        ) : (
                            <label className="dropzone-label">
                                <Upload size={32} />
                                <p>点击或拖拽文件到此处</p>
                                <p className="file-types">支持 PDF, DOC, DOCX, MD, TXT, HTML</p>
                                <input type="file" onChange={handleFileChange} accept=".pdf,.doc,.docx,.md,.txt,.html" />
                            </label>
                        )}
                    </div>

                    <div className="extract-toolbar">
                        <button
                            type="button"
                            className="btn-secondary"
                            onClick={handleExtract}
                            disabled={!selectedFile || isExtracting}
                        >
                            {isExtracting ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
                            {isExtracting ? '提取中...' : '智能提取'}
                        </button>
                        <span>自动填充分类、标签、来源、摘要，提交前请人工审核</span>
                    </div>
                    {extractError && <div className="extract-error">{extractError}</div>}

                    <div className="form-grid">
                        <div className="form-group">
                            <label>目标文件夹</label>
                            <div className="select-wrapper">
                                <select
                                    value={formData.folderId || ''}
                                    onChange={e => setFormData(prev => ({ ...prev, folderId: e.target.value ? Number(e.target.value) : null }))}
                                >
                                    <option value="">根目录</option>
                                    {flattenFolders(folders).map(folder => (
                                        <option key={folder.id} value={folder.id}>
                                            {formatFolderOptionLabel(folder)}
                                        </option>
                                    ))}
                                </select>
                                <ChevronDown size={14} className="select-arrow" aria-hidden="true" />
                            </div>
                        </div>

                        <div className="form-group">
                            <label>嵌入模型</label>
                            <div className="select-wrapper">
                                <select
                                    value={formData.embeddingModel}
                                    onChange={e => setFormData(prev => ({ ...prev, embeddingModel: e.target.value }))}
                                >
                                    {embeddingModels.map(model => (
                                        <option key={model.id} value={model.id}>
                                            {model.name} ({model.dimensions}维)
                                            {model.isDefault && ' (默认)'}
                                        </option>
                                    ))}
                                </select>
                                <ChevronDown size={14} className="select-arrow" aria-hidden="true" />
                            </div>
                        </div>
                    </div>

                    <div className="form-group">
                        <label>文档标题</label>
                        <input
                            type="text"
                            value={formData.title}
                            onChange={e => setFormData(prev => ({ ...prev, title: e.target.value }))}
                            placeholder="请输入文档标题"
                        />
                    </div>

                    <div className="form-grid">
                        <div className="form-group">
                            <label>分类</label>
                            {renderInputWithDropdown('category', cache.categories, '如: 补贴政策')}
                        </div>
                        <div className="form-group">
                            <label>标签 (逗号分隔)</label>
                            {renderInputWithDropdown('tags', cache.tags, '如: 济南市, 2024, 以旧换新')}
                        </div>
                    </div>

                    <div className="form-grid">
                        <div className="form-group">
                            <label>发布日期</label>
                            <input
                                type="date"
                                value={formData.publishDate}
                                onChange={e => setFormData(prev => ({ ...prev, publishDate: e.target.value }))}
                            />
                        </div>
                        <div className="form-group">
                            <label>来源</label>
                            {renderInputWithDropdown('source', cache.sources, '如: 济南市人民政府')}
                        </div>
                    </div>

                    <div className="form-group">
                        <label>摘要</label>
                        <textarea
                            value={formData.summary}
                            onChange={e => setFormData(prev => ({ ...prev, summary: e.target.value }))}
                            rows={3}
                            placeholder="文档摘要（可选）"
                        />
                    </div>

                    {uploadProgress > 0 && (
                        <div className="upload-progress">
                            <div className="progress-bar">
                                <div className="progress-fill" style={{ width: `${uploadProgress}%` }} />
                            </div>
                            <span>{uploadProgress}%</span>
                        </div>
                    )}

                    <div className="dialog-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            取消
                        </button>
                        <button type="submit" className="btn-primary" disabled={!selectedFile || uploadProgress > 0}>
                            上传并解析
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

const ConfigPanel = ({ config, embeddingModels, onClose, onSave }) => {
    const [formData, setFormData] = useState({});

    useEffect(() => {
        if (config) {
            setFormData({ ...config });
        }
    }, [config]);

    const handleChange = (e) => {
        const { name, value, type } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'number' ? parseInt(value) : value
        }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        onSave(formData);
    };

    if (!config) return null;

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog config-dialog" onClick={e => e.stopPropagation()}>
                <div className="dialog-header">
                    <h3>知识库配置</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>
                <form className="dialog-body" onSubmit={handleSubmit}>
                    <div className="form-section">
                        <h4>文档切片配置</h4>
                        <div className="form-grid">
                            <div className="form-group">
                                <label>切片大小 (chunkSize)</label>
                                <input
                                    type="number"
                                    name="chunkSize"
                                    value={formData.chunkSize || ''}
                                    onChange={handleChange}
                                    min="100"
                                    max="10000"
                                />
                            </div>
                            <div className="form-group">
                                <label>重叠大小 (chunkOverlap)</label>
                                <input
                                    type="number"
                                    name="chunkOverlap"
                                    value={formData.chunkOverlap || ''}
                                    onChange={handleChange}
                                    min="0"
                                    max="2000"
                                />
                            </div>
                        </div>
                    </div>

                    <div className="form-section">
                        <h4>默认嵌入模型</h4>
                        <div className="form-group">
                            <select
                                name="defaultEmbeddingModel"
                                value={formData.defaultEmbeddingModel || ''}
                                onChange={handleChange}
                            >
                                {embeddingModels.map(model => (
                                    <option key={model.id} value={model.id}>
                                        {model.name} ({model.dimensions}维)
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="form-section">
                        <h4>MinIO 存储配置</h4>
                        <div className="form-grid">
                            <div className="form-group">
                                <label>Endpoint</label>
                                <input
                                    type="text"
                                    name="minioEndpoint"
                                    value={formData.minioEndpoint || ''}
                                    onChange={handleChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Bucket 名称</label>
                                <input
                                    type="text"
                                    name="minioBucketName"
                                    value={formData.minioBucketName || ''}
                                    onChange={handleChange}
                                />
                            </div>
                        </div>
                        <div className="form-grid">
                            <div className="form-group">
                                <label>Access Key</label>
                                <input
                                    type="text"
                                    name="minioAccessKey"
                                    value={formData.minioAccessKey || ''}
                                    onChange={handleChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Secret Key</label>
                                <input
                                    type="password"
                                    name="minioSecretKey"
                                    value={formData.minioSecretKey || ''}
                                    onChange={handleChange}
                                />
                            </div>
                        </div>
                    </div>

                    <div className="dialog-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            取消
                        </button>
                        <button type="submit" className="btn-primary">
                            保存
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default KnowledgeBaseTab;
