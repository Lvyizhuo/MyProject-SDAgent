import React, { useEffect, useMemo, useState } from 'react';
import {
    MessageSquare,
    Calculator,
    FileSearch,
    BarChart3,
    Mic,
    Plus,
    Search,
    Edit2,
    Trash2,
    Copy,
    Star,
    X
} from 'lucide-react';
import { adminAgentApi } from '../../services/adminAgentApi';
import { adminModelApi } from '../../services/adminModelApi';
import adminKnowledgeApi from '../../services/adminKnowledgeApi';
import './admin-card.css';
import './admin-modal.css';

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

// 智能体编辑面板支持的角色：LLM / VISION / AUDIO / EMBEDDING / RERANK
const BINDING_ROLES = [
    { key: 'LLM', label: '大语言模型', desc: '负责主对话与推理输出' },
    { key: 'VISION', label: '视觉模型', desc: '用于图片、票据和设备识别' },
    { key: 'AUDIO', label: '语音模型', desc: '用于语音识别与音频转写' },
    { key: 'EMBEDDING', label: '嵌入模型', desc: '用于文本向量化与知识库检索' },
    { key: 'RERANK', label: '重排序模型', desc: '用于检索结果精排' }
];

// 技能元数据（与 ConfigPanel 对齐）
const SKILL_META = [
    {
        key: 'webSearch',
        label: '联网搜索',
        title: '联网搜索 (Web Search)',
        description: '允许智能体搜索实时政策和产品价格。',
        defaultEnabled: true
    },
    {
        key: 'subsidyCalculator',
        label: '补贴计算',
        title: '补贴计算器 (Subsidy Calculator)',
        description: '提供山东省以旧换新补贴金额的精确计算。',
        defaultEnabled: true
    },
    {
        key: 'fileParser',
        label: '文件解析',
        title: '文件解析 (File Parser)',
        description: '允许解析用户上传的发票和旧机参数表。',
        defaultEnabled: true
    }
];

