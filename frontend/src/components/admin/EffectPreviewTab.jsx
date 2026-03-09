import React from 'react';
import { Bot } from 'lucide-react';
import MessageBubble from '../MessageBubble';
import InputArea from '../InputArea';
import '../ChatWindow.css'; // Reuse chat window styles to keep it consistent
import './EffectPreviewTab.css';

const EffectPreviewTab = ({ config }) => {
    const skillEntries = Object.entries(config?.skills || {});

    return (
        <div className="effect-preview-tab">
            <div className="chat-window mock-chat-window">
                <header className="chat-panel-header">
                    <div className="chat-panel-bot-icon">
                        <Bot size={17} />
                    </div>
                    <div className="chat-panel-meta">
                        <h2>AI政策助手</h2>
                        <p>
                            <span className="online-dot" />
                            在线服务中
                        </p>
                    </div>
                </header>
                
                <div className="messages-list mock-messages-list">
                    <MessageBubble 
                        id="welcome"
                        role="assistant"
                        content={config?.greetingMessage || '您好！我是山东省以旧换新政策咨询智能助手。'}
                    />
                    
                    <div className="mock-system-info">
                        <div className="info-divider"><span>当前配置生效预览</span></div>
                        
                        <div className="info-card">
                            <h4>系统提示词 (System Prompt)</h4>
                            <p>{config?.systemPrompt || '未设置'}</p>
                        </div>
                        
                        <div className="info-card">
                            <h4>温度 (Temperature): {config?.temperature ?? 0}</h4>
                            <div className="stat-bar-container">
                                <div className="stat-bar" style={{ width: `${(config?.temperature ?? 0) * 100}%` }}></div>
                            </div>
                        </div>
                        
                        <div className="info-card">
                            <h4>启用的技能</h4>
                            <div className="tools-list">
                                {skillEntries.map(([key, value]) => (
                                    <div key={key} className={`tool-badge ${value?.enabled ? 'enabled' : 'disabled'}`}>
                                        {key}: {value?.enabled ? '启用' : '禁用'}
                                    </div>
                                ))}
                                {skillEntries.length === 0 && <div className="tool-badge disabled">暂无技能配置</div>}
                            </div>
                        </div>
                    </div>
                </div>

                <div className="input-wrapper disabled-wrapper">
                    <InputArea onSend={() => {}} isGenerating={false} disabled={true} />
                </div>
            </div>
        </div>
    );
};

export default EffectPreviewTab;