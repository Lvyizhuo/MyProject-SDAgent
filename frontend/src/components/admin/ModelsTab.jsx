import React, { useEffect, useMemo, useState } from 'react';
import {
    Cpu,
    Eye,
    Mic,
    BarChart3,
    Server,
    Plus,
    Search,
    X,
    Trash2,
    Edit2,
    Power,
    Star
} from 'lucide-react';
import { adminModelApi } from '../../services/adminModelApi';
import './admin-card.css';
import './admin-modal.css';

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

const ICON_MAP = { Cpu, Eye, Mic, BarChart3, Server };

const ICON_TONE_MAP = {
    Cpu: '',
    Eye: 'tone-accent',
    Mic: 'tone-warning',
    BarChart3: 'tone-success',
    Server: 'tone-secondary'
};

const TYPE_ICON_HINT = {
    LLM: 'Cpu',
    VISION: 'Eye',
    AUDIO: 'Mic',
    EMBEDDING: 'Server',
    RERANK: 'BarChart3'
};

const TYPE_LABEL = {
    LLM: 'LLM',
    VISION: '视觉',
    AUDIO: '语音',
    EMBEDDING: '嵌入',
    RERANK: '重排序'
};

// 常见 provider 预设（用户输入"其他"自定义）
const PROVIDER_PRESETS = [
    { value: 'dashscope', label: '阿里云 DashScope (通义千问)' },
    { value: 'openai', label: 'OpenAI' },
    { value: 'azure-openai', label: 'Azure OpenAI' },
    { value: 'anthropic', label: 'Anthropic Claude' },
    { value: 'deepseek', label: 'DeepSeek' },
    { value: 'zhipu', label: '智谱 GLM' },
    { value: 'moonshot', label: '月之暗面 Kimi' },
    { value: 'ollama', label: 'Ollama (本地)' },
    { value: 'local', label: '本地自建 (vLLM / Xinference 等)' },
    { value: 'custom', label: '自定义 / 其他' }
];

