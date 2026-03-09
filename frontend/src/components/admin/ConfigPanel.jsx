import React, { useEffect, useState } from 'react';
import { adminApi } from '../../services/adminApi';
import { useAdminConsole } from './useAdminConsole';
import './ConfigPanel.css';

const MODEL_TYPE_META = [
    { key: 'LLM', field: 'llmModelId', label: '大语言模型', description: '负责主对话与推理输出。' },
    { key: 'VISION', field: 'visionModelId', label: '视觉模型', description: '用于图片、票据和设备识别。' },
    { key: 'AUDIO', field: 'audioModelId', label: '语音模型', description: '用于语音识别和音频转写。' },
    { key: 'EMBEDDING', field: 'embeddingModelId', label: '嵌入模型', description: '用于知识库检索与向量召回。' }
];

const SKILL_META = [
    {
        key: 'webSearch',
        label: '联网搜索',
        title: '联网搜索 (Web Search)',
        description: '允许智能体搜索实时政策和产品价格。'
    },
    {
        key: 'subsidyCalculator',
        label: '补贴计算',
        title: '补贴计算器 (Subsidy Calculator)',
        description: '提供山东省以旧换新补贴金额的精确计算。'
    },
    {
        key: 'fileParser',
        label: '文件解析',
        title: '文件解析 (File Parser)',
        description: '允许解析用户上传的发票和旧机参数表。'
    }
];

const formatDateTime = (value) => {
    if (!value) {
        return '尚未保存';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '时间未知';
    }

    return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
};

