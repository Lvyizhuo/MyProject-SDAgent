import React, { useState, useEffect } from 'react';
import { X, Zap } from 'lucide-react';
import { adminApi } from '../../services/adminApi';
import { useAdminConsole } from './useAdminConsole';
import './ModelFormModal.css';

// 服务商配置模板
const PROVIDER_TEMPLATES = {
    deepseek: {
        name: 'DeepSeek',
        apiUrl: 'https://api.deepseek.com/v1',
        defaultModel: 'deepseek-chat'
    },
    siliconflow: {
        name: '硅基流动',
        apiUrl: 'https://api.siliconflow.cn/v1',
        defaultModel: 'Qwen/Qwen2.5-7B-Instruct'
    },
    dashscope: {
        name: '阿里云百炼',
        apiUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        defaultModel: 'qwen-plus'
    },
    zhipuai: {
        name: '智谱AI',
        apiUrl: 'https://open.bigmodel.cn/api/paas/v4',
        defaultModel: 'glm-4'
    },
    moonshot: {
        name: 'kimi',
        apiUrl: 'https://api.moonshot.cn/v1',
        defaultModel: 'moonshot-v1-8k'
    },
    modelscope: {
        name: '魔搭社区',
        apiUrl: 'https://api.modelscope.cn/v1',
        defaultModel: 'qwen/Qwen2-7B-Instruct'
    },
    volcano: {
        name: '火山引擎',
        apiUrl: 'https://ark.cn-beijing.volces.com/api/v3',
        defaultModel: 'doubao-seed-code-preview-251028'
    }
};

const TYPE_LABELS = {
    LLM: '大语言模型',
    VISION: '视觉模型',
    AUDIO: '语音模型',
    EMBEDDING: '嵌入模型'
};

