import React, { useState } from 'react';
import { MessageSquare, Code, LayoutTemplate } from 'lucide-react';
import ChatTestTab from './ChatTestTab';
import ConfigJsonTab from './ConfigJsonTab';
import EffectPreviewTab from './EffectPreviewTab';
import './PreviewPanel.css';

const PreviewPanel = ({ config }) => {
    const [activeTab, setActiveTab] = useState('chat');

    return (
        <div className="preview-panel">
            <div className="preview-header">
                <div className="preview-tabs">
                    <button 
                        className={`preview-tab ${activeTab === 'chat' ? 'active' : ''}`}
                        onClick={() => setActiveTab('chat')}
                    >
                        <MessageSquare size={16} />
                        聊天测试
                    </button>
                    <button 
                        className={`preview-tab ${activeTab === 'effect' ? 'active' : ''}`}
                        onClick={() => setActiveTab('effect')}
                    >
                        <LayoutTemplate size={16} />
                        效果预览
                    </button>
                    <button 
                        className={`preview-tab ${activeTab === 'json' ? 'active' : ''}`}
                        onClick={() => setActiveTab('json')}
                    >
                        <Code size={16} />
                        配置 JSON
                    </button>
                </div>
            </div>

            <div className="preview-content">
                {activeTab === 'chat' && <ChatTestTab greetingMessage={config?.greetingMessage} />}
                {activeTab === 'effect' && <EffectPreviewTab config={config} />}
                {activeTab === 'json' && <ConfigJsonTab config={config} />}
            </div>
        </div>
    );
};

export default PreviewPanel;