const ConfigPanel = ({ config, onSave, onReset }) => {
    const { notify, confirm } = useAdminConsole();
    const [formData, setFormData] = useState(() => (config ? JSON.parse(JSON.stringify(config)) : {}));
    const [isDirty, setIsDirty] = useState(false);
    const [saving, setSaving] = useState(false);
    const [modelOptions, setModelOptions] = useState({
        LLM: [],
        VISION: [],
        AUDIO: [],
        EMBEDDING: []
    });

    useEffect(() => {
        const fetchModelOptions = async () => {
            try {
                const options = await adminApi.getModelOptions();
                setModelOptions({
                    LLM: options.LLM || [],
                    VISION: options.VISION || [],
                    AUDIO: options.AUDIO || [],
                    EMBEDDING: options.EMBEDDING || []
                });
            } catch (err) {
                console.error('加载模型选项失败:', err);
                notify({ text: '加载模型选项失败，请稍后重试', type: 'error', source: '管理员-智能体配置' });
            }
        };

        fetchModelOptions();
    }, [notify]);

    const handleModelSelectChange = (field, value) => {
        setFormData((prev) => ({
            ...prev,
            [field]: value ? Number(value) : null
        }));
        setIsDirty(true);
    };

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;

        if (name.startsWith('skills.')) {
            const skillName = name.split('.')[1];
            setFormData((prev) => ({
                ...prev,
                skills: {
                    ...prev.skills,
                    [skillName]: {
                        ...prev.skills?.[skillName],
                        enabled: checked
                    }
                }
            }));
        } else {
            setFormData((prev) => ({
                ...prev,
                [name]: type === 'checkbox'
                    ? checked
                    : type === 'number'
                        ? (value === '' ? null : Number(value))
                        : value
            }));
        }
        setIsDirty(true);
    };

    const handleSaveClick = async () => {
        setSaving(true);

        if (!formData.systemPrompt?.trim()) {
            notify({ text: '系统提示词不能为空', type: 'warning', source: '管理员-智能体配置' });
            setSaving(false);
            return;
        }

        if (formData.llmModelId == null) {
            if (!formData.modelName?.trim()) {
                notify({ text: '未选择已配置模型时，模型名称不能为空', type: 'warning', source: '管理员-智能体配置' });
                setSaving(false);
                return;
            }
            if (!formData.apiUrl?.trim()) {
                notify({ text: '未选择已配置模型时，API 地址不能为空', type: 'warning', source: '管理员-智能体配置' });
                setSaving(false);
                return;
            }
            if (formData.temperature < 0 || formData.temperature > 1) {
                notify({ text: '温度必须在 0 到 1 之间', type: 'warning', source: '管理员-智能体配置' });
                setSaving(false);
                return;
            }
        }

        const result = await onSave(formData);
        if (result.success) {
            notify({ text: '配置已成功保存并生效', type: 'success', source: '管理员-智能体配置' });
            setIsDirty(false);
        } else {
            notify({ text: result.message || '保存失败', type: 'error', source: '管理员-智能体配置' });
        }
        setSaving(false);
    };

    const handleResetClick = async () => {
        const confirmed = await confirm({
            title: '重置智能体配置',
            message: '确定要将当前配置恢复为系统默认值吗？此操作不可撤销。',
            confirmText: '确认重置',
            tone: 'danger'
        });
        if (!confirmed) {
            return;
        }

        setSaving(true);
        const result = await onReset();
        if (result.success) {
            notify({ text: '已重置为默认配置', type: 'success', source: '管理员-智能体配置' });
            setIsDirty(false);
        } else {
            notify({ text: result.message || '重置失败', type: 'error', source: '管理员-智能体配置' });
        }
        setSaving(false);
    };

    const renderModelSelect = ({ key, field, label, description }) => (
        <div className="form-group model-select-group" key={field}>
            <div className="form-label-row">
                <label>{label}</label>
                <span>{key}</span>
            </div>
            <select
                value={formData[field] ?? ''}
                onChange={(e) => handleModelSelectChange(field, e.target.value)}
                className="model-select"
            >
                <option value="">{key === 'LLM' ? '系统默认（手动配置）' : '未指定，后续接入时再配置'}</option>
                {modelOptions[key]?.map((model) => (
                    <option key={model.id} value={model.id}>
                        {model.name}{model.isDefault ? '（默认）' : ''}
                    </option>
                ))}
            </select>
            <p className="field-note">{description}</p>
        </div>
    );

    if (!formData || Object.keys(formData).length === 0) {
        return null;
    }

    const usingManagedLlm = formData.llmModelId != null;
    return (
        <div className="config-panel">
            <div className="config-header">
                <div className="config-heading">
                    <span className="config-kicker">编辑区</span>
                    <div>
                        <h2>智能体配置</h2>
                        <p>
                            {usingManagedLlm
                                ? '当前 LLM 由模型管理接管，手动参数作为兜底配置。'
                                : '当前使用手动 LLM 配置，修改后会直接作用于运行时。'}
                        </p>
                    </div>
                </div>
                <div className="config-actions">
                    <span className={`config-sync-pill ${isDirty ? 'dirty' : 'clean'}`}>
                        {isDirty ? '有未保存更改' : '已同步'}
                    </span>
                    <button
                        className="btn-reset"
                        onClick={handleResetClick}
                        disabled={saving}
                        title="恢复默认设置"
                    >
                        重置
                    </button>
                    <button
                        className="btn-save"
                        onClick={handleSaveClick}
                        disabled={!isDirty || saving}
                    >
                        {saving ? '保存中...' : '保存'}
                    </button>
                </div>
            </div>
            <div className="config-form">
                <div className="form-section">
                    <div className="section-heading">
                        <div>
                            <span className="section-eyebrow">模型路由</span>
                            <h3>运行时模型与能力绑定</h3>
                        </div>
                    </div>

                    <div className="select-grid">
                        {MODEL_TYPE_META.map(renderModelSelect)}
                    </div>

                    {usingManagedLlm ? (
                        <p className="field-hint">
                            当前已选择“模型管理”中的大语言模型。运行时会使用该模型的 API 地址、密钥、模型名和高级参数。
                        </p>
                    ) : (
                        <>
                            <div className="form-group">
                                <label>模型提供商</label>
                                <input
                                    type="text"
                                    name="modelProvider"
                                    value={formData.modelProvider || ''}
                                    onChange={handleChange}
                                    placeholder="如: dashscope"
                                />
                                <p className="field-note">建议填写供应商标识，方便排查运行时到底命中了哪一路配置。</p>
                            </div>
                            <div className="form-group">
                                <label>模型名称</label>
                                <input
                                    type="text"
                                    name="modelName"
                                    value={formData.modelName || ''}
                                    onChange={handleChange}
                                    placeholder="如: qwen3.5-plus"
                                />
                                <p className="field-note">该字段会作为请求中的模型名发送给后端。</p>
                            </div>
                            <div className="form-group">
                                <label>API 地址</label>
                                <input
                                    type="text"
                                    name="apiUrl"
                                    value={formData.apiUrl || ''}
                                    onChange={handleChange}
                                    placeholder="https://dashscope.aliyuncs.com/compatible-mode"
                                />
                                <p className="field-note">请填写完整的 OpenAI 兼容基地址，不要省略协议头。</p>
                            </div>
                            <div className="form-group row">
                                <div className="half">
                                    <label>API Key (脱敏)</label>
                                    <input
                                        type="text"
                                        name="apiKey"
                                        value={formData.apiKey || ''}
                                        onChange={handleChange}
                                        placeholder="输入新 Key 将覆盖原值"
                                    />
                                    <p className="field-note">留空表示保持后端已保存的密钥不变。</p>
                                </div>
                                <div className="half">
                                    <label>温度 (Temperature: 0.0 - 1.0)</label>
                                    <input
                                        type="number"
                                        name="temperature"
                                        min="0"
                                        max="1"
                                        step="0.1"
                                        value={formData.temperature ?? 0.7}
                                        onChange={handleChange}
                                    />
                                    <p className="field-note">数值越高越发散，政策问答场景通常建议控制在 0.2 到 0.7。</p>
                                </div>
                            </div>
                        </>
                    )}
                </div>

                <div className="form-section">
                    <div className="section-heading">
                        <div>
                            <span className="section-eyebrow">角色定义</span>
                            <h3>核心提示词 (System Prompt)</h3>
                        </div>
                    </div>
                    <div className="form-group">
                        <textarea
                            className="prompt-textarea"
                            name="systemPrompt"
                            value={formData.systemPrompt || ''}
                            onChange={handleChange}
                            rows={8}
                            placeholder="定义智能体的行为和角色..."
                        />
                    </div>
                </div>

                <div className="form-section">
                    <div className="section-heading">
                        <div>
                            <span className="section-eyebrow">首屏体验</span>
                            <h3>开场白 (Greeting Message)</h3>
                        </div>
                    </div>
                    <div className="form-group">
                        <textarea
                            name="greetingMessage"
                            value={formData.greetingMessage || ''}
                            onChange={handleChange}
                            rows={4}
                            placeholder="用户打开聊天窗口时看到的第一句话..."
                        />
                    </div>
                </div>

                <div className="form-section">
                    <div className="section-heading">
                        <div>
                            <span className="section-eyebrow">能力开关</span>
                            <h3>技能模块 (Skills)</h3>
                        </div>
                    </div>
                    <div className="skills-list">
                        {SKILL_META.map((skill) => {
                            const enabled = formData.skills?.[skill.key]?.enabled ?? true;

                            return (
                                <label className={`skill-item ${enabled ? 'enabled' : 'disabled'}`} key={skill.key}>
                                    <input
                                        type="checkbox"
                                        name={`skills.${skill.key}`}
                                        checked={enabled}
                                        onChange={handleChange}
                                    />
                                    <div className="skill-info">
                                        <div className="skill-head">
                                            <span className="skill-name">{skill.title}</span>
                                            <span className={`skill-state ${enabled ? 'enabled' : 'disabled'}`}>
                                                {enabled ? '已启用' : '已关闭'}
                                            </span>
                                        </div>
                                        <span className="skill-desc">{skill.description}</span>
                                        <span className="skill-tag">{skill.label}</span>
                                    </div>
                                </label>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ConfigPanel;