const ModelFormModal = ({ model, type, modelTypes = [], onSave, onClose }) => {
    const { notify } = useAdminConsole();
    const [formData, setFormData] = useState({
        name: '',
        type: type,
        provider: 'deepseek',
        apiUrl: PROVIDER_TEMPLATES.deepseek.apiUrl,
        apiKey: '',
        modelName: PROVIDER_TEMPLATES.deepseek.defaultModel,
        temperature: 0.7,
        maxTokens: 4096,
        topP: 0.9,
        isDefault: false,
        isEnabled: true
    });
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);

    useEffect(() => {
        if (model) {
            setFormData({
                name: model.modelName || model.name || '',
                type: model.type || type,
                provider: model.provider || 'deepseek',
                apiUrl: model.apiUrl || '',
                apiKey: '', // 不回显已保存的 API Key
                modelName: model.modelName || '',
                temperature: model.temperature || 0.7,
                maxTokens: model.maxTokens || 4096,
                topP: model.topP || 0.9,
                isDefault: model.isDefault || false,
                isEnabled: model.isEnabled !== false
            });
        } else {
            // 新增时设置默认值
            setFormData(prev => ({
                ...prev,
                type: type
            }));
        }
    }, [model, type]);

    const handleProviderChange = (provider) => {
        const template = PROVIDER_TEMPLATES[provider];
        if (template) {
            setFormData(prev => ({
                ...prev,
                provider,
                apiUrl: template.apiUrl,
                modelName: template.defaultModel,
                name: template.defaultModel
            }));
        } else {
            setFormData(prev => ({
                ...prev,
                provider,
                apiUrl: '',
                modelName: '',
                name: ''
            }));
        }
    };

    const handleChange = (e) => {
        const { name, value, type: inputType, checked } = e.target;
        const parsedValue = inputType === 'checkbox' ? checked :
                inputType === 'number' ? parseFloat(value) : value;
        setFormData(prev => ({
            ...prev,
            [name]: parsedValue,
            ...(name === 'modelName' ? { name: typeof parsedValue === 'string' ? parsedValue : prev.name } : {})
        }));
    };

    const currentType = formData.type || type;

    const handleTest = async () => {
        if (!model && !formData.apiKey) {
            notify({ text: '请先填写 API Key', type: 'warning', source: '管理员-模型管理' });
            return;
        }
        setTesting(true);
        let tempModelId = null;
        try {
            let targetId = model?.id;

            if (!targetId) {
                const tempModel = await adminApi.createModel({
                    ...formData,
                    name: formData.modelName,
                    isDefault: false
                });
                targetId = tempModel.id;
                tempModelId = tempModel.id;
            }

            const result = await adminApi.testModelConnection(targetId);

            if (result.success) {
                notify({ text: `连接测试成功，耗时 ${result.latencyMs ?? '-'} ms`, type: 'success', source: '管理员-模型管理' });
            } else {
                notify({ text: '测试失败：' + (result.message || '未知错误'), type: 'error', source: '管理员-模型管理' });
            }
        } catch (err) {
            notify({ text: '测试失败：' + (err.message || '未知错误'), type: 'error', source: '管理员-模型管理' });
        } finally {
            if (tempModelId) {
                try {
                    await adminApi.deleteModel(tempModelId);
                } catch (cleanupErr) {
                    console.error('清理临时测试模型失败:', cleanupErr);
                }
            }
            setTesting(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // 验证
        if (!formData.apiUrl.trim()) {
            notify({ text: '请输入 API 地址', type: 'warning', source: '管理员-模型管理' });
            return;
        }
        if (!model && !formData.apiKey.trim()) {
            notify({ text: '请输入 API Key', type: 'warning', source: '管理员-模型管理' });
            return;
        }
        if (!formData.modelName.trim()) {
            notify({ text: '请输入模型名称', type: 'warning', source: '管理员-模型管理' });
            return;
        }

        setSaving(true);

        try {
            await onSave(formData);
        } catch (err) {
            notify({ text: err.message || '保存失败', type: 'error', source: '管理员-模型管理' });
        } finally {
            setSaving(false);
        }
    };

    const showAdvanced = currentType === 'LLM';

    return (
        <div className="model-form-modal-overlay" onClick={onClose}>
            <div className="model-form-modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h3>{model ? '编辑模型' : '新增模型'}</h3>
                    <button className="close-btn" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="model-type-chip">
                            <span>{model ? '模型类型' : '保存后将归类到'}</span>
                            <strong>{TYPE_LABELS[currentType] || currentType}</strong>
                        </div>

                        <div className="form-group">
                            <label>模型类型 *</label>
                            <select
                                name="type"
                                value={currentType}
                                onChange={handleChange}
                            >
                                {(modelTypes.length ? modelTypes : Object.keys(TYPE_LABELS).map(key => ({ id: key, label: TYPE_LABELS[key] }))).map((item) => (
                                    <option key={item.id} value={item.id}>{item.label}</option>
                                ))}
                            </select>
                            <p className="field-helper">
                                新增模型时可直接选择归属类型；如果与你当前查看的分类不同，保存后会自动提示并切换到对应分类。
                            </p>
                        </div>

                        <div className="form-group">
                            <label>服务商 *</label>
                            <select
                                name="provider"
                                value={formData.provider}
                                onChange={(e) => handleProviderChange(e.target.value)}
                            >
                                {Object.entries(PROVIDER_TEMPLATES).map(([key, tmpl]) => (
                                    <option key={key} value={key}>{tmpl.name}</option>
                                ))}
                                <option value="custom">自定义</option>
                            </select>
                            <p className="field-helper">
                                选择内置服务商会自动填充推荐 API 地址和默认模型名。
                            </p>
                        </div>

                        <div className="form-group">
                            <label>API 地址 *</label>
                            <input
                                type="text"
                                name="apiUrl"
                                value={formData.apiUrl}
                                onChange={handleChange}
                                placeholder="https://api.example.com/v1"
                            />
                            <p className="field-helper">
                                当前按 OpenAI 兼容协议做连通性测试，会访问 <code>/models</code> 接口。
                            </p>
                        </div>

                        <div className="form-group">
                            <label>API Key *</label>
                            <input
                                type="password"
                                name="apiKey"
                                value={formData.apiKey}
                                onChange={handleChange}
                                placeholder={model ? '留空保持原值' : '请输入 API Key'}
                            />
                            {model && (
                                <p className="field-helper">编辑已有模型时可留空，后端会保留原来的密钥。</p>
                            )}
                        </div>

                        <div className="form-group">
                            <label>调用模型名 *</label>
                            <input
                                type="text"
                                name="modelName"
                                value={formData.modelName}
                                onChange={handleChange}
                                placeholder="如：deepseek-chat"
                            />
                            <p className="field-helper">
                                列表显示名会与这里保持一致，不需要再单独填写一个名字。火山引擎填写 Doubao-Seed-Code 时会自动映射到当前可用的代码模型版本。
                            </p>
                        </div>

                        {showAdvanced && (
                            <>
                                <div className="form-section-title">高级参数（可选）</div>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label>温度</label>
                                        <input
                                            type="number"
                                            name="temperature"
                                            value={formData.temperature}
                                            onChange={handleChange}
                                            min="0"
                                            max="2"
                                            step="0.1"
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label>最大 Token</label>
                                        <input
                                            type="number"
                                            name="maxTokens"
                                            value={formData.maxTokens}
                                            onChange={handleChange}
                                            min="1"
                                            max="100000"
                                        />
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label>TopP</label>
                                    <input
                                        type="number"
                                        name="topP"
                                        value={formData.topP}
                                        onChange={handleChange}
                                        min="0"
                                        max="1"
                                        step="0.05"
                                    />
                                </div>
                            </>
                        )}

                        {!showAdvanced && (
                            <div className="field-helper-block">
                                当前类型默认只使用基础连接参数。后续如果该模型链路需要额外参数，可以继续扩展表单。
                            </div>
                        )}

                        <div className="form-check-group">
                            <label className="form-check">
                                <input
                                    type="checkbox"
                                    name="isDefault"
                                    checked={formData.isDefault}
                                    onChange={handleChange}
                                />
                                <span>设为默认模型</span>
                            </label>

                            <label className="form-check">
                                <input
                                    type="checkbox"
                                    name="isEnabled"
                                    checked={formData.isEnabled}
                                    onChange={handleChange}
                                />
                                <span>启用</span>
                            </label>
                        </div>
                    </div>

                    <div className="modal-footer">
                        <button type="button" className="btn-secondary" onClick={onClose}>
                            取消
                        </button>
                        <button
                            type="button"
                            className="btn-test"
                            onClick={handleTest}
                            disabled={testing}
                        >
                            <Zap size={16} />
                            {testing ? '测试中...' : '测试连接'}
                        </button>
                        <button type="submit" className="btn-primary" disabled={saving}>
                            {saving ? '保存中...' : '保存'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default ModelFormModal;
