import React, { useState } from 'react';
import { MessageSquare, Code, LayoutTemplate } from 'lucide-react';
import ChatTestTab from './ChatTestTab';
import ConfigJsonTab from './ConfigJsonTab';
import EffectPreviewTab from './EffectPreviewTab';
import './PreviewPanel.css';

const TABS = [
    { id: 'chat', label: '聊天测试', icon: MessageSquare, description: '直接调用当前配置，验证回答效果和接口联通性。' },
    { id: 'effect', label: '效果预览', icon: LayoutTemplate, description: '预览欢迎语、温度和技能启用后的界面观感。' },
    { id: 'json', label: '配置 JSON', icon: Code, description: '查看当前运行配置的结构化结果，便于排查字段是否生效。' }
];

const PreviewPanel = ({ config }) => {
    const [activeTab, setActiveTab] = useState('chat');
    const activeTabMeta = TABS.find((item) => item.id === activeTab) || TABS[0];

    return (
        <div className="preview-panel">
            <div className="preview-header">
                <div className="preview-tabs" role="tablist" aria-label="预览切换">
                    {TABS.map((tab) => {
                        const Icon = tab.icon;

                        return (
                            <button
                                key={tab.id}
                                className={`preview-tab ${activeTab === tab.id ? 'active' : ''}`}
                                onClick={() => setActiveTab(tab.id)}
                            >
                                <Icon size={16} />
                                {tab.label}
                            </button>
                        );
                    })}
                </div>
            </div>

            <div className="preview-content">
                <div className="preview-content-caption">{activeTabMeta.description}</div>
                <div className="preview-stage">
                    {activeTab === 'chat' && <ChatTestTab config={config} />}
                    {activeTab === 'effect' && <EffectPreviewTab config={config} />}
                    {activeTab === 'json' && <ConfigJsonTab config={config} />}
                </div>
            </div>
        </div>
    );
};

export default PreviewPanel;
