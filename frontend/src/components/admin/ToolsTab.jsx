import React from 'react';
import { Wrench } from 'lucide-react';
import './ToolsTab.css';

const ToolsTab = () => {
    return (
        <div className="tools-tab">
            <div className="placeholder-icon">
                <Wrench size={36} />
            </div>
            <h2>工具管理</h2>
            <p>工具配置、插件管理、外部 API 接入等功能即将上线，敬请期待。</p>
            <span className="coming-soon-badge">即将推出</span>
        </div>
    );
};

export default ToolsTab;
