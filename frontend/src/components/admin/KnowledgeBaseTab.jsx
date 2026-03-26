import React, { useState, useEffect, useCallback } from 'react';
import {
    Folder,
    FolderPlus,
    FolderInput,
    FileText,
    Link2,
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
    Sparkles,
    ExternalLink,
    Square,
    CheckSquare
} from 'lucide-react';
import './KnowledgeBaseTab.css';
import adminKnowledgeApi from '../../services/adminKnowledgeApi';
import { useAdminConsole } from './useAdminConsole';

const flattenFoldersTree = (folderList, depth = 0) => {
    let result = [];
    for (const folder of folderList) {
        result.push({ ...folder, depth });
        if (folder.children) {
            result = result.concat(flattenFoldersTree(folder.children, depth + 1));
        }
    }
    return result;
};

const KnowledgeBaseTab = () => {
    const { notify, confirm, prompt } = useAdminConsole();
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
    const [urlImportJobs, setUrlImportJobs] = useState([]);
    const [urlImportCandidates, setUrlImportCandidates] = useState([]);
    const [showUrlImportDialog, setShowUrlImportDialog] = useState(false);
    const [showTaskListDialog, setShowTaskListDialog] = useState(false);
    const [showImportPreviewDialog, setShowImportPreviewDialog] = useState(false);
    const [selectedImportItem, setSelectedImportItem] = useState(null);
    const [selectedTaskId, setSelectedTaskId] = useState(null);
    const [taskDocuments, setTaskDocuments] = useState([]);
    const [taskPagination, setTaskPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [pagination, setPagination] = useState({ page: 0, size: 20, totalElements: 0, totalPages: 0 });
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState(null);
    const [selectedDocumentIds, setSelectedDocumentIds] = useState([]);
    const [showBatchMoveDialog, setShowBatchMoveDialog] = useState(false);
    const [archiveBusy, setArchiveBusy] = useState(false);

    const loadFolderTree = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getFolderTree();
            setFolders(data.folders || []);
        } catch (error) {
            console.error('Failed to load folder tree:', error);
            notify({ text: '加载文件夹树失败', type: 'error', source: '管理员-知识库' });
        }
    }, [notify]);

    const loadDocuments = useCallback(async (folderId = null, page = 0, keyword = '') => {
        try {
            const params = {
                folderId,
                page,
                size: pagination.size,
                status: filterStatus,
                q: keyword?.trim() || null
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
            notify({ text: '加载文档列表失败', type: 'error', source: '管理员-知识库' });
        }
    }, [filterStatus, notify, pagination.size]);

    const loadTaskDocuments = useCallback(async (taskId, page = 0, keyword = '') => {
        if (!taskId) {
            setTaskDocuments([]);
            setTaskPagination(prev => ({ ...prev, page: 0, totalElements: 0, totalPages: 0 }));
            return;
        }

        try {
            const params = {
                importJobId: taskId,
                page,
                size: taskPagination.size,
                status: filterStatus,
                q: keyword?.trim() || null
            };
            const data = await adminKnowledgeApi.listDocuments(params);
            setTaskDocuments(data.content || []);
            setTaskPagination({
                page: data.page,
                size: data.size,
                totalElements: data.totalElements,
                totalPages: data.totalPages
            });
        } catch (error) {
            console.error('Failed to load task documents:', error);
            notify({ text: '加载任务文档失败', type: 'error', source: '管理员-知识库' });
        }
    }, [filterStatus, notify, taskPagination.size]);

    const loadEmbeddingModels = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getEmbeddingModels();
            setEmbeddingModels(data.models || []);
        } catch (error) {
            console.error('Failed to load embedding models:', error);
            notify({ text: '加载嵌入模型列表失败', type: 'error', source: '管理员-知识库' });
        }
    }, [notify]);

    const loadConfig = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.getConfig();
            setConfig(data);
        } catch (error) {
            console.error('Failed to load config:', error);
            notify({ text: '加载知识库配置失败', type: 'error', source: '管理员-知识库' });
        }
    }, [notify]);

    const loadUrlImports = useCallback(async () => {
        try {
            const data = await adminKnowledgeApi.listUrlImports();
            const nextJobs = data.jobs || [];
            setUrlImportJobs(nextJobs);
            setUrlImportCandidates(data.candidates || []);
            setSelectedTaskId(prev => {
                if (!nextJobs.length) {
                    return null;
                }
                if (prev && nextJobs.some(job => job.id === prev)) {
                    return prev;
                }
                return nextJobs[0].id;
            });
        } catch (error) {
            console.error('Failed to load url imports:', error);
        }
    }, []);

    useEffect(() => {
        const initialize = async () => {
            setLoading(true);
            await Promise.all([
                loadFolderTree(),
                loadEmbeddingModels(),
                loadConfig(),
                loadUrlImports()
            ]);
            await loadDocuments();
            setLoading(false);
        };
        initialize();
    }, [loadFolderTree, loadDocuments, loadEmbeddingModels, loadConfig, loadUrlImports]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            loadDocuments(selectedFolderId, 0, searchQuery);
        }, 250);
        return () => window.clearTimeout(timer);
    }, [filterStatus, loadDocuments, searchQuery, selectedFolderId]);

    useEffect(() => {
        if (!showTaskListDialog || !selectedTaskId) {
            return undefined;
        }
        const timer = window.setTimeout(() => {
            loadTaskDocuments(selectedTaskId, 0, searchQuery);
        }, 250);
        return () => window.clearTimeout(timer);
    }, [filterStatus, loadTaskDocuments, searchQuery, selectedTaskId, showTaskListDialog]);

    useEffect(() => {
        setSelectedDocumentIds([]);
    }, [selectedFolderId, filterStatus, searchQuery]);

    useEffect(() => {
        const timer = window.setInterval(() => {
            loadUrlImports();
        }, 15000);
        return () => window.clearInterval(timer);
    }, [loadUrlImports]);

    useEffect(() => {
        const hasProcessingDocuments = documents.some(doc => doc.status === 'PENDING' || doc.status === 'PROCESSING');
        if (!hasProcessingDocuments && pendingDocuments.length === 0) {
            return undefined;
        }

        const timer = window.setInterval(() => {
            loadDocuments(selectedFolderId, pagination.page, searchQuery);
        }, 5000);

        return () => window.clearInterval(timer);
    }, [documents, loadDocuments, pagination.page, pendingDocuments.length, searchQuery, selectedFolderId]);

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
        const name = await prompt({
            title: '新建文件夹',
            message: '请输入知识库文件夹名称，用于分类政策文档。',
            label: '文件夹名称',
            placeholder: '例如：2025 家电补贴政策',
            confirmText: '创建'
        });
        if (name === null) {
            return;
        }

        const trimmedName = name.trim();
        if (!trimmedName) {
            notify({ text: '文件夹名称不能为空', type: 'warning', source: '管理员-知识库' });
            return;
        }

        try {
            await adminKnowledgeApi.createFolder({
                parentId: selectedFolderId,
                name: trimmedName,
                description: ''
            });
            notify({ text: '文件夹创建成功', type: 'success', source: '管理员-知识库' });
            await loadFolderTree();
        } catch (error) {
            notify({ text: '文件夹创建失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleDeleteFolder = async (folderId, event) => {
        event.stopPropagation();
        const confirmed = await confirm({
            title: '删除文件夹',
            message: '确定要删除此文件夹及其全部内容吗？已关联的文档也会一并移除。',
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            await adminKnowledgeApi.deleteFolder(folderId);
            notify({ text: '文件夹删除成功', type: 'success', source: '管理员-知识库' });
            if (selectedFolderId === folderId) {
                setSelectedFolderId(null);
            }
            await loadFolderTree();
        } catch (error) {
            notify({ text: '文件夹删除失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleDeleteDocument = async (docId) => {
        const confirmed = await confirm({
            title: '删除文档',
            message: '确定要删除这份文档吗？删除后无法恢复。',
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            await adminKnowledgeApi.deleteDocument(docId);
            notify({ text: '文档删除成功', type: 'success', source: '管理员-知识库' });
            await loadDocuments(selectedFolderId, pagination.page, searchQuery);
            if (showTaskListDialog && selectedTaskId) {
                await loadTaskDocuments(selectedTaskId, taskPagination.page, searchQuery);
            }
        } catch (error) {
            notify({ text: '文档删除失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleToggleDocumentSelection = (docId) => {
        setSelectedDocumentIds(prev => prev.includes(docId)
            ? prev.filter(id => id !== docId)
            : [...prev, docId]);
    };

    const handleToggleSelectAllDocuments = async () => {
        const allSelected = pagination.totalElements > 0 && selectedDocumentIds.length === pagination.totalElements;
        if (allSelected) {
            setSelectedDocumentIds([]);
            return;
        }

        try {
            const result = await adminKnowledgeApi.listDocumentSelection({
                folderId: selectedFolderId,
                status: filterStatus,
                q: searchQuery?.trim() || null
            });
            const uniqueIds = [...new Set(result.ids || [])];
            setSelectedDocumentIds(uniqueIds);
            notify({
                text: `已选中当前范围内 ${uniqueIds.length} 份文档`,
                type: 'success',
                source: '管理员-知识库'
            });
        } catch (error) {
            notify({ text: '全选失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleBatchDeleteDocuments = async () => {
        const uniqueSelectedIds = [...new Set(selectedDocumentIds)];
        if (uniqueSelectedIds.length === 0) {
            notify({ text: '请先选择需要删除的文档', type: 'warning', source: '管理员-知识库' });
            return;
        }

        const confirmed = await confirm({
            title: '批量删除文档',
            message: `确定要删除选中的 ${uniqueSelectedIds.length} 份文档吗？删除后无法恢复。`,
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        try {
            await adminKnowledgeApi.batchDeleteDocuments(uniqueSelectedIds);
            notify({ text: '批量删除成功', type: 'success', source: '管理员-知识库' });
            setSelectedDocumentIds([]);
            await loadDocuments(selectedFolderId, pagination.page, searchQuery);
        } catch (error) {
            notify({ text: '批量删除失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleBatchMoveDocuments = async (targetFolderId) => {
        if (selectedDocumentIds.length === 0) {
            notify({ text: '请先选择需要移动的文档', type: 'warning', source: '管理员-知识库' });
            return;
        }

        try {
            await adminKnowledgeApi.batchMoveDocuments(selectedDocumentIds, targetFolderId);
            notify({ text: '批量移动成功', type: 'success', source: '管理员-知识库' });
            setShowBatchMoveDialog(false);
            setSelectedDocumentIds([]);
            await Promise.all([
                loadFolderTree(),
                loadDocuments(selectedFolderId, pagination.page, searchQuery)
            ]);
        } catch (error) {
            notify({ text: '批量移动失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleReingestDocument = async (docId) => {
        try {
            await adminKnowledgeApi.reingestDocument(docId);
            notify({ text: '文档已加入重新处理队列', type: 'info', source: '管理员-知识库' });
            await loadDocuments(selectedFolderId, pagination.page, searchQuery);
        } catch (error) {
            notify({ text: '重新处理失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleCreateUrlImport = async ({ url, folderId, embeddingModel, titleOverride, remark }) => {
        try {
            const result = await adminKnowledgeApi.createUrlImport({
                url,
                folderId,
                embeddingModel,
                titleOverride,
                remark
            });
            notify({
                text: `网站导入任务已创建，任务 #${result.id} 正在抓取中`,
                type: 'info',
                source: '管理员-知识库'
            });
            setSelectedTaskId(result.id);
            setShowUrlImportDialog(false);
            await loadUrlImports();
        } catch (error) {
            notify({ text: '创建网站导入失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handlePreviewImportItem = async (itemId) => {
        try {
            const detail = await adminKnowledgeApi.getUrlImportItem(itemId);
            setSelectedImportItem(detail);
            setShowImportPreviewDialog(true);
        } catch (error) {
            notify({ text: '加载待入库内容失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleConfirmImportItem = async (itemId, payload) => {
        try {
            await adminKnowledgeApi.confirmUrlImport(itemId, payload);
            notify({ text: '候选内容已进入知识库处理流程', type: 'success', source: '管理员-知识库' });
            setShowImportPreviewDialog(false);
            setSelectedImportItem(null);
            await Promise.all([
                loadUrlImports(),
                loadDocuments(selectedFolderId, 0, searchQuery),
                loadTaskDocuments(selectedTaskId, 0, searchQuery)
            ]);
        } catch (error) {
            notify({ text: '确认入库失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleBatchConfirmImportItems = async (itemIds) => {
        if (!itemIds.length) {
            notify({ text: '当前没有可一键入库的候选内容', type: 'warning', source: '管理员-知识库' });
            return;
        }

        const folderHint = selectedFolderId
            ? `将统一入库到当前目录。`
            : '将按各自导入任务的默认目录入库。';
        const confirmed = await confirm({
            title: '一键入库',
            message: `确定要一键审批并入库当前 ${itemIds.length} 条候选内容吗？${folderHint}`,
            confirmText: '确认入库',
            tone: 'primary'
        });
        if (!confirmed) {
            return;
        }

        try {
            const result = await adminKnowledgeApi.batchConfirmUrlImports({
                ids: itemIds,
                folderId: selectedFolderId || null
            });
            const summary = result.failedCount > 0
                ? `已入库 ${result.successCount} 条，失败 ${result.failedCount} 条`
                : `已完成 ${result.successCount} 条候选内容入库`;
            notify({ text: summary, type: result.failedCount > 0 ? 'warning' : 'success', source: '管理员-知识库' });
            await Promise.all([
                loadUrlImports(),
                loadDocuments(selectedFolderId, 0, searchQuery),
                loadTaskDocuments(selectedTaskId, 0, searchQuery)
            ]);
        } catch (error) {
            notify({ text: '一键入库失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleRejectImportItem = async (itemId) => {
        const reason = await prompt({
            title: '驳回待入库内容',
            message: '请输入驳回原因，便于后续回溯。',
            label: '驳回原因',
            placeholder: '例如：活动新闻，非政策正文',
            confirmText: '确认驳回'
        });
        if (reason === null) {
            return;
        }

        const trimmedReason = reason.trim();
        if (!trimmedReason) {
            notify({ text: '驳回原因不能为空', type: 'warning', source: '管理员-知识库' });
            return;
        }

        try {
            await adminKnowledgeApi.rejectUrlImport(itemId, { reason: trimmedReason });
            notify({ text: '待入库内容已驳回', type: 'success', source: '管理员-知识库' });
            setShowImportPreviewDialog(false);
            setSelectedImportItem(null);
            await loadUrlImports();
        } catch (error) {
            notify({ text: '驳回失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleCancelImportJob = async (jobId) => {
        const confirmed = await confirm({
            title: '取消网站导入任务',
            message: '确定要取消这个网站导入任务吗？已生成的待入库候选会保留当前状态。',
            confirmText: '确认取消',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            await adminKnowledgeApi.cancelUrlImport(jobId);
            notify({ text: '网站导入任务已取消', type: 'success', source: '管理员-知识库' });
            await loadUrlImports();
        } catch (error) {
            notify({ text: '取消任务失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleDeleteImportJob = async (jobId) => {
        const confirmed = await confirm({
            title: '删除网站导入任务',
            message: '确定要彻底删除这个网站导入任务吗？未入库的候选内容会一并移除。',
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            await adminKnowledgeApi.deleteUrlImport(jobId);
            notify({ text: '网站导入任务已删除', type: 'success', source: '管理员-知识库' });
            await loadUrlImports();
        } catch (error) {
            notify({ text: '删除任务失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleDeleteImportItem = async (itemId) => {
        const confirmed = await confirm({
            title: '删除待入库内容',
            message: '确定要删除这条抓取内容吗？删除后将不会出现在审批列表中。',
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) return;

        try {
            await adminKnowledgeApi.deleteUrlImportItem(itemId);
            notify({ text: '待入库内容已删除', type: 'success', source: '管理员-知识库' });
            if (selectedImportItem?.id === itemId) {
                setShowImportPreviewDialog(false);
                setSelectedImportItem(null);
            }
            await Promise.all([
                loadUrlImports(),
                loadDocuments(selectedFolderId, 0, searchQuery),
                loadTaskDocuments(selectedTaskId, 0, searchQuery)
            ]);
        } catch (error) {
            notify({ text: '删除待入库内容失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handlePreviewDocument = async (doc) => {
        try {
            const result = await adminKnowledgeApi.getDocumentPreview(doc.id);
            if (!result?.previewUrl) {
                throw new Error('未获取到可预览地址');
            }
            window.open(result.previewUrl, '_blank', 'noopener,noreferrer');
        } catch (error) {
            notify({ text: '打开预览失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleDownloadDocument = async (doc) => {
        if (doc.externalSourceUrl) {
            window.open(doc.externalSourceUrl, '_blank', 'noopener,noreferrer');
            notify({ text: '该文档来自网站抓取，已跳转至原网页', type: 'info', source: '管理员-知识库' });
            return;
        }

        try {
            const blob = await adminKnowledgeApi.downloadDocument(doc.id);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = doc.fileName;
            a.click();
            window.URL.revokeObjectURL(url);
            notify({ text: `已开始下载：${doc.fileName}`, type: 'info', source: '管理员-知识库' });
        } catch (error) {
            notify({ text: '下载失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        }
    };

    const handleExportArchive = async () => {
        try {
            setArchiveBusy(true);
            const blob = await adminKnowledgeApi.exportArchive();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `knowledge-archive-${new Date().toISOString().slice(0, 10)}.zip`;
            a.click();
            window.URL.revokeObjectURL(url);
            notify({ text: '知识库导出已开始，请保存生成的 zip 包。', type: 'success', source: '管理员-知识库' });
        } catch (error) {
            notify({ text: '导出知识库失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        } finally {
            setArchiveBusy(false);
        }
    };

    const handleImportArchive = async (file) => {
        if (!file) {
            return;
        }

        const confirmed = await confirm({
            title: '导入知识库归档',
            message: `确认导入 ${file.name} 吗？系统会自动创建缺失文件夹，并跳过已存在的重复文档。`,
            confirmText: '开始导入'
        });
        if (!confirmed) {
            return;
        }

        try {
            setArchiveBusy(true);
            const result = await adminKnowledgeApi.importArchive(file);
            const summary = `导入完成：新增 ${result.importedCount || 0}，跳过 ${result.skippedCount || 0}，失败 ${result.failedCount || 0}`;
            notify({ text: summary, type: result.failedCount > 0 ? 'warning' : 'success', source: '管理员-知识库' });
            if (Array.isArray(result.messages) && result.messages.length > 0) {
                console.info('知识库导入详情:', result.messages);
            }
            await Promise.all([
                loadFolderTree(),
                loadDocuments(selectedFolderId, 0, searchQuery),
                loadUrlImports()
            ]);
        } catch (error) {
            notify({ text: '导入知识库失败: ' + error.message, type: 'error', source: '管理员-知识库' });
        } finally {
            setArchiveBusy(false);
        }
    };

    const handleImportArchiveClick = () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.zip,application/zip';
        input.onchange = () => {
            const file = input.files?.[0];
            if (file) {
                handleImportArchive(file);
            }
        };
        input.click();
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
            notify({ text: '获取分段失败: ' + error.message, type: 'error', source: '管理员-知识库' });
            setShowChunkDialog(false);
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
            case 'CANCELED':
                return '已取消';
            default:
                return '等待中';
        }
    };

    const getImportStatusText = (status) => {
        switch (status) {
            case 'CRAWLING':
                return '抓取中';
            case 'PROCESSING':
                return '解析中';
            case 'WAITING_CONFIRM':
                return '待入库';
            case 'PARTIALLY_IMPORTED':
                return '部分已入库';
            case 'COMPLETED':
                return '已完成';
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

    const normalizedSearchQuery = searchQuery.trim().toLowerCase();

    const jobMap = new Map(urlImportJobs.map(job => [job.id, job]));

    const taskJobs = urlImportJobs.filter(job => {
        if (!normalizedSearchQuery) {
            return true;
        }
        const haystack = `${job.title || ''} ${job.sourceUrl || ''} ${job.sourceSite || ''}`.toLowerCase();
        return haystack.includes(normalizedSearchQuery);
    });

    const selectedTask = urlImportJobs.find(job => job.id === selectedTaskId) || null;

    const candidateDocs = urlImportCandidates
        .map(item => {
            const job = jobMap.get(item.jobId);
            return {
        id: `import-item-${item.id}`,
        title: item.title,
        fileName: item.sourceUrl,
        fileSize: null,
        folderPath: job?.targetFolderPath || '/待入库',
        status: 'PENDING',
        chunkCount: 0,
        errorMessage: item.reviewComment || item.errorMessage,
        isUrlImportCandidate: true,
        importItemId: item.id,
        importStatus: item.reviewStatus,
        jobId: item.jobId,
        targetFolderId: job?.targetFolderId ?? null,
        taskTitle: job?.title || `网站导入任务 #${item.jobId}`,
        sourceUrl: item.sourceUrl,
        publishDate: item.publishDate,
        qualityScore: item.qualityScore,
        suspectedDuplicate: item.suspectedDuplicate,
        previewText: item.summary || item.cleanedText || ''
    };
        })
        .filter(doc => {
            if (selectedFolderId !== null && doc.targetFolderId !== selectedFolderId) {
                return false;
            }
            if (!normalizedSearchQuery) {
                return true;
            }
            const haystack = `${doc.title || ''} ${doc.fileName || ''} ${doc.sourceUrl || ''}`.toLowerCase();
            return haystack.includes(normalizedSearchQuery);
        });

    const visibleKnowledgeDocuments = [...pendingDocuments, ...documents];

    const mergedDocuments = [...candidateDocs, ...visibleKnowledgeDocuments];

    const taskCandidateDocs = candidateDocs.filter(doc => !selectedTask || doc.jobId === selectedTask.id);
    const taskMergedDocuments = selectedTask
        ? [...taskCandidateDocs, ...taskDocuments]
        : [];

    const visibleCandidateIds = mergedDocuments
        .filter(doc => doc.isUrlImportCandidate)
        .map(doc => doc.importItemId);

    const taskVisibleCandidateIds = taskMergedDocuments
        .filter(doc => doc.isUrlImportCandidate)
        .map(doc => doc.importItemId);

    const allMatchingSelected = pagination.totalElements > 0 && selectedDocumentIds.length === pagination.totalElements;

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
                    <button className="btn-secondary" onClick={() => setShowUrlImportDialog(true)}>
                        <Link2 size={16} />
                        网站导入
                    </button>
                    <button className="btn-secondary" onClick={() => setShowTaskListDialog(true)}>
                        <Clock size={16} />
                        任务列表
                    </button>
                    <button className="btn-secondary" onClick={handleExportArchive} disabled={archiveBusy}>
                        <Download size={16} />
                        导出知识库
                    </button>
                    <button className="btn-secondary" onClick={handleImportArchiveClick} disabled={archiveBusy}>
                        <FolderInput size={16} />
                        导入知识库
                    </button>
                    <button className="btn-primary" onClick={() => setShowUploadDialog(true)}>
                        <Upload size={16} />
                        上传文档
                    </button>
                </div>
            </div>
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
                            onClick={() => {
                                loadDocuments(selectedFolderId, pagination.page, searchQuery);
                                loadUrlImports();
                                if (showTaskListDialog && selectedTaskId) {
                                    loadTaskDocuments(selectedTaskId, taskPagination.page, searchQuery);
                                }
                            }}
                            title="刷新"
                        >
                            <RefreshCw size={16} />
                        </button>
                    </div>
                    <div className="kb-batch-toolbar">
                                <button
                                    className="btn-secondary"
                                    onClick={handleToggleSelectAllDocuments}
                                    disabled={pagination.totalElements === 0}
                                >
                                    {allMatchingSelected ? <CheckSquare size={16} /> : <Square size={16} />}
                                    {allMatchingSelected ? '取消全选' : '全选当前目录'}
                                </button>
                                <span className="kb-batch-count">
                                    已选 {selectedDocumentIds.length} / {pagination.totalElements} 份文档
                                </span>
                                <button
                                    className="btn-secondary"
                                    onClick={() => setShowBatchMoveDialog(true)}
                                    disabled={selectedDocumentIds.length === 0}
                                >
                                    <FolderInput size={16} />
                                    批量移动
                                </button>
                                <button
                                    className="btn-secondary danger-outline"
                                    onClick={handleBatchDeleteDocuments}
                                    disabled={selectedDocumentIds.length === 0}
                                >
                                    <Trash2 size={16} />
                                    批量删除
                                </button>
                                <button
                                    className="btn-primary"
                                    onClick={() => handleBatchConfirmImportItems(visibleCandidateIds)}
                                    disabled={visibleCandidateIds.length === 0}
                                >
                                    <Sparkles size={16} />
                                    一键入库 {visibleCandidateIds.length > 0 ? `(${visibleCandidateIds.length})` : ''}
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
                                    <div className="doc-selection">
                                        {!doc.isTransient && !doc.isImportJob && !doc.isUrlImportCandidate ? (
                                            <button
                                                className="btn-icon select-toggle"
                                                onClick={() => handleToggleDocumentSelection(doc.id)}
                                                title={selectedDocumentIds.includes(doc.id) ? '取消选择' : '选择文档'}
                                            >
                                                {selectedDocumentIds.includes(doc.id) ? <CheckSquare size={16} /> : <Square size={16} />}
                                            </button>
                                        ) : null}
                                    </div>
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
                                                {doc.isImportJob
                                                    ? getImportStatusText(doc.importStatus)
                                                    : doc.isUrlImportCandidate
                                                        ? '待入库'
                                                        : doc.status === 'PROCESSING' || doc.status === 'PENDING'
                                                    ? '正在解析'
                                                    : getStatusText(doc.status)}
                                            </span>
                                            {doc.chunkCount > 0 && (
                                                <span className="doc-chunks">{doc.chunkCount} 个切片</span>
                                            )}
                                            {doc.qualityScore != null && (
                                                <span className="doc-import-badge">评分 {doc.qualityScore}</span>
                                            )}
                                            {doc.taskTitle && (
                                                <span className="doc-import-badge">{doc.taskTitle}</span>
                                            )}
                                            {doc.isImportJob && (
                                                <span className="doc-import-badge">候选 {doc.candidateCount ?? 0}</span>
                                            )}
                                            {doc.isImportJob && (doc.importedCount ?? 0) > 0 && (
                                                <span className="doc-import-badge">已入库 {doc.importedCount}</span>
                                            )}
                                            {doc.isImportJob && (doc.rejectedCount ?? 0) > 0 && (
                                                <span className="doc-import-badge warning">已驳回 {doc.rejectedCount}</span>
                                            )}
                                            {doc.publishDate && (
                                                <span className="doc-import-badge">{doc.publishDate}</span>
                                            )}
                                            {doc.suspectedDuplicate && (
                                                <span className="doc-import-badge warning">疑似重复</span>
                                            )}
                                        </div>
                                        <div className={`doc-status-bar ${getStatusBarClass(doc.status)}`}>
                                            <div className="doc-status-bar-fill" />
                                            <span className="doc-status-bar-text">
                                                {doc.isImportJob
                                                    ? getImportStatusText(doc.importStatus)
                                                    : doc.isUrlImportCandidate
                                                        ? '等待管理员确认入库'
                                                        : doc.status === 'PROCESSING' || doc.status === 'PENDING'
                                                    ? '正在解析'
                                                    : getStatusText(doc.status)}
                                            </span>
                                        </div>
                                        {doc.sourceUrl && (
                                            <div className="doc-source-link">
                                                <a href={doc.sourceUrl} target="_blank" rel="noreferrer">
                                                    <ExternalLink size={14} />
                                                    查看原文
                                                </a>
                                            </div>
                                        )}
                                        {doc.previewText && (
                                            <div className="doc-preview-text">{doc.previewText}</div>
                                        )}
                                        {doc.errorMessage && (
                                            <div className="doc-error">{doc.errorMessage}</div>
                                        )}
                                    </div>
                                    <div className="doc-actions">
                                        {doc.isUrlImportCandidate && (
                                            <>
                                                <button
                                                    className="btn-icon"
                                                    onClick={() => handlePreviewImportItem(doc.importItemId)}
                                                    title="预览待入库内容"
                                                >
                                                    <Eye size={16} />
                                                </button>
                                                <button
                                                    className="btn-icon success"
                                                    onClick={() => handleConfirmImportItem(doc.importItemId, { folderId: selectedFolderId || null })}
                                                    title="确认入库"
                                                >
                                                    <CheckCircle2 size={16} />
                                                </button>
                                                <button
                                                    className="btn-icon danger"
                                                    onClick={() => handleRejectImportItem(doc.importItemId)}
                                                    title="驳回"
                                                >
                                                    <X size={16} />
                                                </button>
                                                <button
                                                    className="btn-icon danger"
                                                    onClick={() => handleDeleteImportItem(doc.importItemId)}
                                                    title="删除待入库内容"
                                                >
                                                    <Trash2 size={16} />
                                                </button>
                                            </>
                                        )}
                                        {!doc.isTransient && !doc.isImportJob && !doc.isUrlImportCandidate && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handlePreviewDocument(doc)}
                                                title="预览"
                                            >
                                                <Eye size={16} />
                                            </button>
                                        )}
                                        {!doc.isTransient && !doc.isImportJob && !doc.isUrlImportCandidate && !doc.externalSourceUrl && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleDownloadDocument(doc)}
                                                title="下载"
                                            >
                                                <Download size={16} />
                                            </button>
                                        )}
                                        {!doc.isTransient && !doc.isImportJob && !doc.isUrlImportCandidate && doc.status === 'COMPLETED' && doc.chunkCount > 0 && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleViewChunks(doc)}
                                                title="查看分段"
                                            >
                                                <Eye size={16} />
                                            </button>
                                        )}
                                        {!doc.isImportJob && !doc.isUrlImportCandidate && doc.status === 'FAILED' && (
                                            <button
                                                className="btn-icon"
                                                onClick={() => handleReingestDocument(doc.id)}
                                                title="重新处理"
                                            >
                                                <RefreshCw size={16} />
                                            </button>
                                        )}
                                        {!doc.isTransient && !doc.isImportJob && !doc.isUrlImportCandidate && (
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
                                        onClick={() => loadDocuments(selectedFolderId, pagination.page - 1, searchQuery)}
                                    >
                                        上一页
                                    </button>
                                    <span>
                                        第 {pagination.page + 1} / {pagination.totalPages} 页，共 {pagination.totalElements} 条
                                    </span>
                                    <button
                                        disabled={pagination.page >= pagination.totalPages - 1}
                                        onClick={() => loadDocuments(selectedFolderId, pagination.page + 1, searchQuery)}
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
                            const uploadedDocument = await adminKnowledgeApi.uploadDocument(formData, (progress) => {
                                setUploadProgress(progress);
                            });
                            setPendingDocuments(prev => prev.filter(doc => doc.id !== tempId));
                            notify({
                                text: `上传成功，已转入后台处理：${uploadedDocument?.title || title || fileName}`,
                                type: 'success',
                                source: '管理员-知识库'
                            });
                            setUploadProgress(0);
                            await loadDocuments(selectedFolderId, 0, searchQuery);
                        } catch (error) {
                            setPendingDocuments(prev => prev.filter(doc => doc.id !== tempId));
                            notify({ text: '上传失败: ' + error.message, type: 'error', source: '管理员-知识库' });
                            setUploadProgress(0);
                        }
                    }}
                    uploadProgress={uploadProgress}
                />
            )}

            {showTaskListDialog && (
                <TaskListDialog
                    jobs={taskJobs}
                    selectedTask={selectedTask}
                    selectedTaskId={selectedTaskId}
                    documents={taskMergedDocuments}
                    pagination={taskPagination}
                    onClose={() => setShowTaskListDialog(false)}
                    onRefresh={() => {
                        loadUrlImports();
                        if (selectedTaskId) {
                            loadTaskDocuments(selectedTaskId, taskPagination.page, searchQuery);
                        }
                    }}
                    onSelectTask={(taskId) => {
                        setSelectedTaskId(taskId);
                        loadTaskDocuments(taskId, 0, searchQuery);
                    }}
                    onCancelTask={handleCancelImportJob}
                    onDeleteTask={handleDeleteImportJob}
                    onPreviewImportItem={handlePreviewImportItem}
                    onConfirmImportItem={(itemId) => handleConfirmImportItem(itemId, { folderId: selectedFolderId || null })}
                    onRejectImportItem={handleRejectImportItem}
                    onDeleteImportItem={handleDeleteImportItem}
                    onPreviewDocument={handlePreviewDocument}
                    onDownloadDocument={handleDownloadDocument}
                    onViewChunks={handleViewChunks}
                    onReingestDocument={handleReingestDocument}
                    onDeleteDocument={handleDeleteDocument}
                    onBatchConfirm={() => handleBatchConfirmImportItems(taskVisibleCandidateIds)}
                    batchConfirmCount={taskVisibleCandidateIds.length}
                    getImportStatusText={getImportStatusText}
                    getStatusIcon={getStatusIcon}
                    getStatusText={getStatusText}
                    getStatusBarClass={getStatusBarClass}
                    onPageChange={(page) => loadTaskDocuments(selectedTaskId, page, searchQuery)}
                />
            )}

            {showUrlImportDialog && (
                <UrlImportDialog
                    embeddingModels={embeddingModels}
                    folders={folders}
                    defaultFolderId={selectedFolderId}
                    onClose={() => setShowUrlImportDialog(false)}
                    onSubmit={handleCreateUrlImport}
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

            {showImportPreviewDialog && selectedImportItem && (
                <UrlImportPreviewDialog
                    item={selectedImportItem}
                    folders={folders}
                    defaultFolderId={selectedFolderId}
                    onClose={() => {
                        setShowImportPreviewDialog(false);
                        setSelectedImportItem(null);
                    }}
                    onConfirm={payload => handleConfirmImportItem(selectedImportItem.id, payload)}
                    onReject={() => handleRejectImportItem(selectedImportItem.id)}
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
                            notify({ text: '配置保存成功', type: 'success', source: '管理员-知识库' });
                            setShowConfigPanel(false);
                            await loadConfig();
                        } catch (error) {
                            notify({ text: '保存失败: ' + error.message, type: 'error', source: '管理员-知识库' });
                        }
                    }}
                />
            )}

            {showBatchMoveDialog && (
                <BatchMoveDialog
                    folders={folders}
                    selectedCount={selectedDocumentIds.length}
                    defaultFolderId={selectedFolderId}
                    onClose={() => setShowBatchMoveDialog(false)}
                    onSubmit={handleBatchMoveDocuments}
                />
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

const TaskListDialog = ({
    jobs,
    selectedTask,
    selectedTaskId,
    documents,
    pagination,
    onClose,
    onRefresh,
    onSelectTask,
    onCancelTask,
    onDeleteTask,
    onPreviewImportItem,
    onConfirmImportItem,
    onRejectImportItem,
    onDeleteImportItem,
    onPreviewDocument,
    onDownloadDocument,
    onViewChunks,
    onReingestDocument,
    onDeleteDocument,
    onBatchConfirm,
    batchConfirmCount,
    getImportStatusText,
    getStatusIcon,
    getStatusText,
    getStatusBarClass,
    onPageChange
}) => {
    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog task-dialog" onClick={event => event.stopPropagation()}>
                <div className="dialog-header">
                    <div>
                        <h3>任务列表</h3>
                        <p className="task-dialog-subtitle">查看网站导入任务、抓取进度和入库结果</p>
                    </div>
                    <div className="task-dialog-header-actions">
                        <button className="btn-icon" onClick={onRefresh} title="刷新任务列表">
                            <RefreshCw size={16} />
                        </button>
                        <button className="btn-close" onClick={onClose}>
                            <X size={20} />
                        </button>
                    </div>
                </div>

                <div className="dialog-body task-dialog-body">
                    <div className="task-dialog-layout">
                        <aside className="task-dialog-sidebar">
                            <div className="task-list">
                                {jobs.length === 0 ? (
                                    <div className="task-empty-state">
                                        <Clock size={28} />
                                        <p>暂无网站导入任务</p>
                                    </div>
                                ) : (
                                    jobs.map(job => {
                                        const isSelected = selectedTaskId === job.id;
                                        const canCancelImportJob = ['CRAWLING', 'PROCESSING'].includes(job.status);
                                        const canDeleteImportJob = !canCancelImportJob;

                                        return (
                                            <div
                                                key={job.id}
                                                className={`task-item ${isSelected ? 'selected' : ''}`}
                                                onClick={() => onSelectTask(job.id)}
                                            >
                                                <div className="task-item-head">
                                                    <div className="task-item-title">{job.title || `网站导入任务 #${job.id}`}</div>
                                                    <span className={`task-status-badge ${job.status.toLowerCase()}`}>
                                                        {getImportStatusText(job.status)}
                                                    </span>
                                                </div>
                                                <div className="task-item-meta">
                                                    <span>{job.sourceSite || '网站导入'}</span>
                                                    <span>{job.targetFolderPath || '/'}</span>
                                                </div>
                                                <div className="task-item-url">{job.sourceUrl}</div>
                                                <div className="task-item-stats">
                                                    <span>抓取 {job.discoveredCount || 0}</span>
                                                    <span>待审 {job.candidateCount || 0}</span>
                                                    <span>已入库 {job.importedCount || 0}</span>
                                                    <span>已驳回 {job.rejectedCount || 0}</span>
                                                </div>
                                                {job.errorMessage && <div className="task-item-error">{job.errorMessage}</div>}
                                                <div className="task-item-actions">
                                                    {canCancelImportJob && (
                                                        <button
                                                            className="btn-icon danger"
                                                            onClick={(event) => {
                                                                event.stopPropagation();
                                                                onCancelTask(job.id);
                                                            }}
                                                            title="取消任务"
                                                        >
                                                            <X size={16} />
                                                        </button>
                                                    )}
                                                    {canDeleteImportJob && (
                                                        <button
                                                            className="btn-icon danger"
                                                            onClick={(event) => {
                                                                event.stopPropagation();
                                                                onDeleteTask(job.id);
                                                            }}
                                                            title="删除任务"
                                                        >
                                                            <Trash2 size={16} />
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        </aside>

                        <section className="task-dialog-content">
                            {selectedTask ? (
                                <>
                                    <div className="task-summary-card task-summary-card-inline">
                                        <div className="task-summary-head">
                                            <div>
                                                <h3>{selectedTask.title || `网站导入任务 #${selectedTask.id}`}</h3>
                                                <p>{selectedTask.sourceUrl}</p>
                                            </div>
                                            <span className={`task-status-badge ${selectedTask.status.toLowerCase()}`}>
                                                {getImportStatusText(selectedTask.status)}
                                            </span>
                                        </div>
                                        <div className="task-summary-grid">
                                            <div className="task-summary-metric">
                                                <span className="task-summary-label">抓取情况</span>
                                                <strong>{selectedTask.discoveredCount || 0}</strong>
                                                <span>已发现政策页面</span>
                                            </div>
                                            <div className="task-summary-metric">
                                                <span className="task-summary-label">待审批</span>
                                                <strong>{selectedTask.candidateCount || 0}</strong>
                                                <span>等待人工确认</span>
                                            </div>
                                            <div className="task-summary-metric">
                                                <span className="task-summary-label">已入库</span>
                                                <strong>{selectedTask.importedCount || 0}</strong>
                                                <span>已进入知识库</span>
                                            </div>
                                            <div className="task-summary-metric">
                                                <span className="task-summary-label">已驳回</span>
                                                <strong>{selectedTask.rejectedCount || 0}</strong>
                                                <span>已被人工剔除</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="kb-batch-toolbar task-batch-toolbar">
                                        <span className="kb-batch-count">
                                            当前任务共 {documents.length} 条内容，含待审批与已入库文档
                                        </span>
                                        <button
                                            className="btn-primary"
                                            onClick={onBatchConfirm}
                                            disabled={batchConfirmCount === 0}
                                        >
                                            <Sparkles size={16} />
                                            一键入库 {batchConfirmCount > 0 ? `(${batchConfirmCount})` : ''}
                                        </button>
                                    </div>

                                    <div className="document-list task-document-list">
                                        {documents.length === 0 ? (
                                            <div className="empty-state">
                                                <FileText size={48} />
                                                <p>该任务下暂无待审批或已入库内容</p>
                                            </div>
                                        ) : (
                                            documents.map(doc => (
                                                <div key={doc.id} className="document-item">
                                                    <div className="doc-selection" />
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
                                                                {doc.isUrlImportCandidate ? '待入库' : getStatusText(doc.status)}
                                                            </span>
                                                            {doc.chunkCount > 0 && (
                                                                <span className="doc-chunks">{doc.chunkCount} 个切片</span>
                                                            )}
                                                            {doc.qualityScore != null && (
                                                                <span className="doc-import-badge">评分 {doc.qualityScore}</span>
                                                            )}
                                                            {doc.publishDate && (
                                                                <span className="doc-import-badge">{doc.publishDate}</span>
                                                            )}
                                                            {doc.suspectedDuplicate && (
                                                                <span className="doc-import-badge warning">疑似重复</span>
                                                            )}
                                                        </div>
                                                        <div className={`doc-status-bar ${getStatusBarClass(doc.status)}`}>
                                                            <div className="doc-status-bar-fill" />
                                                            <span className="doc-status-bar-text">
                                                                {doc.isUrlImportCandidate ? '等待管理员确认入库' : getStatusText(doc.status)}
                                                            </span>
                                                        </div>
                                                        {doc.sourceUrl && (
                                                            <div className="doc-source-link">
                                                                <a href={doc.sourceUrl} target="_blank" rel="noreferrer">
                                                                    <ExternalLink size={14} />
                                                                    查看原文
                                                                </a>
                                                            </div>
                                                        )}
                                                        {doc.previewText && (
                                                            <div className="doc-preview-text">{doc.previewText}</div>
                                                        )}
                                                        {doc.errorMessage && (
                                                            <div className="doc-error">{doc.errorMessage}</div>
                                                        )}
                                                    </div>
                                                    <div className="doc-actions">
                                                        {doc.isUrlImportCandidate && (
                                                            <>
                                                                <button className="btn-icon" onClick={() => onPreviewImportItem(doc.importItemId)} title="预览待入库内容">
                                                                    <Eye size={16} />
                                                                </button>
                                                                <button className="btn-icon success" onClick={() => onConfirmImportItem(doc.importItemId)} title="确认入库">
                                                                    <CheckCircle2 size={16} />
                                                                </button>
                                                                <button className="btn-icon danger" onClick={() => onRejectImportItem(doc.importItemId)} title="驳回">
                                                                    <X size={16} />
                                                                </button>
                                                                <button className="btn-icon danger" onClick={() => onDeleteImportItem(doc.importItemId)} title="删除待入库内容">
                                                                    <Trash2 size={16} />
                                                                </button>
                                                            </>
                                                        )}
                                                        {!doc.isUrlImportCandidate && (
                                                            <button className="btn-icon" onClick={() => onPreviewDocument(doc)} title="预览">
                                                                <Eye size={16} />
                                                            </button>
                                                        )}
                                                        {!doc.isUrlImportCandidate && !doc.externalSourceUrl && (
                                                            <button className="btn-icon" onClick={() => onDownloadDocument(doc)} title="下载">
                                                                <Download size={16} />
                                                            </button>
                                                        )}
                                                        {!doc.isUrlImportCandidate && doc.status === 'COMPLETED' && doc.chunkCount > 0 && (
                                                            <button className="btn-icon" onClick={() => onViewChunks(doc)} title="查看分段">
                                                                <Eye size={16} />
                                                            </button>
                                                        )}
                                                        {!doc.isUrlImportCandidate && doc.status === 'FAILED' && (
                                                            <button className="btn-icon" onClick={() => onReingestDocument(doc.id)} title="重新处理">
                                                                <RefreshCw size={16} />
                                                            </button>
                                                        )}
                                                        {!doc.isUrlImportCandidate && (
                                                            <button className="btn-icon danger" onClick={() => onDeleteDocument(doc.id)} title="删除">
                                                                <Trash2 size={16} />
                                                            </button>
                                                        )}
                                                    </div>
                                                </div>
                                            ))
                                        )}
                                    </div>

                                    {pagination.totalPages > 1 && (
                                        <div className="kb-pagination task-pagination">
                                            <button
                                                disabled={pagination.page === 0}
                                                onClick={() => onPageChange(pagination.page - 1)}
                                            >
                                                上一页
                                            </button>
                                            <span>
                                                第 {pagination.page + 1} / {pagination.totalPages} 页，共 {pagination.totalElements} 条
                                            </span>
                                            <button
                                                disabled={pagination.page >= pagination.totalPages - 1}
                                                onClick={() => onPageChange(pagination.page + 1)}
                                            >
                                                下一页
                                            </button>
                                        </div>
                                    )}
                                </>
                            ) : (
                                <div className="empty-state task-dialog-empty">
                                    <Clock size={40} />
                                    <p>请选择左侧任务查看抓取与入库详情</p>
                                </div>
                            )}
                        </section>
                    </div>
                </div>
            </div>
        </div>
    );
};

const UrlImportDialog = ({ embeddingModels, folders, defaultFolderId, onClose, onSubmit }) => {
    const [formData, setFormData] = useState({
        url: 'http://commerce.shandong.gov.cn/col/col352659/index.html',
        folderId: defaultFolderId,
        embeddingModel: embeddingModels.find(model => model.isDefault)?.id || '',
        titleOverride: '',
        remark: ''
    });

    const handleSubmit = (event) => {
        event.preventDefault();
        onSubmit({
            ...formData,
            folderId: formData.folderId || null
        });
    };

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog upload-dialog" onClick={event => event.stopPropagation()}>
                <div className="dialog-header">
                    <h3>网站链接导入</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>
                <form className="dialog-body" onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label>网站链接</label>
                        <input
                            type="url"
                            value={formData.url}
                            onChange={event => setFormData(prev => ({ ...prev, url: event.target.value }))}
                            placeholder="请输入公开政策栏目链接"
                            required
                        />
                    </div>

                    <div className="form-grid">
                        <div className="form-group">
                            <label>目标文件夹</label>
                            <select
                                value={formData.folderId || ''}
                                onChange={event => setFormData(prev => ({
                                    ...prev,
                                    folderId: event.target.value ? Number(event.target.value) : null
                                }))}
                            >
                                <option value="">根目录</option>
                                {flattenFoldersTree(folders).map(folder => (
                                    <option key={folder.id} value={folder.id}>
                                        {' '.repeat(folder.depth * 2)}{folder.name}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="form-group">
                            <label>嵌入模型</label>
                            <select
                                value={formData.embeddingModel}
                                onChange={event => setFormData(prev => ({ ...prev, embeddingModel: event.target.value }))}
                            >
                                {embeddingModels.map(model => (
                                    <option key={model.id} value={model.id}>
                                        {model.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="form-group">
                        <label>标题覆盖</label>
                        <input
                            type="text"
                            value={formData.titleOverride}
                            onChange={event => setFormData(prev => ({ ...prev, titleOverride: event.target.value }))}
                            placeholder="可选，用于任务展示"
                        />
                    </div>

                    <div className="form-group">
                        <label>备注</label>
                        <textarea
                            value={formData.remark}
                            onChange={event => setFormData(prev => ({ ...prev, remark: event.target.value }))}
                            rows={3}
                            placeholder="可选，用于记录导入目的"
                        />
                    </div>

                    <div className="url-import-hint">
                        首期仅支持山东省商务厅指定以旧换新栏目链接，系统会自动抓取页面正文及政策附件，并将高质量内容放入待入库列表。
                    </div>

                    <div className="dialog-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            取消
                        </button>
                        <button type="submit" className="btn-primary">
                            开始抓取
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

const UrlImportPreviewDialog = ({ item, folders, defaultFolderId, onClose, onConfirm, onReject }) => {
    const [formData, setFormData] = useState({
        folderId: defaultFolderId || item.defaultFolderId || '',
        title: item.title || '',
        category: item.category || '',
        tags: Array.isArray(item.tags) ? item.tags.join(', ') : '',
        summary: item.summary || ''
    });

    const handleSubmit = (event) => {
        event.preventDefault();
        onConfirm({
            folderId: formData.folderId ? Number(formData.folderId) : null,
            title: formData.title,
            category: formData.category,
            tags: formData.tags
                .split(',')
                .map(tag => tag.trim())
                .filter(Boolean),
            summary: formData.summary,
            publishDate: item.publishDate,
            source: item.sourceSite
        });
    };

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog import-preview-dialog" onClick={event => event.stopPropagation()}>
                <div className="dialog-header">
                    <h3>待入库内容预览</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                <form className="dialog-body import-preview-body" onSubmit={handleSubmit}>
                    <div className="import-preview-meta">
                        <div><strong>标题：</strong>{item.title || '-'}</div>
                        <div><strong>发布时间：</strong>{item.publishDate || '-'}</div>
                        <div><strong>质量评分：</strong>{item.qualityScore ?? '-'}</div>
                        <div><strong>来源站点：</strong>{item.sourceSite || '-'}</div>
                        <div><strong>默认目录：</strong>{item.defaultFolderPath || '/'}</div>
                        <div>
                            <strong>原文链接：</strong>
                            <a href={item.sourceUrl} target="_blank" rel="noreferrer">{item.sourceUrl}</a>
                        </div>
                        {item.reviewComment && <div><strong>备注：</strong>{item.reviewComment}</div>}
                    </div>

                    {item.attachments?.length > 0 && (
                        <div className="import-preview-attachments">
                            <h4>附件</h4>
                            {item.attachments.map(attachment => (
                                <a
                                    key={attachment.id || attachment.attachmentUrl}
                                    href={attachment.attachmentUrl}
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    <ExternalLink size={14} />
                                    {attachment.fileName || attachment.attachmentUrl}
                                </a>
                            ))}
                        </div>
                    )}

                    <div className="form-grid">
                        <div className="form-group">
                            <label>目标文件夹</label>
                            <select
                                value={formData.folderId}
                                onChange={event => setFormData(prev => ({ ...prev, folderId: event.target.value }))}
                            >
                                <option value="">沿用导入任务配置</option>
                                {flattenFoldersTree(folders).map(folder => (
                                    <option key={folder.id} value={folder.id}>
                                        {' '.repeat(folder.depth * 2)}{folder.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="form-group">
                            <label>分类</label>
                            <input
                                type="text"
                                value={formData.category}
                                onChange={event => setFormData(prev => ({ ...prev, category: event.target.value }))}
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label>标题</label>
                        <input
                            type="text"
                            value={formData.title}
                            onChange={event => setFormData(prev => ({ ...prev, title: event.target.value }))}
                        />
                    </div>

                    <div className="form-group">
                        <label>标签</label>
                        <input
                            type="text"
                            value={formData.tags}
                            onChange={event => setFormData(prev => ({ ...prev, tags: event.target.value }))}
                            placeholder="多个标签请使用逗号分隔"
                        />
                    </div>

                    <div className="form-group">
                        <label>摘要</label>
                        <textarea
                            value={formData.summary}
                            onChange={event => setFormData(prev => ({ ...prev, summary: event.target.value }))}
                            rows={3}
                        />
                    </div>

                    <div className="import-preview-content">
                        <h4>正文预览</h4>
                        <pre>{item.cleanedText || item.summary || '暂无可预览内容'}</pre>
                    </div>

                    <div className="dialog-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            关闭
                        </button>
                        <button type="button" className="btn-secondary danger-outline" onClick={onReject}>
                            驳回
                        </button>
                        <button type="submit" className="btn-primary">
                            确认入库
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

const BatchMoveDialog = ({ folders, selectedCount, defaultFolderId, onClose, onSubmit }) => {
    const [targetFolderId, setTargetFolderId] = useState(defaultFolderId || '');

    const handleSubmit = (event) => {
        event.preventDefault();
        onSubmit(targetFolderId ? Number(targetFolderId) : null);
    };

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog upload-dialog" onClick={event => event.stopPropagation()}>
                <div className="dialog-header">
                    <h3>批量移动文档</h3>
                    <button className="btn-close" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                <form className="dialog-body" onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label>目标文件夹</label>
                        <select
                            value={targetFolderId}
                            onChange={event => setTargetFolderId(event.target.value)}
                        >
                            <option value="">根目录</option>
                            {flattenFoldersTree(folders).map(folder => (
                                <option key={folder.id} value={folder.id}>
                                    {' '.repeat(folder.depth * 2)}{folder.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="url-import-hint">
                        本次将移动 {selectedCount} 份文档。仅变更知识库目录归属，不会删除原始文件内容。
                    </div>

                    <div className="dialog-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            取消
                        </button>
                        <button type="submit" className="btn-primary">
                            确认移动
                        </button>
                    </div>
                </form>
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
                            <select
                                value={formData.folderId || ''}
                                onChange={e => setFormData(prev => ({ ...prev, folderId: e.target.value ? Number(e.target.value) : null }))}
                            >
                                <option value="">根目录</option>
                                {flattenFoldersTree(folders).map(folder => (
                                    <option key={folder.id} value={folder.id}>
                                        {' '.repeat(folder.depth * 2)}{folder.name}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="form-group">
                            <label>嵌入模型</label>
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
    const [formData, setFormData] = useState(() => (config ? { ...config } : {}));

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