const AgentsTab = () => {
    const [agents, setAgents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sectionTab, setSectionTab] = useState('all');
    const [statusFilter, setStatusFilter] = useState('all');
    const [search, setSearch] = useState('');
    const [editing, setEditing] = useState(null); // null | 'new' | { id, ... }
    const [confirmDelete, setConfirmDelete] = useState(null);

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
            if (sectionTab !== 'all' && agent.category !== sectionTab) return false;
            if (statusFilter !== 'all' && agent.status !== statusFilter) return false;
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

    const handleSetDefault = async (agent) => {
        try {
            await adminAgentApi.setDefault(agent.id);
            await loadAgents();
        } catch (err) {
            alert(`设置默认失败：${err.message}`);
        }
    };

    const handleDuplicate = async (agent) => {
        try {
            const created = await adminAgentApi.duplicateAgent(agent.id);
            await loadAgents();
            if (created?.id) {
                // 找到完整对象并打开编辑
                const list = await adminAgentApi.listAgents();
                const next = list.find((a) => a.id === created.id);
                if (next) {
                    setEditing({ ...next, isNew: false });
                    return;
                }
            }
        } catch (err) {
            alert(`复制失败：${err.message}`);
        }
    };

    const handleDelete = async () => {
        if (!confirmDelete) return;
        try {
            await adminAgentApi.deleteAgent(confirmDelete.id);
            setConfirmDelete(null);
            await loadAgents();
        } catch (err) {
            alert(`删除失败：${err.message}`);
        }
    };

    const handleCreate = () => {
        setEditing({
            isNew: true,
            name: '',
            code: '',
            description: '',
            category: 'chat',
            icon: 'MessageSquare',
            status: 'draft',
            version: '0.1',
            isDefault: false,
            bindings: [],
            enabledTools: [],
            systemPrompt: '你是山东省智能政策咨询助手...',
            greetingMessage: '您好！请问有什么可以帮您？',
            skills: SKILL_META.reduce((acc, s) => {
                acc[s.key] = { enabled: s.defaultEnabled };
                return acc;
            }, {}),
            mcpServersConfig: []
        });
    };

    const handleEdit = async (agent) => {
        try {
            // 拉详情（含 systemPrompt / greetingMessage / skills / mcpServersConfig）
            const detail = await adminAgentApi.getAgent(agent.id);
            setEditing({ ...detail, isNew: false });
        } catch {
            // 如果 /api/admin/agents/{id} 没有全字段（早期版本），降级用列表数据
            setEditing({ ...agent, isNew: false });
        }
    };

    return (
        <section className="admin-card-section">
            <div className="admin-card-section-head">
                <h2 className="admin-card-section-title">智能体</h2>
                <span className="admin-card-section-sub">
                    Agents · 多个独立智能体，每个绑定不同模型
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
                <div className="admin-card-filter-spacer" />
                <button className="admin-card-action primary" onClick={handleCreate}>
                    <Plus size={14} /> 新建智能体
                </button>
            </div>

            {error && <div className="admin-card-error">{error}</div>}
            {loading && <div className="admin-card-loading">加载中…</div>}

            <div className="admin-card-grid">
                {filtered.map((agent) => {
                    const Icon = ICON_MAP[agent.icon] || MessageSquare;
                    const tone = ICON_TONE_MAP[agent.icon] || '';
                    return (
                        <div className="admin-card" key={agent.id}>
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
                                    onClick={() => handleEdit(agent)}
                                >
                                    <Edit2 size={12} /> 编辑
                                </button>
                                {!agent.isDefault && (
                                    <button
                                        className="admin-card-action"
                                        onClick={() => handleSetDefault(agent)}
                                    >
                                        <Star size={12} /> 设为默认
                                    </button>
                                )}
                                <button
                                    className="admin-card-action"
                                    onClick={() => handleDuplicate(agent)}
                                >
                                    <Copy size={12} /> 复制
                                </button>
                                {!agent.isDefault && (
                                    <button
                                        className="admin-card-action danger"
                                        onClick={() => setConfirmDelete(agent)}
                                    >
                                        <Trash2 size={12} /> 删除
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
                <div className="admin-card create" onClick={handleCreate}>
                    <div className="plus">
                        <Plus size={20} />
                    </div>
                    <div>新建智能体</div>
                </div>
            </div>

            {editing && (
                <AgentEditModal
                    agent={editing}
                    onClose={() => setEditing(null)}
                    onSaved={async () => {
                        setEditing(null);
                        await loadAgents();
                    }}
                />
            )}

            {confirmDelete && (
                <div className="admin-modal-mask" onClick={() => setConfirmDelete(null)}>
                    <div className="admin-modal narrow" onClick={(e) => e.stopPropagation()}>
                        <header className="admin-modal-head">
                            <h2>删除智能体</h2>
                            <button className="admin-modal-close" onClick={() => setConfirmDelete(null)}>×</button>
                        </header>
                        <div className="admin-modal-body">
                            <p>确认删除智能体 <strong>{confirmDelete.name}</strong>？</p>
                            <p className="admin-modal-hint">删除后无法恢复，请先解除依赖关系。</p>
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

const AgentEditModal = ({ agent, onClose, onSaved }) => {
    const isNew = !!agent.isNew;
    const [form, setForm] = useState(agent);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [modelOptions, setModelOptions] = useState({});
    const [folders, setFolders] = useState([]);
    const [loadingOpts, setLoadingOpts] = useState(true);

    useEffect(() => {
        const loadOpts = async () => {
            try {
                const [options, folderTree] = await Promise.all([
                    adminModelApi.getModelOptions(),
                    adminKnowledgeApi.getFolderTree()
                ]);
                setModelOptions(options || {});
                const tree = folderTree?.folders || folderTree || [];
                const flat = [];
                const walk = (nodes) => {
                    for (const n of nodes || []) {
                        flat.push(n);
                        if (n.children?.length) walk(n.children);
                    }
                };
                walk(tree);
                setFolders(flat);
            } catch (err) {
                console.error('加载选项失败:', err);
            } finally {
                setLoadingOpts(false);
            }
        };
        loadOpts();
    }, []);

    const updateField = (field, value) => {
        setForm((prev) => ({ ...prev, [field]: value }));
    };

    const getBindingByRole = (role) => {
        return form.bindings?.find((b) => b.role === role) || null;
    };

    const setBindingForRole = (role, modelId, modelName) => {
        setForm((prev) => {
            const next = [...(prev.bindings || [])];
            const idx = next.findIndex((b) => b.role === role);
            if (modelId == null) {
                if (idx >= 0) next.splice(idx, 1);
            } else {
                if (idx >= 0) {
                    next[idx] = { ...next[idx], modelId, modelName };
                } else {
                    next.push({ role, modelId, modelName });
                }
            }
            return { ...prev, bindings: next };
        });
    };

    const handleSkillToggle = (skillKey, enabled) => {
        setForm((prev) => ({
            ...prev,
            skills: {
                ...(prev.skills || {}),
                [skillKey]: {
                    ...(prev.skills?.[skillKey] || {}),
                    enabled
                }
            }
        }));
    };

    const handleSave = async () => {
        setError('');
        if (!form.name?.trim()) return setError('智能体名称必填');
        if (!form.systemPrompt?.trim()) return setError('系统提示词必填');

        setSaving(true);
        try {
            const payload = {
                name: form.name.trim(),
                code: form.code?.trim() || undefined,
                description: form.description || '',
                category: form.category || 'chat',
                icon: form.icon || 'MessageSquare',
                status: form.status || 'draft',
                version: form.version || '0.1',
                isDefault: !!form.isDefault,
                bindings: (form.bindings || []).map((b) => ({
                    role: b.role,
                    modelId: b.modelId,
                    toolCode: b.toolCode,
                    displayName: b.modelName
                })),
                enabledTools: Object.entries(form.skills || {})
                    .filter(([, v]) => v?.enabled)
                    .map(([k]) => k),
                systemPrompt: form.systemPrompt,
                greetingMessage: form.greetingMessage || '',
                skills: form.skills || {},
                mcpServersConfig: form.mcpServersConfig || []
            };

            if (isNew) {
                await adminAgentApi.createAgent(payload);
            } else {
                await adminAgentApi.updateAgent(form.id, payload);
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
                    <h2>{isNew ? '新建智能体' : `编辑智能体 · ${agent.name}`}</h2>
                    <button className="admin-modal-close" onClick={onClose} aria-label="关闭">×</button>
                </header>
                <div className="admin-modal-body">
                    {error && <div className="admin-modal-error">{error}</div>}

                    {/* 基础信息 */}
                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">基础信息</h3>
                        <div className="admin-modal-grid">
                            <div className="admin-form-group">
                                <label>名称 <span className="required">*</span></label>
                                <input
                                    value={form.name || ''}
                                    onChange={(e) => updateField('name', e.target.value)}
                                    placeholder="如：政策咨询助手"
                                />
                            </div>
                            <div className="admin-form-group">
                                <label>编码 (kebab-case)</label>
                                <input
                                    value={form.code || ''}
                                    onChange={(e) => updateField('code', e.target.value)}
                                    placeholder="policy-assistant (留空自动生成)"
                                />
                            </div>
                            <div className="admin-form-group">
                                <label>分类</label>
                                <select
                                    value={form.category || 'chat'}
                                    onChange={(e) => updateField('category', e.target.value)}
                                >
                                    <option value="chat">对话型 (chat)</option>
                                    <option value="workflow">工作流 (workflow)</option>
                                    <option value="support">客服型 (support)</option>
                                </select>
                            </div>
                            <div className="admin-form-group">
                                <label>图标</label>
                                <select
                                    value={form.icon || 'MessageSquare'}
                                    onChange={(e) => updateField('icon', e.target.value)}
                                >
                                    <option value="MessageSquare">MessageSquare 对话</option>
                                    <option value="Calculator">Calculator 计算</option>
                                    <option value="FileSearch">FileSearch 文档</option>
                                    <option value="BarChart3">BarChart3 图表</option>
                                    <option value="Mic">Mic 语音</option>
                                </select>
                            </div>
                            <div className="admin-form-group">
                                <label>状态</label>
                                <select
                                    value={form.status || 'draft'}
                                    onChange={(e) => updateField('status', e.target.value)}
                                >
                                    <option value="draft">草稿 (draft)</option>
                                    <option value="published">已发布 (published)</option>
                                </select>
                            </div>
                            <div className="admin-form-group">
                                <label>版本</label>
                                <input
                                    value={form.version || ''}
                                    onChange={(e) => updateField('version', e.target.value)}
                                    placeholder="如 1.0.0"
                                />
                            </div>
                            <div className="admin-form-group full">
                                <label>描述</label>
                                <textarea
                                    value={form.description || ''}
                                    onChange={(e) => updateField('description', e.target.value)}
                                    rows={2}
                                    placeholder="一句话说明这个智能体的用途..."
                                />
                            </div>
                        </div>
                    </div>

                    {/* 模型绑定 */}
                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">模型绑定</h3>
                        <p className="admin-edit-section-hint">
                            为不同角色选择模型，LLM 必选；视觉/语音/嵌入/重排序可选；不选时使用系统默认。
                        </p>
                        {loadingOpts ? (
                            <div className="admin-card-loading">加载模型列表...</div>
                        ) : (
                            <div className="admin-bindings-list">
                                {BINDING_ROLES.map((role) => {
                                    const binding = getBindingByRole(role.key);
                                    const opts = modelOptions[role.key] || [];
                                    return (
                                        <div className="admin-binding-row" key={role.key}>
                                            <div className="admin-binding-role">
                                                <strong>{role.key}</strong>
                                                <span>{role.label}</span>
                                                <p>{role.desc}</p>
                                            </div>
                                            <div className="admin-binding-select">
                                                <select
                                                    value={binding?.modelId ?? ''}
                                                    onChange={(e) => {
                                                        const v = e.target.value;
                                                        if (!v) {
                                                            setBindingForRole(role.key, null, null);
                                                            return;
                                                        }
                                                        const opt = opts.find((o) => String(o.id) === v);
                                                        const name = opt
                                                            ? `${opt.name}${opt.isDefault ? '（默认）' : ''}`
                                                            : '';
                                                        setBindingForRole(role.key, Number(v), name);
                                                    }}
                                                >
                                                    <option value="">未指定（使用系统默认）</option>
                                                    {opts.map((m) => (
                                                        <option key={m.id} value={m.id}>
                                                            {m.name}{m.isDefault ? '（默认）' : ''} · {m.provider}
                                                        </option>
                                                    ))}
                                                </select>
                                                {binding?.modelName && (
                                                    <span className="admin-binding-name">已选: {binding.modelName}</span>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>

                    {/* 知识库 */}
                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">知识库</h3>
                        <div className="admin-form-group">
                            <label>知识库目录</label>
                            <select
                                value={form.knowledgeBaseFolderId ?? ''}
                                onChange={(e) =>
                                    updateField('knowledgeBaseFolderId', e.target.value ? Number(e.target.value) : null)
                                }
                            >
                                <option value="">全部知识库</option>
                                {folders.map((f) => (
                                    <option key={f.id} value={f.id}>
                                        {'　'.repeat(f.depth || 0)}{f.name}
                                    </option>
                                ))}
                            </select>
                            <p className="admin-form-hint">
                                选择后智能体仅检索此目录下的文档。
                            </p>
                        </div>
                    </div>

                    {/* 提示词 */}
                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">系统提示词 (System Prompt) <span className="required">*</span></h3>
                        <div className="admin-form-group full">
                            <textarea
                                value={form.systemPrompt || ''}
                                onChange={(e) => updateField('systemPrompt', e.target.value)}
                                rows={8}
                                placeholder="定义智能体的行为和角色..."
                            />
                            <p className="admin-form-hint">最多 10000 字符</p>
                        </div>
                    </div>

                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">开场白 (Greeting Message)</h3>
                        <div className="admin-form-group full">
                            <textarea
                                value={form.greetingMessage || ''}
                                onChange={(e) => updateField('greetingMessage', e.target.value)}
                                rows={3}
                                placeholder="用户打开聊天窗口时看到的第一句话..."
                            />
                        </div>
                    </div>

                    {/* 技能 */}
                    <div className="admin-edit-section">
                        <h3 className="admin-edit-section-title">技能模块 (Skills)</h3>
                        <div className="admin-skills-list">
                            {SKILL_META.map((skill) => {
                                const enabled = form.skills?.[skill.key]?.enabled ?? skill.defaultEnabled;
                                return (
                                    <label className={`admin-skill-item ${enabled ? 'enabled' : 'disabled'}`} key={skill.key}>
                                        <input
                                            type="checkbox"
                                            checked={enabled}
                                            onChange={(e) => handleSkillToggle(skill.key, e.target.checked)}
                                        />
                                        <div>
                                            <div className="admin-skill-head">
                                                <strong>{skill.title}</strong>
                                                <span className={`admin-skill-state ${enabled ? 'enabled' : 'disabled'}`}>
                                                    {enabled ? '已启用' : '已关闭'}
                                                </span>
                                            </div>
                                            <span className="admin-skill-desc">{skill.description}</span>
                                        </div>
                                    </label>
                                );
                            })}
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

export default AgentsTab;
