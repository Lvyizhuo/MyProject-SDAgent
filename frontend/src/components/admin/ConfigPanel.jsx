import React, { useEffect, useState } from 'react';
import { Save, RotateCcw } from 'lucide-react';
import { adminApi } from '../../services/adminApi';
import { useAdminConsole } from './useAdminConsole';
import './ConfigPanel.css';

const MODEL_TYPE_META = [
    { key: 'LLM', field: 'llmModelId', label: '大语言模型' },
    { key: 'VISION', field: 'visionModelId', label: '视觉模型' },
    { key: 'AUDIO', field: 'audioModelId', label: '语音模型' },
    { key: 'EMBEDDING', field: 'embeddingModelId', label: '嵌入模型' }
];

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

    const renderModelSelect = ({ key, field, label }) => (
        <div className="form-group" key={field}>
            <label>{label}</label>
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
        </div>
    );

    if (!formData || Object.keys(formData).length === 0) {
        return null;
    }

    const usingManagedLlm = formData.llmModelId != null;

    return (
        <div className="config-panel">
            <div className="config-header">
                <h2>智能体配置</h2>
                <div className="config-actions">
                    <button
                        className="btn-reset"
                        onClick={handleResetClick}
                        disabled={saving}
                        title="恢复默认设置"
                    >
                        <RotateCcw size={16} />
                    </button>
                    <button
                        className="btn-save"
                        onClick={handleSaveClick}
                        disabled={!isDirty || saving}
                    >
                        <Save size={16} />
                        {saving ? '保存中...' : '保存'}
                    </button>
                </div>
            </div>
            <div className="config-form">
                <div className="form-section">
                    <h3>基础设置</h3>
                    {MODEL_TYPE_META.map(renderModelSelect)}

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
                                </div>
                            </div>
                        </>
                    )}
                </div>

                <div className="form-section">
                    <h3>核心提示词 (System Prompt)</h3>
                    <div className="form-group">
                        <textarea
                            name="systemPrompt"
                            value={formData.systemPrompt || ''}
                            onChange={handleChange}
                            rows={8}
                            placeholder="定义智能体的行为和角色..."
                        />
                    </div>
                </div>

                <div className="form-section">
                    <h3>开场白 (Greeting Message)</h3>
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
                    <h3>技能模块 (Skills)</h3>
                    <div className="skills-list">
                        <label className="skill-item">
                            <input
                                type="checkbox"
                                name="skills.webSearch"
                                checked={formData.skills?.webSearch?.enabled ?? true}
                                onChange={handleChange}
                            />
                            <div className="skill-info">
                                <span className="skill-name">联网搜索 (Web Search)</span>
                                <span className="skill-desc">允许智能体搜索实时政策和产品价格</span>
                            </div>
                        </label>

                        <label className="skill-item">
                            <input
                                type="checkbox"
                                name="skills.subsidyCalculator"
                                checked={formData.skills?.subsidyCalculator?.enabled ?? true}
                                onChange={handleChange}
                            />
                            <div className="skill-info">
                                <span className="skill-name">补贴计算器 (Subsidy Calculator)</span>
                                <span className="skill-desc">提供山东省以旧换新补贴金额的精确计算</span>
                            </div>
                        </label>

                        <label className="skill-item">
                            <input
                                type="checkbox"
                                name="skills.fileParser"
                                checked={formData.skills?.fileParser?.enabled ?? true}
                                onChange={handleChange}
                            />
                            <div className="skill-info">
                                <span className="skill-name">文件解析 (File Parser)</span>
                                <span className="skill-desc">允许解析用户上传的发票和旧机参数表</span>
                            </div>
                        </label>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ConfigPanel;
