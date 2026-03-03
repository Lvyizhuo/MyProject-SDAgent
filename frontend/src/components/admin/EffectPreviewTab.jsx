import React from 'react';
import './EffectPreviewTab.css';

const EffectPreviewTab = ({ config }) => {
    return (
        <div className="effect-preview-tab">
            <div className="preview-card">
                <h3>当前角色设定 (System Prompt)</h3>
                <div className="preview-content-box">
                    <pre>{config?.systemPrompt || '未设置'}</pre>
                </div>
            </div>

            <div className="preview-card">
                <h3>欢迎语</h3>
                <div className="preview-content-box">
                    <p>{config?.greetingMessage || '未设置'}</p>
                </div>
            </div>

            <div className="preview-card">
                <h3>温度 (Temperature)</h3>
                <div className="preview-content-box">
                    <div className="stat-bar-container">
                        <div className="stat-bar" style={{ width: `${(config?.temperature || 0) * 100}%` }}></div>
                    </div>
                    <span>{config?.temperature || 0} - {config?.temperature > 0.5 ? '较发散/创造性' : '较严谨/确定性'}</span>
                </div>
            </div>

            <div className="preview-card">
                <h3>启用的工具</h3>
                <div className="preview-content-box tools-list">
                    {Object.entries(config?.tools || {}).map(([key, enabled]) => (
                        <div key={key} className={`tool-badge ${enabled ? 'enabled' : 'disabled'}`}>
                            {key}: {enabled ? '启用' : '禁用'}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default EffectPreviewTab;