import React, { useState, useEffect } from 'react';
import './ConfigPanel.css';
import { Save, RotateCcw, AlertTriangle } from 'lucide-react';

const ConfigPanel = ({ config, onSave, onReset }) => {
    const [formData, setFormData] = useState({});
    const [isDirty, setIsDirty] = useState(false);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });

    useEffect(() => {
        if (config) {
            setFormData(JSON.parse(JSON.stringify(config)));
            setIsDirty(false);
        }
    }, [config]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        
        if (name.startsWith('skills.')) {
            const skillName = name.split('.')[1];
            setFormData(prev => ({
                ...prev,
                skills: {
                    ...prev.skills,
                    [skillName]: {
                        ...prev.skills[skillName],
                        enabled: checked
                    }
                }
            }));
        } else {
            setFormData(prev => ({
                ...prev,
                [name]: type === 'checkbox' ? checked : 
                        type === 'number' ? parseFloat(value) : value
            }));
        }
        setIsDirty(true);
    };

    const handleSaveClick = async () => {
        setSaving(true);
        setMessage({ text: '', type: '' });
        
        // Basic validation
        if (!formData.systemPrompt?.trim()) {
            setMessage({ text: '系统提示词不能为空', type: 'error' });
            setSaving(false);
            return;
        }
        if (formData.temperature < 0 || formData.temperature > 1) {
            setMessage({ text: '温度必须在 0 到 1 之间', type: 'error' });
            setSaving(false);
            return;
        }

        const result = await onSave(formData);
        
        if (result.success) {
            setMessage({ text: '配置已成功保存并生效', type: 'success' });
            setIsDirty(false);
            setTimeout(() => setMessage({ text: '', type: '' }), 3000);
        } else {
            setMessage({ text: result.message || '保存失败', type: 'error' });
        }
        setSaving(false);
    };

    const handleResetClick = async () => {
        if (window.confirm('确定要重置所有配置为系统默认值吗？此操作不可撤销。')) {
            setSaving(true);
            const result = await onReset();
            if (result.success) {
                setMessage({ text: '已重置为默认配置', type: 'success' });
                setTimeout(() => setMessage({ text: '', type: '' }), 3000);
            } else {
                setMessage({ text: result.message || '重置失败', type: 'error' });
            }
            setSaving(false);
        }
    };

    if (!formData || Object.keys(formData).length === 0) return null;

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

            {message.text && (
                <div className={`status-message ${message.type}`}>
                    {message.type === 'error' && <AlertTriangle size={16} />}
                    {message.text}
                </div>
            )}

            <div className="config-form">
                <div className="form-section">
                    <h3>基础设置</h3>
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
                                value={formData.temperature || 0} 
                                onChange={handleChange}
                            />
                        </div>
                    </div>
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