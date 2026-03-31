import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { Plus, RefreshCw } from 'lucide-react';
import { adminApi } from '../../services/adminApi';
import ModelListPanel from './ModelListPanel';
import ModelFormModal from './ModelFormModal';
import { useAdminConsole } from './useAdminConsole';
import './ModelsTab.css';

const MODEL_TYPES = [
    { id: 'LLM', label: '大语言模型', description: '用于智能体对话' },
    { id: 'VISION', label: '视觉模型', description: '用于图像分析' },
    { id: 'AUDIO', label: '语音模型', description: '用于语音识别' },
    { id: 'EMBEDDING', label: '嵌入模型', description: '用于向量嵌入' },
    { id: 'RERANK', label: '重排序模型', description: '用于检索结果重排' }
];

const ModelsTab = ({ intent }) => {
    const { notify, confirm } = useAdminConsole();
    const [activeSubTab, setActiveSubTab] = useState('LLM');
    const [allModels, setAllModels] = useState([]);
    const [modelsByType, setModelsByType] = useState({});
    const [loading, setLoading] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editingModel, setEditingModel] = useState(null);
    const [testingId, setTestingId] = useState(null);
    const requestSequenceRef = useRef(0);
    const modelCacheRef = useRef({});

    const loadModels = useCallback(async (targetType = activeSubTab, options = {}) => {
        const { preserveContent = false } = options;
        const requestId = ++requestSequenceRef.current;

        if (preserveContent) {
            setRefreshing(true);
        } else {
            setLoading(true);
            setError('');
        }

        try {
            const [activeData, allData] = await Promise.all([
                adminApi.getModels(targetType),
                adminApi.getModels()
            ]);

            if (requestId !== requestSequenceRef.current) {
                return;
            }

            modelCacheRef.current[targetType] = activeData;
            setModelsByType((current) => ({
                ...current,
                [targetType]: activeData
            }));
            setAllModels(allData);
        } catch (err) {
            if (requestId !== requestSequenceRef.current) {
                return;
            }

            if (!preserveContent) {
                setError(err.message || '加载模型列表失败');
            }
            notify({ text: err.message || '加载模型列表失败', type: 'error', source: '管理员-模型管理' });
        } finally {
            if (requestId === requestSequenceRef.current) {
                setLoading(false);
                setRefreshing(false);
            }
        }
    }, [activeSubTab, notify]);

    useEffect(() => {
        const hasCachedModels = Object.prototype.hasOwnProperty.call(modelCacheRef.current, activeSubTab);
        loadModels(activeSubTab, { preserveContent: hasCachedModels });
    }, [activeSubTab, loadModels]);

    useEffect(() => {
        if (!intent?.nonce) {
            return;
        }
        if (intent.modelType) {
            setActiveSubTab(intent.modelType);
        }
        if (intent.openCreateModel) {
            setEditingModel(null);
            setShowModal(true);
        }
    }, [intent]);

    const handleAdd = () => {
        setEditingModel(null);
        setShowModal(true);
    };

    const handleEdit = (model) => {
        setEditingModel(model);
        setShowModal(true);
    };

    const handleDelete = async (id) => {
        const confirmed = await confirm({
            title: '删除模型',
            message: '确定要删除该模型吗？如果它正在被配置引用，后续需要重新指定。',
            confirmText: '确认删除',
            tone: 'danger'
        });
        if (!confirmed) return;
        try {
            await adminApi.deleteModel(id);
            notify({ text: '模型已删除', type: 'success', source: '管理员-模型管理' });
            await loadModels(activeSubTab, { preserveContent: true });
        } catch (err) {
            notify({ text: err.message || '删除失败', type: 'error', source: '管理员-模型管理' });
        }
    };

    const handleSetDefault = async (id) => {
        try {
            await adminApi.setDefaultModel(id);
            notify({ text: '默认模型已更新', type: 'success', source: '管理员-模型管理' });
            await loadModels(activeSubTab, { preserveContent: true });
        } catch (err) {
            notify({ text: err.message || '设置默认失败', type: 'error', source: '管理员-模型管理' });
        }
    };

    const handleTest = async (id) => {
        setTestingId(id);
        try {
            const result = await adminApi.testModelConnection(id);
            if (result.success) {
                notify({
                    text: `连接测试成功，耗时 ${result.latencyMs ?? '-'} ms`,
                    type: 'success',
                    source: '管理员-模型管理'
                });
            } else {
                notify({ text: `连接测试失败：${result.message || '未知错误'}`, type: 'error', source: '管理员-模型管理' });
            }
        } catch (err) {
            notify({ text: `连接测试失败：${err.message || '未知错误'}`, type: 'error', source: '管理员-模型管理' });
        } finally {
            setTestingId(null);
        }
    };

    const handleSave = async (modelData) => {
        const targetType = modelData.type || activeSubTab;
        if (editingModel) {
            await adminApi.updateModel(editingModel.id, modelData);
        } else {
            await adminApi.createModel(modelData);
        }
        setShowModal(false);
        if (targetType !== activeSubTab) {
            notify({
                text: `模型已保存到“${MODEL_TYPES.find(item => item.id === targetType)?.label || targetType}”分类，已为你切换过去`,
                type: 'info',
                source: '管理员-模型管理'
            });
            setActiveSubTab(targetType);
        } else {
            notify({ text: editingModel ? '模型已更新' : '模型已创建', type: 'success', source: '管理员-模型管理' });
        }
        await loadModels(targetType, { preserveContent: Object.prototype.hasOwnProperty.call(modelCacheRef.current, targetType) });
    };

    const activeType = MODEL_TYPES.find((type) => type.id === activeSubTab);
    const models = modelsByType[activeSubTab] || [];
    const typeCounts = useMemo(() => {
        return allModels.reduce((accumulator, model) => {
            accumulator[model.type] = (accumulator[model.type] || 0) + 1;
            return accumulator;
        }, {});
    }, [allModels]);

    return (
        <div className="models-tab">
            <div className="models-tab-header">
                <div className="models-tab-header-main">
                    <span className="models-tab-eyebrow">模型编排</span>
                    <h2>模型服务管理</h2>
                    <p className="models-tab-desc">统一管理对话、视觉、语音与嵌入模型。分类切换、列表查看和模型维护保持与知识库页一致的浏览方式。</p>
                </div>
                <div className="models-tab-header-actions">
                    <button
                        className="models-header-button secondary"
                        onClick={() => loadModels(activeSubTab, { preserveContent: models.length > 0 })}
                        disabled={loading || refreshing}
                    >
                        <RefreshCw size={16} />
                        {refreshing ? '刷新中...' : '刷新'}
                    </button>
                    <button className="models-header-button primary" onClick={handleAdd}>
                        <Plus size={16} />
                        新增模型
                    </button>
                </div>
            </div>

            <div className="models-content">
                <aside className="models-sidebar">
                    <div className="models-sidebar-header">
                        <h3>模型类型</h3>
                    </div>
                    <div className="models-type-list">
                        {MODEL_TYPES.map((type) => (
                            <button
                                key={type.id}
                                className={`models-type-item ${activeSubTab === type.id ? 'active' : ''}`}
                                onClick={() => setActiveSubTab(type.id)}
                            >
                                <span>{type.label}</span>
                                <strong>{typeCounts[type.id] || 0}</strong>
                            </button>
                        ))}
                    </div>
                </aside>

                <section className="models-main">
                    <ModelListPanel
                        typeLabel={activeType?.label}
                        models={models}
                        loading={loading}
                        refreshing={refreshing}
                        error={error}
                        onEdit={handleEdit}
                        onDelete={handleDelete}
                        onSetDefault={handleSetDefault}
                        onTest={handleTest}
                        testingId={testingId}
                    />
                </section>
            </div>

            {showModal && (
                <ModelFormModal
                    model={editingModel}
                    type={activeSubTab}
                    modelTypes={MODEL_TYPES}
                    onSave={handleSave}
                    onClose={() => setShowModal(false)}
                />
            )}
        </div>
    );
};

export default ModelsTab;