const ModelsTab = () => {
    const [models, setModels] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [statusFilter, setStatusFilter] = useState('all');
    const [search, setSearch] = useState('');
    const [editing, setEditing] = useState(null); // null | 'new' | { id, ... }
    const [confirmDelete, setConfirmDelete] = useState(null);

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

    const filtered = useMemo(() => {
        return models.filter((m) => {
            if (sectionTab !== 'all' && m.type !== sectionTab) return false;
            if (statusFilter === 'default' && !m.isDefault) return false;
            if (statusFilter === 'enabled' && !m.isEnabled) return false;
            if (statusFilter === 'disabled' && m.isEnabled) return false;
            if (search) {
                const kw = search.toLowerCase();
                return (
                    (m.name || '').toLowerCase().includes(kw) ||
                    (m.modelName || '').toLowerCase().includes(kw) ||
                    (m.provider || '').toLowerCase().includes(kw)
                );
            }
            return true;
        });
    }, [models, sectionTab, statusFilter, search]);

    const counts = useMemo(() => {
        const isDefault = models.filter((m) => m.isDefault).length;
        const enabled = models.filter((m) => m.isEnabled).length;
        const disabled = models.filter((m) => !m.isEnabled).length;
        return { isDefault, enabled, disabled };
    }, [models]);

    const handleTest = async (model, e) => {
        e?.stopPropagation();
        try {
            const result = await adminModelApi.testConnection(model.id);
            const ok = result?.success ?? result?.ok;
            const msg = result?.message || (ok ? '连接成功' : '连接失败');
            alert(ok ? `✓ ${msg}` : `✗ ${msg}`);
            await loadModels();
        } catch (err) {
            alert(`测试失败：${err.message}`);
        }
    };

    const handleSetDefault = async (model, e) => {
        e?.stopPropagation();
        try {
            await adminModelApi.setDefault(model.id);
            await loadModels();
        } catch (err) {
            setError(err.message || '设置默认失败');
        }
    };

    const handleToggleEnabled = async (model, e) => {
        e?.stopPropagation();
        try {
            await adminModelApi.updateModel(model.id, { ...model, isEnabled: !model.isEnabled });
            await loadModels();
        } catch (err) {
            setError(err.message || '切换启用状态失败');
        }
    };

    const handleCreate = () => {
        setEditing({
            isNew: true,
            type: 'LLM',
            provider: 'dashscope',
            name: '',
            modelName: '',
            apiUrl: '',
            apiKey: '',
            temperature: 0.7,
            maxTokens: 4096,
            topP: 0.8,
            isDefault: false,
            isEnabled: true
        });
    };

    const handleEdit = (model) => {
        setEditing({
            isNew: false,
            id: model.id,
            type: model.type,
            provider: model.provider,
            name: model.name,
            modelName: model.modelName,
            apiUrl: model.apiUrl,
            apiKey: '', // 后端返回脱敏，留空 = 保持原值
            temperature: model.temperature,
            maxTokens: model.maxTokens,
            topP: model.topP,
            isDefault: model.isDefault,
            isEnabled: model.isEnabled
        });
    };

    const handleDelete = async () => {
        if (!confirmDelete) return;
        try {
            await adminModelApi.deleteModel(confirmDelete.id);
            setConfirmDelete(null);
            await loadModels();
        } catch (err) {
            alert(`删除失败：${err.message}`);
        }
    };

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">模型</h2>
                <span className="admin-card-section-sub">
                    Models · LLM / 视觉 / 语音 / 重排序 / 嵌入 （后端接口：/api/admin/models）
                </span>
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
                <div className="admin-card-filter-spacer" />
                <button className="admin-card-action primary" onClick={handleCreate}>
                    <Plus size={14} /> 接入新模型
                </button>
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((model) => {
                    const Icon = ICON_MAP[TYPE_ICON_HINT[model.type]] || Cpu;
                    const tone = ICON_TONE_MAP[ICON_MAP[TYPE_ICON_HINT[model.type]] ? TYPE_ICON_HINT[model.type] : ''] || '';
                    const typeBadge = {
                        LLM: 'primary',
                        VISION: 'accent',
                        AUDIO: 'warning',
                        RERANK: 'secondary',
                        EMBEDDING: 'success'
                    }[model.type] || 'muted';
                    const statusOk = model.isEnabled !== false;
                    return (
                        <div className="admin-card" key={model.id}>
                            <div className="admin-card-head-row">
                                <div className="admin-card-icon-row">
                                    <div className={`admin-card-icon ${tone}`}>
                                        <Icon size={16} strokeWidth={2.2} />
                                    </div>
                                    <div className="admin-card-title-wrap">
                                        <h3 className="admin-card-title">{model.name}</h3>
                                        <span className="admin-card-subtitle">
                                            {model.provider} · {model.modelName}
                                        </span>
                                    </div>
                                </div>
                                {model.isDefault ? (
                                    <span className="admin-card-badge primary">默认</span>
                                ) : (
                                    <span className={`admin-card-badge ${typeBadge}`}>
                                        {TYPE_LABEL[model.type] || model.type}
                                    </span>
                                )}
                            </div>
                            <p className="admin-card-desc">
                                {model.apiUrl || '点击「编辑」配置 API 地址'}
                            </p>
                            <div className="admin-card-meta">
                                <span className={`admin-card-test-dot ${statusOk ? 'live' : 'off'}`}>
                                    {statusOk ? '已启用' : '已停用'}
                                </span>
                                {model.isDefault && (
                                    <>
                                        <span className="dot" />
                                        <span><strong>类型默认</strong></span>
                                    </>
                                )}
                                {model.builtIn && (
                                    <>
                                        <span className="dot" />
                                        <span className="admin-card-tag-muted">内置</span>
                                    </>
                                )}
                            </div>
                            <div className="admin-card-actions">
                                <button
                                    className="admin-card-action primary"
                                    onClick={() => handleEdit(model)}
                                >
                                    <Edit2 size={12} /> 编辑
                                </button>
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => handleTest(model, e)}
                                >
                                    测试
                                </button>
                                {!model.isDefault && model.isEnabled && (
                                    <button
                                        className="admin-card-action"
                                        onClick={(e) => handleSetDefault(model, e)}
                                    >
                                        <Star size={12} /> 设为默认
                                    </button>
                                )}
                                <button
                                    className="admin-card-action"
                                    onClick={(e) => handleToggleEnabled(model, e)}
                                >
                                    <Power size={12} /> {model.isEnabled ? '停用' : '启用'}
                                </button>
                                <button
                                    className="admin-card-action danger"
                                    onClick={() => setConfirmDelete(model)}
                                >
                                    <Trash2 size={12} /> 删除
                                </button>
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={handleCreate}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>接入新模型</div>
                </div>
            </div>

            {editing && (
                <ModelEditModal
                    model={editing}
                    onClose={() => setEditing(null)}
                    onSaved={async () => {
                        setEditing(null);
                        await loadModels();
                    }}
                />
            )}

            {confirmDelete && (
                <div className="admin-modal-mask" onClick={() => setConfirmDelete(null)}>
                    <div className="admin-modal narrow" onClick={(e) => e.stopPropagation()}>
                        <header className="admin-modal-head">
                            <h2>删除模型</h2>
                            <button className="admin-modal-close" onClick={() => setConfirmDelete(null)}>×</button>
                        </header>
                        <div className="admin-modal-body">
                            <p>确认删除模型 <strong>{confirmDelete.name}</strong>？</p>
                            <p className="admin-modal-hint">
                                删除后引用此模型的智能体将无法使用该模型，请先解除绑定。
                            </p>
                        </div>
                        <footer className="admin-modal-foot">
                            <button className="admin-drawer-btn" onClick={() => setConfirmDelete(null)}>取消</button>
                            <button className="admin-drawer-btn danger" onClick={handleDelete}>确认删除</button>
                        </footer>
                    </div>
                </div>
            )}
        </section>
    );
};

const ModelEditModal = ({ model, onClose, onSaved }) => {
    const isNew = !!model.isNew;
    const [form, setForm] = useState(model);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    const updateField = (field, value) => {
        setForm((prev) => ({ ...prev, [field]: value }));
    };

    const handleSave = async () => {
        setError('');
        if (!form.name?.trim()) return setError('模型名称必填');
        if (!form.provider?.trim()) return setError('服务商必填');
        if (!form.apiUrl?.trim()) return setError('API 地址必填');
        if (!form.modelName?.trim()) return setError('具体模型名称必填');
        if (form.temperature < 0 || form.temperature > 2) return setError('温度必须在 0 ~ 2 之间');
        if (form.topP < 0 || form.topP > 1) return setError('TopP 必须在 0 ~ 1 之间');
        if (form.maxTokens && (form.maxTokens < 1 || form.maxTokens > 1000000)) {
            return setError('最大 Token 数必须在 1 ~ 1000000');
        }

        setSaving(true);
        try {
            const payload = {
                name: form.name.trim(),
                type: form.type,
                provider: form.provider.trim(),
                apiUrl: form.apiUrl.trim(),
                modelName: form.modelName.trim(),
                temperature: form.temperature != null && form.temperature !== '' ? Number(form.temperature) : null,
                maxTokens: form.maxTokens ? Number(form.maxTokens) : null,
                topP: form.topP != null && form.topP !== '' ? Number(form.topP) : null,
                isDefault: !!form.isDefault,
                isEnabled: form.isEnabled !== false
            };
            if (form.apiKey && form.apiKey.trim()) {
                payload.apiKey = form.apiKey.trim();
            }

            if (isNew) {
                await adminModelApi.createModel(payload);
            } else {
                await adminModelApi.updateModel(model.id, payload);
            }
            await onSaved();
        } catch (err) {
            setError(err.message || '保存失败');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="admin-modal-mask" onClick={onClose}>
            <div className="admin-modal wide" onClick={(e) => e.stopPropagation()}>
                <header className="admin-modal-head">
                    <h2>{isNew ? '接入新模型' : `编辑模型 · ${model.name}`}</h2>
                    <button className="admin-modal-close" onClick={onClose} aria-label="关闭">×</button>
                </header>
                <div className="admin-modal-body">
                    {error && <div className="admin-modal-error">{error}</div>}
                    <div className="admin-modal-grid">
                        <div className="admin-form-group">
                            <label>显示名称 <span className="required">*</span></label>
                            <input
                                value={form.name || ''}
                                onChange={(e) => updateField('name', e.target.value)}
                                placeholder="如：通义千问 Plus"
                            />
                        </div>
                        <div className="admin-form-group">
                            <label>类型 <span className="required">*</span></label>
                            <select
                                value={form.type}
                                onChange={(e) => updateField('type', e.target.value)}
                                disabled={!isNew}
                            >
                                {Object.entries(TYPE_LABEL).map(([v, l]) => (
                                    <option key={v} value={v}>{v} · {l}</option>
                                ))}
                            </select>
                            {!isNew && <p className="admin-form-hint">类型在创建后不可修改</p>}
                        </div>

                        <div className="admin-form-group">
                            <label>服务商 <span className="required">*</span></label>
                            <select
                                value={form.provider}
                                onChange={(e) => updateField('provider', e.target.value)}
                            >
                                {PROVIDER_PRESETS.map((p) => (
                                    <option key={p.value} value={p.value}>{p.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="admin-form-group">
                            <label>具体模型名称 <span className="required">*</span></label>
                            <input
                                value={form.modelName || ''}
                                onChange={(e) => updateField('modelName', e.target.value)}
                                placeholder="如：qwen-plus / gpt-4o / bge-m3"
                            />
                        </div>

                        <div className="admin-form-group full">
                            <label>API 地址 <span className="required">*</span></label>
                            <input
                                value={form.apiUrl || ''}
                                onChange={(e) => updateField('apiUrl', e.target.value)}
                                placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
                            />
                        </div>

                        <div className="admin-form-group full">
                            <label>API Key {!isNew && <span className="admin-form-hint-inline">（留空 = 保持原值）</span>}</label>
                            <input
                                type="password"
                                value={form.apiKey || ''}
                                onChange={(e) => updateField('apiKey', e.target.value)}
                                placeholder={isNew ? '请输入 API Key' : '留空保持不变；新值会覆盖'}
                            />
                        </div>

                        <div className="admin-form-group">
                            <label>温度 (0.0 - 2.0)</label>
                            <input
                                type="number"
                                step="0.1"
                                min="0"
                                max="2"
                                value={form.temperature ?? ''}
                                onChange={(e) => updateField('temperature', e.target.value)}
                            />
                        </div>
                        <div className="admin-form-group">
                            <label>TopP (0.0 - 1.0)</label>
                            <input
                                type="number"
                                step="0.05"
                                min="0"
                                max="1"
                                value={form.topP ?? ''}
                                onChange={(e) => updateField('topP', e.target.value)}
                            />
                        </div>
                        <div className="admin-form-group">
                            <label>最大 Token 数</label>
                            <input
                                type="number"
                                min="1"
                                max="1000000"
                                value={form.maxTokens ?? ''}
                                onChange={(e) => updateField('maxTokens', e.target.value)}
                                placeholder="如 4096 / 8192 / 32768"
                            />
                        </div>
                        <div className="admin-form-group">
                            <label>状态</label>
                            <div className="admin-form-toggle-row">
                                <label className="admin-form-toggle">
                                    <input
                                        type="checkbox"
                                        checked={form.isEnabled !== false}
                                        onChange={(e) => updateField('isEnabled', e.target.checked)}
                                    />
                                    <span>启用</span>
                                </label>
                                <label className="admin-form-toggle">
                                    <input
                                        type="checkbox"
                                        checked={!!form.isDefault}
                                        onChange={(e) => updateField('isDefault', e.target.checked)}
                                    />
                                    <span>设为该类型默认</span>
                                </label>
                            </div>
                        </div>
                    </div>
                </div>
                <footer className="admin-modal-foot">
                    <button className="admin-drawer-btn" onClick={onClose} disabled={saving}>取消</button>
                    <button className="admin-drawer-btn primary" onClick={handleSave} disabled={saving}>
                        {saving ? '保存中...' : (isNew ? '创建' : '保存')}
                    </button>
                </footer>
            </div>
        </div>
    );
};

export default ModelsTab;
